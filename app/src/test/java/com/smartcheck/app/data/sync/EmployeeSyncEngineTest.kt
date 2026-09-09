package com.smartcheck.app.data.sync

import com.smartcheck.app.api.model.PullChangesResponse
import com.smartcheck.app.api.model.SyncOperationResult
import com.smartcheck.app.api.model.SyncResultStatus
import com.smartcheck.app.api.model.UploadChangesResponse
import com.smartcheck.app.data.db.SyncOutboxDao
import com.smartcheck.app.data.db.SyncOutboxEntity
import com.smartcheck.app.data.db.SyncStateDao
import com.smartcheck.app.data.db.UserDao
import com.smartcheck.app.data.db.DeletedEmployeeVersionDao
import com.smartcheck.app.data.repository.SettingsRepository
import com.smartcheck.app.ml.FaceEngine
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test

class EmployeeSyncEngineTest {

    @Test
    fun `duplicate delete result updates tombstone version before removing outbox`() = runTest {
        val syncApi = mockk<EmployeeSyncApi>()
        val syncRepo = mockk<EmployeeSyncRepository>(relaxed = true)
        val outboxDao = mockk<SyncOutboxDao>(relaxed = true)
        val userDao = mockk<UserDao>(relaxed = true)
        val deletedVersionDao = mockk<DeletedEmployeeVersionDao>(relaxed = true)
        val syncStateDao = mockk<SyncStateDao>(relaxed = true)
        val settingsRepository = mockk<SettingsRepository>()

        val operation = SyncOutboxEntity(
            operationId = "op-duplicate",
            operationType = "DELETE",
            employeeId = "E001",
            expectedVersion = 3,
        )
        coEvery { outboxDao.getPending(any()) } returnsMany listOf(
            listOf(operation),
            emptyList(),
        )
        coEvery { syncApi.uploadChanges(any()) } returns Result.success(
            UploadChangesResponse(
                batchId = "batch-1",
                accepted = 0,
                duplicates = 1,
                conflicts = 0,
                rejected = 0,
                serverCursor = 12,
                results = listOf(
                    SyncOperationResult(
                        operationId = operation.operationId,
                        employeeId = operation.employeeId,
                        status = SyncResultStatus.DUPLICATE,
                        employeeVersion = 4,
                        cursor = 12,
                    )
                ),
            )
        )
        coEvery { syncRepo.getLastCursor() } returns 0
        coEvery { syncApi.pullChanges(any(), any()) } returns Result.success(
            PullChangesResponse(
                changes = emptyList(),
                nextCursor = 0,
                hasMore = false,
                serverTime = 1_787_788_800_000,
            )
        )
        every { settingsRepository.deviceId } returns MutableStateFlow("DEVICE001")

        val engine = EmployeeSyncEngine(
            syncApi = syncApi,
            syncRepo = syncRepo,
            outboxDao = outboxDao,
            userDao = userDao,
            deletedVersionDao = deletedVersionDao,
            syncStateDao = syncStateDao,
            imageHelper = mockk<ImageSyncHelper>(relaxed = true),
            faceEngine = mockk<FaceEngine>(relaxed = true),
            settingsRepository = settingsRepository,
        )

        engine.triggerSync()

        coVerify(exactly = 1) {
            deletedVersionDao.insert(match {
                it.employeeId == "E001" && it.platformVersion == 4L
            })
        }
        coVerify(exactly = 1) { outboxDao.delete("op-duplicate") }
    }
}
