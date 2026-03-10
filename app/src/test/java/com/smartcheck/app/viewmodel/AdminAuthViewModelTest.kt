package com.smartcheck.app.viewmodel

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.smartcheck.app.domain.repository.IAdminAuthService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AdminAuthViewModelTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()

    private val loginStateFlow = MutableStateFlow(false)
    private val usernameFlow = MutableStateFlow<String?>(null)

    private lateinit var adminAuthService: IAdminAuthService
    private lateinit var viewModel: AdminAuthViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        adminAuthService = mockk(relaxed = true)
        every { adminAuthService.observeLoginState() } returns loginStateFlow
        every { adminAuthService.observeCurrentUsername() } returns usernameFlow
        every { adminAuthService.getDefaultAccount() } returns "admin"
        viewModel = AdminAuthViewModel(adminAuthService)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── 初始状态 ────────────────────────────────────────────────────────────

    @Test
    fun `初始状态 isLoggedIn 为 false`() = runTest {
        advanceUntilIdle()

        assertFalse(viewModel.isLoggedIn.value)
    }

    @Test
    fun `初始状态 currentRole 为 null`() {
        assertNull(viewModel.currentRole.value)
    }

    // ── observeLoginState 联动 ──────────────────────────────────────────────

    @Test
    fun `observeLoginState 发出 true 时 isLoggedIn 变为 true`() = runTest {
        advanceUntilIdle()   // 启动 init 协程

        loginStateFlow.value = true
        advanceUntilIdle()

        assertTrue(viewModel.isLoggedIn.value)
    }

    @Test
    fun `observeLoginState 由 true 变回 false 时 isLoggedIn 恢复 false`() = runTest {
        advanceUntilIdle()

        loginStateFlow.value = true
        advanceUntilIdle()
        loginStateFlow.value = false
        advanceUntilIdle()

        assertFalse(viewModel.isLoggedIn.value)
    }

    // ── observeCurrentUsername 联动 ─────────────────────────────────────────

    @Test
    fun `observeCurrentUsername 为 null 时 account 使用 defaultAccount`() = runTest {
        advanceUntilIdle()

        assertEquals("admin", viewModel.account.value)
    }

    @Test
    fun `observeCurrentUsername 有值时 account 更新为该用户名`() = runTest {
        advanceUntilIdle()

        usernameFlow.value = "manager_01"
        advanceUntilIdle()

        assertEquals("manager_01", viewModel.account.value)
    }

    // ── setCurrentRole ──────────────────────────────────────────────────────

    @Test
    fun `setCurrentRole 直接更新 currentRole StateFlow`() {
        viewModel.setCurrentRole("manager")

        assertEquals("manager", viewModel.currentRole.value)
    }

    @Test
    fun `setCurrentRole null 时 currentRole 为 null`() {
        viewModel.setCurrentRole("admin")
        viewModel.setCurrentRole(null)

        assertNull(viewModel.currentRole.value)
    }

    // ── login ───────────────────────────────────────────────────────────────

    @Test
    fun `login 成功时回调 success 并将 currentRole 设为 admin`() = runTest {
        coEvery { adminAuthService.login("admin", "123456") } returns Result.success("token")

        var callbackResult: Result<String>? = null
        viewModel.login("admin", "123456") { callbackResult = it }
        advanceUntilIdle()

        assertNotNull(callbackResult)
        assertTrue(callbackResult!!.isSuccess)
        assertEquals("admin", viewModel.currentRole.value)
    }

    @Test
    fun `login 非 admin 账号成功时 currentRole 设为 employee`() = runTest {
        coEvery { adminAuthService.login("user01", "pass") } returns Result.success("token")

        viewModel.login("user01", "pass") { }
        advanceUntilIdle()

        assertEquals("employee", viewModel.currentRole.value)
    }

    @Test
    fun `login 失败时回调 failure 并附带原始错误信息`() = runTest {
        coEvery { adminAuthService.login(any(), any()) } returns Result.failure(Exception("密码错误"))

        var callbackResult: Result<String>? = null
        viewModel.login("admin", "wrong") { callbackResult = it }
        advanceUntilIdle()

        assertNotNull(callbackResult)
        assertTrue(callbackResult!!.isFailure)
        assertEquals("密码错误", callbackResult!!.exceptionOrNull()?.message)
    }

    @Test
    fun `login 失败时 currentRole 不变`() = runTest {
        coEvery { adminAuthService.login(any(), any()) } returns Result.failure(Exception("failed"))

        viewModel.login("admin", "wrong") { }
        advanceUntilIdle()

        assertNull(viewModel.currentRole.value)
    }

    // ── logout ──────────────────────────────────────────────────────────────

    @Test
    fun `logout 调用 adminAuthService logout`() = runTest {
        coEvery { adminAuthService.logout() } returns Result.success(Unit)

        viewModel.logout()
        advanceUntilIdle()

        coVerify { adminAuthService.logout() }
    }

    // ── changePassword ──────────────────────────────────────────────────────

    @Test
    fun `changePassword 成功时回调 success`() = runTest {
        coEvery { adminAuthService.changePassword("old", "new") } returns Result.success(Unit)

        var callbackResult: Result<Unit>? = null
        viewModel.changePassword("old", "new") { callbackResult = it }
        advanceUntilIdle()

        assertTrue(callbackResult?.isSuccess == true)
    }

    @Test
    fun `changePassword 失败时回调 failure 并附带错误信息`() = runTest {
        coEvery { adminAuthService.changePassword(any(), any()) } returns Result.failure(Exception("原密码错误"))

        var callbackResult: Result<Unit>? = null
        viewModel.changePassword("wrong", "new") { callbackResult = it }
        advanceUntilIdle()

        assertTrue(callbackResult?.isFailure == true)
        assertEquals("原密码错误", callbackResult?.exceptionOrNull()?.message)
    }
}
