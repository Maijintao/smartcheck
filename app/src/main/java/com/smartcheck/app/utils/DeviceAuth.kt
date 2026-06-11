package com.smartcheck.app.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.NetworkInterface
import java.net.URL
import java.util.Locale

/**
 * 设备 MAC 白名单授权验证工具（单文件，可复用）
 *
 * 使用方式：
 * 1. Application.onCreate() 中调用 DeviceAuth.init(context, "http://your-server:port")
 * 2. 需要校验时调用 DeviceAuth.verifyDeviceAccess(): Result<Boolean>
 * 3. 可通过 DeviceAuth.isActivated() 查询本地激活状态
 */
object DeviceAuth {

    private const val TAG = "DeviceAuth"
    private const val PREFS_NAME = "device_auth"
    private const val KEY_ACTIVATED = "activated"
    private const val KEY_ACTIVATED_TIME = "activated_time"
    private const val KEY_LAST_VERIFIED_MAC = "last_verified_mac"
    private const val KEY_LAST_VERIFIED_TIME = "last_verified_time"
    private const val KEY_LEGACY_EXEMPT = "legacy_exempt"
    private const val KEY_LEGACY_EXEMPT_AT = "legacy_exempt_at"
    private const val INVALID_MAC = "02:00:00:00:00:00"

    private lateinit var prefs: SharedPreferences
    private lateinit var appContext: Context

    /** 服务器地址，末尾不带斜杠 */
    var serverUrl: String = ""
        private set

    /** 激活接口路径 */
    var activatePath: String = "/api/device/activate"
        set(value) {
            field = value.trim().trimStart('/').let { "/$it" }
        }

    /** 连接超时（毫秒） */
    var connectTimeoutMs: Int = 10_000

    /** 读取超时（毫秒） */
    var readTimeoutMs: Int = 10_000

    /**
     * 初始化，必须在任何其他调用之前执行。
     *
     * @param context   ApplicationContext 或 Context
     * @param serverUrl 授权服务器地址，如 "http://112.74.39.40:8080"
     */
    fun init(context: Context, serverUrl: String) {
        appContext = context.applicationContext
        prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        this.serverUrl = serverUrl.trimEnd('/')
        migrateLegacyActivationIfNeeded()
    }

    /** 本地是否曾经完成过授权校验（仅用于展示，不作为最终准入依据） */
    fun isActivated(): Boolean {
        if (!::prefs.isInitialized) return false
        return prefs.getBoolean(KEY_ACTIVATED, false)
    }

    /** 获取激活时间戳 */
    fun getActivatedTime(): Long {
        if (!::prefs.isInitialized) return 0L
        return prefs.getLong(KEY_ACTIVATED_TIME, 0)
    }

    /** 获取最后一次验证通过的 MAC */
    fun getLastVerifiedMac(): String? {
        if (!::prefs.isInitialized) return null
        return prefs.getString(KEY_LAST_VERIFIED_MAC, null)
    }

    /** 获取最后一次验证通过的时间戳 */
    fun getLastVerifiedTime(): Long {
        if (!::prefs.isInitialized) return 0L
        return prefs.getLong(KEY_LAST_VERIFIED_TIME, 0)
    }

    /**
     * 历史激活码时代遗留：只有本地 activated 标识，没有 last_verified_mac。
     * 命中该标记则升级后继续放行，不再强制 MAC 校验。
     */
    fun isLegacyActivationExempt(): Boolean {
        if (!::prefs.isInitialized) return false

        if (prefs.getBoolean(KEY_LEGACY_EXEMPT, false)) {
            return true
        }

        val isLegacyActivated = isActivated() && getActivatedTime() > 0 && getLastVerifiedMac().isNullOrBlank()
        if (isLegacyActivated) {
            prefs.edit()
                .putBoolean(KEY_LEGACY_EXEMPT, true)
                .putLong(KEY_LEGACY_EXEMPT_AT, System.currentTimeMillis())
                .apply()
            Log.i(TAG, "识别到历史本地激活标识，启用升级免校验")
            return true
        }

        return false
    }

