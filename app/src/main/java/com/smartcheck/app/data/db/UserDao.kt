package com.smartcheck.app.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    
    @Query("SELECT * FROM users WHERE isActive = 1")
    fun getAllActiveUsers(): Flow<List<UserEntity>>

    /** 获取所有员工（同步版，用于快照同步对比） */
    @Query("SELECT * FROM users")
    suspend fun getAllUsersSync(): List<UserEntity>
    
    @Query("SELECT * FROM users WHERE id = :userId")
    suspend fun getUserById(userId: Long): UserEntity?
    
    @Query("SELECT * FROM users WHERE id > :lastId ORDER BY id ASC LIMIT :limit")
    suspend fun getUsersAfterId(lastId: Long, limit: Int): List<UserEntity>
    
    @Query("SELECT * FROM users WHERE employeeId = :employeeId")
    suspend fun getUserByEmployeeId(employeeId: String): UserEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity): Long
    
    @Update
    suspend fun updateUser(user: UserEntity)
    
    @Delete
    suspend fun deleteUser(user: UserEntity)
    
    @Query("DELETE FROM users WHERE id = :userId")
    suspend fun deleteUserById(userId: Long)
    
    @Query("DELETE FROM users WHERE employeeId = :employeeId")
    suspend fun deleteUserByEmployeeId(employeeId: String)
    
    @Query("DELETE FROM users")
    suspend fun deleteAllUsers()

    // === 平台同步方法（v10 新增，不触发 outbox）===

    /** 远程写入（@Insert REPLACE）— Repository 层负责处理 id 匹配逻辑 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFromRemote(user: UserEntity)

    /** 远程 DELETE：用于应用平台拉取的删除，不触发 outbox */
    @Query("DELETE FROM users WHERE employeeId = :employeeId")
    suspend fun deleteFromRemote(employeeId: String)

    /** 更新员工的平台版本号 */
    @Query("UPDATE users SET platformVersion = :version WHERE employeeId = :employeeId")
    suspend fun updateVersionFromRemote(employeeId: String, version: Long)

    /** 更新员工同步状态 */
    @Query("UPDATE users SET syncStatus = :syncStatus WHERE employeeId = :employeeId")
    suspend fun updateSyncStatus(employeeId: String, syncStatus: String)

    /** 查询指定同步状态的员工 */
    @Query("SELECT * FROM users WHERE syncStatus = :status")
    suspend fun getUsersBySyncStatus(status: String): List<UserEntity>

    /** 观察因平台数据异常而被本地保留的员工 */
    @Query("SELECT * FROM users WHERE syncStatus = 'RECOVERY_REQUIRED' ORDER BY employeeId")
    fun observeRecoveryRequiredUsers(): Flow<List<UserEntity>>

    /**
     * 查找尚未取得平台版本号的本地员工。
     * PENDING_UPLOAD 也纳入检查，用于修复员工状态已更新但 outbox 意外缺失的情况。
     */
    @Query("""
        SELECT * FROM users
        WHERE platformVersion = 0
          AND syncStatus IN ('SYNCED', 'PENDING_UPLOAD')
        ORDER BY id
    """)
    suspend fun getLocalOnlyUsersForUpload(): List<UserEntity>

    /** 图片下载后更新：file_id、sha256、本地路径、特征向量 */
    @Query("""
        UPDATE users
        SET faceImageFileId = :fileId,
            faceImageSha256 = :sha256,
            faceImagePath = :imagePath,
            faceEmbedding = :embedding
        WHERE employeeId = :employeeId
    """)
    suspend fun updateFaceImageMeta(
        employeeId: String,
        fileId: String,
        sha256: String,
        imagePath: String,
        embedding: ByteArray?
    )

    /** 图片下载后更新健康证照片元数据 */
    @Query("""
        UPDATE users
        SET healthCertImageFileId = :fileId,
            healthCertImageSha256 = :sha256,
            healthCertImagePath = :imagePath
        WHERE employeeId = :employeeId
    """)
    suspend fun updateHealthCertImageMeta(
        employeeId: String,
        fileId: String,
        sha256: String,
        imagePath: String
    )

    /** 仅更新人脸特征向量 */
    @Query("UPDATE users SET faceEmbedding = :embedding WHERE employeeId = :employeeId")
    suspend fun updateFaceEmbedding(employeeId: String, embedding: ByteArray?)
}
