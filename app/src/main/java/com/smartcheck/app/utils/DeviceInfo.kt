package com.smartcheck.app.utils

import android.content.Context
import android.os.Build
import android.provider.Settings
import timber.log.Timber
import java.io.File
import java.net.NetworkInterface
import java.util.Locale

object DeviceInfo {

    private const val INVALID_MAC = "02:00:00:00:00:00"

    fun getDeviceId(context: Context): String {
        return try {
            // 优先使用 Android ID
            val androidId = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ANDROID_ID
            )
            if (!androidId.isNullOrEmpty()) {
                Timber.d("[DeviceInfo] Android ID: $androidId")
                return androidId
            }
            
            // 备选：序列号
            val serial = Build.SERIAL
            if (!serial.isNullOrEmpty() && serial != "unknown") {
                Timber.d("[DeviceInfo] Serial: $serial")
                return serial
            }
            
            // 最后：随机 UUID（不应该走到这里）
            Timber.w("[DeviceInfo] 无法获取设备ID，使用随机ID")
            java.util.UUID.randomUUID().toString()
        } catch (e: Exception) {
            Timber.e(e, "[DeviceInfo] 获取设备ID失败")
            java.util.UUID.randomUUID().toString()
        }
    }

    fun getDeviceModel(): String {
        return "${Build.MANUFACTURER} ${Build.MODEL}"
    }

    fun getStableMacAddress(context: Context): String? {
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
                Timber.i("[DeviceInfo] 固定使用 wlan0 MAC: $macFromInterface")
                return macFromInterface
            }

            readMacFromSysfs()
        } catch (e: Exception) {
            Timber.e(e, "[DeviceInfo] 获取 wlan0 MAC 失败")
            readMacFromSysfs()
        }
    }

    private fun readMacFromSysfs(): String? {
        return runCatching {
            val mac = File("/sys/class/net/wlan0/address").readText().trim().uppercase(Locale.US)
            if (mac == INVALID_MAC || mac == "00:00:00:00:00:00") return@runCatching null
            if (!Regex("^[0-9A-F]{2}(:[0-9A-F]{2}){5}$").matches(mac)) return@runCatching null
            Timber.i("[DeviceInfo] 固定从 /sys 读取 wlan0 MAC: $mac")
            mac
        }.getOrNull()
    }

    fun normalizeMac(mac: String): String {
        return mac.trim().uppercase(Locale.US).replace('-', ':')
    }

    fun getAppVersion(context: Context): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }
    }
}
