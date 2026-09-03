package com.smartcheck.app.data.upload

import android.content.Context
import com.smartcheck.app.api.model.MorningCheckUploadResponse
import com.smartcheck.app.data.db.RecordDao
import com.smartcheck.app.data.db.RecordEntity
import com.smartcheck.app.data.repository.SettingsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingUploadManagerTest {

    private val context = mockk<Context>(relaxed = true)
    private val recordDao = mockk<RecordDao>(relaxed = true)
    private val cloudRecordService = mockk<CloudRecordService>()
    private val settingsRepository = mockk<SettingsRepository> {
        every { platformUrl } returns MutableStateFlow("https://platform.example.com")
        every { apiKey } returns MutableStateFlow("api-key")
        every { deviceId } returns MutableStateFlow("device-1")
    }

    @Test
    fun `manual upload retries every unuploaded record`() = runTest {
        val first = record(id = 1L)
        val second = record(id = 2L)
        coEvery { recordDao.countUnuploadedRecords() } returnsMany listOf(2, 0)
        coEvery { recordDao.getPendingUploads(any()) } returns listOf(first, second)
        coEvery { recordDao.getRecordById(1L) } returns first
        coEvery { recordDao.getRecordById(2L) } returns second
        coEvery { cloudRecordService.uploadToPlatform(any(), "device-1") } returns
            Result.success(MorningCheckUploadResponse(code = 200, message = "success"))

        val result = manager(this).uploadAllUnuploaded()

        assertEquals(ManualUploadResult.Finished(2, 2, 0), result)
        coVerify(exactly = 1) { recordDao.prepareUnuploadedForManualRetry() }
        coVerify(exactly = 1) { recordDao.markAsUploaded(1L) }
        coVerify(exactly = 1) { recordDao.markAsUploaded(2L) }
    }

    @Test
    fun `manual upload continues after a retryable failure`() = runTest {
        val first = record(id = 1L)
        val second = record(id = 2L)
        coEvery { recordDao.countUnuploadedRecords() } returnsMany listOf(2, 1)
        coEvery { recordDao.getPendingUploads(any()) } returns listOf(first, second)
        coEvery { recordDao.getRecordById(1L) } returns first
        coEvery { recordDao.getRecordById(2L) } returns second
        coEvery { cloudRecordService.uploadToPlatform(match { it.id == 1L }, "device-1") } returns
            Result.failure(RetryableUploadException("network error"))
        coEvery { cloudRecordService.uploadToPlatform(match { it.id == 2L }, "device-1") } returns
            Result.success(MorningCheckUploadResponse(code = 200, message = "success"))

        val result = manager(this).uploadAllUnuploaded()

        assertEquals(ManualUploadResult.Finished(2, 1, 1), result)
        coVerify(exactly = 1) { recordDao.markRetryableFailure(1L, 1, any(), "network error") }
        coVerify(exactly = 1) { recordDao.markAsUploaded(2L) }
        coVerify(exactly = 0) { recordDao.markPermanentFailure(1L, any()) }
    }

    @Test
    fun `manual upload reports missing platform configuration without resetting records`() = runTest {
        every { settingsRepository.platformUrl } returns MutableStateFlow("")
        coEvery { recordDao.countUnuploadedRecords() } returns 3

        val result = manager(this).uploadAllUnuploaded()

        assertTrue(result is ManualUploadResult.ConfigurationMissing)
        assertEquals(3, (result as ManualUploadResult.ConfigurationMissing).pendingCount)
        coVerify(exactly = 0) { recordDao.prepareUnuploadedForManualRetry() }
    }

    private fun manager(scope: CoroutineScope) = PendingUploadManager(
        context = context,
        recordDao = recordDao,
        cloudRecordService = cloudRecordService,
        settingsRepository = settingsRepository,
        appScope = scope,
    )

    private fun record(id: Long) = RecordEntity(
        id = id,
        recordUuid = "record-$id",
        uploadDeviceId = "device-1",
        userId = id,
        userName = "User $id",
        employeeId = "E$id",
        temperature = 36.5f,
        isTempNormal = true,
        isHandNormal = true,
        isPassed = true,
    )
}
