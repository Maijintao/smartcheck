package com.smartcheck.app.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordDao {
    
    @Query("SELECT * FROM check_records ORDER BY checkTime DESC LIMIT :limit")
    fun getRecentRecords(limit: Int = 100): Flow<List<RecordEntity>>
    
    @Query("SELECT * FROM check_records WHERE userId = :userId ORDER BY checkTime DESC")
    fun getRecordsByUser(userId: Long): Flow<List<RecordEntity>>
    
    @Query("SELECT * FROM check_records WHERE checkTime >= :startTime AND checkTime <= :endTime ORDER BY checkTime DESC")
    fun getRecordsByTimeRange(startTime: Long, endTime: Long): Flow<List<RecordEntity>>

    @Query("SELECT * FROM check_records WHERE checkTime >= :startTime AND checkTime <= :endTime ORDER BY checkTime DESC")
    suspend fun getRecordsByTimeRangeSync(startTime: Long, endTime: Long): List<RecordEntity>

    @Query("SELECT * FROM check_records WHERE userId = :userId AND checkTime >= :todayStart ORDER BY checkTime DESC LIMIT 1")
    suspend fun getLatestTodayRecordByUser(userId: Long, todayStart: Long): RecordEntity?

    @Query("SELECT * FROM check_records WHERE userId = :userId AND checkTime >= :todayStart ORDER BY checkTime ASC LIMIT 1")
    suspend fun getFirstTodayRecordByUser(userId: Long, todayStart: Long): RecordEntity?

    @Query("SELECT * FROM check_records WHERE TRIM(employeeId) = TRIM(:employeeId) COLLATE NOCASE AND checkTime >= :todayStart ORDER BY checkTime DESC LIMIT 1")
    suspend fun getLatestTodayRecordByEmployeeId(employeeId: String, todayStart: Long): RecordEntity?

    @Query("SELECT * FROM check_records WHERE TRIM(employeeId) = TRIM(:employeeId) COLLATE NOCASE AND checkTime >= :todayStart ORDER BY checkTime ASC LIMIT 1")
    suspend fun getFirstTodayRecordByEmployeeId(employeeId: String, todayStart: Long): RecordEntity?

    @Query("SELECT * FROM check_records WHERE id > :lastId ORDER BY id ASC LIMIT :limit")
    suspend fun getRecordsAfterId(lastId: Long, limit: Int): List<RecordEntity>
    
    @Insert
    suspend fun insertRecord(record: RecordEntity): Long

    @Update
    suspend fun updateRecord(record: RecordEntity)

    @Transaction
    suspend fun updateRecordBeforeUpload(record: RecordEntity): Boolean {
        val current = getRecordById(record.id) ?: return false
        if (current.uploadStatus != "PENDING" || current.uploadDeviceId.isNotBlank()) return false
        updateRecord(
            record.copy(
                recordUuid = current.recordUuid,
                uploadDeviceId = current.uploadDeviceId,
                isUploaded = current.isUploaded,
                uploadStatus = current.uploadStatus,
                uploadRetryCount = current.uploadRetryCount,
                nextUploadAttemptAt = current.nextUploadAttemptAt,
                uploadLastError = current.uploadLastError,
            )
        )
        return true
    }

    @Query("SELECT * FROM check_records WHERE id = :recordId")
    suspend fun getRecordById(recordId: Long): RecordEntity?
    
    @Query("DELETE FROM check_records WHERE checkTime < :beforeTime")
    suspend fun deleteOldRecords(beforeTime: Long)

    @Query("DELETE FROM check_records WHERE checkTime < :beforeTime AND isUploaded = 1")
    suspend fun deleteOldUploadedRecords(beforeTime: Long)

    @Query("DELETE FROM check_records")
    suspend fun deleteAllRecords()

    @Query("""
        SELECT * FROM check_records
        WHERE uploadStatus IN ('PENDING', 'RETRYING')
          AND nextUploadAttemptAt <= :now
        ORDER BY checkTime ASC
    """)
    suspend fun getPendingUploads(now: Long): List<RecordEntity>

    @Query("""
        SELECT EXISTS(
            SELECT 1 FROM check_records
            WHERE uploadStatus IN ('PENDING', 'RETRYING')
              AND nextUploadAttemptAt <= :now
            LIMIT 1
        )
    """)
    suspend fun hasPendingUploads(now: Long): Boolean

    @Query("SELECT COUNT(*) FROM check_records WHERE isUploaded = 0")
    fun observeUnuploadedCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM check_records WHERE isUploaded = 0")
    suspend fun countUnuploadedRecords(): Int

    @Query("""
        UPDATE check_records
        SET uploadStatus = 'PENDING',
            uploadRetryCount = 0,
            nextUploadAttemptAt = 0,
            uploadLastError = NULL
        WHERE isUploaded = 0
    """)
    suspend fun prepareUnuploadedForManualRetry()

    @Query("""
        UPDATE check_records
        SET recordUuid = :recordUuid, uploadDeviceId = :deviceId
        WHERE id = :recordId AND uploadStatus = 'PENDING' AND uploadDeviceId = ''
    """)
    suspend fun claimUploadIdentity(recordId: Long, recordUuid: String, deviceId: String): Int

    @Query("""
        UPDATE check_records
        SET isUploaded = 1, uploadStatus = 'UPLOADED', nextUploadAttemptAt = 0, uploadLastError = NULL
        WHERE id = :recordId
    """)
    suspend fun markAsUploaded(recordId: Long)

    @Query("""
        UPDATE check_records
        SET isUploaded = 0,
            uploadStatus = 'RETRYING',
            uploadRetryCount = :retryCount,
            nextUploadAttemptAt = :nextAttemptAt,
            uploadLastError = :error
        WHERE id = :recordId
    """)
    suspend fun markRetryableFailure(recordId: Long, retryCount: Int, nextAttemptAt: Long, error: String)

    @Query("""
        UPDATE check_records
        SET isUploaded = 0, uploadStatus = 'FAILED', nextUploadAttemptAt = 0, uploadLastError = :error
        WHERE id = :recordId
    """)
    suspend fun markPermanentFailure(recordId: Long, error: String)

    @Query("SELECT * FROM check_records WHERE isUploaded = 1 AND checkTime < :beforeTime")
    suspend fun getOldUploadedRecords(beforeTime: Long): List<RecordEntity>
}
