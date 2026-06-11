package com.smartcheck.app.data.upload

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.smartcheck.app.api.model.MorningCheckEmployee
import com.smartcheck.app.api.model.MorningCheckUploadRequest
import com.smartcheck.app.api.model.MorningCheckUploadResponse
import com.smartcheck.app.data.repository.SettingsRepository
import com.smartcheck.app.domain.model.Record
import com.smartcheck.app.utils.FileUtil
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CloudRecordService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val httpClient: HttpClient,
    private val settingsRepository: SettingsRepository
) {
    companion object {
        private const val PLATFORM_ENDPOINT = "/api/device/morning-check/upload"
    }

    /**
     * 上报到平台（新接口）
     */
    suspend fun uploadToPlatform(record: Record, deviceId: String): Result<MorningCheckUploadResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val platformUrl = settingsRepository.platformUrl.value.trimEnd('/')
                val apiKey = settingsRepository.apiKey.value

                if (platformUrl.isBlank() || apiKey.isBlank()) {
                    Timber.d("Platform URL or API Key not configured, skipping upload")
                    return@withContext Result.failure(Exception("平台地址或API Key未配置"))
                }

                Timber.d("=== Platform Upload Start ===")
                Timber.d("Device ID: $deviceId")
                Timber.d("Record fields: employeeId=${record.employeeId}, userName='${record.userName}', temp=${record.temperature}, checkTime=${record.checkTime}")
                Timber.d("Image paths: face=${record.faceImagePath}, palm=${record.handPalmPath}, back=${record.handBackPath}")

                val facePhoto = getImageBase64(record.faceImagePath)
                val palmPhoto = getImageBase64(record.handPalmPath)
                val backPhoto = getImageBase64(record.handBackPath)
                Timber.d("Base64 lengths: face=${facePhoto.length}, palm=${palmPhoto.length}, back=${backPhoto.length}")

                val employee = MorningCheckEmployee(
                    id = record.employeeId,
                    name = record.userName,
                    temperature = record.temperature,
                    photo = facePhoto,
                    handPalmPhoto = palmPhoto,
                    handBackPhoto = backPhoto
                )

                val request = MorningCheckUploadRequest(
                    deviceId = deviceId,
                    timestamp = record.checkTime,
                    employees = listOf(employee)
                )
                Timber.d("Request JSON: deviceId=$deviceId, timestamp=${record.checkTime}, employee=${employee}")

                val url = "$platformUrl$PLATFORM_ENDPOINT"
                Timber.d("POST $url")

                val response = httpClient.post(url) {
                    contentType(ContentType.Application.Json)
                    header("api-key", apiKey)
                    setBody(request)
                }

                Timber.d("Response status: ${response.status}")

                if (response.status.isSuccess()) {
                    val responseBody = response.body<MorningCheckUploadResponse>()
                    Timber.d("Response: code=${responseBody.code}, message=${responseBody.message}")

                    if (responseBody.isSuccess) {
                        Timber.d("=== Platform Upload SUCCESS ===")
                        Result.success(responseBody)
                    } else {
                        Timber.e("=== Platform Upload FAILED: ${responseBody.message} ===")
                        Result.failure(Exception(responseBody.message))
                    }
                } else {
                    Timber.e("=== Platform Upload HTTP ERROR: ${response.status} ===")
                    Result.failure(Exception("HTTP ${response.status}"))
                }
            } catch (e: java.util.concurrent.CancellationException) {
                Timber.w("Platform upload cancelled")
                Result.failure(e)
            } catch (e: Exception) {
                Timber.e(e, "=== Platform Upload EXCEPTION ===")
                Result.failure(e)
            }
        }
    }

    private fun getImageBase64(imagePath: String?): String {
        if (imagePath.isNullOrBlank()) return ""

        val file = FileUtil.getRecordImageFile(context, imagePath) ?: return ""
        if (!file.exists()) {
            Timber.w("Image file not found: $imagePath")
            return ""
        }

        return try {
            // 1. 读取图片尺寸
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(file.absolutePath, options)

            val srcWidth = options.outWidth
            val srcHeight = options.outHeight
            if (srcWidth <= 0 || srcHeight <= 0) {
                Timber.w("Invalid image dimensions: ${srcWidth}x$srcHeight for $imagePath")
                return ""
            }

            // 2. 计算采样率（最大边不超过 1280）
            val maxDimension = 1280
            var inSampleSize = 1
            while ((srcWidth / inSampleSize > maxDimension) || (srcHeight / inSampleSize > maxDimension)) {
                inSampleSize *= 2
            }

            // 3. 解码压缩后的 Bitmap
            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                this.inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bitmap = BitmapFactory.decodeFile(file.absolutePath, decodeOptions)
                ?: run {
                    Timber.w("Failed to decode bitmap: $imagePath")
                    return ""
                }

            // 4. 如果采样后仍超过最大尺寸，继续按比例缩放
            val scaledBitmap = if (bitmap.width > maxDimension || bitmap.height > maxDimension) {
                val scale = maxDimension.toFloat() / maxOf(bitmap.width, bitmap.height)
                val newWidth = (bitmap.width * scale).toInt()
                val newHeight = (bitmap.height * scale).toInt()
                Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true).also {
                    if (it != bitmap) bitmap.recycle()
                }
            } else {
                bitmap
            }

            // 5. 压缩为 JPEG 并转 Base64
            val outputStream = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
            val bytes = outputStream.toByteArray()
            outputStream.close()
            scaledBitmap.recycle()

            val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            Timber.d("Image compressed: $imagePath -> ${bytes.size / 1024} KB (base64: ${base64.length / 1024} KB)")
            base64
        } catch (e: Exception) {
            Timber.w(e, "Failed to compress image to base64: $imagePath")
            ""
        }
    }
}
