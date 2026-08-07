package com.smartcheck.app.data.cleanup

import android.content.Context
import com.smartcheck.app.data.db.RecordDao
import com.smartcheck.app.utils.FileUtil
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 晨检记录自动清理调度器
 *
 * 每 7 天执行一次清理，删除 7 天前已上传的晨检记录（数据库 + 图片文件）。
 * 未上传的记录不会被删除，避免数据丢失。
 */
@Singleton
class RecordCleanupScheduler @Inject constructor(
    private val recordDao: RecordDao,
    @ApplicationContext private val context: Context,
    private val appScope: CoroutineScope
) {
    companion object {
        private const val TAG = "RecordCleanup"
        private const val CLEANUP_INTERVAL_MS = 7 * 24 * 60 * 60 * 1000L  // 每 7 天清理一次
        private const val RETENTION_DAYS = 7
    }

    private val isRunning = AtomicBoolean(false)
    private var cleanupJob: Job? = null

    /**
     * 启动定时清理（幂等，可重复调用）
     */
    fun start() {
        if (!isRunning.compareAndSet(false, true)) {
            Timber.d("$TAG: already running, skip start")
            return
        }
        Timber.d("$TAG: started, interval=7d, retention=${RETENTION_DAYS}d")
        cleanupJob = appScope.launch {
            while (isActive && isRunning.get()) {
                delay(CLEANUP_INTERVAL_MS)
                if (!isActive) break
                try {
                    performCleanup()
                } catch (e: java.util.concurrent.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.w(e, "$TAG: periodic cleanup failed")
                }
            }
        }
    }

    /**
     * 停止定时清理
     */
    fun stop() {
        if (isRunning.compareAndSet(true, false)) {
            cleanupJob?.cancel()
            cleanupJob = null
            Timber.d("$TAG: stopped")
        }
    }

    /**
     * 执行一次清理：删除 7 天前已上传的数据库记录和对应图片文件
     */
    private suspend fun performCleanup() = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(RETENTION_DAYS.toLong())

        // 1. 删除已上传的旧数据库记录
        recordDao.deleteOldUploadedRecords(cutoff)
        Timber.d("$TAG: deleted uploaded records older than ${RETENTION_DAYS} days (cutoff=$cutoff)")

        // 2. 清理旧图片文件
        FileUtil.clearOldRecords(context, RETENTION_DAYS)
        Timber.d("$TAG: cleaned image files older than ${RETENTION_DAYS} days")
    }
}
