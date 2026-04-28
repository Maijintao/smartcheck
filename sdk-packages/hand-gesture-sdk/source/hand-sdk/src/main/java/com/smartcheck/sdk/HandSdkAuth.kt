package com.smartcheck.sdk

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.NetworkInterface
import java.net.URL
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object HandSdkAuth {

    private const val TAG = "HandSdkAuth"
    private const val INVALID_MAC = "02:00:00:00:00:00"
    private const val PREFS_NAME = "hand_sdk_auth"
    private const val KEY_ACTIVATED = "activated"
    private const val KEY_ACTIVATED_TIME = "activated_time"
    private const val KEY_LAST_VERIFIED_MAC = "last_verified_mac"
    private const val KEY_LAST_VERIFIED_TIME = "last_verified_time"
    private const val CONNECT_TIMEOUT_MS = 10000
    private const val READ_TIMEOUT_MS = 10000

    private var serverUrl: String = "http://112.74.39.40:8080"
    private var authRequired: Boolean = true

    private fun prefs(context: Context): SharedPreferences {
        return context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    @JvmStatic
    fun configure(serverUrl: String, required: Boolean = true) {
        this.serverUrl = serverUrl.trimEnd('/')
        this.authRequired = required
        Log.i(TAG, "configure: server=$serverUrl, required=$required")
    }

    @JvmStatic
    fun isAuthRequired(): Boolean = authRequired

    @JvmStatic
    fun getServerUrl(): String = serverUrl

    @JvmStatic
    fun getCurrentDeviceMac(context: Context): String? = getStableMacAddress(context)

    @JvmStatic
    fun isActivated(context: Context): Boolean = prefs(context).getBoolean(KEY_ACTIVATED, false)

    @JvmStatic
    fun clearActivation(context: Context) {
        prefs(context).edit().clear().apply()
    }

    @JvmStatic
    fun verifyDeviceAccess(context: Context): Result<Boolean> {
        if (!authRequired) {
            Log.i(TAG, "MAC auth disabled by configure(required=false)")
            return Result.success(true)
        }

        val mac = getStableMacAddress(context)
        if (mac.isNullOrBlank()) {
            return Result.failure(Exception("无法获取稳定 MAC 地址，请联系管理员录入设备白名单"))
        }

        val executor = Executors.newSingleThreadExecutor()
        return try {
            val future = executor.submit<Result<Boolean>> {
                verifyFromServer(mac)
            }
            future.get((CONNECT_TIMEOUT_MS + READ_TIMEOUT_MS + 3000).toLong(), TimeUnit.MILLISECONDS)
        } catch (e: Exception) {
            Result.failure(Exception("网络错误: ${e.message}"))
        } finally {
            executor.shutdownNow()
        }
    }

    private fun verifyFromServer(mac: String): Result<Boolean> {
        val fullUrl = "${serverUrl.trimEnd('/')}/api/device/activate"
        return try {
            val json = """
                {
                    "deviceMac": "$mac"
                }
            """.trimIndent()

            val url = URL(fullUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS

            connection.outputStream.use { output ->
                output.write(json.toByteArray())
            }

            val responseCode = connection.responseCode
            if (responseCode != 200) {
                return Result.failure(Exception("设备授权校验失败: $responseCode"))
            }

            val response = connection.inputStream.bufferedReader().readText()
            val body = JSONObject(response)
            val code = body.optInt("code", -1)
            val activated = body.optJSONObject("data")?.optBoolean("activated", false) ?: false
            if (code == 0 || activated) {
                return Result.success(true)
            }

            val message = body.optString("message", "设备未授权")
            Result.failure(Exception(message))
        } catch (e: Exception) {
            Result.failure(Exception("网络错误: ${e.message}"))
        }
    }

    @JvmStatic
    fun saveVerification(context: Context, mac: String) {
        prefs(context).edit().apply {
            putBoolean(KEY_ACTIVATED, true)
            putLong(KEY_ACTIVATED_TIME, System.currentTimeMillis())
            putString(KEY_LAST_VERIFIED_MAC, normalizeMac(mac))
            putLong(KEY_LAST_VERIFIED_TIME, System.currentTimeMillis())
            apply()
        }
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
                Log.i(TAG, "固定使用 wlan0 MAC: $macFromInterface")
                return macFromInterface
            }

            readMacFromSysfs()
        } catch (e: Exception) {
            Log.e(TAG, "获取 wlan0 MAC 失败", e)
            readMacFromSysfs()
        }
    }

    private fun readMacFromSysfs(): String? {
        return readSysfsMac("wlan0")
    }

    private fun readSysfsMac(ifaceName: String): String? {
        return runCatching {
            val mac = File("/sys/class/net/$ifaceName/address").readText().trim().uppercase(Locale.US)
            if (mac == INVALID_MAC || mac == "00:00:00:00:00:00") return@runCatching null
            if (!Regex("^[0-9A-F]{2}(:[0-9A-F]{2}){5}$").matches(mac)) return@runCatching null
            Log.i(TAG, "固定从 /sys 读取 wlan0 MAC: $mac")
            mac
        }.getOrNull()
    }

    private fun normalizeMac(mac: String): String {
        return mac.trim().uppercase(Locale.US).replace('-', ':')
    }
}
