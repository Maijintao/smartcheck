package com.smartcheck.app.utils

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.net.HttpURLConnection
import java.net.URL

object DeviceAuth {

    private const val PREFS_NAME = "device_auth"
    private const val KEY_ACTIVATED = "activated"
    private const val KEY_ACTIVATED_TIME = "activated_time"
    private const val KEY_LAST_VERIFIED_MAC = "last_verified_mac"
    private const val KEY_LAST_VERIFIED_TIME = "last_verified_time"
    private const val KEY_LEGACY_EXEMPT = "legacy_exempt"
    private const val KEY_LEGACY_EXEMPT_AT = "legacy_exempt_at"

    const val SERVER_URL = "http://112.74.39.40:8080"

    private lateinit var prefs: SharedPreferences
    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        migrateLegacyActivationIfNeeded()
    }

    fun getActivationServerUrl(): String = SERVER_URL

    /** 本地是否曾经完成过授权校验（仅用于展示，不作为最终准入依据）。 */
    fun isActivated(): Boolean {
        return prefs.getBoolean(KEY_ACTIVATED, false)
    }

    /**
     * 获取激活时间
     */
    fun getActivatedTime(): Long {
        return prefs.getLong(KEY_ACTIVATED_TIME, 0)
    }

    fun getLastVerifiedMac(): String? {
        return prefs.getString(KEY_LAST_VERIFIED_MAC, null)
    }

    /**
     * 历史激活码时代：只有本地 activated 标识，没有 last_verified_mac。
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
            Timber.i("[DeviceAuth] 识别到历史本地激活标识，启用升级免校验")
            return true
        }

        return false
    }

    fun getCurrentDeviceMac(): String? {
        if (!::appContext.isInitialized) return null
        return DeviceInfo.getStableMacAddress(appContext)
    }

    suspend fun activate(activationCode: String): Result<Boolean> = verifyDeviceAccess()

    suspend fun verifyDeviceAccess(): Result<Boolean> = withContext(Dispatchers.IO) {
        Timber.d("[DeviceAuth] === 开始设备授权校验 ===")

        if (isLegacyActivationExempt()) {
            Timber.i("[DeviceAuth] 命中历史激活豁免，跳过 MAC 在线校验")
            return@withContext Result.success(true)
        }

        if (!::appContext.isInitialized) {
            return@withContext Result.failure(Exception("设备授权未初始化"))
        }

        val mac = DeviceInfo.getStableMacAddress(appContext)
        if (mac.isNullOrBlank()) {
            return@withContext Result.failure(Exception("无法获取稳定 MAC 地址，请联系管理员录入设备白名单"))
        }

        Timber.d("[DeviceAuth] 服务器地址: $SERVER_URL")
        Timber.d("[DeviceAuth] 当前设备 MAC: $mac")

        try {
            Timber.d("[DeviceAuth] 正在连接授权服务器...")

            val json = """
                {
                    "deviceMac": "$mac"
                }
            """.trimIndent()

            // 确保 URL 以 / 结尾，然后添加路径
            val baseUrl = SERVER_URL.trimEnd('/')
            val fullUrl = "$baseUrl/api/device/activate"
            Timber.d("[DeviceAuth] 完整URL: $fullUrl")
            
            val url = URL(fullUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            connection.outputStream.use { output ->
                output.write(json.toByteArray())
            }

            val responseCode = connection.responseCode
            Timber.d("[DeviceAuth] 响应码: $responseCode")

            if (responseCode == 200) {
                val response = connection.inputStream.bufferedReader().readText()
                Timber.d("[DeviceAuth] 响应: $response")

                // 解析响应
                // 解析 JSON 响应
                try {
                    val json = org.json.JSONObject(response)
                    val code = json.optInt("code", -1)
                    val activated = json.optJSONObject("data")?.optBoolean("activated", false) ?: false
                    
                    if (code == 0 || activated) {
                        saveVerification(mac)
                        return@withContext Result.success(true)
                    }
                    
                    val errorMsg = json.optString("message", "设备未授权")
                    Timber.w("[DeviceAuth] 授权校验失败: $errorMsg")
                    return@withContext Result.failure(Exception(errorMsg))
                } catch (e: Exception) {
                    Timber.e(e, "[DeviceAuth] 解析响应失败: $response")
                    return@withContext Result.failure(Exception("响应解析失败"))
                }
            }

            Timber.w("[DeviceAuth] 授权校验失败: $responseCode")
            Result.failure(Exception("设备授权校验失败: $responseCode"))
        } catch (e: Exception) {
            Timber.e(e, "[DeviceAuth] 授权校验异常")
            Result.failure(Exception("网络错误: ${e.message}"))
        }
    }

    private fun parseErrorMessage(response: String): String? {
        return try {
            // 简单解析 message 字段
            val regex = """\"message\"\s*:\s*\"([^\"]+)\"""".toRegex()
            regex.find(response)?.groupValues?.get(1)
        } catch (e: Exception) {
            null
        }
    }

    private fun saveVerification(mac: String) {
        prefs.edit().apply {
            putBoolean(KEY_ACTIVATED, true)
            putLong(KEY_ACTIVATED_TIME, System.currentTimeMillis())
            putString(KEY_LAST_VERIFIED_MAC, DeviceInfo.normalizeMac(mac))
            putLong(KEY_LAST_VERIFIED_TIME, System.currentTimeMillis())
            putBoolean(KEY_LEGACY_EXEMPT, false)
            apply()
        }
        Timber.d("[DeviceAuth] 设备授权状态已保存")
    }

    private fun migrateLegacyActivationIfNeeded() {
        if (prefs.getBoolean(KEY_LEGACY_EXEMPT, false)) return

        val isLegacyActivated = isActivated() && getActivatedTime() > 0 && getLastVerifiedMac().isNullOrBlank()
        if (!isLegacyActivated) return

        prefs.edit()
            .putBoolean(KEY_LEGACY_EXEMPT, true)
            .putLong(KEY_LEGACY_EXEMPT_AT, System.currentTimeMillis())
            .apply()
        Timber.i("[DeviceAuth] 完成历史激活迁移，后续免 MAC 校验")
    }

    /**
     * 清除激活状态（用于重置）
     */
    fun clearActivation() {
        prefs.edit().clear().apply()
        Timber.d("[DeviceAuth] 激活状态已清除")
    }
}
