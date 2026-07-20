package com.smartcheck.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 已删除员工的平台版本号记录
 * 用于处理删除重放和后续重新添加时的 expected_version
 */
@Entity(tableName = "deleted_employee_versions")
data class DeletedEmployeeVersionEntity(
    @PrimaryKey
    @ColumnInfo(name = "employee_id")
    val employeeId: String,

    @ColumnInfo(name = "platform_version")
    val platformVersion: Long,

    @ColumnInfo(name = "deleted_at")
    val deletedAt: Long = System.currentTimeMillis()
)
