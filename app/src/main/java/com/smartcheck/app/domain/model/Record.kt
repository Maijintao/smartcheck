package com.smartcheck.app.domain.model

import java.util.UUID

data class Record(
    val id: Long = 0,
    val recordUuid: String = UUID.randomUUID().toString(),
    val uploadDeviceId: String = "",
    val userId: Long,
    val userName: String,
    val employeeId: String,
    val temperature: Float,
    val isTempNormal: Boolean,
    val isHandNormal: Boolean,
    val isPassed: Boolean,
    val handStatus: HandStatus = HandStatus.NOT_CHECKED,
    val handAbnormalTypes: List<String> = emptyList(),
    val healthCertStatus: HealthCertStatus = HealthCertStatus.VALID,
    val symptomFlags: List<SymptomType> = emptyList(),
    val faceImagePath: String? = null,
    val handPalmPath: String? = null,
    val handBackPath: String? = null,
    val checkTime: Long = System.currentTimeMillis(),
    val remark: String = "",
    val isUploaded: Boolean = false,
    val uploadStatus: UploadStatus = UploadStatus.PENDING,
    val uploadRetryCount: Int = 0,
    val nextUploadAttemptAt: Long = 0,
    val uploadLastError: String? = null
)
