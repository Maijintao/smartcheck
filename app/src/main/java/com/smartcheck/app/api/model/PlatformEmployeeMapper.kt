package com.smartcheck.app.api.model

import com.smartcheck.app.data.db.UserEntity
import com.smartcheck.app.domain.model.User
import com.smartcheck.app.domain.model.toDomain
import java.time.LocalDate
import java.time.ZoneId

/**
 * 平台员工对象 ↔ 本地 User/Entity 双向转换
 */

// ==================== 平台 → 本地 ====================

/** PlatformEmployee → User 域对象 */
fun PlatformEmployee.toDomain(): User = User(
    id = 0,   // 本地 id 由 Room 自动生成
    name = name,
    employeeId = employeeId,
    idCardNumber = idCardNumber ?: "",
    phone = phone ?: "",
    position = position ?: "",
    department = "",
    healthCertCode = healthCertificate?.code ?: "",
    healthCertStartDate = healthCertificate?.startDate?.parseDateToMillis(),
    healthCertEndDate = healthCertificate?.endDate?.parseDateToMillis(),
    healthCertImagePath = "",  // 图片路径由下载后本地设置
    faceImagePath = null,      // 同上
    faceEmbedding = null,      // 特征由本地提取，不从平台获取
    isActive = status == "ACTIVE",
    createdAt = updatedAt,     // 平台 updated_at 作为创建时间参考
    platformVersion = version,
    faceImageFileId = faceImage?.fileId,
    faceImageSha256 = faceImage?.sha256,
    healthCertImageFileId = healthCertificate?.image?.fileId,
    healthCertImageSha256 = healthCertificate?.image?.sha256,
    syncStatus = "SYNCED"
)

/** PlatformEmployee → UserEntity */
fun PlatformEmployee.toEntity(): UserEntity = toDomain().toEntity()

// ==================== 本地 → 上传 ====================

/** User → UploadEmployee（用于 outbox 生成上传 payload） */
fun User.toUploadEmployee(
    faceImageAction: SyncImageAction,
    faceImageBase64: String? = null,
    faceImageSha256: String? = null,
    faceImageMimeType: String = "image/jpeg",
    healthCertImageAction: SyncImageAction,
    healthCertImageBase64: String? = null,
    healthCertImageSha256: String? = null,
    healthCertMimeType: String = "image/jpeg"
): UploadEmployee {

    val faceImage = ImageUploadPayload(
        action = faceImageAction,
        mimeType = if (faceImageAction == SyncImageAction.REPLACE) faceImageMimeType else null,
        sha256 = if (faceImageAction == SyncImageAction.REPLACE) faceImageSha256 else null,
        base64 = if (faceImageAction == SyncImageAction.REPLACE) faceImageBase64 else null
    )

    val healthCert = if (healthCertCode.isNotBlank() || healthCertStartDate != null) {
        HealthCertUploadPayload(
            code = healthCertCode,
            startDate = healthCertStartDate?.formatMillisToDate() ?: "",
            endDate = healthCertEndDate?.formatMillisToDate() ?: "",
            status = getHealthCertStatus().name,
            image = ImageUploadPayload(
                action = healthCertImageAction,
                mimeType = if (healthCertImageAction == SyncImageAction.REPLACE) healthCertMimeType else null,
                sha256 = if (healthCertImageAction == SyncImageAction.REPLACE) healthCertImageSha256 else null,
                base64 = if (healthCertImageAction == SyncImageAction.REPLACE) healthCertImageBase64 else null
            )
        )
    } else null

    return UploadEmployee(
        name = name,
        idCardNumber = idCardNumber.ifBlank { null },
        phone = phone.ifBlank { null },
        position = position.ifBlank { null },
        status = if (isActive) "ACTIVE" else "DISABLED",
        faceImage = faceImage,
        healthCertificate = healthCert
    )
}

// ==================== 工具函数 ====================

/** "yyyy-MM-dd" → 毫秒时间戳（线程安全，使用 java.time） */
fun String.parseDateToMillis(): Long? {
    if (isBlank()) return null
    return try {
        LocalDate.parse(this)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    } catch (e: Exception) {
        null
    }
}

/** 毫秒时间戳 → "yyyy-MM-dd"（线程安全，使用 java.time） */
fun Long.formatMillisToDate(): String {
    return try {
        java.time.Instant.ofEpochMilli(this)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .toString()
    } catch (e: Exception) {
        ""
    }
}
