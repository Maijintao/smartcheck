package com.smartcheck.app.data.sync

import com.smartcheck.app.api.model.PlatformEmployee
import com.smartcheck.app.api.model.toDomain
import com.smartcheck.app.data.db.SyncOutboxDao
import com.smartcheck.app.data.db.UserDao
import com.smartcheck.app.domain.model.User
import com.smartcheck.app.domain.model.toDomain as entityToDomain
import com.smartcheck.app.domain.model.toEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 版本冲突处理器
 *
 * 当上传操作收到 CONFLICT 时，设备需要：
 * 1. 拉取冲突员工的平台最新版本
 * 2. 让用户选择：接受平台数据 或 用新版本重试本地修改
 */
@Singleton
class ConflictHandler @Inject constructor(
    private val syncApi: EmployeeSyncApi,
    private val syncRepo: EmployeeSyncRepository,
    private val outboxDao: SyncOutboxDao,
    private val userDao: UserDao
) {
    companion object {
        private const val TAG = "ConflictHandler"
    }

    /**
     * 获取当前所有冲突员工及其平台最新状态
     */
    suspend fun getConflicts(): List<ConflictInfo> = withContext(Dispatchers.IO) {
        val conflictedUsers = userDao.getUsersBySyncStatus("CONFLICT")
        conflictedUsers.mapNotNull { localEntity ->
            try {
                val remoteResult = syncApi.getEmployee(localEntity.employeeId)
                val remoteData = remoteResult.getOrNull()

                ConflictInfo(
                    employeeId = localEntity.employeeId,
                    localVersion = localEntity.platformVersion,
                    remoteVersion = remoteData?.version ?: 0,
                    localEmployee = localEntity.entityToDomain(),
                    remoteEmployee = remoteData?.employee,
                    remoteDeleted = remoteData?.deleted ?: false
                )
            } catch (e: Exception) {
                Timber.w(e, "$TAG: 拉取冲突员工远程状态失败: ${localEntity.employeeId}")
                null
            }
        }
    }

    /**
     * 用户选择「使用平台数据」— 放弃本地修改，应用平台最新版本
     */
    suspend fun acceptRemote(employeeId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val remoteResult = syncApi.getEmployee(employeeId)
            if (remoteResult.isFailure) {
                return@withContext Result.failure(remoteResult.exceptionOrNull()!!)
            }
            val remoteData = remoteResult.getOrThrow()

            if (remoteData.deleted || remoteData.employee == null) {
                // 用户已明确接受平台删除，不再按自动同步的冲突保护逻辑保留本地员工。
                userDao.deleteFromRemote(employeeId)
                syncRepo.recordDeletedVersion(employeeId, remoteData.version)
            } else {
                // 应用平台数据
                val entity = remoteData.employee.toDomain().toEntity()
                syncRepo.applyRemoteUpsert(entity)
                userDao.updateSyncStatus(employeeId, "SYNCED")
            }

            // 删除冲突的 outbox 条目
            val conflictedOps = outboxDao.getUnresolvedByEmployeeId(employeeId)
            conflictedOps.forEach { outboxDao.delete(it.operationId) }

            Timber.d("$TAG: 接受平台数据: $employeeId, version=${remoteData.version}")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "$TAG: acceptRemote failed for $employeeId")
            Result.failure(e)
        }
    }

    /**
     * 用户选择「重提本地修改」— 用平台最新 version 作为 expectedVersion 重新入队
     */
    suspend fun retryLocal(employeeId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val localEntity = userDao.getUserByEmployeeId(employeeId)
                ?: return@withContext Result.failure(Exception("本地员工不存在: $employeeId"))

            val remoteResult = syncApi.getEmployee(employeeId)
            if (remoteResult.isFailure) {
                return@withContext Result.failure(remoteResult.exceptionOrNull()!!)
            }
            val remoteData = remoteResult.getOrThrow()
            val newVersion = remoteData.version

            // 更新本地版本号 + 清除冲突状态
            userDao.updateVersionFromRemote(employeeId, newVersion)
            userDao.updateSyncStatus(employeeId, "PENDING_UPLOAD")

            // 删除旧的冲突 outbox 条目
            val conflictedOps = outboxDao.getUnresolvedByEmployeeId(employeeId)
            conflictedOps.forEach { outboxDao.delete(it.operationId) }

            // 重新从 DB 获取最新实体（包含更新后的 platformVersion）
            val updatedEntity = userDao.getUserByEmployeeId(employeeId)
                ?: return@withContext Result.failure(Exception("本地员工不存在: $employeeId"))
            val user = updatedEntity.entityToDomain()
            syncRepo.updateLocal(
                user,
                faceImagePath = localEntity.faceImagePath?.takeIf { it.isNotBlank() },
                certImagePath = localEntity.healthCertImagePath.takeIf { it.isNotBlank() }
            )

            Timber.d("$TAG: 重提本地修改: $employeeId, newVersion=$newVersion")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "$TAG: retryLocal failed for $employeeId")
            Result.failure(e)
        }
    }
}

/**
 * 冲突信息
 */
data class ConflictInfo(
    val employeeId: String,
    val localVersion: Long,
    val remoteVersion: Long,
    val localEmployee: User,
    val remoteEmployee: PlatformEmployee?,
    val remoteDeleted: Boolean
)
