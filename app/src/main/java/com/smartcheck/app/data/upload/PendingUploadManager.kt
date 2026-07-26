package com.smartcheck.app.data.upload

import com.smartcheck.app.data.db.RecordDao
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

            // 优先使用用户配置的设备ID，其次 MAC 地址，最后回退到自动生成的设备ID
            val deviceId = settingsRepository.deviceId.value.takeIf { it.isNotBlank() }
                ?: DeviceAuth.getCurrentDeviceMac()
                ?: DeviceInfo.getDeviceId(context)

            for (entity in pendingRecords) {
                try {
                    val record = entity.toDomain()
                    val result = cloudRecordService.uploadToPlatform(record, deviceId)

                    result.onSuccess {
                        withContext(Dispatchers.IO) {
                            recordDao.markAsUploaded(entity.id)
                        }
                        Timber.d("Record uploaded successfully: id=${entity.id}")
                    }.onFailure { e ->
                        // 平台整体不可达（连接/超时/DNS）时，后续记录大概率也失败，提前结束本轮留待下轮重试
                        if (isPlatformUnreachable(e)) {
                            Timber.w(e, "Platform unreachable at record id=${entity.id}, abort this round and retry later")
                            return
                        }
                        // 单条业务错误（如 4xx / 平台返回 code!=200）：跳过该条，继续尝试后续记录
                        Timber.w(e, "Upload failed for record id=${entity.id}, skip and continue")
                    }
                } catch (e: java.util.concurrent.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (isPlatformUnreachable(e)) {
                        Timber.w(e, "Platform unreachable (exception) at record id=${entity.id}, abort this round and retry later")
                        return
                    }
                    Timber.w(e, "Upload exception for record id=${entity.id}, skip and continue")
                }
            }

            Timber.d("Pending upload queue processed")
        } finally {
            isProcessing.set(false)
        }
    }

    /**
     * 判断异常是否属于「平台整体不可达」：连接被拒绝、DNS 解析失败、网络/请求超时、无路由等。
     * 连接类异常与平台 5xx 意味着平台整体断联，后续记录大概率同样失败，应提前结束本轮、留待下轮重试。
     * HTTP 4xx / 平台业务校验错误按单条问题跳过，避免一条坏数据堵住整个队列。
     */
    private fun isPlatformUnreachable(e: Throwable): Boolean {
        var cause: Throwable? = e
        while (cause != null) {
            when (cause) {
                is java.net.ConnectException,
                is java.net.UnknownHostException,
                is java.net.NoRouteToHostException,
                is java.net.SocketTimeoutException,
                is java.net.SocketException,
                is io.ktor.client.plugins.HttpRequestTimeoutException,
                is PlatformUnavailableException -> return true
            }
            cause = cause.cause
        }
        return false
    }
}
