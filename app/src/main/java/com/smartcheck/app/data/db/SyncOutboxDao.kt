package com.smartcheck.app.data.db

import androidx.room.*

@Dao
interface SyncOutboxDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(operation: SyncOutboxEntity)

    @Query("SELECT * FROM sync_outbox WHERE status = 'PENDING' ORDER BY created_at ASC LIMIT :limit")
    suspend fun getPending(limit: Int = 50): List<SyncOutboxEntity>

    @Query("SELECT * FROM sync_outbox WHERE status = 'PENDING' OR status = 'IN_PROGRESS' ORDER BY created_at ASC")
    suspend fun getAllPendingOrInProgress(): List<SyncOutboxEntity>

    @Query("UPDATE sync_outbox SET status = :status, updated_at = :updatedAt WHERE operation_id = :operationId")
    suspend fun updateStatus(
        operationId: String,
        status: String,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("""
        UPDATE sync_outbox
        SET retry_count = retry_count + 1,
            status = 'PENDING',
            last_error = :error,
            updated_at = :updatedAt
        WHERE operation_id = :operationId
    """)
    suspend fun incrementRetry(
        operationId: String,
        error: String? = null,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("DELETE FROM sync_outbox WHERE operation_id = :operationId")
    suspend fun delete(operationId: String)

    @Query("DELETE FROM sync_outbox WHERE status = 'COMPLETED'")
    suspend fun deleteCompleted()

    @Query("SELECT COUNT(*) FROM sync_outbox WHERE status = 'PENDING' OR status = 'IN_PROGRESS'")
    suspend fun countPending(): Int

    @Query("""
        SELECT COUNT(*) FROM sync_outbox
        WHERE employee_id = :employeeId
          AND operation_type = 'UPSERT'
          AND status IN ('PENDING', 'IN_PROGRESS')
    """)
    suspend fun countActiveUpserts(employeeId: String): Int

    @Query("DELETE FROM sync_outbox")
    suspend fun deleteAll()
}
