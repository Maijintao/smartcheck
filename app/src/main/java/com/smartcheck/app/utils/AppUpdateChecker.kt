package com.smartcheck.app.utils

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.FileProvider
import com.smartcheck.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val releaseNotes: String,
    val createdAt: String = "",
    val isLatest: Boolean = false
)

object AppUpdateChecker {

    private const val TAG = "AppUpdateChecker"

    /**
     * 检查是否有新版本。
     * @return Result.success(UpdateInfo) 有新版；Result.success(null) 已是最新；Result.failure 网络/解析错误
     */
    suspend fun checkUpdate(serverBaseUrl: String): Result<UpdateInfo?> = withContext(Dispatchers.IO) {
        try {
            val url = URL("${serverBaseUrl.trimEnd('/')}/api/app/version/latest")
            Timber.i("$TAG 检查更新 URL: $url (本地 versionCode=${BuildConfig.VERSION_CODE})")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 10_000
            conn.readTimeout    = 10_000
            conn.connect()

            val httpCode = conn.responseCode
            if (httpCode != 200) {
                Timber.w("$TAG 服务器返回 $httpCode")
                return@withContext Result.failure(Exception("服务器返回 $httpCode"))
            }

            val body = conn.inputStream.bufferedReader().readText()
            val json = JSONObject(body)
            if (json.optInt("code", -1) != 0) {
                val msg = json.optString("message", "检查失败")
                Timber.w("$TAG 服务器错误: $msg")
                return@withContext Result.failure(Exception(msg))
            }

            val data = json.optJSONObject("data")
                ?: return@withContext Result.success(null)

            val remoteVersionCode = data.optInt("versionCode", 0)
            if (remoteVersionCode <= BuildConfig.VERSION_CODE) {
                Timber.i("$TAG 已是最新版本 (本地=${BuildConfig.VERSION_CODE}, 远端=$remoteVersionCode)")
                return@withContext Result.success(null)
            }

            val apkUrl = data.optString("apkUrl", "")
            if (apkUrl.isBlank()) {
                Timber.w("$TAG 服务器未配置 APK 下载地址")
                return@withContext Result.failure(Exception("服务器未配置 APK 下载地址，请联系管理员"))
            }

            val info = UpdateInfo(
                versionCode  = remoteVersionCode,
                versionName  = data.optString("versionName", ""),
                apkUrl       = apkUrl,
                releaseNotes = data.optString("releaseNotes", ""),
                createdAt    = data.optString("createdAt", ""),
                isLatest     = true
            )
            Timber.i("$TAG 发现新版本: ${info.versionName} (versionCode=${info.versionCode})")
            Result.success(info)
        } catch (e: Exception) {
            Timber.e(e, "$TAG 检查更新异常")
            Result.failure(Exception("网络错误: ${e.message}"))
        }
    }

    /**
     * 获取版本历史列表（最近 10 条），供 App 展示更新记录。
     */
    suspend fun getVersionHistory(serverBaseUrl: String): Result<List<UpdateInfo>> = withContext(Dispatchers.IO) {
        try {
            val url = URL("${serverBaseUrl.trimEnd('/')}/api/app/version/history")
            Timber.i("$TAG 获取版本历史: $url")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 10_000
            conn.readTimeout    = 10_000
            conn.connect()

            val httpCode = conn.responseCode
            if (httpCode != 200) {
                return@withContext Result.failure(Exception("服务器返回 $httpCode"))
            }

            val body = conn.inputStream.bufferedReader().readText()
            val json = JSONObject(body)
            if (json.optInt("code", -1) != 0) {
                return@withContext Result.failure(Exception(json.optString("message", "获取失败")))
            }

            val arr: JSONArray = json.optJSONArray("data") ?: JSONArray()
            val list = (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                UpdateInfo(
                    versionCode  = obj.optInt("versionCode", 0),
                    versionName  = obj.optString("versionName", ""),
                    apkUrl       = obj.optString("apkUrl", ""),
                    releaseNotes = obj.optString("releaseNotes", ""),
                    createdAt    = obj.optString("createdAt", ""),
                    isLatest     = obj.optBoolean("isLatest", false)
                )
            }
            Timber.i("$TAG 获取到 ${list.size} 条版本历史")
            Result.success(list)
        } catch (e: Exception) {
            Timber.e(e, "$TAG 获取版本历史异常")
            Result.failure(Exception("网络错误: ${e.message}"))
        }
    }

