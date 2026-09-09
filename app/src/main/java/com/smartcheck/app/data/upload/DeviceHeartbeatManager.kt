package com.smartcheck.app.data.upload

import com.smartcheck.app.api.model.DeviceHeartbeatResponse
import com.smartcheck.app.data.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 设备心跳管理器
 *
 * 负责定期向平台发送心跳保活请求，并在网络恢复时立即补发。
 * 同时提供手动测试连接能力。
 * 心跳间隔可通过 SettingsRepository 动态配置。
 */
@Singleton
class DeviceHeartbeatManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val httpClient: HttpClient,
    private val settingsRepository: SettingsRepository,
    private val pendingUploadManager: PendingUploadManager,
    private val appScope: CoroutineScope
) {
    companion object {
        private const val DEFAULT_HEARTBEAT_INTERVAL_SEC = 30 // 默认心跳间隔 30 秒
        private const val REQUEST_TIMEOUT_MS = 20_000L // 整体请求超时 20 秒
    }

    private val isRunning = AtomicBoolean(false)
    private var heartbeatJob: Job? = null
    private var lastHeartbeatAt: Long = 0L
    private var lastHeartbeatSuccess: Boolean = false

    /**
     * 启动心跳定时器
     */
    fun start() {
        if (isRunning.compareAndSet(false, true)) {
            Timber.d("DeviceHeartbeatManager started")
            // 启动时立即发送一次心跳（验证连通性）
            appScope.launch {
                delay(5000) // 延迟 5 秒，等待网络就绪
                sendHeartbeat()
            }
            // 启动定时心跳（读取当前配置间隔）
            startPeriodicHeartbeat()
            // 监听间隔配置变化，自动重启定时器
            appScope.launch {
                settingsRepository.heartbeatInterval.collectLatest { intervalSec ->
                    if (isRunning.get()) {
                        Timber.d("[Heartbeat] Interval changed to ${intervalSec}s, restarting timer")
                        startPeriodicHeartbeat()
                    }
                }
            }
        }
    }

    /**
     * 停止心跳定时器
     */
    fun stop() {
        if (isRunning.compareAndSet(true, false)) {
            heartbeatJob?.cancel()
            heartbeatJob = null
            Timber.d("DeviceHeartbeatManager stopped")
        }
    }

    /**
     * 手动触发一次心跳（网络恢复时调用）
     */
    fun triggerImmediate() {
        if (!isRunning.get()) return
        appScope.launch {
            sendHeartbeat()
        }
    }

    /**
     * 测试平台连接（返回详细结果，用于 UI 展示）
     */
    suspend fun testConnection(): ConnectionTestResult {
        return try {
            val platformUrl = settingsRepository.platformUrl.value.trimEnd('/')
            val apiKey = settingsRepository.apiKey.value

            if (platformUrl.isBlank()) {
                return ConnectionTestResult(false, "平台地址未配置")
            }
            if (apiKey.isBlank()) {
                return ConnectionTestResult(false, "API Key 未配置")
            }

            // 校验 URL 格式
            if (!platformUrl.startsWith("http://", ignoreCase = true)
                && !platformUrl.startsWith("https://", ignoreCase = true)
            ) {
                Timber.w("[TestConnection] Invalid URL scheme: $platformUrl")
                return ConnectionTestResult(
                    false,
                    "平台地址缺少协议头（应以 http:// 或 https:// 开头）"
                )
            }

            val url = PlatformUrlResolver.heartbeatUrl(platformUrl)
            Timber.d("[TestConnection] POST $url | api-key=${apiKey.take(8)}...")

            val startAt = System.currentTimeMillis()
            val response = withTimeout(REQUEST_TIMEOUT_MS) {
                httpClient.post(url) {
                    header("api-key", apiKey)
                }
            }
            val elapsed = System.currentTimeMillis() - startAt
            Timber.d("[TestConnection] Response in ${elapsed}ms, status=${response.status}")

            if (response.status.isSuccess()) {
                val body = response.body<DeviceHeartbeatResponse>()
                if (body.code == 200) {
                    ConnectionTestResult(true, "连接成功：${body.message}（${elapsed}ms）")
                } else {
                    ConnectionTestResult(false, "平台返回错误：${body.message} (code=${body.code})")
                }
            } else {
                ConnectionTestResult(false, "HTTP 错误：${response.status}")
            }
        } catch (e: ClientRequestException) {
            Timber.e(e, "[TestConnection] Client request error")
            ConnectionTestResult(false, "请求被拒绝：${e.response.status}")
        } catch (e: ServerResponseException) {
            Timber.e(e, "[TestConnection] Server error")
            ConnectionTestResult(false, "服务器错误：${e.response.status}")
        } catch (e: HttpRequestTimeoutException) {
            Timber.e(e, "[TestConnection] Http request timeout")
            ConnectionTestResult(false, "请求超时（${REQUEST_TIMEOUT_MS / 1000}秒），请检查网络或平台地址")
        } catch (e: TimeoutCancellationException) {
            Timber.e(e, "[TestConnection] Coroutine timeout")
            ConnectionTestResult(false, "请求超时（${REQUEST_TIMEOUT_MS / 1000}秒），请检查网络或平台地址")
        } catch (e: java.net.UnknownHostException) {
            Timber.e(e, "[TestConnection] Unknown host")
            ConnectionTestResult(false, "无法解析域名，请检查平台地址")
        } catch (e: java.net.ConnectException) {
            Timber.e(e, "[TestConnection] Connection refused")
            ConnectionTestResult(false, "连接被拒绝，请检查平台地址和端口")
        } catch (e: Exception) {
            Timber.e(e, "[TestConnection] Unexpected error: ${e.javaClass.simpleName}")
            ConnectionTestResult(false, "连接异常(${e.javaClass.simpleName})：${e.message}")
        }
    }

    /**
     * 获取最后一次心跳状态
     */
    fun getLastStatus(): HeartbeatStatus {
        return HeartbeatStatus(
            lastHeartbeatAt = lastHeartbeatAt,
            lastSuccess = lastHeartbeatSuccess
        )
    }

    private fun startPeriodicHeartbeat() {
        heartbeatJob?.cancel()
        val intervalSec = settingsRepository.heartbeatInterval.value
            .coerceIn(10, 300)
            .takeIf { it > 0 } ?: DEFAULT_HEARTBEAT_INTERVAL_SEC
        val intervalMs = intervalSec * 1000L
        Timber.d("[Heartbeat] Starting periodic timer with interval=${intervalSec}s")
        heartbeatJob = appScope.launch {
            while (isActive && isRunning.get()) {
                delay(intervalMs)
                if (isActive) {
                    sendHeartbeat()
                }
            }
        }
    }

    private suspend fun sendHeartbeat() {
        val platformUrl = settingsRepository.platformUrl.value.trimEnd('/')
        val apiKey = settingsRepository.apiKey.value

        if (platformUrl.isBlank() || apiKey.isBlank()) {
            Timber.d("[Heartbeat] Platform URL or API Key not configured, skipping")
            return
        }

        val url = runCatching { PlatformUrlResolver.heartbeatUrl(platformUrl) }
            .getOrElse { error ->
                Timber.w(error, "[Heartbeat] Invalid platform URL")
                return
            }
        val hadPreviousHeartbeat = lastHeartbeatAt > 0L
        lastHeartbeatAt = System.currentTimeMillis()

        try {
            val response = httpClient.post(url) {
                header("api-key", apiKey)
            }

            if (response.status.isSuccess()) {
                val body = response.body<DeviceHeartbeatResponse>()
                if (body.code == 200) {
                    // 平台从断联恢复（上一拍失败、本拍成功）时，立即补发积压的离线记录，
                    // 无需等待周期重试，实现平台恢复秒级响应
                    val wasDisconnected = hadPreviousHeartbeat && !lastHeartbeatSuccess
                    lastHeartbeatSuccess = true
                    Timber.d("[Heartbeat] SUCCESS: ${body.message}")
                    if (wasDisconnected) {
                        Timber.d("[Heartbeat] Platform recovered, triggering pending uploads")
                        pendingUploadManager.enqueue(0L)
                    }
                } else {
                    lastHeartbeatSuccess = false
                    Timber.w("[Heartbeat] Platform error: code=${body.code}, message=${body.message}")
                }
            } else {
                lastHeartbeatSuccess = false
                Timber.w("[Heartbeat] HTTP error: ${response.status}")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            lastHeartbeatSuccess = false
            Timber.w(e, "[Heartbeat] Failed")
        }
    }
}

/**
 * 连接测试结果
 */
data class ConnectionTestResult(
    val success: Boolean,
    val message: String
)

/**
 * 心跳状态
 */
data class HeartbeatStatus(
    val lastHeartbeatAt: Long,
    val lastSuccess: Boolean
)
