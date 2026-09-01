package com.smartcheck.app.data.sync

import com.smartcheck.app.api.model.SnapshotResponse
import com.smartcheck.app.data.db.SyncOutboxDao
import com.smartcheck.app.data.db.SyncStateDao
import com.smartcheck.app.data.db.UserDao
import com.smartcheck.app.data.db.UserEntity
import com.smartcheck.app.data.repository.SettingsRepository
import com.smartcheck.app.ml.FaceEngine
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
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
}
