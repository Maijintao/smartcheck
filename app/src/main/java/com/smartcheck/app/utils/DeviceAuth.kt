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

    const val SERVER_URL = "http://112.74.39.40:8080"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getActivationServerUrl(): String = SERVER_URL

    /**
     * 检查是否已激活
     */
    fun isActivated(): Boolean {
        return prefs.getBoolean(KEY_ACTIVATED, false)
    }

    /**
     * 获取激活时间
     */
    fun getActivatedTime(): Long {
        return prefs.getLong(KEY_ACTIVATED_TIME, 0)
    }

    /**
     * 请求激活
     */
<<<<<<< feature/wcx
    suspend fun activate(activationCode: String): Result<Boolean> = withContext(Dispatchers.IO) {
        Timber.d("[DeviceAuth] === 开始激活流程 ===")
=======
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

        if (!::appContext.isInitialized) {
            return@withContext Result.failure(Exception("设备授权未初始化"))
        }

        val mac = DeviceInfo.getStableMacAddress(appContext)
        if (mac.isNullOrBlank()) {
            return@withContext Result.failure(Exception("无法获取稳定 MAC 地址，请联系管理员录入设备白名单"))
        }
>>>>>>> local

        Timber.d("[DeviceAuth] 服务器地址: $SERVER_URL")
        Timber.d("[DeviceAuth] 请求激活: code=$activationCode")

        try {
            Timber.d("[DeviceAuth] 正在连接服务器...")

            val json = """
                {
                    "activationCode": "$activationCode"
                }
            """.trimIndent()

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

                try {
                    val json = org.json.JSONObject(response)
                    val code = json.optInt("code", -1)
                    val activated = json.optJSONObject("data")?.optBoolean("activated", false) ?: false
                    
                    if (code == 0 || activated) {
                        saveActivation()
                        return@withContext Result.success(true)
                    }
                    
                    val errorMsg = json.optString("message", "激活失败")
                    Timber.w("[DeviceAuth] 激活失败: $errorMsg")
                    return@withContext Result.failure(Exception(errorMsg))
                } catch (e: Exception) {
                    Timber.e(e, "[DeviceAuth] 解析响应失败: $response")
                    return@withContext allowPreviousActivationOnRequestFailure(mac, "响应解析失败")
                }
            }

<<<<<<< feature/wcx
            Timber.w("[DeviceAuth] 激活失败: $responseCode")
            Result.failure(Exception("激活失败: $responseCode"))
        } catch (e: Exception) {
            Timber.e(e, "[DeviceAuth] 激活异常")
            Result.failure(Exception("网络错误: ${e.message}"))
=======
            Timber.w("[DeviceAuth] 授权服务请求未成功: $responseCode")
            allowPreviousActivationOnRequestFailure(mac, "设备授权校验失败: $responseCode")
        } catch (e: Exception) {
            Timber.e(e, "[DeviceAuth] 授权校验请求失败")
            allowPreviousActivationOnRequestFailure(mac, "网络错误: ${e.message}")
>>>>>>> local
        }
    }

    private fun allowPreviousActivationOnRequestFailure(mac: String, message: String): Result<Boolean> {
        val normalizedMac = DeviceInfo.normalizeMac(mac)
        val lastVerifiedMac = getLastVerifiedMac()?.let { DeviceInfo.normalizeMac(it) }
        val hasPreviousActivation = isActivated() && getActivatedTime() > 0
        val hasSameMacVerification = hasPreviousActivation && lastVerifiedMac == normalizedMac
        val hasLegacyActivation = isLegacyActivationExempt()

        if (hasSameMacVerification || hasLegacyActivation) {
            Timber.w("[DeviceAuth] 在线授权请求失败，使用历史成功激活状态放行: $message")
            return Result.success(true)
        }

        Timber.w("[DeviceAuth] 首次授权必须完成在线校验: $message")
        return Result.failure(Exception(message))
    }

    private fun saveActivation() {
        prefs.edit().apply {
            putBoolean(KEY_ACTIVATED, true)
            putLong(KEY_ACTIVATED_TIME, System.currentTimeMillis())
            apply()
        }
        Timber.d("[DeviceAuth] 激活状态已保存")
    }

    /**
     * 清除激活状态（用于重置）
     */
    fun clearActivation() {
        prefs.edit().clear().apply()
        Timber.d("[DeviceAuth] 激活状态已清除")
    }
}
