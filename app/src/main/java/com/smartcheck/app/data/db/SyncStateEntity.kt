package com.smartcheck.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 同步状态单行表（CHECK id=1）
 * 存储 last_cursor 和同步引擎状态
 */
@Entity(tableName = "sync_state")
data class SyncStateEntity(
    @PrimaryKey
    val id: Int = 1,

    @ColumnInfo(name = "last_cursor")
    val lastCursor: Long = 0,

    @ColumnInfo(name = "sync_status")
    val syncStatus: String = "IDLE",      // IDLE / SYNCING / ERROR

    @ColumnInfo(name = "last_sync_time")
    val lastSyncTime: Long? = null,

    @ColumnInfo(name = "error_message")
    val errorMessage: String? = null
)
