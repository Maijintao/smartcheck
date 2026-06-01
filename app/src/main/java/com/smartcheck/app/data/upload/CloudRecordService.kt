package com.smartcheck.app.data.upload

import android.content.Context
import android.util.Base64
import com.smartcheck.app.api.ProvincePlatformService
import com.smartcheck.app.api.model.CloudCheckRecordRequest
import com.smartcheck.app.api.model.CloudCheckRecordResponse
import com.smartcheck.app.api.model.HandCheckParam
import com.smartcheck.app.api.model.MorningCheckEmployee
import com.smartcheck.app.api.model.MorningCheckUploadRequest
import com.smartcheck.app.api.model.MorningCheckUploadResponse
import com.smartcheck.app.api.model.ProvinceMorningCheckUpload
import com.smartcheck.app.data.repository.ProvincePlatformRepository
import com.smartcheck.app.data.repository.SettingsRepository
import com.smartcheck.app.domain.model.HealthCertStatus
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
import java.net.InetAddress
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CloudRecordService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val httpClient: HttpClient,
    private val settingsRepository: SettingsRepository,
    private val provincePlatformService: ProvincePlatformService,
    private val provincePlatformRepository: ProvincePlatformRepository
) {
    companion object {
        private const val BASE_URL = "http://api.qhk12.iyouxin.cn:50082"
        private const val ENDPOINT = "/kitchen/morningCheck/saveData"
        private const val PLATFORM_ENDPOINT = "/api/device/morning-check/upload"
    }

    /**
     * 上报到省平台（替代原平台上传接口）
     *
     * 自动处理登录、SM4 加密、数据字段映射
     */
    suspend fun uploadToPlatform(record: Record, deviceId: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                Timber.d("=== Province Platform Upload Start ===")
                Timber.d("Device ID: $deviceId, Record: ${record.employeeId} / ${record.userName}")

                // 1. 确保已登录
                val loginResult = provincePlatformRepository.ensureLogin()
                if (loginResult.isFailure) {
                    val error = loginResult.exceptionOrNull()
                    if (error != null) {
                        Timber.e("Province platform login failed, cannot upload: ${error.message}")
                    } else {
                        Timber.e("Province platform login failed, cannot upload")
                    }
                    return@withContext Result.failure(
                        error ?: Exception("省平台登录失败")
                    )
                }

                val orgId = settingsRepository.provincePlatformOrgId.value
                if (orgId == 0) {
                    Timber.e("Province platform orgId not available")
                    return@withContext Result.failure(Exception("组织ID未获取"))
                }

                // 2. 图片转 Base64
                val palmImage = getImageBase64(record.handPalmPath)
                val backImage = getImageBase64(record.handBackPath)
                val faceImage = getImageBase64(record.faceImagePath)
                Timber.d("Image sizes - palm: ${palmImage.length}, back: ${backImage.length}, face: ${faceImage.length}")

                // 3. 健康证状态映射
                val healthCertState = when (record.healthCertStatus) {
                    HealthCertStatus.VALID -> 1
                    else -> 0
                }

                // 4. 晨检结果
                val health = if (record.isPassed) "健康" else "异常"

                // 5. 组装上传数据
                val uploadData = ProvinceMorningCheckUpload(
                    orgId = orgId,
                    personName = record.userName,
                    idCard = record.employeeId,
                    temperature = record.temperature.toString(),
                    health = health,
                    checkDate = formatDateTime(record.checkTime),
                    picture_img = palmImage.takeIf { it.isNotEmpty() },
                    picture_back_img = backImage.takeIf { it.isNotEmpty() },
                    scene_img = faceImage.takeIf { it.isNotEmpty() },
                    health_certificate_state = healthCertState
                )

                // 6. 上传
                val uploadResult = provincePlatformService.uploadMorningCheckData(listOf(uploadData))

                if (uploadResult.isSuccess) {
                    val response = uploadResult.getOrNull()!!
                    if (response.statuCode == 200) {
                        Timber.d("=== Province Platform Upload SUCCESS ===")
                        Result.success(Unit)
                    } else {
                        val msg: String = response.info
                        Timber.e("=== Province Platform Upload FAILED: $msg ===")
                        Result.failure(Exception(msg))
                    }
                } else {
                    val e = uploadResult.exceptionOrNull()
                    Timber.e("=== Province Platform Upload FAILED: ${e?.message} ===")
                    Result.failure(e ?: Exception("上传失败"))
                }
            } catch (e: java.util.concurrent.CancellationException) {
                Timber.w("Province platform upload cancelled")
                Result.failure(e)
            } catch (e: Exception) {
                Timber.e(e, "=== Province Platform Upload EXCEPTION ===")
                Result.failure(e)
            }
        }
    }

    /**
     * 格式化时间戳为省平台要求的格式：yyyy-MM-dd HH:mm:ss
     */
    private fun formatDateTime(timestamp: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return sdf.format(java.util.Date(timestamp))
    }

    /**
     * 旧接口：客户云端上报（保留兼容）
     */
    suspend fun uploadCheckRecord(record: Record, deviceSn: String): Result<CloudCheckRecordResponse> {
        return withContext(Dispatchers.IO) {
            try {
                Timber.d("=== Cloud Record Upload Start (legacy) ===")
                Timber.d("Record: personCode=${record.employeeId}, personName=${record.userName}, temp=${record.temperature}, isPassed=${record.isPassed}")
                Timber.d("Hand paths - palm=${record.handPalmPath}, back=${record.handBackPath}")
                Timber.d("Device SN: $deviceSn")

                val deviceIp = getDeviceIp() ?: ""
                Timber.d("Device IP: $deviceIp")

                val facePhoto = getImageBase64(record.faceImagePath)
                val handPalmPhoto = getImageBase64(record.handPalmPath)
                val handBackPhoto = getImageBase64(record.handBackPath)
                Timber.d("Image sizes - face: ${facePhoto.length}, palm: ${handPalmPhoto.length}, back: ${handBackPhoto.length}")

                val temperatureType = if (record.isTempNormal) 0 else 1
                val handResult = when {
                    record.isHandNormal -> "true"
                    record.isHandNormal == false -> "false"
                    else -> "unknown"
                }
                // 晨检结果：体温正常 且 手部正常 才通过
                val result = if (record.isTempNormal && record.isHandNormal) "true" else "false"
                Timber.d("temperatureType=$temperatureType, result=$result, handResult=$handResult")

                val handCheckParam = HandCheckParam(
                    result = handResult,
                    handPalmPhoto = handPalmPhoto,
                    handBackPhoto = handBackPhoto
                )

                val request = CloudCheckRecordRequest(
                    deviceIp = deviceIp,
                    deviceSn = deviceSn,
                    personCode = record.employeeId,
                    personName = record.userName,
                    photo = facePhoto,
                    timestamp = record.checkTime,
                    verificationMode = 9,
                    temperature = record.temperature.toString(),
                    temperatureType = temperatureType,
                    result = result,
                    handCheck = handCheckParam,
                    recognitionType = 1,
                    livenessType = 1,
                    maskType = 1,
                    healthyState = 0,
                    passType = 1,
                    serverVerify = "0",
                    verificationType = 0
                )

                Timber.d("Sending POST request to: $BASE_URL$ENDPOINT")

                val response = httpClient.post("$BASE_URL$ENDPOINT") {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }

                Timber.d("Response status: ${response.status}")

                if (response.status.isSuccess()) {
                    // 打印原始响应
                    val rawBody: String = response.body()
                    Timber.d("Raw response body: $rawBody")

                    val responseBody = response.body<CloudCheckRecordResponse>()
                    Timber.d("Response body: code=${responseBody.code}, isSuccess=${responseBody.isSuccess}, message=${responseBody.message}")

                    if (responseBody.isSuccess) {
                        Timber.d("=== Cloud Record Upload SUCCESS ===")
                        Result.success(responseBody)
                    } else {
                        Timber.e("=== Cloud Record Upload FAILED: ${responseBody.message} ===")
                        Result.failure(Exception(responseBody.message))
                    }
                } else {
                    Timber.e("=== Cloud Record Upload HTTP ERROR: ${response.status} ===")
                    Result.failure(Exception("HTTP ${response.status}"))
                }
            } catch (e: java.util.concurrent.CancellationException) {
                Timber.w("Cloud record upload cancelled (ViewModel destroyed)")
                Result.failure(e)
            } catch (e: Exception) {
                Timber.e(e, "=== Cloud Record Upload EXCEPTION ===")
                Result.failure(e)
            }
        }
    }

    private fun getImageBase64(imagePath: String?): String {
        if (imagePath.isNullOrBlank()) return ""
        return try {
            val bitmap = FileUtil.loadBitmapFromInternal(context, imagePath)
            if (bitmap != null) {
                val bos = java.io.ByteArrayOutputStream()
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, bos)
                val bytes = bos.toByteArray()
                Base64.encodeToString(bytes, Base64.DEFAULT)
            } else {
                ""
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to convert image to base64: $imagePath")
            ""
        }
    }

    private fun getDeviceIp(): String? {
        return try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (!address.isLoopbackAddress && address is InetAddress) {
                        val ip = address.hostAddress
                        if (ip.contains(".")) {
                            return ip
                        }
                    }
                }
            }
            null
        } catch (e: Exception) {
            Timber.w(e, "Failed to get device IP")
            null
        }
    }
}
