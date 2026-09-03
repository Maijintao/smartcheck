package com.smartcheck.app.data.sync

import com.smartcheck.app.api.model.PullChangesResponse
import com.smartcheck.app.api.model.SnapshotResponse
import com.smartcheck.app.api.model.SyncOperationResult
import com.smartcheck.app.api.model.SyncResultStatus
import com.smartcheck.app.api.model.UploadChangesRequest
import com.smartcheck.app.api.model.UploadChangesResponse
import com.smartcheck.app.data.db.SyncOutboxDao
import com.smartcheck.app.data.db.SyncOutboxEntity
import com.smartcheck.app.data.db.SyncStateDao
import com.smartcheck.app.data.db.UserDao
import com.smartcheck.app.data.db.UserEntity
import com.smartcheck.app.data.repository.SettingsRepository
import com.smartcheck.app.ml.FaceEngine
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class EmployeeSyncEngineTest {

    private lateinit var syncApi: EmployeeSyncApi
    private lateinit var syncRepository: EmployeeSyncRepository
    private lateinit var outboxDao: SyncOutboxDao
    private lateinit var userDao: UserDao
    private lateinit var syncStateDao: SyncStateDao
    private lateinit var imageHelper: ImageSyncHelper
    private lateinit var faceEngine: FaceEngine
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var engine: EmployeeSyncEngine

    @Before
    fun setUp() {
        syncApi = mockk(relaxed = true)
        syncRepository = mockk(relaxed = true)
        outboxDao = mockk(relaxed = true)
        userDao = mockk(relaxed = true)
        syncStateDao = mockk(relaxed = true)
        imageHelper = mockk(relaxed = true)
        faceEngine = mockk(relaxed = true)
        settingsRepository = mockk(relaxed = true)
        engine = EmployeeSyncEngine(
            syncApi = syncApi,
            syncRepo = syncRepository,
            outboxDao = outboxDao,
            userDao = userDao,
            syncStateDao = syncStateDao,
            imageHelper = imageHelper,
            faceEngine = faceEngine,
            settingsRepository = settingsRepository,
        )
    }

    @Test
    fun `历史员工上传任务创建失败时不拉取平台变化`() = runTest {
        coEvery { syncRepository.enqueueLocalOnlyEmployeesForUpload() } returns
            Result.failure(IllegalStateException("queue failed"))

        engine.triggerSync()

        coVerify(exactly = 0) { syncApi.pullChanges(any(), any()) }
        coVerify { syncStateDao.updateStatus("ERROR", any()) }
    }

    @Test
    fun `平台快照缺少本地员工时标记恢复而不删除`() = runTest {
        val localEmployee = UserEntity(
            name = "本地员工",
            employeeId = "EMP-001",
            platformVersion = 1,
            syncStatus = "SYNCED",
        )
        coEvery { syncApi.getSnapshot() } returns Result.success(
            SnapshotResponse(
                employees = emptyList(),
                total = 0,
                cursor = 10,
                serverTime = 1_000,
            )
        )
        coEvery { userDao.getAllUsersSync() } returns listOf(localEmployee)

        engine.fullSnapshotSync()

        coVerify { syncRepository.markRecoveryRequired("EMP-001") }
        coVerify(exactly = 0) { userDao.deleteFromRemote(any()) }
    }

    @Test
    fun `同一员工的积压操作不会在同一批次上传`() = runTest {
        val first = pendingUpsert("operation-1", "EMP-001")
        val second = pendingUpsert("operation-2", "EMP-001")
        val request = slot<UploadChangesRequest>()
        prepareRegularSync(listOf(first, second))
        coEvery { syncApi.uploadChanges(capture(request)) } returns Result.success(
            uploadResponse(
                SyncOperationResult(
                    operationId = first.operationId,
                    employeeId = first.employeeId,
                    status = SyncResultStatus.APPLIED,
                    employeeVersion = 4,
                ),
            ),
        )

        engine.triggerSync()

        assertEquals(listOf(first.operationId), request.captured.operations.map { it.operationId })
        coVerify { outboxDao.updatePendingExpectedVersion("EMP-001", 4, any()) }
    }

    @Test
    fun `重复响应仍回写员工版本并完成同步`() = runTest {
        val operation = pendingUpsert("operation-1", "EMP-001")
        prepareRegularSync(listOf(operation))
        coEvery { syncApi.uploadChanges(any()) } returns Result.success(
            uploadResponse(
                SyncOperationResult(
                    operationId = operation.operationId,
                    employeeId = operation.employeeId,
                    status = SyncResultStatus.DUPLICATE,
                    employeeVersion = 7,
                ),
            ),
        )
        coEvery { outboxDao.countActiveUpserts(operation.employeeId) } returns 0

        engine.triggerSync()

        coVerify { outboxDao.delete(operation.operationId) }
        coVerify { userDao.updateVersionFromRemote(operation.employeeId, 7) }
        coVerify { userDao.updateSyncStatus(operation.employeeId, "SYNCED") }
    }

    @Test
    fun `删除成功保存平台返回的最终版本`() = runTest {
        val operation = SyncOutboxEntity(
            operationId = "operation-delete",
            operationType = "DELETE",
            employeeId = "EMP-001",
            expectedVersion = 6,
        )
        prepareRegularSync(listOf(operation))
        coEvery { syncApi.uploadChanges(any()) } returns Result.success(
            uploadResponse(
                SyncOperationResult(
                    operationId = operation.operationId,
                    employeeId = operation.employeeId,
                    status = SyncResultStatus.APPLIED,
                    employeeVersion = 7,
                ),
            ),
        )

        engine.triggerSync()

        coVerify { syncRepository.recordDeletedVersion("EMP-001", 7) }
        coVerify(exactly = 0) { userDao.updateVersionFromRemote(any(), any()) }
    }

    @Test
    fun `冲突操作进入冲突状态而不是失败状态`() = runTest {
        val operation = pendingUpsert("operation-1", "EMP-001")
        prepareRegularSync(listOf(operation))
        coEvery { syncApi.uploadChanges(any()) } returns Result.success(
            uploadResponse(
                SyncOperationResult(
                    operationId = operation.operationId,
                    employeeId = operation.employeeId,
                    status = SyncResultStatus.CONFLICT,
                    employeeVersion = 8,
                    message = "employee version conflict",
                ),
            ),
        )

        engine.triggerSync()

        coVerify { outboxDao.markConflict(operation.operationId, "employee version conflict", any()) }
        coVerify { userDao.updateSyncStatus(operation.employeeId, "CONFLICT") }
    }

    @Test
    fun `相同操作集合重试时保持批次ID不变`() {
        val first = pendingUpsert("operation-1", "EMP-001")
        val second = pendingUpsert("operation-2", "EMP-002")

        assertEquals(
            stableSyncBatchId(listOf(first, second)),
            stableSyncBatchId(listOf(second, first)),
        )
    }

    private fun prepareRegularSync(pending: List<SyncOutboxEntity>) {
        coEvery { syncRepository.enqueueLocalOnlyEmployeesForUpload() } returns Result.success(0)
        coEvery { outboxDao.getPending(any()) } returnsMany listOf(pending, emptyList())
        coEvery { syncApi.pullChanges(any(), any()) } returns Result.success(
            PullChangesResponse(
                changes = emptyList(),
                nextCursor = 0,
                hasMore = false,
                serverTime = 1_000,
            ),
        )
        every { settingsRepository.deviceId.value } returns "DEVICE-001"
    }

    private fun pendingUpsert(operationId: String, employeeId: String): SyncOutboxEntity {
        return SyncOutboxEntity(
            operationId = operationId,
            operationType = "UPSERT",
            employeeId = employeeId,
            payloadJson = """{"name":"张三","status":"ACTIVE","face_image":{"action":"KEEP"}}""",
        )
    }

    private fun uploadResponse(vararg results: SyncOperationResult): UploadChangesResponse {
        return UploadChangesResponse(
            accepted = results.count { it.status == SyncResultStatus.APPLIED },
            duplicates = results.count { it.status == SyncResultStatus.DUPLICATE },
            conflicts = 0,
            serverCursor = 1,
            results = results.toList(),
        )
    }
}