    /** 获取当前设备的稳定 MAC 地址（wlan0） */
    fun getCurrentDeviceMac(): String? {
        if (!::appContext.isInitialized) return null
        return getStableMacAddress(appContext)
    }

    /**
     * 执行设备授权校验（在线 MAC 白名单验证）。
     *
     * 放行规则：
     * 1. 命中 [isLegacyActivationExempt]，直接返回成功（历史遗留激活）
     * 2. 有网络时，向平台查询白名单，通过则保存激活状态
     * 3. 无网络时，若之前已激活过（本地有激活记录），则放行；否则失败
     *
     * @return Result.success(true) 表示授权通过；Result.failure(Exception) 包含失败原因
     */
    suspend fun verifyDeviceAccess(): Result<Boolean> = withContext(Dispatchers.IO) {
        Log.d(TAG, "=== 开始设备授权校验 ===")

        if (isLegacyActivationExempt()) {
            Log.i(TAG, "命中历史激活豁免，跳过 MAC 在线校验")
            return@withContext Result.success(true)
        }

        if (!::appContext.isInitialized) {
            return@withContext Result.failure(Exception("DeviceAuth 未初始化，请先调用 init()"))
        }

        if (serverUrl.isBlank()) {
            return@withContext Result.failure(Exception("服务器地址未设置"))
        }

        val mac = getStableMacAddress(appContext)
        if (mac.isNullOrBlank()) {
            return@withContext Result.failure(
                Exception("无法获取稳定 MAC 地址，请联系管理员录入设备白名单")
            )
        }

        Log.d(TAG, "服务器地址: $serverUrl")
        Log.d(TAG, "当前设备 MAC: $mac")

        // 先尝试在线查询白名单
        val hasNetwork = isNetworkAvailable()
        if (hasNetwork) {
            Log.d(TAG, "网络可用，向平台查询白名单")
            try {
                val fullUrl = "$serverUrl$activatePath"
                Log.d(TAG, "完整URL: $fullUrl")

                val jsonBody = """{"deviceMac":"$mac"}"""

                val url = URL(fullUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.doOutput = true
                connection.connectTimeout = connectTimeoutMs
                connection.readTimeout = readTimeoutMs

                connection.outputStream.use { output ->
                    output.write(jsonBody.toByteArray(Charsets.UTF_8))
                }

                val responseCode = connection.responseCode
                Log.d(TAG, "响应码: $responseCode")

                if (responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().readText()
                    Log.d(TAG, "响应: $response")

                    val body = JSONObject(response)
                    val code = body.optInt("code", -1)
                    val dataObj = body.optJSONObject("data")
                    val activated = dataObj?.optBoolean("activated", false) ?: false

                    if (code == 0 || activated) {
                        saveVerification(mac)
                        Log.i(TAG, "设备授权通过 MAC=$mac")
                        return@withContext Result.success(true)
                    }

                    val errorMsg = body.optString("message", "设备未授权")
                    Log.w(TAG, "授权校验失败: $errorMsg")
                    return@withContext Result.failure(Exception(errorMsg))
                }

                Log.w(TAG, "授权校验失败: HTTP $responseCode")
                return@withContext Result.failure(Exception("设备授权校验失败: HTTP $responseCode"))
            } catch (e: Exception) {
                Log.e(TAG, "在线授权校验异常", e)
                // 网络请求异常，降级到本地已激活检查
                if (isActivated()) {
                    Log.i(TAG, "网络请求异常，但本地已有激活记录，放行")
                    return@withContext Result.success(true)
                }
                return@withContext Result.failure(Exception("网络错误: ${e.message}"))
            }
        } else {
            // 无网络
            Log.d(TAG, "网络不可用，检查本地激活记录")
            if (isActivated()) {
                Log.i(TAG, "无网络但本地已有激活记录，放行")
                return@withContext Result.success(true)
            }
            Log.w(TAG, "无网络且本地未激活，拒绝访问")
            return@withContext Result.failure(Exception("无网络连接且设备未激活"))
        }
    }

    /**
     * 清除激活状态（用于重置或调试）
     */
    fun clearActivation() {
        if (!::prefs.isInitialized) {
            Log.w(TAG, "clearActivation 调用时未初始化")
            return
        }
        prefs.edit().clear().apply()
        Log.d(TAG, "激活状态已清除")
    }

    // ────────────────────────────────────────────
    // 内部方法
    // ────────────────────────────────────────────

    private fun saveVerification(mac: String) {
        prefs.edit().apply {
            putBoolean(KEY_ACTIVATED, true)
            putLong(KEY_ACTIVATED_TIME, System.currentTimeMillis())
            putString(KEY_LAST_VERIFIED_MAC, normalizeMac(mac))
            putLong(KEY_LAST_VERIFIED_TIME, System.currentTimeMillis())
            putBoolean(KEY_LEGACY_EXEMPT, false)
            apply()
        }
        Log.d(TAG, "设备授权状态已保存")
    }

    private fun migrateLegacyActivationIfNeeded() {
        if (!::prefs.isInitialized) return
        if (prefs.getBoolean(KEY_LEGACY_EXEMPT, false)) return

        val isLegacyActivated = isActivated() && getActivatedTime() > 0 && getLastVerifiedMac().isNullOrBlank()
        if (!isLegacyActivated) return

        prefs.edit()
            .putBoolean(KEY_LEGACY_EXEMPT, true)
            .putLong(KEY_LEGACY_EXEMPT_AT, System.currentTimeMillis())
            .apply()
        Log.i(TAG, "完成历史激活迁移，后续免 MAC 校验")
    }

    private fun getStableMacAddress(context: Context): String? {
        return try {
            val wlan0 = NetworkInterface.getByName("wlan0")
            val macFromInterface = runCatching {
                if (wlan0 == null || wlan0.isLoopback || wlan0.isVirtual) return@runCatching null
                val raw = wlan0.hardwareAddress ?: return@runCatching null
                if (raw.size != 6) return@runCatching null
                raw.joinToString(":") { "%02X".format(it) }
            }.getOrNull()

            if (!macFromInterface.isNullOrBlank()
                && macFromInterface != INVALID_MAC
                && macFromInterface != "00:00:00:00:00:00"
            ) {
                Log.i(TAG, "使用 wlan0 MAC: $macFromInterface")
                return macFromInterface
            }

            readMacFromSysfs()
        } catch (e: Exception) {
            Log.e(TAG, "获取 wlan0 MAC 失败", e)
            readMacFromSysfs()
        }
    }

    private fun readMacFromSysfs(): String? {
        return runCatching {
            val mac = File("/sys/class/net/wlan0/address")
                .readText()
                .trim()
                .uppercase(Locale.US)
            if (mac == INVALID_MAC || mac == "00:00:00:00:00:00") return@runCatching null
            if (!Regex("^[0-9A-F]{2}(:[0-9A-F]{2}){5}$").matches(mac)) return@runCatching null
            Log.i(TAG, "从 /sys 读取 wlan0 MAC: $mac")
            mac
        }.getOrNull()
    }

    private fun normalizeMac(mac: String): String {
        return mac.trim().uppercase(Locale.US).replace('-', ':')
    }

    /** 检查当前是否有可用网络 */
    private fun isNetworkAvailable(): Boolean {
        return try {
            val connectivityManager = appContext.getSystemService(Context.CONNECTIVITY_SERVICE)
                    as android.net.ConnectivityManager
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (e: Exception) {
            Log.w(TAG, "网络状态检查异常", e)
            false
        }
    }
}
