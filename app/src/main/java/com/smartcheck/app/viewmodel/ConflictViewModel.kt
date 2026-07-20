package com.smartcheck.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartcheck.app.data.sync.ConflictHandler
import com.smartcheck.app.data.sync.ConflictInfo
import com.smartcheck.app.data.sync.EmployeeSyncEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class ConflictViewModel @Inject constructor(
    private val conflictHandler: ConflictHandler,
    private val syncEngine: EmployeeSyncEngine
) : ViewModel() {

    private val _conflicts = MutableStateFlow<List<ConflictInfo>>(emptyList())
    val conflicts: StateFlow<List<ConflictInfo>> = _conflicts.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun loadConflicts() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _conflicts.value = conflictHandler.getConflicts()
            } catch (e: Exception) {
                Timber.e(e, "加载冲突列表失败")
            }
            _isLoading.value = false
        }
    }

    /** 用户选择「使用平台数据」 */
    fun acceptRemote(employeeId: String) {
        viewModelScope.launch {
            val result = conflictHandler.acceptRemote(employeeId)
            if (result.isSuccess) {
                syncEngine.triggerSync()
            }
            loadConflicts()
        }
    }

    /** 用户选择「重提本地修改」 */
    fun retryLocal(employeeId: String) {
        viewModelScope.launch {
            val result = conflictHandler.retryLocal(employeeId)
            if (result.isSuccess) {
                syncEngine.triggerSync()
            }
            loadConflicts()
        }
    }
}
