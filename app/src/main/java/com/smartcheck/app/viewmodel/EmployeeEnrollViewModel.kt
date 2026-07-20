package com.smartcheck.app.viewmodel

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartcheck.app.data.sync.EmployeeSyncEngine
import com.smartcheck.app.data.sync.EmployeeSyncRepository
import com.smartcheck.app.domain.model.User
import com.smartcheck.app.domain.repository.IUserRepository
import com.smartcheck.app.ml.FaceEngine
import com.smartcheck.app.utils.FileUtil
import com.smartcheck.sdk.face.FaceSdk
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject

@HiltViewModel
class EmployeeEnrollViewModel @Inject constructor(
    private val userRepository: IUserRepository,
    private val faceEngine: FaceEngine,
    private val syncRepo: EmployeeSyncRepository,
    private val syncEngine: EmployeeSyncEngine,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    val users: Flow<List<User>> = userRepository.observeAllUsers()

    fun enrollWithFrame(
        name: String,
        employeeId: String,
        department: String,
        idCardNumber: String,
        healthCertImagePath: String,
        healthCertStartDate: Long?,
        healthCertEndDate: Long?,
        frame: Bitmap,
        onResult: (Long?) -> Unit
    ) {
        viewModelScope.launch {
            val result = enrollWithFrameSuspend(
                name, employeeId, department, idCardNumber,
                healthCertImagePath, healthCertStartDate, healthCertEndDate, frame
            )
            onResult(result)
        }
    }

    private suspend fun enrollWithFrameSuspend(
        name: String,
        employeeId: String,
        department: String,
        idCardNumber: String,
        healthCertImagePath: String,
        healthCertStartDate: Long?,
        healthCertEndDate: Long?,
        frame: Bitmap
    ): Long? = withContext(Dispatchers.IO) {
        val trimmedEmployeeId = employeeId.trim()
        val trimmedName = name.trim()
        val trimmedDepartment = department.trim()
        val trimmedIdCard = idCardNumber.trim()
        val trimmedCertPath = healthCertImagePath.trim()

        if (trimmedEmployeeId.isEmpty() || trimmedName.isEmpty()) return@withContext null
        if (healthCertStartDate != null && healthCertEndDate != null && healthCertEndDate < healthCertStartDate) {
            return@withContext null
        }

        // 保存人脸照片到文件（用于同步上传）
        var faceImageFileName: String? = null
        var faceEmbedding: ByteArray? = null
        try {
            faceImageFileName = "face_enroll_${System.currentTimeMillis()}.jpg"
            FileUtil.saveBitmapToInternal(appContext, frame, faceImageFileName)

            // 提取人脸特征
            synchronized(FaceSdk) {
                if (!FaceSdk.isInitialized()) {
                    FaceSdk.init(appContext)
                }
                val feature = FaceSdk.extractFeature(frame)
                if (feature != null) {
                    faceEmbedding = floatArrayToByteArray(feature)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "保存人脸照片/提取特征失败")
            faceImageFileName = null
        }

        // 检查健康证图片路径有效性
        val certPath = trimmedCertPath.ifBlank { null }

        val existingResult = userRepository.getUserByEmployeeId(trimmedEmployeeId)

        existingResult.fold(
            onSuccess = { existing ->
                // 更新已有员工（走 outbox）
                val updated = existing.copy(
                    name = trimmedName,
                    department = trimmedDepartment,
                    idCardNumber = trimmedIdCard,
                    healthCertImagePath = trimmedCertPath,
                    healthCertStartDate = healthCertStartDate,
                    healthCertEndDate = healthCertEndDate,
                    faceImagePath = faceImageFileName ?: existing.faceImagePath,
                    faceEmbedding = faceEmbedding ?: existing.faceEmbedding,
                    syncStatus = "PENDING_UPLOAD"
                )
                val updateResult = syncRepo.updateLocal(
                    updated,
                    faceImagePath = faceImageFileName,
                    certImagePath = certPath
                )
                if (updateResult.isSuccess) {
                    syncEngine.triggerSync()
                }
                existing.id
            },
            onFailure = {
                // 新增员工（走 outbox）
                val newUser = User(
                    name = trimmedName,
                    employeeId = trimmedEmployeeId,
                    idCardNumber = trimmedIdCard,
                    healthCertImagePath = trimmedCertPath,
                    healthCertStartDate = healthCertStartDate,
                    healthCertEndDate = healthCertEndDate,
                    department = trimmedDepartment,
                    faceImagePath = faceImageFileName,
                    faceEmbedding = faceEmbedding,
                    syncStatus = "PENDING_UPLOAD"
                )
                val createResult = syncRepo.createLocal(
                    newUser,
                    faceImagePath = faceImageFileName,
                    certImagePath = certPath
                )
                if (createResult.isSuccess) {
                    syncEngine.triggerSync()
                    // 获取 Room 生成的 ID
                    userRepository.getUserByEmployeeId(trimmedEmployeeId).getOrNull()?.id
                } else null
            }
        )
    }

    private fun floatArrayToByteArray(floatArray: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(floatArray.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        buffer.asFloatBuffer().put(floatArray)
        return buffer.array()
    }
}
