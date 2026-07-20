package com.smartcheck.app.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncStateDao {

    @Query("SELECT * FROM sync_state WHERE id = 1")
    suspend fun getState(): SyncStateEntity?

    @Query("SELECT * FROM sync_state WHERE id = 1")
    fun observeState(): Flow<SyncStateEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: SyncStateEntity)

    @Query("UPDATE sync_state SET last_cursor = :cursor WHERE id = 1")
    suspend fun updateCursor(cursor: Long)

    @Query("UPDATE sync_state SET sync_status = :status, error_message = :error WHERE id = 1")
    suspend fun updateStatus(status: String, error: String? = null)

    @Query("UPDATE sync_state SET sync_status = :status, last_sync_time = :syncTime WHERE id = 1")
    suspend fun updateStatusWithTime(status: String, syncTime: Long = System.currentTimeMillis())

    /**
     * 确保单行存在，不存在则插入默认值
     */
    @Query("INSERT OR IGNORE INTO sync_state (id, last_cursor, sync_status) VALUES (1, 0, 'IDLE')")
    suspend fun ensureExists()
}
