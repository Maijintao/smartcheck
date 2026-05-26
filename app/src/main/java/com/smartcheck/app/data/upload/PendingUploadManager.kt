package com.smartcheck.app.data.upload

import com.smartcheck.app.data.db.RecordDao
import com.smartcheck.app.domain.model.toDomain
import com.smartcheck.app.data.repository.SettingsRepository
import com.smartcheck.app.utils.DeviceInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
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
            val pendingRecords = withContext(Dispatchers.IO) {
                recordDao.getUnuploadedRecords()
            }

            if (pendingRecords.isEmpty()) {
                Timber.d("No pending records to upload")
                return
            }

            Timber.d("Found ${pendingRecords.size} pending records to upload")

            val deviceSn = settingsRepository.getDeviceSn()
                .ifEmpty { DeviceInfo.getDeviceId(context) }

            for (entity in pendingRecords) {
                try {
                    val record = entity.toDomain()
                    val result = cloudRecordService.uploadCheckRecord(record, deviceSn)

                    result.onSuccess {
                        withContext(Dispatchers.IO) {
                            recordDao.markAsUploaded(entity.id)
                        }
                        Timber.d("Record uploaded successfully: id=${entity.id}")
                    }.onFailure { e ->
                        Timber.w(e, "Upload failed for record id=${entity.id}, will retry later")
                        return
                    }
                } catch (e: Exception) {
                    Timber.w(e, "Upload exception for record id=${entity.id}, will retry later")
                    return
                }
            }

            Timber.d("Pending upload queue processed")
        } finally {
            isProcessing.set(false)
        }
    }
}