    /**
     * 下载 APK 并触发系统安装器。
     * [fix#3] onProgress 保证在主线程回调，且只在进度变化时触发。
     * [fix#7] 日志只在进度跨越 20% 点时打印一次。
     * @param onProgress (progress: Int, speed: String) -> Unit 进度百分比和下载速度
     */
    suspend fun downloadAndInstall(
        context: Context,
        apkUrl: String,
        onProgress: (Int, String) -> Unit
    ) = withContext(Dispatchers.IO) {
        val apkFile = File(context.filesDir, "update.apk")

        try {
            Timber.i("$TAG 开始下载 APK: $apkUrl")
            val conn = URL(apkUrl).openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout    = 60_000
            conn.connect()

            val totalSize = conn.contentLength.toLong()
            if (totalSize > 0) {
                Timber.i("$TAG APK 大小: ${"%.1f".format(totalSize / 1024.0 / 1024.0)} MB")
            }

            conn.inputStream.use { input ->
                FileOutputStream(apkFile).use { output ->
                    val buf = ByteArray(8192)
                    var downloaded   = 0L
                    var lastProgress = -1          // 上次回调的进度值
                    var lastLogMark  = -1          // 上次打日志的 20% 档位
                    var lastTime     = System.currentTimeMillis()
                    var read: Int

                    while (input.read(buf).also { read = it } != -1) {
                        output.write(buf, 0, read)
                        downloaded += read

                        val now = System.currentTimeMillis()
                        val timeDiff = now - lastTime
                        val speed = if (timeDiff >= 500) {
                            val bytesPerSec = downloaded * 1000 / timeDiff
                            lastTime = now
                            formatSpeed(bytesPerSec)
                        } else {
                            ""
                        }

                        val progress = if (totalSize > 0)
                            (downloaded * 100 / totalSize).toInt()
                        else -1

                        // 只在值变化时回调（且切换到主线程）
                        if (progress != lastProgress) {
                            lastProgress = progress
                            withContext(Dispatchers.Main) { onProgress(progress, speed) }

                            // 每跨越 20% 打一条日志
                            if (progress >= 0) {
                                val mark = progress / 20
                                if (mark != lastLogMark) {
                                    lastLogMark = mark
                                    Timber.d("$TAG 下载进度: $progress%")
                                }
                            }
                        }
                    }
                }
            }

            Timber.i("$TAG APK 下载完成: ${"%.2f".format(apkFile.length() / 1024.0 / 1024.0)} MB")

            validateDownloadedApk(context, apkFile)

            withContext(Dispatchers.Main) {
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    apkFile
                )
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                }
                Timber.i("$TAG 触发系统安装器")
                context.startActivity(intent)
            }
        } catch (e: Exception) {
            apkFile.delete()
            Timber.e(e, "$TAG 下载/安装失败")
            throw e
        }
    }

    private fun validateDownloadedApk(context: Context, apkFile: File) {
        val packageManager = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }

        val archiveInfo = packageManager.getPackageArchiveInfo(apkFile.absolutePath, flags)
            ?: run {
                apkFile.delete()
                throw IllegalStateException("更新包无效或已损坏，请重新下载")
            }

        val archivePackageName = archiveInfo.packageName.orEmpty()
        if (archivePackageName != context.packageName) {
            apkFile.delete()
            throw IllegalStateException("更新包包名不匹配：$archivePackageName")
        }

        val currentInfo = packageManager.getPackageInfoCompat(context.packageName, flags)
        val archiveVersionCode = archiveInfo.versionCodeCompat()
        val currentVersionCode = currentInfo.versionCodeCompat()
        if (archiveVersionCode <= currentVersionCode) {
            apkFile.delete()
            throw IllegalStateException("更新包版本过低：当前=$currentVersionCode，更新包=$archiveVersionCode")
        }

        if (!hasMatchingSignature(currentInfo, archiveInfo)) {
            apkFile.delete()
            throw IllegalStateException("更新包签名与当前应用不一致，请使用同一签名重新打包")
        }
    }

    private fun PackageManager.getPackageInfoCompat(packageName: String, flags: Int): PackageInfo {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            @Suppress("DEPRECATION")
            getPackageInfo(packageName, flags)
        }
    }

    private fun PackageInfo.versionCodeCompat(): Long {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            longVersionCode
        } else {
            @Suppress("DEPRECATION")
            versionCode.toLong()
        }
    }

    private fun hasMatchingSignature(currentInfo: PackageInfo, archiveInfo: PackageInfo): Boolean {
        val currentSignatures = currentInfo.signaturesCompat()
        val archiveSignatures = archiveInfo.signaturesCompat()
        return currentSignatures.isNotEmpty() && currentSignatures == archiveSignatures
    }

    private fun PackageInfo.signaturesCompat(): List<ByteArray> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = signingInfo ?: return emptyList()
            signingInfo.apkContentsSigners
                .map { it.toByteArray() }
                .sortedBy { it.contentHashCode() }
        } else {
            @Suppress("DEPRECATION")
            signatures
                ?.map { it.toByteArray() }
                ?.sortedBy { it.contentHashCode() }
                ?: emptyList()
        }
    }

    private fun formatSpeed(bytesPerSec: Long): String {
        return when {
            bytesPerSec >= 1024 * 1024 -> "%.1f MB/s".format(bytesPerSec / 1024.0 / 1024.0)
            bytesPerSec >= 1024 -> "%.1f KB/s".format(bytesPerSec / 1024.0)
            else -> "$bytesPerSec B/s"
        }
    }
}
