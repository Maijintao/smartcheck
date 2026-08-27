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
import com.smartcheck.app.domain.model.HandStatus
import com.smartcheck.app.utils.FileUtil
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.net.URI
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
        private const val MAX_IMAGE_DIMENSION = 1280
        private const val MAX_IMAGE_BYTES = 2 * 1024 * 1024
        private const val MAX_BASE64_PAYLOAD_CHARS = 9 * 1024 * 1024
        private const val MAX_REQUEST_BYTES = 10 * 1024 * 1024
        private val JPEG_QUALITIES = intArrayOf(80, 70, 60, 50)
        private val ALLOWED_HAND_ABNORMAL_TYPES = setOf("band_aid", "bracelet", "ring", "watch", "foreign")
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
                    return@withContext Result.failure(PermanentUploadException("平台地址或API Key未配置"))
                }
                if (!runCatching { URI(platformUrl).scheme.equals("https", ignoreCase = true) }.getOrDefault(false)) {
                    return@withContext Result.failure(PermanentUploadException("晨检记录上报地址必须使用HTTPS"))
                }
                if (deviceId.isBlank() || record.recordUuid.isBlank() || record.employeeId.isBlank()) {
                    return@withContext Result.failure(
                        PermanentUploadException("device_id、employees[].employee_id和record_uuid不能为空")
                    )
                }

                Timber.d("=== Platform Upload Start ===")
                Timber.d("Device ID: $deviceId")
                Timber.d("Record fields: employeeId=${record.employeeId}, userName='${record.userName}', temp=${record.temperature}, checkTime=${record.checkTime}")
                Timber.d("Image paths: face=${record.faceImagePath}, palm=${record.handPalmPath}, back=${record.handBackPath}")

                val facePhoto = getImageBase64(record.faceImagePath)
                val palmPhoto = getImageBase64(record.handPalmPath)
                val backPhoto = getImageBase64(record.handBackPath)
                val base64PayloadChars = listOf(facePhoto, palmPhoto, backPhoto).sumOf { it?.length ?: 0 }
                if (base64PayloadChars > MAX_BASE64_PAYLOAD_CHARS) {
                    return@withContext Result.failure(PermanentUploadException("图片Base64总大小超过9MiB"))
                }
                Timber.d(
                    "Base64 lengths: face=${facePhoto?.length ?: 0}, " +
                        "palm=${palmPhoto?.length ?: 0}, back=${backPhoto?.length ?: 0}"
                )

                val handAbnormalTypes = if (record.handStatus == HandStatus.ABNORMAL) {
                    record.handAbnormalTypes
                        .map { it.trim().lowercase() }
                        .filter { it in ALLOWED_HAND_ABNORMAL_TYPES }
                        .distinct()
                        .ifEmpty { listOf("foreign") }
                } else {
                    emptyList()
                }

                val employee = MorningCheckEmployee(
                    employeeId = record.employeeId,
                    name = record.userName,
                    recordId = record.id,
                    recordUuid = record.recordUuid,
                    userId = record.userId,
                    temperature = record.temperature,
                    isTempNormal = record.isTempNormal,
                    isHandNormal = when (record.handStatus) {
                        HandStatus.NORMAL -> true
                        HandStatus.ABNORMAL -> false
                        HandStatus.NOT_CHECKED -> null
                    },
                    isPassed = record.isPassed,
                    handStatus = record.handStatus.name,
                    hasForeignObject = when (record.handStatus) {
                        HandStatus.NORMAL -> false
                        HandStatus.ABNORMAL -> true
                        HandStatus.NOT_CHECKED -> null
                    },
                    handAbnormalTypes = handAbnormalTypes,
                    healthCertStatus = record.healthCertStatus.name,
                    symptomFlags = record.symptomFlags.map { it.name },
                    remark = record.remark,
                    photo = facePhoto,
                    handPalmPhoto = palmPhoto,
                    handBackPhoto = backPhoto
                )

                val request = MorningCheckUploadRequest(
                    deviceId = deviceId,
                    timestamp = record.checkTime,
                    employees = listOf(employee)
                )
                val requestJson = Json.encodeToString(request)
                if (requestJson.toByteArray(Charsets.UTF_8).size > MAX_REQUEST_BYTES) {
                    return@withContext Result.failure(PermanentUploadException("请求总大小超过10MiB"))
                }
                Timber.d(
                    "Request: deviceId=$deviceId, timestamp=${record.checkTime}, " +
                        "recordUuid=${record.recordUuid}, employeeId=${record.employeeId}"
                )

                val url = "$platformUrl$PLATFORM_ENDPOINT"
                Timber.d("POST $url")

                val response = httpClient.post(url) {
                    contentType(ContentType.Application.Json)
                    header("api-key", apiKey)
                    setBody(requestJson)
                }

                Timber.d("Response status: ${response.status}")

                if (response.status.value == 200) {
                    val responseBody = response.body<MorningCheckUploadResponse>()
                    Timber.d("Response: code=${responseBody.code}, message=${responseBody.message}")

                    if (responseBody.isSuccess) {
                        if (responseBody.data?.recordIds?.size != 1) {
                            return@withContext Result.failure(
                                PermanentUploadException("平台成功响应必须返回且仅返回一个recordIds元素")
                            )
                        }
                        Timber.d("=== Platform Upload SUCCESS ===")
                        Result.success(responseBody)
                    } else {
                        Timber.e("=== Platform Upload FAILED: ${responseBody.message} ===")
                        if (responseBody.code >= 500) {
                            Result.failure(
                                RetryableUploadException("平台错误 ${responseBody.code}: ${responseBody.message}")
                            )
                        } else {
                            Result.failure(
                                PermanentUploadException("平台错误 ${responseBody.code}: ${responseBody.message}")
                            )
                        }
                    }
                } else {
                    val responseText = runCatching { response.bodyAsText() }.getOrDefault("")
                    val errorMessage = buildString {
                        append("HTTP ${response.status}")
                        if (responseText.isNotBlank()) {
                            append(": ")
                            append(responseText.take(2_000))
                        }
                    }
                    Timber.e("=== Platform Upload HTTP ERROR: $errorMessage ===")
                    if (response.status.value == 408 || response.status.value == 429 || response.status.value >= 500) {
                        Result.failure(RetryableUploadException(errorMessage))
                    } else {
                        Result.failure(PermanentUploadException(errorMessage))
                    }
                }
            } catch (e: java.util.concurrent.CancellationException) {
                Timber.w("Platform upload cancelled")
                throw e
            } catch (e: UploadException) {
                Timber.e(e, "=== Platform Upload FAILED ===")
                Result.failure(e)
            } catch (e: SerializationException) {
                Timber.e(e, "=== Platform Upload INVALID RESPONSE ===")
                Result.failure(PermanentUploadException("平台响应格式错误", e))
            } catch (e: Exception) {
                Timber.e(e, "=== Platform Upload EXCEPTION ===")
                Result.failure(RetryableUploadException("晨检记录上报网络异常", e))
            }
        }
    }

    private fun getImageBase64(imagePath: String?): String? {
        if (imagePath.isNullOrBlank()) return null

        val file = FileUtil.getRecordImageFile(context, imagePath)
            ?: throw PermanentUploadException("无法解析图片路径: $imagePath")
        if (!file.exists()) {
            throw PermanentUploadException("图片文件不存在: $imagePath")
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
                throw PermanentUploadException("图片格式无效: $imagePath")
            }

            var inSampleSize = 1
            while ((srcWidth / inSampleSize > MAX_IMAGE_DIMENSION) ||
                (srcHeight / inSampleSize > MAX_IMAGE_DIMENSION)
            ) {
                inSampleSize *= 2
            }

            // 3. 解码压缩后的 Bitmap
            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                this.inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bitmap = BitmapFactory.decodeFile(file.absolutePath, decodeOptions)
                ?: throw PermanentUploadException("无法解码图片: $imagePath")

            val scaledBitmap = if (bitmap.width > MAX_IMAGE_DIMENSION || bitmap.height > MAX_IMAGE_DIMENSION) {
                val scale = MAX_IMAGE_DIMENSION.toFloat() / maxOf(bitmap.width, bitmap.height)
                val newWidth = (bitmap.width * scale).toInt()
                val newHeight = (bitmap.height * scale).toInt()
                Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true).also {
                    if (it != bitmap) bitmap.recycle()
                }
            } else {
                bitmap
            }

            val bytes = try {
                var compressed: ByteArray? = null
                for (quality in JPEG_QUALITIES) {
                    val candidate = ByteArrayOutputStream().use { outputStream ->
                        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
                        outputStream.toByteArray()
                    }
                    if (candidate.size <= MAX_IMAGE_BYTES) {
                        compressed = candidate
                        break
                    }
                }
                compressed ?: throw PermanentUploadException("图片压缩后仍超过2MiB: $imagePath")
            } finally {
                scaledBitmap.recycle()
            }

            val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            Timber.d("Image compressed: $imagePath -> ${bytes.size / 1024} KB (base64: ${base64.length / 1024} KB)")
            base64
        } catch (e: PermanentUploadException) {
            throw e
        } catch (e: Exception) {
            throw PermanentUploadException("图片处理失败: $imagePath", e)
        }
    }
}

/** 上传失败分类决定队列继续退避重试，还是记录失败并停止自动重试。 */
sealed class UploadException(message: String, cause: Throwable? = null) : Exception(message, cause)

class RetryableUploadException(message: String, cause: Throwable? = null) : UploadException(message, cause)

class PermanentUploadException(message: String, cause: Throwable? = null) : UploadException(message, cause)
