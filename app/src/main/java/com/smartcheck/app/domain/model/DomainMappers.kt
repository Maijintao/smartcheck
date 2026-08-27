package com.smartcheck.app.domain.model

import com.smartcheck.app.data.db.RecordEntity
import com.smartcheck.app.data.db.UserEntity

fun UserEntity.toDomain(): User = User(
    id = id,
    name = name,
    employeeId = employeeId,
    idCardNumber = idCardNumber,
    phone = phone,
    position = position,
    department = department,
    healthCertCode = healthCertCode,
    faceImagePath = faceImagePath,
    faceEmbedding = faceEmbedding,
    healthCertImagePath = healthCertImagePath,
    healthCertStartDate = healthCertStartDate,
    healthCertEndDate = healthCertEndDate,
    isActive = isActive,
    createdAt = createdAt,
    platformVersion = platformVersion,
    faceImageFileId = faceImageFileId,
    faceImageSha256 = faceImageSha256,
    healthCertImageFileId = healthCertImageFileId,
    healthCertImageSha256 = healthCertImageSha256,
    syncStatus = syncStatus
)

fun User.toEntity(): UserEntity = UserEntity(
    id = id,
    name = name,
    employeeId = employeeId,
    idCardNumber = idCardNumber,
    phone = phone,
    position = position,
    department = department,
    healthCertCode = healthCertCode,
    faceImagePath = faceImagePath,
    faceEmbedding = faceEmbedding,
    healthCertImagePath = healthCertImagePath,
    healthCertStartDate = healthCertStartDate,
    healthCertEndDate = healthCertEndDate,
    isActive = isActive,
    createdAt = createdAt,
    platformVersion = platformVersion,
    faceImageFileId = faceImageFileId,
    faceImageSha256 = faceImageSha256,
    healthCertImageFileId = healthCertImageFileId,
    healthCertImageSha256 = healthCertImageSha256,
    syncStatus = syncStatus
)

fun RecordEntity.toDomain(): Record = Record(
    id = id,
    recordUuid = recordUuid,
    uploadDeviceId = uploadDeviceId,
    userId = userId,
    userName = userName,
    employeeId = employeeId,
    temperature = temperature,
    isTempNormal = isTempNormal,
    isHandNormal = isHandNormal,
    isPassed = isPassed,
    handStatus = handStatus.toHandStatus(),
    handAbnormalTypes = handAbnormalTypes.toStringList(),
    healthCertStatus = healthCertStatus.toHealthCertStatus(),
    symptomFlags = symptomFlags.toSymptomTypeList(),
    faceImagePath = faceImagePath,
    handPalmPath = handPalmPath,
    handBackPath = handBackPath,
    checkTime = checkTime,
    remark = remark,
    isUploaded = isUploaded,
    uploadStatus = uploadStatus.toUploadStatus(),
    uploadRetryCount = uploadRetryCount,
    nextUploadAttemptAt = nextUploadAttemptAt,
    uploadLastError = uploadLastError
)

fun Record.toEntity(): RecordEntity = RecordEntity(
    id = id,
    recordUuid = recordUuid,
    uploadDeviceId = uploadDeviceId,
    userId = userId,
    userName = userName,
    employeeId = employeeId,
    temperature = temperature,
    isTempNormal = isTempNormal,
    isHandNormal = isHandNormal,
    isPassed = isPassed,
    handStatus = handStatus.name,
    handAbnormalTypes = handAbnormalTypes.joinToString(","),
    healthCertStatus = healthCertStatus.name,
    symptomFlags = symptomFlags.joinToString(",") { it.name },
    faceImagePath = faceImagePath,
    handPalmPath = handPalmPath,
    handBackPath = handBackPath,
    checkTime = checkTime,
    remark = remark,
    isUploaded = isUploaded,
    uploadStatus = uploadStatus.name,
    uploadRetryCount = uploadRetryCount,
    nextUploadAttemptAt = nextUploadAttemptAt,
    uploadLastError = uploadLastError
)

private fun String.toHandStatus(): HandStatus = try {
    HandStatus.valueOf(this)
} catch (e: IllegalArgumentException) {
    HandStatus.NOT_CHECKED
}

private fun String.toHealthCertStatus(): HealthCertStatus = try {
    HealthCertStatus.valueOf(this)
} catch (e: IllegalArgumentException) {
    HealthCertStatus.VALID
}

private fun String.toUploadStatus(): UploadStatus = try {
    UploadStatus.valueOf(this)
} catch (e: IllegalArgumentException) {
    UploadStatus.PENDING
}

private fun String.toStringList(): List<String> = if (isBlank()) {
    emptyList()
} else {
    split(",").map { it.trim() }.filter { it.isNotEmpty() }
}

private fun String.toSymptomTypeList(): List<SymptomType> = if (this.isEmpty()) {
    emptyList()
} else {
    this.split(",").mapNotNull {
        try {
            SymptomType.valueOf(it)
        } catch (e: IllegalArgumentException) {
            null
        }
    }
}
