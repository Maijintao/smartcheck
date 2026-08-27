package com.smartcheck.app.data.upload

import com.smartcheck.app.data.db.RecordDao
import com.smartcheck.app.data.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 晨检记录重试调度器
 *
 * 周期性扫描本地未上传（isUploaded = 0）的晨检记录并触发重发，
 * 作为「平台服务器不可达」断联场景的兜底机制：
 * 设备有网但平台宕机/超时/5xx 时，NetworkMonitor 的网络恢复回调不会触发，
 * 只能依靠周期轮询在平台恢复后自动补发。
 *
 * 设备断网场景仍由 NetworkMonitor.onAvailable 覆盖，二者互为补充。
 */
@Singleton
class RecordUploadRetryScheduler @Inject constructor(
    private val recordDao: RecordDao,
    private val pendingUploadManager: PendingUploadManager,
    private val settingsRepository: SettingsRepository,
    private val appScope: CoroutineScope
) {
    companion object {
        private const val TAG = "RecordUploadRetry"
        private const val RETRY_INTERVAL_MS = 60_000L  // 每 60 秒兜底扫描一次
    }

    private val isRunning = AtomicBoolean(false)
    private var retryJob: Job? = null

    /**
     * 启动周期重试定时器（幂等，可重复调用）
     */
    fun start() {
        if (!isRunning.compareAndSet(false, true)) {
            Timber.d("$TAG: already running, skip start")
            return
        }
        Timber.d("$TAG: started, interval=${RETRY_INTERVAL_MS}ms")
        retryJob = appScope.launch {
            while (isActive && isRunning.get()) {
                delay(RETRY_INTERVAL_MS)
                if (!isActive) break
                try {
                    tick()
                } catch (e: java.util.concurrent.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.w(e, "$TAG: periodic retry failed")
                }
            }
        }
    }

    /**
     * 停止周期重试定时器
     */
    fun stop() {
        if (isRunning.compareAndSet(true, false)) {
            retryJob?.cancel()
            retryJob = null
            Timber.d("$TAG: stopped")
        }
    }

    /**
     * 单轮重试：仅当平台已配置且存在未上传记录时才触发上传队列，避免空转
     */
    private suspend fun tick() {
        val platformUrl = settingsRepository.platformUrl.value
        val apiKey = settingsRepository.apiKey.value
        if (platformUrl.isBlank() || apiKey.isBlank()) {
            Timber.d("$TAG: platform not configured, skip")
            return
        }

        val hasPending = withContext(Dispatchers.IO) {
            recordDao.hasPendingUploads(System.currentTimeMillis())
        }
        if (!hasPending) {
            Timber.d("$TAG: no pending records, skip")
            return
        }

        Timber.d("$TAG: pending records found, triggering upload")
        pendingUploadManager.enqueue(0L)
    }
}
