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
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
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
        private const val INITIAL_RETRY_DELAY_MS = 60_000L
        private const val MAX_RETRY_DELAY_MS = 60 * 60_000L
    }

    private val isProcessing = AtomicBoolean(false)

    fun enqueue(recordId: Long) {
        appScope.launch(Dispatchers.IO) {
            processPendingUploads()
        }
    }

    suspend fun processPendingUploads() {
        if (!isProcessing.compareAndSet(false, true)) {
            Timber.d("Upload queue already processing, skipping")
            return
        }

        try {
            if (settingsRepository.platformUrl.value.isBlank() || settingsRepository.apiKey.value.isBlank()) {
                Timber.d("Platform not configured, keeping pending uploads queued")
                return
            }

            val pendingRecords = withContext(Dispatchers.IO) {
                recordDao.getPendingUploads(System.currentTimeMillis())
            }

            if (pendingRecords.isEmpty()) {
                Timber.d("No pending records to upload")
                return
            }

            Timber.d("Found ${pendingRecords.size} pending records to upload")

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
                        Timber.d("Record uploaded successfully: id=${entity.id}")
                    }.onFailure { e ->
                        if (e is RetryableUploadException) {
                            markRetryableFailure(entity, e)
                            Timber.w(e, "Temporary upload failure for record id=${entity.id}; retry scheduled")
                            return
                        }
                        markPermanentFailure(entity.id, e)
                    }
                } catch (e: java.util.concurrent.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    markPermanentFailure(pendingEntity.id, e)
                }
            }

            Timber.d("Pending upload queue processed")
        } finally {
            isProcessing.set(false)
        }
    }

    private suspend fun markRetryableFailure(entity: RecordEntity, error: Throwable) {
        val retryCount = entity.uploadRetryCount + 1
        val shift = (retryCount - 1).coerceAtMost(6)
        val retryDelay = (INITIAL_RETRY_DELAY_MS * (1L shl shift)).coerceAtMost(MAX_RETRY_DELAY_MS)
        withContext(Dispatchers.IO) {
            recordDao.markRetryableFailure(
                recordId = entity.id,
                retryCount = retryCount,
                nextAttemptAt = System.currentTimeMillis() + retryDelay,
                error = error.message.orEmpty()
            )
        }
    }

    private suspend fun markPermanentFailure(recordId: Long, error: Throwable) {
        withContext(Dispatchers.IO) {
            recordDao.markPermanentFailure(recordId, error.message.orEmpty())
        }
        Timber.e(error, "Permanent upload failure for record id=$recordId; automatic retry stopped")
    }
}
