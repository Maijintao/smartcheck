package com.smartcheck.app.data.sync

import com.smartcheck.app.api.model.*
import com.smartcheck.app.data.db.*
import com.smartcheck.app.data.repository.SettingsRepository
import com.smartcheck.app.ml.FaceEngine
import com.smartcheck.app.utils.FileUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 员工同步引擎
 *
 * 编排完整的同步流程：
 *   1. 上传本地 outbox 操作
 *   2. 增量拉取平台变化
 *   3. 异步下载图片 + 提特征 + 刷缓存
 *
 * 使用 Mutex 保证同一时刻只有一个同步任务运行。
 */
@Singleton
class EmployeeSyncEngine @Inject constructor(
    private val syncApi: EmployeeSyncApi,
    private val syncRepo: EmployeeSyncRepository,
    private val outboxDao: SyncOutboxDao,
    private val userDao: UserDao,
    private val syncStateDao: SyncStateDao,
    private val imageHelper: ImageSyncHelper,
    private val faceEngine: FaceEngine,
    private val settingsRepository: SettingsRepository
) {
    companion object {
        private const val TAG = "EmployeeSyncEngine"
        private const val MAX_OPS_PER_BATCH = 20  // 保守值，避免超 20MB
    }

    private val syncLock = Mutex()

    private val _syncState = MutableStateFlow(SyncEngineStatus.IDLE)
    val syncState: StateFlow<SyncEngineStatus> = _syncState.asStateFlow()

    private val _syncError = MutableStateFlow<String?>(null)
    val syncError: StateFlow<String?> = _syncError.asStateFlow()

    /**
     * 触发同步（幂等：如果正在同步则跳过）
     */
    suspend fun triggerSync() {
        if (!syncLock.tryLock()) {
            Timber.d("$TAG: 同步已在进行中，跳过")
            return
        }
        try {
            _syncState.value = SyncEngineStatus.SYNCING
            _syncError.value = null
            syncStateDao.updateStatus("SYNCING")

            // 先为历史本地员工补建 outbox。此步骤失败时禁止继续拉取，避免误删本地数据。
            val localUploadResult = syncRepo.enqueueLocalOnlyEmployeesForUpload()
            if (localUploadResult.isFailure) {
                throw localUploadResult.exceptionOrNull() ?: Exception("本地员工上传任务创建失败")
            }
            val queuedLocalCount = localUploadResult.getOrDefault(0)
            if (queuedLocalCount > 0) {
                Timber.i("$TAG: 已将 $queuedLocalCount 名历史本地员工加入上传队列")
            }

            // Step 1: 上传 outbox（失败不阻止后续拉取）
            try {
                uploadOutbox()
            } catch (e: Exception) {
                Timber.w(e, "$TAG: uploadOutbox 失败，继续拉取阶段")
            }

            // Step 2: 增量拉取
            val imageDownloadQueue = mutableListOf<ChangeItem>()
            try {
                pullChangesLoop(imageDownloadQueue)
            } catch (e: CursorExpiredException) {
                // 游标过期 → 切换到快照同步（协议 §5.6 规则 7）
                Timber.w("$TAG: 游标过期，执行快照同步")
                doFullSnapshotSync()
            }

            // Step 3: 处理图片下载（在同步锁内完成，确保一致性）
            if (imageDownloadQueue.isNotEmpty()) {
                processImageDownloads(imageDownloadQueue)
            }

            _syncState.value = SyncEngineStatus.IDLE
            syncStateDao.updateStatusWithTime("IDLE")
            Timber.d("$TAG: 同步完成")
        } catch (e: Exception) {
            Timber.e(e, "$TAG: 同步失败")
            _syncState.value = SyncEngineStatus.ERROR
            _syncError.value = e.message
            syncStateDao.updateStatus("ERROR", e.message)
        } finally {
            syncLock.unlock()
        }
    }

    /**
     * 完整快照同步（首次接入 / 游标过期 / 数据恢复）
     * 此方法会获取 syncLock；如已在 triggerSync 锁内，调用 doFullSnapshotSync()
     */
    suspend fun fullSnapshotSync() {
        syncLock.withLock {
            doFullSnapshotSync()
        }
    }

    /** 快照同步内部逻辑（必须在 syncLock 内调用） */
    private suspend fun doFullSnapshotSync() {
        _syncState.value = SyncEngineStatus.SYNCING
        syncStateDao.updateStatus("SYNCING")

        val snapshotResult = syncApi.getSnapshot()
        if (snapshotResult.isFailure) {
            throw snapshotResult.exceptionOrNull()!!
        }
        val snapshot = snapshotResult.getOrThrow()

        Timber.d("$TAG: 快照同步: ${snapshot.employees.size} 名员工, cursor=${snapshot.cursor}")

        // 收集快照中所有 employeeId
        val snapshotIds = snapshot.employees.map { it.employeeId }.toSet()

        // 逐条应用快照员工
        for (platformEmp in snapshot.employees) {
            val entity = platformEmp.toEntity()
            syncRepo.applyRemoteUpsert(entity)
        }

        // 快照缺失不能等同于平台明确删除。保留本地员工，交由主页提示用户确认恢复。
        val allLocalUsers = userDao.getAllUsersSync()
        for (localUser in allLocalUsers) {
            if (localUser.employeeId !in snapshotIds && localUser.syncStatus == "SYNCED") {
                syncRepo.markRecoveryRequired(localUser.employeeId)
                Timber.w("$TAG: 平台快照缺少本地员工，已保留并等待恢复确认: ${localUser.employeeId}")
            }
        }

        syncRepo.advanceCursor(snapshot.cursor)

        // 下载图片
        val itemsWithImages = snapshot.employees.filter { it.faceImage != null }
        for (emp in itemsWithImages) {
            downloadAndProcessImage(emp)
        }

        faceEngine.refreshUserCache()

        Timber.d("$TAG: 快照同步完成")
    }

    // ==================== 上传 Outbox ====================

    private suspend fun uploadOutbox() {
        while (true) {
            val pending = outboxDao.getPending(MAX_OPS_PER_BATCH)
            if (pending.isEmpty()) break

            val batchId = UUID.randomUUID().toString()
            val deviceId = settingsRepository.deviceId.value.ifBlank { "UNKNOWN" }

            val operations = pending.map { op ->
                buildSyncOperation(op)
            }

            val request = UploadChangesRequest(
                deviceId = deviceId,
                batchId = batchId,
                timestamp = System.currentTimeMillis(),
                operations = operations
            )

            val result = syncApi.uploadChanges(request)
            if (result.isFailure) {
                val error = result.exceptionOrNull()
                Timber.w("$TAG: uploadOutbox 失败: ${error?.message}")
                // 网络错误等可重试的异常，标记 IN_PROGRESS 保留 operation_id 下次重试
                pending.forEach { op ->
                    outboxDao.incrementRetry(op.operationId, error?.message)
                }
                throw error ?: Exception("上传失败")
            }

            val response = result.getOrThrow()
            handleUploadResponse(response, pending)

            // 如果本批全部处理完，继续检查下一批
            if (response.results.size < pending.size) break
        }
    }

    /** 将 outbox 条目转换为 SyncOperation（上传前读取图片文件编码 Base64） */
    private suspend fun buildSyncOperation(op: SyncOutboxEntity): SyncOperation {
        if (op.operationType == "DELETE") {
            return SyncOperation(
                operationId = op.operationId,
                type = SyncOperationType.DELETE,
                employeeId = op.employeeId,
                expectedVersion = op.expectedVersion,
                employee = null
            )
        }

        // UPSERT：组装 employee payload + 图片 Base64
        val faceImage = when (op.faceImageAction) {
            "REPLACE" -> {
                val base64 = op.faceImageLocalPath?.let { imageHelper.fileToBase64(it) }
                ImageUploadPayload(
                    action = SyncImageAction.REPLACE,
                    mimeType = "image/jpeg",
                    sha256 = op.faceImageSha256,
                    base64 = base64
                )
            }
            "CLEAR" -> ImageUploadPayload(action = SyncImageAction.CLEAR)
            else -> ImageUploadPayload(action = SyncImageAction.KEEP)
        }

        val healthCertImage = when (op.healthCertImageAction) {
            "REPLACE" -> {
                val base64 = op.healthCertImageLocalPath?.let { imageHelper.fileToBase64(it) }
                ImageUploadPayload(
                    action = SyncImageAction.REPLACE,
                    mimeType = "image/jpeg",
                    sha256 = op.healthCertImageSha256,
                    base64 = base64
                )
            }
            "CLEAR" -> ImageUploadPayload(action = SyncImageAction.CLEAR)
            else -> ImageUploadPayload(action = SyncImageAction.KEEP)
        }

        // 从 payloadJson 还原 UploadEmployee 并注入实际图片
        val baseEmployee = op.payloadJson?.let {
            try {
                kotlinx.serialization.json.Json.decodeFromString(UploadEmployee.serializer(), it)
            } catch (e: Exception) {
                Timber.w("$TAG: 解析 payloadJson 失败: ${e.message}")
                null
            }
        }

        val uploadEmployee = baseEmployee?.copy(
            faceImage = faceImage,
            healthCertificate = baseEmployee.healthCertificate?.copy(image = healthCertImage)
        )

        return SyncOperation(
            operationId = op.operationId,
            type = SyncOperationType.UPSERT,
            employeeId = op.employeeId,
            expectedVersion = op.expectedVersion,
            employee = uploadEmployee
        )
    }

    /** 处理上传响应：逐条标记 outbox 状态 */
    private suspend fun handleUploadResponse(
        response: UploadChangesResponse,
        operations: List<SyncOutboxEntity>
    ) {
        for (result in response.results) {
            val op = operations.find { it.operationId == result.operationId } ?: continue

            when (result.status) {
                SyncResultStatus.APPLIED -> {
                    outboxDao.delete(op.operationId)
                    // 更新本地员工的平台版本号
                    if (result.employeeVersion != null) {
                        userDao.updateVersionFromRemote(op.employeeId, result.employeeVersion)
                        userDao.updateSyncStatus(op.employeeId, "SYNCED")
                    }
                }
                SyncResultStatus.DUPLICATE -> {
                    // 已处理过，视为成功
                    outboxDao.delete(op.operationId)
                }
                SyncResultStatus.CONFLICT -> {
                    // 标记冲突，等用户处理
                    outboxDao.updateStatus(op.operationId, "FAILED")
                    userDao.updateSyncStatus(op.employeeId, "CONFLICT")
                    Timber.w("$TAG: 版本冲突 employeeId=${op.employeeId}, errorCode=${result.errorCode}")
                }
                SyncResultStatus.REJECTED -> {
                    // 数据错误，标记失败，不重试
                    outboxDao.updateStatus(op.operationId, "FAILED")
                    Timber.w("$TAG: 操作被拒绝 employeeId=${op.employeeId}: ${result.message}")
                }
            }
        }
    }

    // ==================== 增量拉取 ====================

    private suspend fun pullChangesLoop(imageDownloadQueue: MutableList<ChangeItem>) {
        var hasMore = true
        while (hasMore) {
            val lastCursor = syncRepo.getLastCursor()
            val result = syncApi.pullChanges(lastCursor)

            if (result.isFailure) {
                val error = result.exceptionOrNull()
                // 检查是否游标过期
                if (error is SyncApiException && error.errorCode == 41010) {
                    Timber.w("$TAG: 游标过期，需要快照同步")
                    throw CursorExpiredException()
                }
                throw error ?: Exception("拉取变化失败")
            }

            val response = result.getOrThrow()

            if (response.changes.isNotEmpty()) {
                // 在一页事务中应用所有远程变化 + 推进游标（协议 §8.3 规则 6-7）
                syncRepo.applyRemoteChangesInTransaction(response.changes) { change ->
                    applySingleRemoteChange(change)
                }
                syncRepo.advanceCursor(response.nextCursor)

                // 收集需要下载图片的变化（人脸或健康证）
                imageDownloadQueue.addAll(response.changes.filter {
                    it.type == SyncOperationType.UPSERT &&
                            (it.employee?.faceImage != null || it.employee?.healthCertificate?.image != null)
                })
            } else {
                // 无变化也推进游标
                syncRepo.advanceCursor(response.nextCursor)
            }

            hasMore = response.hasMore
        }
    }

    /** 应用单条远程变化 */
    private suspend fun applySingleRemoteChange(change: ChangeItem) {
        when (change.type) {
            SyncOperationType.UPSERT -> {
                val platformEmp = change.employee ?: return
                val entity = platformEmp.toEntity()
                syncRepo.applyRemoteUpsert(entity)
            }
            SyncOperationType.DELETE -> {
                syncRepo.applyRemoteDelete(
                    change.employeeId,
                    change.employeeVersion
                )
            }
        }
    }

    // ==================== 图片异步处理 ====================

    private suspend fun processImageDownloads(changes: List<ChangeItem>) {
        var needRefreshCache = false

        for (change in changes) {
            val emp = change.employee ?: continue

            // 下载人脸图片（sha256 不同 或 本地图片文件不存在 都需要下载）
            if (emp.faceImage != null) {
                val faceImageRef = emp.faceImage
                val localEntity = userDao.getUserByEmployeeId(emp.employeeId)
                val needFaceDownload = localEntity?.faceImageSha256 != faceImageRef.sha256
                        || localEntity?.faceImagePath.isNullOrBlank()
                if (needFaceDownload) {
                    try {
                        val downloadResult = imageHelper.downloadVerifySave(
                            syncApi, faceImageRef.fileId, faceImageRef.sha256
                        )
                        if (downloadResult.isSuccess) {
                            val localFileName = downloadResult.getOrThrow()
                            val featureResult = imageHelper.extractFaceFeature(localFileName)
                            val embedding = featureResult.getOrNull()

                            if (embedding == null) {
                                Timber.w("$TAG: 人脸图片下载成功但无法提取特征: ${emp.employeeId}")
                            }

                            userDao.updateFaceImageMeta(
                                employeeId = emp.employeeId,
                                fileId = faceImageRef.fileId,
                                sha256 = faceImageRef.sha256,
                                imagePath = localFileName,
                                embedding = embedding
                            )
                            needRefreshCache = true
                            Timber.d("$TAG: 人脸图片下载+特征提取完成: ${emp.employeeId}, path=$localFileName")
                        } else {
                            Timber.w("$TAG: 人脸图片下载失败: ${emp.employeeId}, ${downloadResult.exceptionOrNull()?.message}")
                        }
                    } catch (e: Exception) {
                        Timber.w(e, "$TAG: 人脸图片下载异常: ${emp.employeeId} - ${faceImageRef.fileId}")
                    }
                }
            }

            // 下载健康证图片（sha256 不同 或 本地图片文件不存在 都需要下载）
            val certImageRef = emp.healthCertificate?.image
            if (certImageRef != null) {
                val localEntity = userDao.getUserByEmployeeId(emp.employeeId)
                val needCertDownload = localEntity?.healthCertImageSha256 != certImageRef.sha256
                        || localEntity?.healthCertImagePath.isNullOrBlank()
                if (needCertDownload) {
                    try {
                        val downloadResult = imageHelper.downloadVerifySave(
                            syncApi, certImageRef.fileId, certImageRef.sha256
                        )
                        if (downloadResult.isSuccess) {
                            val localFileName = downloadResult.getOrThrow()
                            userDao.updateHealthCertImageMeta(
                                employeeId = emp.employeeId,
                                fileId = certImageRef.fileId,
                                sha256 = certImageRef.sha256,
                                imagePath = localFileName
                            )
                            Timber.d("$TAG: 健康证图片下载完成: ${emp.employeeId}, path=$localFileName")
                        } else {
                            Timber.w("$TAG: 健康证图片下载失败: ${emp.employeeId}, ${downloadResult.exceptionOrNull()?.message}")
                        }
                    } catch (e: Exception) {
                        Timber.w(e, "$TAG: 健康证图片下载异常: ${emp.employeeId} - ${certImageRef.fileId}")
                    }
                }
            }
        }

        if (needRefreshCache) {
            try {
                faceEngine.refreshUserCache()
                Timber.d("$TAG: 人脸识别缓存已刷新")
            } catch (e: Exception) {
                Timber.e(e, "$TAG: 刷新人脸缓存失败")
            }
        }
    }

    /** 快照同步时的图片处理 */
    private suspend fun downloadAndProcessImage(emp: PlatformEmployee) {
        // 人脸图片
        val faceImageRef = emp.faceImage
        if (faceImageRef != null) {
            try {
                val result = imageHelper.downloadVerifySave(syncApi, faceImageRef.fileId, faceImageRef.sha256)
                if (result.isSuccess) {
                    val fileName = result.getOrThrow()
                    val featureResult = imageHelper.extractFaceFeature(fileName)
                    userDao.updateFaceImageMeta(
                        employeeId = emp.employeeId,
                        fileId = faceImageRef.fileId,
                        sha256 = faceImageRef.sha256,
                        imagePath = fileName,
                        embedding = featureResult.getOrNull()
                    )
                }
            } catch (e: Exception) {
                Timber.w(e, "$TAG: 快照人脸图片处理失败: ${emp.employeeId}")
            }
        }

        // 健康证图片
        val certImageRef = emp.healthCertificate?.image
        if (certImageRef != null) {
            try {
                val result = imageHelper.downloadVerifySave(syncApi, certImageRef.fileId, certImageRef.sha256)
                if (result.isSuccess) {
                    val fileName = result.getOrThrow()
                    userDao.updateHealthCertImageMeta(
                        employeeId = emp.employeeId,
                        fileId = certImageRef.fileId,
                        sha256 = certImageRef.sha256,
                        imagePath = fileName
                    )
                }
            } catch (e: Exception) {
                Timber.w(e, "$TAG: 快照健康证图片处理失败: ${emp.employeeId}")
            }
        }
    }
}

/** 同步引擎状态 */
enum class SyncEngineStatus {
    IDLE, SYNCING, ERROR
}

/** 游标过期异常 — 需要触发快照同步 */
class CursorExpiredException : Exception("平台游标已过期，需要快照同步")
