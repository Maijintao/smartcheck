package com.smartcheck.app.data.sync

import android.content.Context
import androidx.room.withTransaction
import com.smartcheck.app.api.model.*
import com.smartcheck.app.data.db.*
import com.smartcheck.app.domain.model.User
import com.smartcheck.app.domain.model.toEntity
import com.smartcheck.app.utils.FileUtil
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 员工同步仓库 — Outbox 感知的事务 CRUD
 *
 * 本地变更：写 DB + 写 outbox，同事务
 * 远程应用：只写 DB，不触发 outbox（防止反馈循环）
 */
@Singleton
class EmployeeSyncRepository @Inject constructor(
    private val appDatabase: AppDatabase,
    private val userDao: UserDao,
    private val outboxDao: SyncOutboxDao,
    private val syncStateDao: SyncStateDao,
    private val deletedVersionDao: DeletedEmployeeVersionDao,
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "EmployeeSyncRepo"
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    // ==================== 本地变更（写 outbox + DB，同事务）====================

    /**
     * 本地新增员工 — 事务：insert user + insert outbox(UPSERT, expectedVersion=null)
     * @return operation_id（UUID）
     */
    suspend fun createLocal(
        user: User,
        faceImagePath: String? = null,
        certImagePath: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val operationId = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()

            val faceSha256 = faceImagePath?.let { sha256OfFile(it) }
            val certSha256 = certImagePath?.let { sha256OfFile(it) }

            val payload = json.encodeToString(
                UploadEmployee.serializer(),
                user.toUploadPayload(faceImagePath, faceSha256, certImagePath, certSha256, isNew = true)
            )

            appDatabase.withTransaction {
                val userId = userDao.insertUser(user.copy(syncStatus = "PENDING_UPLOAD").toEntity())

                outboxDao.insert(SyncOutboxEntity(
                    operationId = operationId,
                    operationType = "UPSERT",
                    employeeId = user.employeeId,
                    expectedVersion = null,   // 首次创建
                    payloadJson = payload,
                    faceImageAction = if (faceImagePath != null) "REPLACE" else "CLEAR",
                    faceImageLocalPath = faceImagePath,
                    faceImageSha256 = faceSha256,
                    healthCertImageAction = if (certImagePath != null) "REPLACE" else "CLEAR",
                    healthCertImageLocalPath = certImagePath,
                    healthCertImageSha256 = certSha256,
                    status = "PENDING",
                    createdAt = now,
                    updatedAt = now
                ))
            }

            Timber.d("$TAG: createLocal operationId=$operationId, employeeId=${user.employeeId}")
            Result.success(operationId)
        } catch (e: Exception) {
            Timber.e(e, "$TAG: createLocal failed")
            Result.failure(e)
        }
    }

    /**
     * 本地修改员工 — 事务：update user + insert outbox(UPSERT)
     * @return operation_id（UUID）
     */
    suspend fun updateLocal(
        user: User,
        faceImagePath: String? = null,
        certImagePath: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val operationId = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()

            // 判断图片 action
            val faceAction = determineImageAction(
                newPath = faceImagePath,
                oldSha256 = user.faceImageSha256,
                newSha256 = faceImagePath?.let { sha256OfFile(it) }
            )
            val certAction = determineImageAction(
                newPath = certImagePath,
                oldSha256 = user.healthCertImageSha256,
                newSha256 = certImagePath?.let { sha256OfFile(it) }
            )

            val faceSha256 = if (faceAction == "REPLACE") faceImagePath?.let { sha256OfFile(it) } else user.faceImageSha256
            val certSha256 = if (certAction == "REPLACE") certImagePath?.let { sha256OfFile(it) } else user.healthCertImageSha256

            val payload = json.encodeToString(
                UploadEmployee.serializer(),
                user.toUploadPayload(
                    faceImagePath.takeIf { faceAction == "REPLACE" },
                    faceSha256,
                    certImagePath.takeIf { certAction == "REPLACE" },
                    certSha256,
                    isNew = false
                )
            )

            appDatabase.withTransaction {
                userDao.updateUser(user.copy(syncStatus = "PENDING_UPLOAD").toEntity())

                outboxDao.insert(SyncOutboxEntity(
                    operationId = operationId,
                    operationType = "UPSERT",
                    employeeId = user.employeeId,
                    expectedVersion = user.platformVersion,
                    payloadJson = payload,
                    faceImageAction = faceAction,
                    faceImageLocalPath = faceImagePath.takeIf { faceAction == "REPLACE" },
                    faceImageSha256 = faceSha256,
                    healthCertImageAction = certAction,
                    healthCertImageLocalPath = certImagePath.takeIf { certAction == "REPLACE" },
                    healthCertImageSha256 = certSha256,
                    status = "PENDING",
                    createdAt = now,
                    updatedAt = now
                ))
            }

            Timber.d("$TAG: updateLocal operationId=$operationId, employeeId=${user.employeeId}, version=${user.platformVersion}")
            Result.success(operationId)
        } catch (e: Exception) {
            Timber.e(e, "$TAG: updateLocal failed")
            Result.failure(e)
        }
    }

    /**
     * 本地删除员工 — 事务：delete user + insert outbox(DELETE) + save deleted_version
     * @return operation_id（UUID）
     */
    suspend fun deleteLocal(
        employeeId: String,
        platformVersion: Long
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val operationId = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()

            appDatabase.withTransaction {
                userDao.deleteUserByEmployeeId(employeeId)

                outboxDao.insert(SyncOutboxEntity(
                    operationId = operationId,
                    operationType = "DELETE",
                    employeeId = employeeId,
                    expectedVersion = platformVersion,
                    status = "PENDING",
                    createdAt = now,
                    updatedAt = now
                ))

                deletedVersionDao.insert(DeletedEmployeeVersionEntity(
                    employeeId = employeeId,
                    platformVersion = platformVersion,
                    deletedAt = now
                ))
            }

            Timber.d("$TAG: deleteLocal operationId=$operationId, employeeId=$employeeId")
            Result.success(operationId)
        } catch (e: Exception) {
            Timber.e(e, "$TAG: deleteLocal failed")
            Result.failure(e)
        }
    }

    // ==================== 远程应用（不写 outbox）====================

    /**
     * 在单个事务中应用一批远程变化（协议 §8.3 规则 6）
     * 如果事务中任何一条失败，整批回滚，游标不推进
     */
    suspend fun applyRemoteChangesInTransaction(
        changes: List<com.smartcheck.app.api.model.ChangeItem>,
        applyOne: suspend (com.smartcheck.app.api.model.ChangeItem) -> Unit
    ) {
        appDatabase.withTransaction {
            for (change in changes) {
                applyOne(change)
            }
        }
    }

    /**
     * 应用平台 UPSERT — 直接写入本地 DB，不触发 outbox
     * 按 employeeId 查找已有行并更新（保留本地 id），或插入新行
     * 保留本地人脸特征和图片路径（如果 sha256 未变化），避免识别中断
     */
    suspend fun applyRemoteUpsert(
        entity: UserEntity
    ) = withContext(Dispatchers.IO) {
        val existing = userDao.getUserByEmployeeId(entity.employeeId)
        val toInsert = if (existing != null) {
            // 如果平台图片 sha256 与本地相同，保留本地图片路径和特征（避免重复下载）
            val preserveFace = entity.faceImageSha256 != null && entity.faceImageSha256 == existing.faceImageSha256
            val preserveCert = entity.healthCertImageSha256 != null && entity.healthCertImageSha256 == existing.healthCertImageSha256

            // 保留 PENDING_UPLOAD 和 CONFLICT 状态（避免远程变化覆盖本地未提交修改）
            val preserveStatus = existing.syncStatus in listOf("PENDING_UPLOAD", "CONFLICT")

            entity.copy(
                id = existing.id,
                syncStatus = if (preserveStatus) existing.syncStatus else "SYNCED",
                faceImagePath = if (preserveFace) existing.faceImagePath else entity.faceImagePath,
                faceEmbedding = if (preserveFace) existing.faceEmbedding else entity.faceEmbedding,
                healthCertImagePath = if (preserveCert) existing.healthCertImagePath else entity.healthCertImagePath
            )
        } else {
            entity.copy(syncStatus = "SYNCED")
        }
        userDao.upsertFromRemote(toInsert)
    }

    /**
     * 应用平台 DELETE — 删除本地 + 保存版本记录，不触发 outbox
     */
    suspend fun applyRemoteDelete(
        employeeId: String,
        platformVersion: Long
    ) = withContext(Dispatchers.IO) {
        appDatabase.withTransaction {
            userDao.deleteFromRemote(employeeId)
            deletedVersionDao.insert(DeletedEmployeeVersionEntity(
                employeeId = employeeId,
                platformVersion = platformVersion
            ))
        }
    }

    // ==================== 游标管理 ====================

    suspend fun getLastCursor(): Long = withContext(Dispatchers.IO) {
        syncStateDao.ensureExists()
        syncStateDao.getState()?.lastCursor ?: 0L
    }

    suspend fun advanceCursor(cursor: Long) {
        syncStateDao.updateCursor(cursor)
    }

    // ==================== 状态观察 ====================

    fun observeSyncState(): Flow<SyncStateEntity?> {
        return syncStateDao.observeState()
    }

    suspend fun getConflictedEmployees(): List<UserEntity> = withContext(Dispatchers.IO) {
        userDao.getUsersBySyncStatus("CONFLICT")
    }

    // ==================== 内部工具 ====================

    /** 计算本地图片文件的 SHA-256（小写十六进制） */
    private fun sha256OfFile(imageFileName: String): String? {
        val file = FileUtil.getRecordImageFile(context, imageFileName) ?: return null
        if (!file.exists()) return null
        return sha256OfBytes(file.readBytes())
    }

    private fun sha256OfBytes(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(bytes).joinToString("") { "%02x".format(it) }
    }

    /** 判断图片 action */
    private fun determineImageAction(
        newPath: String?,
        oldSha256: String?,
        newSha256: String?
    ): String {
        return when {
            newPath == null && oldSha256 == null -> "KEEP"    // 都没有，保持不变
            newPath == null && oldSha256 != null -> "CLEAR"   // 删除图片
            newPath != null && oldSha256 == null -> "REPLACE" // 新增图片
            newSha256 != oldSha256 -> "REPLACE"               // 图片变化
            else -> "KEEP"                                    // 图片未变化
        }
    }

    /** User → UploadEmployee 辅助 */
    private fun User.toUploadPayload(
        faceImagePath: String?,
        faceSha256: String?,
        certImagePath: String?,
        certSha256: String?,
        isNew: Boolean
    ): UploadEmployee {
        val faceAction = when {
            faceImagePath != null -> SyncImageAction.REPLACE
            else -> SyncImageAction.KEEP
        }
        val certAction = when {
            certImagePath != null -> SyncImageAction.REPLACE
            !isNew && healthCertImageFileId == null && healthCertCode.isBlank() -> SyncImageAction.KEEP
            else -> SyncImageAction.KEEP
        }

        return toUploadEmployee(
            faceImageAction = faceAction,
            faceImageSha256 = faceSha256,
            healthCertImageAction = certAction,
            healthCertImageSha256 = certSha256
        )
    }
}
