package com.smartcheck.app.viewmodel

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartcheck.app.api.model.CloudStaffItem
import com.smartcheck.app.api.model.EmployeeImportItem
import com.smartcheck.app.domain.model.User
import com.smartcheck.app.domain.repository.IUserRepository
import com.smartcheck.app.data.repository.SettingsRepository
import com.smartcheck.app.domain.usecase.ImageStorageUseCase
import com.smartcheck.app.ml.FaceEngine
import com.smartcheck.sdk.face.FaceSdk
import dagger.hilt.android.lifecycle.HiltViewModel
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class CloudImportViewModel @Inject constructor(
    private val userRepository: IUserRepository,
    private val httpClient: HttpClient,
    private val imageStorageUseCase: ImageStorageUseCase,
    private val faceEngine: FaceEngine,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    data class CloudEmployeeItem(
        val employeeId: String,
        val name: String,
        val phone: String,
        val position: String,
        val healthCertCode: String,
        val facePicUrl: String,
        val healthCertPicUrl: String,
        val healthCertStartDate: String,
        val healthCertEndDate: String,
        val selected: Boolean = true
    )

    data class ImportResult(
        val total: Int,
        val success: Int,
        val failed: Int,
        val message: String
    )

    data class ImportProgress(
        val current: Int,
        val total: Int
    )

    data class UiState(
        val deviceSn: String = "",
        val pageIndex: Int = 0,
        val pageSize: String = "",
        val isLoading: Boolean = false,
        val employees: List<CloudEmployeeItem> = emptyList(),
        val total: Int = 0,
        val error: String? = null,
        val importResult: ImportResult? = null,
        val importSuccess: Boolean = false,
        val snHistory: List<String> = emptyList(),
        val importProgress: ImportProgress? = null
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        _uiState.value = _uiState.value.copy(
            deviceSn = settingsRepository.getDeviceSn(),
            snHistory = settingsRepository.getDeviceSnHistory()
        )
    }

    fun setDeviceSn(sn: String) {
        _uiState.value = _uiState.value.copy(deviceSn = sn)
    }

    fun selectHistorySn(sn: String) {
        _uiState.value = _uiState.value.copy(deviceSn = sn)
    }

    fun removeHistorySn(sn: String) {
        settingsRepository.removeDeviceSnHistory(sn)
        _uiState.value = _uiState.value.copy(snHistory = settingsRepository.getDeviceSnHistory())
    }

    fun setPageIndex(index: Int) {
        _uiState.value = _uiState.value.copy(pageIndex = index)
    }

    fun setPageSize(size: String) {
        _uiState.value = _uiState.value.copy(pageSize = size)
    }
    
    fun getPageSizeInt(): Int {
        return _uiState.value.pageSize.toIntOrNull()?.coerceIn(1, 100) ?: 50
    }

    fun toggleEmployeeSelection(employeeId: String) {
        val currentEmployees = _uiState.value.employees
        val updatedEmployees = currentEmployees.map { emp ->
            if (emp.employeeId == employeeId) {
                emp.copy(selected = !emp.selected)
            } else {
                emp
            }
        }
        _uiState.value = _uiState.value.copy(employees = updatedEmployees)
    }

    fun selectAll(selected: Boolean) {
        val updatedEmployees = _uiState.value.employees.map { it.copy(selected = selected) }
        _uiState.value = _uiState.value.copy(employees = updatedEmployees)
    }

    fun fetchEmployees() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                val baseUrl = "http://api.kitchen.iyouxin.cn"
                val endpoint = "/wosapi/YGCJRobotOpenApi/PageStaff"

                Timber.d("Fetching employees from: $baseUrl$endpoint, deviceSn=${_uiState.value.deviceSn}")

                // 创建请求体（yg_sn 只通过 header 传递，不在 body 中）
                // 手动构建 JSON 以避免 encodeDefaults=true 导致多余字段被序列化
                val jsonBody = """{"pageIndex":${_uiState.value.pageIndex},"pageSize":${getPageSizeInt()}}"""
                Timber.d("Request JSON: $jsonBody")

                val response = httpClient.post("$baseUrl$endpoint") {
                    header("yg_sn", _uiState.value.deviceSn)
                    contentType(ContentType.Application.Json)
                    setBody(io.ktor.http.content.TextContent(jsonBody, ContentType.Application.Json))
                }

                if (response.status.isSuccess()) {
                    // 打印原始响应内容用于调试
                    val rawBody: String = response.body()
                    Timber.d("Raw response: $rawBody")
                    
                    // 检查错误响应
                    if (rawBody.contains("\"IsSuccess\":false") || rawBody.contains("\"code\":500") || rawBody.contains("\"code\":405")) {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = "接口错误: $rawBody"
                        )
                        return@launch
                    }
                    
                    val result = response.body<com.smartcheck.app.api.model.CloudStaffResponse>()
                    
                    if (result.isSuccess) {
                        val employees = result.dataList.map { item ->
                            Timber.d("[CloudImport] 员工: ${item.personName}, facePicUrl='${item.faceToFacePicUrl}', hcPicUrl='${item.hcPicUrl}'")
                            CloudEmployeeItem(
                                employeeId = item.thirdKey,
                                name = item.personName,
                                phone = item.phone,
                                position = item.position,
                                healthCertCode = item.hcCode,
                                facePicUrl = item.faceToFacePicUrl,
                                healthCertPicUrl = item.hcPicUrl,
                                healthCertStartDate = item.hcStartTime,
                                healthCertEndDate = item.hcEndTime
                            )
                        }
                        // 保存SN码到历史记录
                        val currentSn = _uiState.value.deviceSn
                        if (currentSn.isNotBlank()) {
                            settingsRepository.addDeviceSnHistory(currentSn)
                        }
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            employees = employees,
                            total = result.total,
                            error = null,
                            snHistory = settingsRepository.getDeviceSnHistory()
                        )
                        Timber.d("Fetched ${employees.size} employees, total: ${result.total}")
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = result.message.ifEmpty { "获取失败" }
                        )
                    }
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "请求失败: ${response.status}"
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to fetch employees")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "网络错误: ${e.message}"
                )
            }
        }
    }

    fun importSelectedEmployees() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, importProgress = null)

            val selectedEmployees = _uiState.value.employees.filter { it.selected }
            if (selectedEmployees.isEmpty()) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "请选择要导入的员工"
                )
                return@launch
            }

            var successCount = 0
            var failedCount = 0
            val total = selectedEmployees.size

            for ((index, cloudEmp) in selectedEmployees.withIndex()) {
                // 更新进度
                _uiState.value = _uiState.value.copy(
                    importProgress = ImportProgress(current = index + 1, total = total)
                )

                try {
                    val existingUser = userRepository.getUserByEmployeeId(cloudEmp.employeeId).getOrNull()
                    Timber.d("[CloudImport] 导入 ${cloudEmp.name}(${cloudEmp.employeeId}), facePicUrl='${cloudEmp.facePicUrl}', 已存在=${existingUser != null}")

                    val healthCertStartDate = parseDate(cloudEmp.healthCertStartDate)
                    val healthCertEndDate = parseDate(cloudEmp.healthCertEndDate)

                    var faceImageBase64: String? = null
                    if (cloudEmp.facePicUrl.isNotBlank()) {
                        Timber.d("[CloudImport] 下载人脸图片: ${cloudEmp.facePicUrl}")
                        faceImageBase64 = downloadImageAsBase64(cloudEmp.facePicUrl)
                        Timber.d("[CloudImport] 人脸图片下载结果: ${if (faceImageBase64 != null) "${faceImageBase64.length} chars" else "FAILED"}")
                    } else {
                        Timber.w("[CloudImport] ${cloudEmp.name} 无人脸图片URL，跳过人脸特征提取")
                    }

                    var healthCertImageBase64: String? = null
                    if (cloudEmp.healthCertPicUrl.isNotBlank()) {
                        Timber.d("[CloudImport] 下载健康证图片: ${cloudEmp.healthCertPicUrl}")
                        healthCertImageBase64 = downloadImageAsBase64(cloudEmp.healthCertPicUrl)
                        Timber.d("[CloudImport] 健康证图片下载结果: ${if (healthCertImageBase64 != null) "${healthCertImageBase64.length} chars" else "FAILED"}")
                    } else {
                        Timber.w("[CloudImport] ${cloudEmp.name} 无健康证图片URL")
                    }

                    if (existingUser != null) {
                        var faceImagePath = existingUser.faceImagePath
                        var faceEmbedding = existingUser.faceEmbedding
                        var healthCertImagePath = existingUser.healthCertImagePath

                        // 如果有新的人脸图片，下载并提取特征
                        if (faceImageBase64 != null) {
                            val saveResult = saveFaceImageFromBase64(faceImageBase64, existingUser.employeeId)
                            saveResult.onSuccess { (path, embedding) ->
                                faceImagePath = path
                                faceEmbedding = embedding
                            }
                        }

                        // 如果有健康证图片，下载并保存
                        if (healthCertImageBase64 != null) {
                            val saveResult = saveHealthCertImageFromBase64(healthCertImageBase64, existingUser.employeeId)
                            saveResult.onSuccess { path ->
                                healthCertImagePath = path
                            }
                        }

                        val updatedUser = existingUser.copy(
                            name = cloudEmp.name,
                            phone = cloudEmp.phone,
                            position = cloudEmp.position,
                            healthCertCode = cloudEmp.healthCertCode,
                            healthCertStartDate = healthCertStartDate,
                            healthCertEndDate = healthCertEndDate,
                            faceImagePath = faceImagePath,
                            faceEmbedding = faceEmbedding,
                            healthCertImagePath = healthCertImagePath
                        )
                        userRepository.updateUser(updatedUser)
                    } else {
                        // 先创建用户
                        val newUser = User(
                            name = cloudEmp.name,
                            employeeId = cloudEmp.employeeId,
                            phone = cloudEmp.phone,
                            position = cloudEmp.position,
                            healthCertCode = cloudEmp.healthCertCode,
                            healthCertStartDate = healthCertStartDate,
                            healthCertEndDate = healthCertEndDate,
                            isActive = true
                        )
                        val userId = userRepository.createUser(newUser).getOrNull()

                        // 如果有人脸图片，保存并提取特征
                        var finalFaceImagePath = ""
                        var finalFaceEmbedding: ByteArray? = null

                        if (userId != null && faceImageBase64 != null) {
                            val saveResult = saveFaceImageFromBase64(faceImageBase64, cloudEmp.employeeId)
                            saveResult.onSuccess { (path, embedding) ->
                                finalFaceImagePath = path
                                finalFaceEmbedding = embedding
                            }
                        }

                        // 如果有健康证图片，下载并保存
                        var finalHealthCertImagePath = ""
                        if (userId != null && healthCertImageBase64 != null) {
                            val saveResult = saveHealthCertImageFromBase64(healthCertImageBase64, cloudEmp.employeeId)
                            saveResult.onSuccess { path ->
                                finalHealthCertImagePath = path
                            }
                        }

                        if (userId != null) {
                            val updatedUser = newUser.copy(
                                id = userId,
                                faceImagePath = finalFaceImagePath,
                                faceEmbedding = finalFaceEmbedding,
                                healthCertImagePath = finalHealthCertImagePath
                            )
                            userRepository.updateUser(updatedUser)
                        }
                    }
                    successCount++
                } catch (e: Exception) {
                    Timber.e(e, "Failed to import employee: ${cloudEmp.employeeId}")
                    failedCount++
                }
            }

            // 刷新人脸特征缓存
            Timber.d("[CloudImport] Refreshing face cache after import")
            faceEngine.refreshUserCache()

            // 验证导入后的用户特征状态
            for (cloudEmp in selectedEmployees) {
                val user = userRepository.getUserByEmployeeId(cloudEmp.employeeId).getOrNull()
                if (user != null) {
                    val embSize = user.faceEmbedding?.size ?: 0
                    val embPreview = if (embSize > 0) user.faceEmbedding!!.take(5).joinToString() else "EMPTY"
                    Timber.d("[CloudImport] 验证: ${user.name}(${user.employeeId}) -> embedding size=$embSize, preview=[$embPreview], faceImagePath='${user.faceImagePath}', healthCertImagePath='${user.healthCertImagePath}'")
                } else {
                    Timber.w("[CloudImport] 验证失败: ${cloudEmp.employeeId} 导入后未找到用户")
                }
            }

            _uiState.value = _uiState.value.copy(
                isLoading = false,
                importResult = ImportResult(
                    total = selectedEmployees.size,
                    success = successCount,
                    failed = failedCount,
                    message = "导入完成"
                ),
                importSuccess = true,
                importProgress = null
            )
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun clearImportResult() {
        _uiState.value = _uiState.value.copy(importResult = null, importSuccess = false)
    }

    private suspend fun parseDate(dateStr: String): Long? = withContext(Dispatchers.Default) {
        if (dateStr.isBlank()) return@withContext null
        try {
            val formats = listOf(
                SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()),
                SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()),
                SimpleDateFormat("yyyyMMdd", Locale.getDefault())
            )
            for (format in formats) {
                try {
                    return@withContext format.parse(dateStr)?.time
                } catch (e: Exception) {
                    continue
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun downloadImageAsBase64(url: String): String? = withContext(Dispatchers.IO) {
        try {
            if (url.isBlank()) return@withContext null
            
            val fullUrl = if (url.startsWith("http")) url else "http://api.kitchen.iyouxin.cn$url"
            Timber.d("Downloading image from: $fullUrl")
            
            val response = httpClient.get(fullUrl) {
                header("yg_sn", _uiState.value.deviceSn)
            }
            if (response.status.isSuccess()) {
                val bytes: ByteArray = response.body()
                Base64.encodeToString(bytes, Base64.DEFAULT)
            } else {
                Timber.w("Image download failed: ${response.status}")
                null
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to download image: $url")
            null
        }
    }

    private suspend fun saveFaceImageFromBase64(base64: String, employeeId: String): Result<Pair<String, ByteArray>> = withContext(Dispatchers.IO) {
        try {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            Timber.d("[CloudImport] 下载图片字节大小: ${bytes.size} bytes, employeeId=$employeeId")
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: return@withContext Result.failure(Exception("Failed to decode image, bytes size=${bytes.size}"))

            Timber.d("[CloudImport] Decoded image for $employeeId: ${bitmap.width}x${bitmap.height}")

            // 保存图片
            val imageSaveResult = imageStorageUseCase.saveFaceImage(bitmap)
            if (imageSaveResult.isFailure) {
                return@withContext Result.failure(imageSaveResult.exceptionOrNull() ?: Exception("Failed to save image"))
            }
            val faceImagePath = imageSaveResult.getOrNull() ?: ""

            // 提取人脸特征
            Timber.d("[CloudImport] Extracting face embedding for $employeeId, bitmap=${bitmap.width}x${bitmap.height}, config=${bitmap.config}")
            val faceEmbedding = FaceSdk.extractFeature(bitmap)
            Timber.d("[CloudImport] FaceSdk.extractFeature result for $employeeId: ${if (faceEmbedding != null) "non-null, size=${faceEmbedding.size}" else "NULL"}")

            if (faceEmbedding != null && faceEmbedding.isNotEmpty()) {
                Timber.d("[CloudImport] Face embedding extracted successfully for $employeeId, size=${faceEmbedding.size}")
                // FloatArray 转 ByteArray
                val embeddingBytes = floatArrayToByteArray(faceEmbedding)
                Timber.d("[CloudImport] Embedding bytes size for $employeeId: ${embeddingBytes.size}")
                Result.success(Pair(faceImagePath, embeddingBytes))
            } else {
                Timber.w("[CloudImport] Failed to extract face embedding for $employeeId - 可能图片中没有检测到人脸")
                Result.success(Pair(faceImagePath, ByteArray(0)))
            }
        } catch (e: Exception) {
            Timber.e(e, "[CloudImport] Failed to save face image for employee: $employeeId")
            Result.failure(e)
        }
    }

    private suspend fun saveHealthCertImageFromBase64(base64: String, employeeId: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: return@withContext Result.failure(Exception("Failed to decode health cert image"))

            Timber.d("[CloudImport] Decoded health cert image for $employeeId: ${bitmap.width}x${bitmap.height}")

            val imageSaveResult = imageStorageUseCase.saveHealthCertImage(bitmap)
            if (imageSaveResult.isFailure) {
                return@withContext Result.failure(imageSaveResult.exceptionOrNull() ?: Exception("Failed to save health cert image"))
            }
            val healthCertImagePath = imageSaveResult.getOrNull() ?: ""
            Timber.d("[CloudImport] Health cert image saved for $employeeId: $healthCertImagePath")
            Result.success(healthCertImagePath)
        } catch (e: Exception) {
            Timber.e(e, "[CloudImport] Failed to save health cert image for employee: $employeeId")
            Result.failure(e)
        }
    }

    private fun floatArrayToByteArray(floatArray: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(floatArray.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        buffer.asFloatBuffer().put(floatArray)
        return buffer.array()
    }
}
