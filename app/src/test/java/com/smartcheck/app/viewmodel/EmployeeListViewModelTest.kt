package com.smartcheck.app.viewmodel

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.smartcheck.app.domain.model.User
import com.smartcheck.app.domain.repository.IUserRepository
import com.smartcheck.app.data.sync.EmployeeSyncEngine
import com.smartcheck.app.data.sync.EmployeeSyncRepository
import com.smartcheck.app.data.sync.SyncEngineStatus
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EmployeeListViewModelTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var userRepository: IUserRepository
    private lateinit var syncRepo: EmployeeSyncRepository
    private lateinit var syncEngine: EmployeeSyncEngine
    private val usersFlow = MutableStateFlow<List<User>>(emptyList())

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        userRepository = mockk()
        syncRepo = mockk(relaxed = true)
        syncEngine = mockk(relaxed = true)
        every { userRepository.observeAllUsers() } returns usersFlow
        every { syncEngine.syncState } returns MutableStateFlow(SyncEngineStatus.IDLE)
        every { syncEngine.syncError } returns MutableStateFlow(null)
        every { syncRepo.observeSyncState() } returns flowOf(null)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = EmployeeListViewModel(userRepository, syncRepo, syncEngine)

    private fun buildUser(id: Long, name: String, employeeId: String) =
        User(id = id, name = name, employeeId = employeeId)

    // ── 初始状态 ────────────────────────────────────────────────────────────

    @Test
    fun `无用户时 uiState 初始 totalCount 为 0`() {
        val viewModel = createViewModel()

        assertEquals(0, viewModel.uiState.value.totalCount)
    }

    @Test
    fun `无用户时 uiState 初始 totalPages 为 1`() {
        val viewModel = createViewModel()

        // maxOf(1, ...) 保底 1 页
        assertEquals(1, viewModel.uiState.value.totalPages)
    }

    // ── 数据更新 ────────────────────────────────────────────────────────────

    @Test
    fun `用户列表更新后 totalCount 正确`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        val job = launch { viewModel.uiState.collect { } }

        usersFlow.value = listOf(
            buildUser(1L, "张三", "E001"),
            buildUser(2L, "李四", "E002"),
        )

        assertEquals(2, viewModel.uiState.value.totalCount)
        job.cancel()
    }

    @Test
    fun `10 条以内用户 totalPages 为 1`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        val job = launch { viewModel.uiState.collect { } }

        usersFlow.value = (1..8).map { buildUser(it.toLong(), "用户$it", "E$it") }

        assertEquals(1, viewModel.uiState.value.totalPages)
        job.cancel()
    }

    @Test
    fun `11 条用户 totalPages 为 2`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        val job = launch { viewModel.uiState.collect { } }

        usersFlow.value = (1..11).map { buildUser(it.toLong(), "用户$it", "E$it") }

        assertEquals(2, viewModel.uiState.value.totalPages)
        job.cancel()
    }

    // ── setQuery：过滤逻辑 ──────────────────────────────────────────────────

    @Test
    fun `setQuery 按姓名过滤`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        usersFlow.value = listOf(buildUser(1L, "张三", "E001"), buildUser(2L, "李四", "E002"))
        val job = launch { viewModel.uiState.collect { } }

        viewModel.setQuery("张")

        assertEquals(1, viewModel.uiState.value.totalCount)
        assertEquals("张三", viewModel.uiState.value.items.first().name)
        job.cancel()
    }

    @Test
    fun `setQuery 按工号过滤`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        usersFlow.value = listOf(buildUser(1L, "张三", "E001"), buildUser(2L, "李四", "E002"))
        val job = launch { viewModel.uiState.collect { } }

        viewModel.setQuery("E002")

        assertEquals(1, viewModel.uiState.value.totalCount)
        assertEquals("李四", viewModel.uiState.value.items.first().name)
        job.cancel()
    }

    @Test
    fun `setQuery 大小写不敏感`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        usersFlow.value = listOf(
            buildUser(1L, "张三", "ABC001"),
            buildUser(2L, "李四", "DEF002"),
        )
        val job = launch { viewModel.uiState.collect { } }

        viewModel.setQuery("abc")

        assertEquals(1, viewModel.uiState.value.totalCount)
        job.cancel()
    }

    @Test
    fun `setQuery 为空时返回全部用户`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        usersFlow.value = listOf(buildUser(1L, "张三", "E001"), buildUser(2L, "李四", "E002"))
        val job = launch { viewModel.uiState.collect { } }

        viewModel.setQuery("张")
        viewModel.setQuery("")

        assertEquals(2, viewModel.uiState.value.totalCount)
        job.cancel()
    }

    @Test
    fun `setQuery 无匹配时 items 为空`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        usersFlow.value = listOf(buildUser(1L, "张三", "E001"))
        val job = launch { viewModel.uiState.collect { } }

        viewModel.setQuery("ZZZZZ")

        assertTrue(viewModel.uiState.value.items.isEmpty())
        job.cancel()
    }

    // ── setQuery：重置分页 ──────────────────────────────────────────────────

    @Test
    fun `setQuery 后 pageIndex 重置为 0`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        usersFlow.value = (1..20).map { buildUser(it.toLong(), "用户$it", "E$it") }
        val job = launch { viewModel.uiState.collect { } }

        viewModel.nextPage()       // pageIndex = 1
        viewModel.setQuery("用户") // 重置 pageIndex 为 0

        assertEquals(0, viewModel.uiState.value.pageIndex)
        job.cancel()
    }

    // ── nextPage / prevPage ─────────────────────────────────────────────────

    @Test
    fun `nextPage 在有多页时增加 pageIndex`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        usersFlow.value = (1..12).map { buildUser(it.toLong(), "用户$it", "E$it") }
        val job = launch { viewModel.uiState.collect { } }

        viewModel.nextPage()

        assertEquals(1, viewModel.uiState.value.pageIndex)
        job.cancel()
    }

    @Test
    fun `nextPage 超出最后一页时 pageIndex 被限制在最后一页`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        // 11 个用户 = 2 页（页码 0 和 1）
        usersFlow.value = (1..11).map { buildUser(it.toLong(), "用户$it", "E$it") }
        val job = launch { viewModel.uiState.collect { } }

        // 连续 nextPage 超过总页数
        viewModel.nextPage()
        viewModel.nextPage()
        viewModel.nextPage()

        // coerceIn(0, totalPages - 1) = coerceIn(0, 1) = 1
        assertEquals(1, viewModel.uiState.value.pageIndex)
        job.cancel()
    }

    @Test
    fun `prevPage 在第 0 页时不低于 0`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        usersFlow.value = listOf(buildUser(1L, "张三", "E001"))
        val job = launch { viewModel.uiState.collect { } }

        viewModel.prevPage()

        assertEquals(0, viewModel.uiState.value.pageIndex)
        job.cancel()
    }

    @Test
    fun `第二页 items 包含正确的用户`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        // 12 个用户：第 1 页 10 个，第 2 页 2 个
        usersFlow.value = (1..12).map { buildUser(it.toLong(), "用户$it", "E$it") }
        val job = launch { viewModel.uiState.collect { } }

        viewModel.nextPage()

        assertEquals(2, viewModel.uiState.value.items.size)
        job.cancel()
    }
}
