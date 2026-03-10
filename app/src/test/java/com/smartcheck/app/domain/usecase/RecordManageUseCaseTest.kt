package com.smartcheck.app.domain.usecase

import com.smartcheck.app.domain.model.HandStatus
import com.smartcheck.app.domain.model.HealthCertStatus
import com.smartcheck.app.domain.model.Record
import com.smartcheck.app.domain.repository.IRecordRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RecordManageUseCaseTest {

    private lateinit var recordRepository: IRecordRepository
    private lateinit var useCase: RecordManageUseCase

    private fun buildRecord(id: Long = 1L, userId: Long = 10L): Record = Record(
        id = id,
        userId = userId,
        userName = "张三",
        employeeId = "E001",
        temperature = 36.5f,
        isTempNormal = true,
        isHandNormal = true,
        isPassed = true
    )

    @Before
    fun setup() {
        recordRepository = mockk(relaxed = true)
        useCase = RecordManageUseCase(recordRepository)
    }

    // ── observeRecentRecords ────────────────────────────────────────────────

    @Test
    fun `observeRecentRecords 返回 repository 的 Flow`() = runTest {
        // Given
        val records = listOf(buildRecord(1L), buildRecord(2L))
        coEvery { recordRepository.observeRecentRecords(100) } returns flowOf(records)

        // When
        val result = useCase.observeRecentRecords().first()

        // Then
        assertEquals(2, result.size)
        assertEquals(1L, result[0].id)
    }

    @Test
    fun `observeRecentRecords 带自定义 limit 时传递正确参数`() = runTest {
        // Given
        coEvery { recordRepository.observeRecentRecords(50) } returns flowOf(emptyList())

        // When
        useCase.observeRecentRecords(limit = 50).first()

        // Then
        coVerify { recordRepository.observeRecentRecords(50) }
    }

    // ── observeRecordsByDateRange ───────────────────────────────────────────

    @Test
    fun `observeRecordsByDateRange 传递正确的时间范围参数`() = runTest {
        // Given
        val startTime = 1_000_000L
        val endTime = 2_000_000L
        val records = listOf(buildRecord())
        coEvery { recordRepository.observeRecordsByDateRange(startTime, endTime) } returns flowOf(records)

        // When
        val result = useCase.observeRecordsByDateRange(startTime, endTime).first()

        // Then
        assertEquals(1, result.size)
        coVerify { recordRepository.observeRecordsByDateRange(startTime, endTime) }
    }

    // ── observeRecordsByUser ────────────────────────────────────────────────

    @Test
    fun `observeRecordsByUser 委托给 repository`() = runTest {
        // Given
        val userId = 10L
        val records = listOf(buildRecord(userId = userId))
        coEvery { recordRepository.observeRecordsByUser(userId) } returns flowOf(records)

        // When
        val result = useCase.observeRecordsByUser(userId).first()

        // Then
        assertEquals(1, result.size)
        assertEquals(userId, result[0].userId)
    }

    // ── getRecordById ───────────────────────────────────────────────────────

    @Test
    fun `getRecordById 成功时返回对应 Record`() = runTest {
        // Given
        val record = buildRecord(id = 5L)
        coEvery { recordRepository.getRecordById(5L) } returns Result.success(record)

        // When
        val result = useCase.getRecordById(5L)

        // Then
        assertTrue(result.isSuccess)
        assertEquals(5L, result.getOrNull()?.id)
    }

    @Test
    fun `getRecordById 失败时返回 failure`() = runTest {
        // Given
        coEvery { recordRepository.getRecordById(99L) } returns Result.failure(Exception("Not found"))

        // When
        val result = useCase.getRecordById(99L)

        // Then
        assertTrue(result.isFailure)
    }

    // ── deleteOldRecords ────────────────────────────────────────────────────

    @Test
    fun `deleteOldRecords 委托给 repository 并传递正确时间戳`() = runTest {
        // Given
        val beforeTime = 9_999_999L
        coEvery { recordRepository.deleteOldRecords(beforeTime) } returns Result.success(Unit)

        // When
        val result = useCase.deleteOldRecords(beforeTime)

        // Then
        assertTrue(result.isSuccess)
        coVerify { recordRepository.deleteOldRecords(beforeTime) }
    }

    // ── deleteAllRecords ────────────────────────────────────────────────────

    @Test
    fun `deleteAllRecords repository 成功时返回 success`() = runTest {
        // Given
        coEvery { recordRepository.deleteAllRecords() } returns Result.success(Unit)

        // When
        val result = useCase.deleteAllRecords()

        // Then
        assertTrue(result.isSuccess)
        coVerify { recordRepository.deleteAllRecords() }
    }

    @Test
    fun `deleteAllRecords repository 失败时返回 failure`() = runTest {
        // Given
        coEvery { recordRepository.deleteAllRecords() } returns Result.failure(Exception("DB error"))

        // When
        val result = useCase.deleteAllRecords()

        // Then
        assertTrue(result.isFailure)
    }

    // ── getTodayRecordByUser ────────────────────────────────────────────────

    @Test
    fun `getTodayRecordByUser 委托给 repository`() = runTest {
        // Given
        val record = buildRecord()
        coEvery { recordRepository.getTodayRecordByUser(10L) } returns Result.success(record)

        // When
        val result = useCase.getTodayRecordByUser(10L)

        // Then
        assertTrue(result.isSuccess)
        assertEquals(record.userId, result.getOrNull()?.userId)
    }
}
