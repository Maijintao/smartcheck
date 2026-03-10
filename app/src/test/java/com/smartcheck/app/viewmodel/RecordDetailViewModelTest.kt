package com.smartcheck.app.viewmodel

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import com.smartcheck.app.domain.model.HandStatus
import com.smartcheck.app.domain.model.HealthCertStatus
import com.smartcheck.app.domain.model.Record
import com.smartcheck.app.domain.model.SymptomType
import com.smartcheck.app.domain.repository.IRecordRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RecordDetailViewModelTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var recordRepository: IRecordRepository

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        recordRepository = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildRecord(id: Long = 5L) = Record(
        id = id,
        userId = 10L,
        userName = "张三",
        employeeId = "E001",
        temperature = 36.5f,
        isTempNormal = true,
        isHandNormal = true,
        isPassed = true,
    )

    private fun createViewModel(idStr: String? = "5"): RecordDetailViewModel {
        val handle = if (idStr != null) SavedStateHandle(mapOf("id" to idStr)) else SavedStateHandle()
        return RecordDetailViewModel(recordRepository, handle)
    }

    // ── init：记录加载 ──────────────────────────────────────────────────────

    @Test
    fun `init 时从 repository 加载 SavedStateHandle 中的 Record`() = runTest {
        val record = buildRecord()
        coEvery { recordRepository.getRecordById(5L) } returns Result.success(record)

        val viewModel = createViewModel("5")
        advanceUntilIdle()

        assertEquals(record, viewModel.record.value)
    }

    @Test
    fun `init repository 返回失败时 record 保持 null`() = runTest {
        coEvery { recordRepository.getRecordById(5L) } returns Result.failure(Exception("Not found"))

        val viewModel = createViewModel("5")
        advanceUntilIdle()

        assertNull(viewModel.record.value)
    }

    @Test
    fun `SavedStateHandle 无 id 时不调用 repository 且 record 为 null`() = runTest {
        val viewModel = createViewModel(idStr = null)
        advanceUntilIdle()

        assertNull(viewModel.record.value)
        coVerify(exactly = 0) { recordRepository.getRecordById(any()) }
    }

    @Test
    fun `SavedStateHandle id 非数字时不加载 Record`() = runTest {
        val viewModel = createViewModel("invalid_id")
        advanceUntilIdle()

        assertNull(viewModel.record.value)
        coVerify(exactly = 0) { recordRepository.getRecordById(any()) }
    }

    // ── updateRecord：温度解析 ──────────────────────────────────────────────

    @Test
    fun `updateRecord 温度低于 37_3 时 isTempNormal 为 true`() = runTest {
        coEvery { recordRepository.getRecordById(5L) } returns Result.success(buildRecord())
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateRecord(36.5f, "NORMAL", "VALID", "", "")
        advanceUntilIdle()

        assertTrue(viewModel.record.value!!.isTempNormal)
    }

    @Test
    fun `updateRecord 温度高于 37_3 时 isTempNormal 为 false`() = runTest {
        coEvery { recordRepository.getRecordById(5L) } returns Result.success(buildRecord())
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateRecord(38.0f, "NORMAL", "VALID", "", "")
        advanceUntilIdle()

        assertFalse(viewModel.record.value!!.isTempNormal)
    }

    // ── updateRecord：isPassed 逻辑 ─────────────────────────────────────────

    @Test
    fun `updateRecord 温度正常手部正常时 isPassed 为 true`() = runTest {
        coEvery { recordRepository.getRecordById(5L) } returns Result.success(buildRecord())
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateRecord(36.5f, "NORMAL", "VALID", "", "")
        advanceUntilIdle()

        assertTrue(viewModel.record.value!!.isPassed)
    }

    @Test
    fun `updateRecord 手部异常时 isPassed 为 false`() = runTest {
        coEvery { recordRepository.getRecordById(5L) } returns Result.success(buildRecord())
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateRecord(36.5f, "ABNORMAL", "VALID", "", "")
        advanceUntilIdle()

        assertFalse(viewModel.record.value!!.isPassed)
    }

    // ── updateRecord：HandStatus 枚举解析 ───────────────────────────────────

    @Test
    fun `updateRecord 解析 HandStatus NORMAL 字符串`() = runTest {
        coEvery { recordRepository.getRecordById(5L) } returns Result.success(buildRecord())
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateRecord(36.5f, "NORMAL", "VALID", "", "")
        advanceUntilIdle()

        assertEquals(HandStatus.NORMAL, viewModel.record.value!!.handStatus)
    }

    @Test
    fun `updateRecord 解析 HandStatus ABNORMAL 字符串`() = runTest {
        coEvery { recordRepository.getRecordById(5L) } returns Result.success(buildRecord())
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateRecord(36.5f, "ABNORMAL", "VALID", "", "")
        advanceUntilIdle()

        assertEquals(HandStatus.ABNORMAL, viewModel.record.value!!.handStatus)
    }

    @Test
    fun `updateRecord 无效 HandStatus 字符串回退为 NOT_CHECKED`() = runTest {
        coEvery { recordRepository.getRecordById(5L) } returns Result.success(buildRecord())
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateRecord(36.5f, "BAD_VALUE", "VALID", "", "")
        advanceUntilIdle()

        assertEquals(HandStatus.NOT_CHECKED, viewModel.record.value!!.handStatus)
    }

    // ── updateRecord：HealthCertStatus 枚举解析 ─────────────────────────────

    @Test
    fun `updateRecord 解析 HealthCertStatus EXPIRED 字符串`() = runTest {
        coEvery { recordRepository.getRecordById(5L) } returns Result.success(buildRecord())
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateRecord(36.5f, "NORMAL", "EXPIRED", "", "")
        advanceUntilIdle()

        assertEquals(HealthCertStatus.EXPIRED, viewModel.record.value!!.healthCertStatus)
    }

    @Test
    fun `updateRecord 无效 HealthCertStatus 字符串回退为 VALID`() = runTest {
        coEvery { recordRepository.getRecordById(5L) } returns Result.success(buildRecord())
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateRecord(36.5f, "NORMAL", "NONSENSE", "", "")
        advanceUntilIdle()

        assertEquals(HealthCertStatus.VALID, viewModel.record.value!!.healthCertStatus)
    }

    // ── updateRecord：SymptomType 解析 ──────────────────────────────────────

    @Test
    fun `updateRecord 解析症状字符串为 SymptomType 列表`() = runTest {
        coEvery { recordRepository.getRecordById(5L) } returns Result.success(buildRecord())
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateRecord(36.5f, "NORMAL", "VALID", "FEVER,COUGH", "")
        advanceUntilIdle()

        assertEquals(listOf(SymptomType.FEVER, SymptomType.COUGH), viewModel.record.value!!.symptomFlags)
    }

    @Test
    fun `updateRecord 无效症状字符串被过滤掉`() = runTest {
        coEvery { recordRepository.getRecordById(5L) } returns Result.success(buildRecord())
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateRecord(36.5f, "NORMAL", "VALID", "FEVER,UNKNOWN_SYMPTOM", "")
        advanceUntilIdle()

        assertEquals(listOf(SymptomType.FEVER), viewModel.record.value!!.symptomFlags)
    }

    @Test
    fun `updateRecord 空症状字符串时 symptomFlags 为空列表`() = runTest {
        coEvery { recordRepository.getRecordById(5L) } returns Result.success(buildRecord())
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateRecord(36.5f, "NORMAL", "VALID", "", "备注")
        advanceUntilIdle()

        assertTrue(viewModel.record.value!!.symptomFlags.isEmpty())
    }

    // ── updateRecord：remark / repository 调用 ──────────────────────────────

    @Test
    fun `updateRecord 调用 recordRepository updateRecord`() = runTest {
        coEvery { recordRepository.getRecordById(5L) } returns Result.success(buildRecord())
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateRecord(36.5f, "NORMAL", "VALID", "", "测试备注")
        advanceUntilIdle()

        coVerify { recordRepository.updateRecord(any()) }
    }

    @Test
    fun `updateRecord record 为 null 时不调用 repository`() = runTest {
        val viewModel = createViewModel(idStr = null)
        advanceUntilIdle()

        viewModel.updateRecord(36.5f, "NORMAL", "VALID", "", "")
        advanceUntilIdle()

        coVerify(exactly = 0) { recordRepository.updateRecord(any()) }
    }
}
