package com.smartcheck.app.api.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ============================================================
// 员工同步协议数据模型
// 严格匹配《晨检仪员工同步接口对接文档》v1.0
// ============================================================

// ==================== 枚举 ====================

/** 操作类型 */
@Serializable
enum class SyncOperationType {
    @SerialName("UPSERT") UPSERT,
    @SerialName("DELETE") DELETE
}

/** 图片操作 */
@Serializable
enum class SyncImageAction {
    @SerialName("KEEP") KEEP,       // 图片未变化，平台保留当前
    @SerialName("CLEAR") CLEAR,     // 删除平台当前图片
    @SerialName("REPLACE") REPLACE  // 替换为新图片
}

/** 上传操作结果状态 */
@Serializable
enum class SyncResultStatus {
    @SerialName("APPLIED") APPLIED,
    @SerialName("DUPLICATE") DUPLICATE,
    @SerialName("CONFLICT") CONFLICT,
    @SerialName("REJECTED") REJECTED
}

// ==================== 图片上传 ====================

/** 图片上传 payload（face_image / health_certificate.image 共用） */
@Serializable
data class ImageUploadPayload(
    val action: SyncImageAction,
    @SerialName("mime_type") val mimeType: String? = null,   // "image/jpeg" / "image/png"
    val sha256: String? = null,                               // 原始图片二进制 SHA-256
    val base64: String? = null                                // 原始图片 Base64，无 data: 前缀，无换行
)

// ==================== 图片引用（平台返回）====================

/** 平台返回的图片引用 */
@Serializable
data class ImageReference(
    @SerialName("file_id") val fileId: String,
    val sha256: String,
    @SerialName("download_url") val downloadUrl: String
)

// ==================== 健康证 ====================

/** 上传时的健康证（§7.2 嵌套在 employee 内） */
@Serializable
data class HealthCertUploadPayload(
    val code: String? = null,
    @SerialName("start_date") val startDate: String,         // "YYYY-MM-DD"
    @SerialName("end_date") val endDate: String,
    val status: String,                                       // VALID / EXPIRED / REVOKED
    val image: ImageUploadPayload                             // 必须提供
)

/** 平台返回的健康证引用（§5.1 嵌套在 employee 内） */
@Serializable
data class HealthCertReference(
    val code: String? = null,
    @SerialName("start_date") val startDate: String,
    @SerialName("end_date") val endDate: String,
    val status: String,
    val image: ImageReference? = null                         // 可能无图片
)

// ==================== 员工对象 ====================

/** 上传时的员工完整状态（§7.2） */
@Serializable
data class UploadEmployee(
    val name: String,
    @SerialName("id_card_number") val idCardNumber: String? = null,
    val phone: String? = null,
    val position: String? = null,
    val status: String = "ACTIVE",                            // ACTIVE / DISABLED
    @SerialName("face_image") val faceImage: ImageUploadPayload,
    @SerialName("health_certificate") val healthCertificate: HealthCertUploadPayload? = null
)

/** 平台返回的完整员工对象（§5.1） */
@Serializable
data class PlatformEmployee(
    @SerialName("employee_id") val employeeId: String,
    val name: String,
    @SerialName("id_card_number") val idCardNumber: String? = null,
    val phone: String? = null,
    val position: String? = null,
    val status: String = "ACTIVE",
    @SerialName("face_image") val faceImage: ImageReference? = null,
    @SerialName("health_certificate") val healthCertificate: HealthCertReference? = null,
    val version: Long = 0,
    @SerialName("updated_at") val updatedAt: Long = 0
)

// ==================== 上传变更 ====================

/** 单条操作（§7.1） */
@Serializable
data class SyncOperation(
    @SerialName("operation_id") val operationId: String,     // UUID
    val type: SyncOperationType,
    @SerialName("employee_id") val employeeId: String,
    @SerialName("expected_version") val expectedVersion: Long? = null,  // null=首次创建
    val employee: UploadEmployee? = null                      // DELETE 时不传
)

/** 上传变更请求（§7.1） */
@Serializable
data class UploadChangesRequest(
    @SerialName("device_id") val deviceId: String,
    @SerialName("batch_id") val batchId: String,              // UUID
    val timestamp: Long,
    val operations: List<SyncOperation>
)

/** 单条操作结果（§7.4） */
@Serializable
data class SyncOperationResult(
    @SerialName("operation_id") val operationId: String,
    @SerialName("employee_id") val employeeId: String,
    val status: SyncResultStatus,
    @SerialName("employee_version") val employeeVersion: Long? = null,
    val cursor: Long? = null,
    @SerialName("error_code") val errorCode: Int? = null,
    val message: String? = null
)

/** 上传变更响应（§7.4） */
@Serializable
data class UploadChangesResponse(
    val accepted: Int,
    val duplicates: Int,
    val conflicts: Int,
    @SerialName("server_cursor") val serverCursor: Long,
    val results: List<SyncOperationResult>
)

// ==================== 增量拉取 ====================

/** 拉取到的单条变化（§8.2） */
@Serializable
data class ChangeItem(
    val cursor: Long,
    val type: SyncOperationType,
    @SerialName("employee_id") val employeeId: String,
    @SerialName("employee_version") val employeeVersion: Long,
    @SerialName("origin_device_id") val originDeviceId: String? = null,
    val employee: PlatformEmployee? = null                    // DELETE 时为 null
)

/** 增量拉取响应（§8.2） */
@Serializable
data class PullChangesResponse(
    val changes: List<ChangeItem>,
    @SerialName("next_cursor") val nextCursor: Long,
    @SerialName("has_more") val hasMore: Boolean,
    @SerialName("server_time") val serverTime: Long
)

// ==================== 快照 ====================

/** 完整员工快照响应（§9.3） */
@Serializable
data class SnapshotResponse(
    val employees: List<PlatformEmployee>,
    val total: Int,
    val cursor: Long,
    @SerialName("server_time") val serverTime: Long
)

// ==================== 单员工查询 ====================

/** 单员工最新状态响应（§8.4） */
@Serializable
data class EmployeeDetailResponse(
    @SerialName("employee_id") val employeeId: String,
    val deleted: Boolean,
    val version: Long,
    val employee: PlatformEmployee? = null                    // deleted=true 时为 null
)
