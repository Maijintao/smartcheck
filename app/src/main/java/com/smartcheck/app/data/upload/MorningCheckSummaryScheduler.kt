package com.smartcheck.app.data.upload

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MorningCheckSummaryScheduler @Inject constructor(
    private val manager: MorningCheckSummaryManager,
    private val appScope: CoroutineScope
) {
    private val isRunning = AtomicBoolean(false)
    private var job: Job? = null

    fun start() {
        if (!isRunning.compareAndSet(false, true)) return
        job = appScope.launch {
            manager.process()
            while (isActive && isRunning.get()) {
                delay(CHECK_INTERVAL_MS)
                manager.process()
            }
        }
        Timber.i("Morning check summary scheduler started")
    }

    fun stop() {
        if (isRunning.compareAndSet(true, false)) {
            job?.cancel()
            job = null
        }
    }

    companion object {
        private const val CHECK_INTERVAL_MS = 5 * 60_000L
    }
}
