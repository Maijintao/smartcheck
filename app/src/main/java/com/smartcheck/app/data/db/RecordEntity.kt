package com.smartcheck.app.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 晨检记录实体
 */
@Entity(
    tableName = "check_records",
    indices = [Index(value = ["recordUuid"], unique = true)]
)
data class RecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val recordUuid: String = "",
    val uploadDeviceId: String = "",
    
    val userId: Long,
    val userName: String,
    val employeeId: String,
    
    // 检测结果
    val temperature: Float,
    val isTempNormal: Boolean,
    val isHandNormal: Boolean,
    val isPassed: Boolean,
    val handStatus: String = "",
    val handAbnormalTypes: String = "",
    val healthCertStatus: String = "",
    val symptomFlags: String = "",
    val faceImagePath: String? = null,
    val handPalmPath: String? = null,
    val handBackPath: String? = null,
    
    // 时间戳
    val checkTime: Long = System.currentTimeMillis(),

    // 备注
    val remark: String = "",

    // 云端上传状态
    val isUploaded: Boolean = false,
    val uploadStatus: String = "PENDING",
    val uploadRetryCount: Int = 0,
    val nextUploadAttemptAt: Long = 0,
    val uploadLastError: String? = null
)
