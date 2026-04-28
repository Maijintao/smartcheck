package com.smartcheck.app.domain.usecase

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import com.smartcheck.app.domain.model.AppError
import com.smartcheck.app.domain.model.User
import com.smartcheck.app.domain.repository.IUserRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class UserManageUseCaseTest {

    private lateinit var userRepository: IUserRepository
    private lateinit var useCase: UserManageUseCase

    @Before
    fun setup() {
        userRepository = mockk(relaxed = true)
        useCase = UserManageUseCase(userRepository)
    }

    @Test
    fun `observeAllUsers returns flow from repository`() = runTest {
        // Given
        val users = listOf(
            User(id = 1, name = "张三", employeeId = "E001"),
            User(id = 2, name = "李四", employeeId = "E002")
        )
        coEvery { userRepository.observeAllUsers() } returns flowOf(users)

        // When
        val result = useCase.observeAllUsers().first()

        // Then
        assertEquals(2, result.size)
        assertEquals("张三", result[0].name)
    }

    @Test
    fun `createUser fails when name is blank`() = runTest {
        // Given
        val user = User(name = "", employeeId = "E001")

        // When
        val result = useCase.createUser(user)

        // Then
        assertTrue(result.isFailure)
    }

    @Test
    fun `createUser fails when employeeId is blank`() = runTest {
        // Given
        val user = User(name = "张三", employeeId = "")

        // When
        val result = useCase.createUser(user)

        // Then
        assertTrue(result.isFailure)
    }

    @Test
    fun `createUser fails when employeeId already exists`() = runTest {
        // Given
        val existingUser = User(id = 1, name = "王五", employeeId = "E001")
        coEvery { userRepository.getUserByEmployeeId("E001") } returns Result.success(existingUser)
        
        val newUser = User(name = "张三", employeeId = "E001")

        // When
        val result = useCase.createUser(newUser)

        // Then
        assertTrue(result.isFailure)
    }

    @Test
    fun `createUser succeeds when valid`() = runTest {
        // Given
        coEvery { userRepository.getUserByEmployeeId("E001") } returns Result.failure(AppError.NotFound)
        coEvery { userRepository.createUser(any()) } returns Result.success(1L)
        
        val newUser = User(name = "张三", employeeId = "E001")

        // When
        val result = useCase.createUser(newUser)

        // Then
        assertTrue(result.isSuccess)
        coVerify { userRepository.createUser(newUser) }
    }

    @Test
    fun `deleteUser calls repository`() = runTest {
        // Given
        coEvery { userRepository.deleteUser(1L) } returns Result.success(Unit)

        // When
        val result = useCase.deleteUser(1L)

        // Then
        assertTrue(result.isSuccess)
        coVerify { userRepository.deleteUser(1L) }
    }

    // ── updateUser ──────────────────────────────────────────────────────────

    @Test
    fun `updateUser 姓名不为空时调用 repository 并返回 success`() = runTest {
        // Given
        val user = User(id = 1L, name = "张三", employeeId = "E001")
        coEvery { userRepository.updateUser(user) } returns Result.success(Unit)

        // When
        val result = useCase.updateUser(user)

        // Then
        assertTrue(result.isSuccess)
        coVerify { userRepository.updateUser(user) }
    }

    @Test
    fun `updateUser 姓名为空时返回 ValidationError 且不调用 repository`() = runTest {
        // Given
        val user = User(id = 1L, name = "", employeeId = "E001")

        // When
        val result = useCase.updateUser(user)

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is AppError.ValidationError)
        coVerify(exactly = 0) { userRepository.updateUser(any()) }
    }

    // ── deactivateUser ──────────────────────────────────────────────────────

    @Test
    fun `deactivateUser 存在的用户时将 isActive 置为 false`() = runTest {
        // Given
        val user = User(id = 1L, name = "张三", employeeId = "E001", isActive = true)
        coEvery { userRepository.getUserById(1L) } returns Result.success(user)
        coEvery { userRepository.updateUser(user.copy(isActive = false)) } returns Result.success(Unit)

        // When
        val result = useCase.deactivateUser(1L)

        // Then
        assertTrue(result.isSuccess)
        coVerify { userRepository.updateUser(user.copy(isActive = false)) }
    }

    @Test
    fun `deactivateUser 用户不存在时返回 failure`() = runTest {
        // Given
        coEvery { userRepository.getUserById(99L) } returns Result.failure(AppError.NotFound)

        // When
        val result = useCase.deactivateUser(99L)

        // Then
        assertTrue(result.isFailure)
        coVerify(exactly = 0) { userRepository.updateUser(any()) }
    }

    // ── activateUser ────────────────────────────────────────────────────────

    @Test
    fun `activateUser 存在的用户时将 isActive 置为 true`() = runTest {
        // Given
        val user = User(id = 1L, name = "张三", employeeId = "E001", isActive = false)
        coEvery { userRepository.getUserById(1L) } returns Result.success(user)
        coEvery { userRepository.updateUser(user.copy(isActive = true)) } returns Result.success(Unit)

        // When
        val result = useCase.activateUser(1L)

        // Then
        assertTrue(result.isSuccess)
        coVerify { userRepository.updateUser(user.copy(isActive = true)) }
    }

    @Test
    fun `activateUser 用户不存在时返回 failure`() = runTest {
        // Given
        coEvery { userRepository.getUserById(99L) } returns Result.failure(AppError.NotFound)

        // When
        val result = useCase.activateUser(99L)

        // Then
        assertTrue(result.isFailure)
    }

    // ── getUserById / getUserByEmployeeId ───────────────────────────────────

    @Test
    fun `getUserById 委托给 userRepository`() = runTest {
        // Given
        val user = User(id = 1L, name = "张三", employeeId = "E001")
        coEvery { userRepository.getUserById(1L) } returns Result.success(user)

        // When
        val result = useCase.getUserById(1L)

        // Then
        assertTrue(result.isSuccess)
        assertEquals(user, result.getOrNull())
    }

    @Test
    fun `getUserByEmployeeId 委托给 userRepository`() = runTest {
        // Given
        val user = User(id = 1L, name = "张三", employeeId = "E001")
        coEvery { userRepository.getUserByEmployeeId("E001") } returns Result.success(user)

        // When
        val result = useCase.getUserByEmployeeId("E001")

        // Then
        assertTrue(result.isSuccess)
        assertEquals(user, result.getOrNull())
    }
}
