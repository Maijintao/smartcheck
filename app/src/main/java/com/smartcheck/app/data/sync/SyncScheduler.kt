package com.smartcheck.app.data.sync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 同步调度器 — 定时触发同步
 */
@Singleton
class SyncScheduler @Inject constructor(
    private val syncEngine: EmployeeSyncEngine
) {
    companion object {
        private const val TAG = "SyncScheduler"
        private const val DEFAULT_INTERVAL_MS = 30_000L  // 30 秒
    }

    private var periodicJob: Job? = null

    /**
     * 启动定时同步
     * @param scope 协程作用域（通常是 App 级别的 CoroutineScope）
     * @param intervalMs 同步间隔，默认 30 秒
     */
    fun startPeriodicSync(scope: CoroutineScope, intervalMs: Long = DEFAULT_INTERVAL_MS) {
        stopPeriodicSync()
        periodicJob = scope.launch {
            Timber.d("$TAG: 启动定时同步, 间隔=${intervalMs}ms")
            while (isActive) {
                delay(intervalMs)
                try {
                    syncEngine.triggerSync()
                } catch (e: Exception) {
                    Timber.w(e, "$TAG: 定时同步失败")
                }
            }
        }
    }

    /**
     * 停止定时同步
     */
    fun stopPeriodicSync() {
        periodicJob?.cancel()
        periodicJob = null
    }
}
