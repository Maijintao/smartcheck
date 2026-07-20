package com.smartcheck.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartcheck.app.data.sync.EmployeeSyncEngine
import com.smartcheck.app.data.sync.EmployeeSyncRepository
import com.smartcheck.app.data.sync.SyncEngineStatus
import com.smartcheck.app.domain.model.User
import com.smartcheck.app.domain.repository.IUserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import java.time.LocalDate
import java.time.ZoneId

@HiltViewModel
class EmployeeListViewModel @Inject constructor(
    userRepository: IUserRepository,
    private val syncRepo: EmployeeSyncRepository,
    private val syncEngine: EmployeeSyncEngine
) : ViewModel() {

    data class EmployeeListItem(
        val id: String,
        val employeeId: String,          // 用于删除时标识
        val platformVersion: Long,       // 用于删除时作为 expected_version
        val name: String,
        val phone: String,
        val position: String,
        val department: String,
        val daysRemaining: Int,
        val faceImagePath: String?,
        val syncStatus: String
    )

    private val query = MutableStateFlow("")
    private val page = MutableStateFlow(0)
    private val pageSize = 10

    /** 同步引擎状态 */
    val syncState: StateFlow<SyncEngineStatus> = syncEngine.syncState

    /** 同步引擎错误信息 */
    val syncError: StateFlow<String?> = syncEngine.syncError

    /** 上次同步时间（从 sync_state 表观察） */
    val lastSyncTime: StateFlow<Long?> = syncRepo.observeSyncState()
        .map { it?.lastSyncTime }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    data class UiState(
        val items: List<EmployeeListItem>,
        val pageIndex: Int,
        val totalPages: Int,
        val totalCount: Int,
        val query: String
    )

    val uiState: StateFlow<UiState> = combine(
        userRepository.observeAllUsers(),
        query,
        page
    ) { users: List<User>, q, p ->
        val filtered = users.filter { user ->
            val key = q.trim().lowercase()
            if (key.isEmpty()) {
                true
            } else {
                user.name.lowercase().contains(key) || user.employeeId.lowercase().contains(key)
            }
        }.map { user ->
            EmployeeListItem(
                id = user.id.toString(),
                employeeId = user.employeeId,
                platformVersion = user.platformVersion,
                name = user.name,
                phone = user.phone,
                position = user.position,
                department = user.department,
                daysRemaining = calcRemainingDays(user.healthCertEndDate),
                faceImagePath = user.faceImagePath,
                syncStatus = user.syncStatus
            )
        }
        val totalPages = maxOf(1, (filtered.size + pageSize - 1) / pageSize)
        val safePage = p.coerceIn(0, totalPages - 1)
        val startIndex = safePage * pageSize
        val endIndex = (startIndex + pageSize).coerceAtMost(filtered.size)
        val pageItems = if (filtered.isEmpty()) emptyList() else filtered.subList(startIndex, endIndex)

        UiState(
            items = pageItems,
            pageIndex = safePage,
            totalPages = totalPages,
            totalCount = filtered.size,
            query = q
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        UiState(emptyList(), 0, 1, 0, "")
    )

    private fun calcRemainingDays(endAt: Long?): Int {
        if (endAt == null) return 0
        val endDate = java.time.Instant.ofEpochMilli(endAt)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
        return java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), endDate).toInt()
    }

    fun setQuery(value: String) {
        query.value = value
        page.value = 0
    }

    fun nextPage() {
        page.value = page.value + 1
    }

    fun prevPage() {
        page.value = (page.value - 1).coerceAtLeast(0)
    }

    /** 手动触发同步 */
    fun triggerSync() {
        viewModelScope.launch {
            try {
                syncEngine.triggerSync()
            } catch (e: Exception) {
                Timber.w(e, "手动同步失败")
            }
        }
    }

    /** 删除员工（走 outbox） */
    fun deleteEmployee(employeeId: String, platformVersion: Long) {
        viewModelScope.launch {
            val result = syncRepo.deleteLocal(employeeId, platformVersion)
            if (result.isSuccess) {
                syncEngine.triggerSync()
                Timber.d("删除员工成功: $employeeId")
            } else {
                Timber.e("删除员工失败: ${result.exceptionOrNull()?.message}")
            }
        }
    }
}
