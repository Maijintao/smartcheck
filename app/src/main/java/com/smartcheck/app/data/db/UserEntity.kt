package com.smartcheck.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 员工信息实体
 */
@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    val name: String,
    val employeeId: String,
    val idCardNumber: String = "",
    val phone: String = "",
    val position: String = "",
    val department: String = "",
    val healthCertCode: String = "",
    val healthCertImagePath: String = "",
    val healthCertStartDate: Long? = null,
    val healthCertEndDate: Long? = null,
    val faceImagePath: String? = null,

    // 人脸特征向量（SeetaFace6 提取）
    val faceEmbedding: ByteArray? = null,

    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),

    // === 平台同步字段（v10 新增）===

    /** 平台员工版本号，设备只保存平台返回的值 */
    val platformVersion: Long = 0,

    /** 平台人脸照片 file_id */
    val faceImageFileId: String? = null,

    /** 平台人脸照片 SHA-256 */
    val faceImageSha256: String? = null,

    /** 平台健康证照片 file_id */
    val healthCertImageFileId: String? = null,

    /** 平台健康证照片 SHA-256 */
    val healthCertImageSha256: String? = null,

    /** 同步状态：SYNCED / PENDING_UPLOAD / CONFLICT / RECOVERY_REQUIRED */
    val syncStatus: String = "SYNCED"
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UserEntity

        if (id != other.id) return false
        if (name != other.name) return false
        if (employeeId != other.employeeId) return false
        if (platformVersion != other.platformVersion) return false
        if (faceEmbedding != null) {
            if (other.faceEmbedding == null) return false
            if (!faceEmbedding.contentEquals(other.faceEmbedding)) return false
        } else if (other.faceEmbedding != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + employeeId.hashCode()
        result = 31 * result + (faceEmbedding?.contentHashCode() ?: 0)
        return result
    }
}
