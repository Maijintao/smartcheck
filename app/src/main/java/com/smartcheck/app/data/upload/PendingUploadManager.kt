package com.smartcheck.app.data.upload

import com.smartcheck.app.data.db.RecordDao
import com.smartcheck.app.data.db.RecordEntity
import com.smartcheck.app.domain.model.toDomain
import com.smartcheck.app.data.repository.SettingsRepository
import com.smartcheck.app.utils.DeviceAuth
import com.smartcheck.app.utils.DeviceInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PendingUploadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val recordDao: RecordDao,
    private val cloudRecordService: CloudRecordService,
    private val settingsRepository: SettingsRepository,
    private val appScope: CoroutineScope
) {
    companion object {
        private const val MAX_RETRY_DELAY_MS = 5 * 60_000L
        private val RETRY_DELAYS_MS = longArrayOf(5_000L, 15_000L, 30_000L, 60_000L)
    }

    private val processingMutex = Mutex()

    fun enqueue(recordId: Long) {
        appScope.launch(Dispatchers.IO) {
            processPendingUploads()
        }
    }

    suspend fun processPendingUploads() {
        if (!processingMutex.tryLock()) {
            Timber.d("Upload queue already processing, skipping")
            return
        }

        try {
            processPendingUploadsLocked(continueAfterRetryableFailure = false)
        } finally {
            processingMutex.unlock()
        }
    }

    suspend fun uploadAllUnuploaded(): ManualUploadResult = processingMutex.withLock {
        val requestedCount = withContext(Dispatchers.IO) {
            recordDao.countUnuploadedRecords()
        }
        if (requestedCount == 0) {
            return@withLock ManualUploadResult.NothingToUpload
        }
        if (settingsRepository.platformUrl.value.isBlank() || settingsRepository.apiKey.value.isBlank()) {
            return@withLock ManualUploadResult.ConfigurationMissing(requestedCount)
        }

        withContext(Dispatchers.IO) {
            recordDao.prepareUnuploadedForManualRetry()
        }
        val uploadedCount = processPendingUploadsLocked(continueAfterRetryableFailure = true)

        val remainingCount = withContext(Dispatchers.IO) {
            recordDao.countUnuploadedRecords()
        }
        ManualUploadResult.Finished(
            requestedCount = requestedCount,
            uploadedCount = uploadedCount.coerceAtMost(requestedCount),
            remainingCount = remainingCount,
        )
    }

    private suspend fun processPendingUploadsLocked(continueAfterRetryableFailure: Boolean): Int {
        if (settingsRepository.platformUrl.value.isBlank() || settingsRepository.apiKey.value.isBlank()) {
            Timber.d("Platform not configured, keeping pending uploads queued")
            return 0
        }

        val pendingRecords = withContext(Dispatchers.IO) {
            recordDao.getPendingUploads(System.currentTimeMillis())
        }

        if (pendingRecords.isEmpty()) {
            Timber.d("No pending records to upload")
            return 0
        }

        Timber.d("Found ${pendingRecords.size} pending records to upload")
        var uploadedCount = 0

        // 优先使用用户配置的设备ID，其次 MAC 地址，最后回退到自动生成的设备ID
        val currentDeviceId = settingsRepository.deviceId.value.takeIf { it.isNotBlank() }
            ?: DeviceAuth.getCurrentDeviceMac()
            ?: DeviceInfo.getDeviceId(context)

        for (pendingEntity in pendingRecords) {
            try {
                val recordUuid = pendingEntity.recordUuid.ifBlank { UUID.randomUUID().toString() }
                val uploadDeviceId = pendingEntity.uploadDeviceId.ifBlank { currentDeviceId }
                if (pendingEntity.uploadDeviceId.isBlank()) {
                    withContext(Dispatchers.IO) {
                        recordDao.claimUploadIdentity(pendingEntity.id, recordUuid, uploadDeviceId)
                    }
                }

                // 认领上传身份后重新读取，确保首次发送与之后重试使用完全相同的业务内容。
                val entity = withContext(Dispatchers.IO) {
                    recordDao.getRecordById(pendingEntity.id)
                } ?: continue
                if (entity.uploadDeviceId.isBlank()) continue

                val record = entity.toDomain()
                val result = cloudRecordService.uploadToPlatform(record, entity.uploadDeviceId)

                result.onSuccess {
                    withContext(Dispatchers.IO) {
                        recordDao.markAsUploaded(entity.id)
                    }
                    uploadedCount++
                    Timber.d("Record uploaded successfully: id=${entity.id}")
                }.onFailure { e ->
                    if (e is RetryableUploadException) {
                        markRetryableFailure(entity, e)
                        Timber.w(e, "Temporary upload failure for record id=${entity.id}; retry scheduled")
                        if (!continueAfterRetryableFailure) {
                            return uploadedCount
                        }
                    } else {
                        markPermanentFailure(entity.id, e)
                    }
                }
            } catch (e: java.util.concurrent.CancellationException) {
                throw e
            } catch (e: Exception) {
                markPermanentFailure(pendingEntity.id, e)
            }
        }

        Timber.d("Pending upload queue processed")
        return uploadedCount
    }

    private suspend fun markRetryableFailure(entity: RecordEntity, error: Throwable) {
        val retryCount = entity.uploadRetryCount + 1
        val retryDelay = (error as? RetryableUploadException)
            ?.retryAfterMillis
            ?.coerceIn(0L, MAX_RETRY_DELAY_MS)
            ?: retryDelayMillis(retryCount)
        withContext(Dispatchers.IO) {
            recordDao.markRetryableFailure(
                recordId = entity.id,
                retryCount = retryCount,
                nextAttemptAt = System.currentTimeMillis() + retryDelay,
                error = error.message.orEmpty()
            )
        }
    }

    internal fun retryDelayMillis(retryCount: Int): Long {
        if (retryCount <= RETRY_DELAYS_MS.size) {
            return RETRY_DELAYS_MS[(retryCount - 1).coerceAtLeast(0)]
        }
        val extraShift = (retryCount - RETRY_DELAYS_MS.size).coerceAtMost(3)
        return (RETRY_DELAYS_MS.last() * (1L shl extraShift)).coerceAtMost(MAX_RETRY_DELAY_MS)
    }

    private suspend fun markPermanentFailure(recordId: Long, error: Throwable) {
        withContext(Dispatchers.IO) {
            recordDao.markPermanentFailure(recordId, error.message.orEmpty())
        }
        Timber.e(error, "Permanent upload failure for record id=$recordId; automatic retry stopped")
    }
}

sealed interface ManualUploadResult {
    data object NothingToUpload : ManualUploadResult
    data class ConfigurationMissing(val pendingCount: Int) : ManualUploadResult
    data class Finished(
        val requestedCount: Int,
        val uploadedCount: Int,
        val remainingCount: Int,
    ) : ManualUploadResult
}
