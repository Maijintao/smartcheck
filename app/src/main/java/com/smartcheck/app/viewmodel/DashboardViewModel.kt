package com.smartcheck.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartcheck.app.domain.repository.IRecordRepository
import com.smartcheck.app.domain.repository.IUserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Calendar
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class TodayCheckSummary(
    val totalCount: Int = 0,
    val completedCount: Int = 0,
    val pendingNames: List<String> = emptyList(),
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    userRepository: IUserRepository,
    recordRepository: IRecordRepository,
) : ViewModel() {

    private val todayStart = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private val todayEnd = todayStart + 24 * 60 * 60 * 1000L - 1

    val todaySummary: StateFlow<TodayCheckSummary> = combine(
        userRepository.observeAllUsers(),
        recordRepository.observeRecordsByDateRange(todayStart, todayEnd),
    ) { users, records ->
        val checkedUserIds = records.map { it.userId }.toSet()
        val checkedEmployeeIds = records.map { it.employeeId.trim() }.filter { it.isNotBlank() }.toSet()
        val pendingUsers = users.filterNot { user ->
            user.id in checkedUserIds || user.employeeId.trim() in checkedEmployeeIds
        }
        TodayCheckSummary(
            totalCount = users.size,
            completedCount = users.size - pendingUsers.size,
            pendingNames = pendingUsers.map { it.name.ifBlank { it.employeeId } },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TodayCheckSummary(),
    )
}
