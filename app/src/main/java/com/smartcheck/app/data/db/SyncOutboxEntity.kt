package com.smartcheck.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 员工同步 Outbox 表
 * 暂存本地未上传的员工变更操作，与本地修改同事务写入。
 * 协议文档 §12.2
 */
@Entity(tableName = "sync_outbox")
data class SyncOutboxEntity(
    @PrimaryKey
    @ColumnInfo(name = "operation_id")
    val operationId: String,              // UUID，幂等键

    @ColumnInfo(name = "operation_type")
    val operationType: String,            // "UPSERT" / "DELETE"

    @ColumnInfo(name = "employee_id")
    val employeeId: String,               // 员工工号字符串

    @ColumnInfo(name = "expected_version")
    val expectedVersion: Long? = null,    // null=首次创建

    @ColumnInfo(name = "payload_json")
    val payloadJson: String? = null,      // UPSERT 时员工完整状态 JSON

    @ColumnInfo(name = "face_image_action")
    val faceImageAction: String? = null,  // KEEP/CLEAR/REPLACE

    @ColumnInfo(name = "face_image_local_path")
    val faceImageLocalPath: String? = null,

    @ColumnInfo(name = "face_image_sha256")
    val faceImageSha256: String? = null,

    @ColumnInfo(name = "health_cert_image_action")
    val healthCertImageAction: String? = null,

    @ColumnInfo(name = "health_cert_image_local_path")
    val healthCertImageLocalPath: String? = null,

    @ColumnInfo(name = "health_cert_image_sha256")
    val healthCertImageSha256: String? = null,

    val status: String = "PENDING",       // PENDING/IN_PROGRESS/COMPLETED/FAILED

    @ColumnInfo(name = "retry_count")
    val retryCount: Int = 0,

    @ColumnInfo(name = "last_error")
    val lastError: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
