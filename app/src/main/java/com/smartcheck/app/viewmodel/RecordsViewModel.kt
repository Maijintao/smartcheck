package com.smartcheck.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartcheck.app.data.db.RecordEntity
import com.smartcheck.app.data.repository.RecordRepository
import com.smartcheck.app.data.upload.ManualUploadResult
import com.smartcheck.app.data.upload.PendingUploadManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import java.util.Calendar
import javax.inject.Inject

@HiltViewModel
class RecordsViewModel @Inject constructor(
    private val recordRepository: RecordRepository,
    private val pendingUploadManager: PendingUploadManager,
) : ViewModel() {

    enum class TimeFilter {
        TODAY,
        WEEK,
        MONTH,
        ALL
    }

    private val _timeFilter = MutableStateFlow(TimeFilter.TODAY)
    val timeFilter: StateFlow<TimeFilter> = _timeFilter.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _handStatus = MutableStateFlow<Set<String>>(emptySet())
    val handStatus: StateFlow<Set<String>> = _handStatus.asStateFlow()

    private val _healthCertStatus = MutableStateFlow<Set<String>>(emptySet())
    val healthCertStatus: StateFlow<Set<String>> = _healthCertStatus.asStateFlow()

    private val _symptomFlags = MutableStateFlow<Set<String>>(emptySet())
    val symptomFlags: StateFlow<Set<String>> = _symptomFlags.asStateFlow()

    val unuploadedCount: StateFlow<Int> = recordRepository.observeUnuploadedCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _manualUploadState = MutableStateFlow(ManualUploadUiState())
    val manualUploadState: StateFlow<ManualUploadUiState> = _manualUploadState.asStateFlow()

    val records: StateFlow<List<RecordEntity>> =
        combine(timeFilter, query, handStatus, healthCertStatus, symptomFlags) { filter, q, hand, cert, symptoms ->
            FilterState(filter, q, hand, cert, symptoms)
        }
            .flatMapLatest { filterState ->
                val now = System.currentTimeMillis()
                val base = when (filterState.filter) {
                    TimeFilter.TODAY -> {
                        val start = Calendar.getInstance().apply {
                            set(Calendar.HOUR_OF_DAY, 0)
                            set(Calendar.MINUTE, 0)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }.timeInMillis
                        recordRepository.getRecordsByTimeRange(start, now)
                    }

                    TimeFilter.WEEK -> {
                        val start = now - TimeUnit.DAYS.toMillis(7)
                        recordRepository.getRecordsByTimeRange(start, now)
                    }

                    TimeFilter.MONTH -> {
                        val start = now - TimeUnit.DAYS.toMillis(30)
                        recordRepository.getRecordsByTimeRange(start, now)
                    }

                    TimeFilter.ALL -> {
                        recordRepository.getRecentRecords(500)
                    }
                }

                base.map { list ->
                    val trimmed = filterState.query.trim()
                    if (trimmed.isEmpty()) {
                        list
                    } else {
                        val lower = trimmed.lowercase()
                        list.filter {
                            it.userName.lowercase().contains(lower) ||
                                it.employeeId.lowercase().contains(lower)
                        }
                    }.filter { record ->
                        val handOk = filterState.handStatus.isEmpty() || filterState.handStatus.contains(record.handStatus)
                        val certOk = filterState.healthCertStatus.isEmpty() || filterState.healthCertStatus.contains(record.healthCertStatus)
                        val symptomOk = filterState.symptomFlags.isEmpty() || filterState.symptomFlags.any { flag ->
                            record.symptomFlags.contains(flag)
                        }
                        handOk && certOk && symptomOk
                    }
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setTimeFilter(filter: TimeFilter) {
        _timeFilter.value = filter
    }

    fun setQuery(value: String) {
        _query.value = value
    }

    fun setHandStatusFilter(status: String?) {
        _handStatus.value = if (status.isNullOrEmpty()) emptySet() else setOf(status)
    }

    fun toggleHandStatus(value: String) {
        _handStatus.value = toggleSetValue(_handStatus.value, value)
    }

    fun toggleHealthCertStatus(value: String) {
        _healthCertStatus.value = toggleSetValue(_healthCertStatus.value, value)
    }

    fun toggleSymptomFlag(value: String) {
        _symptomFlags.value = toggleSetValue(_symptomFlags.value, value)
    }

    fun uploadAllUnuploaded() {
        if (_manualUploadState.value.isUploading) return

        viewModelScope.launch {
            _manualUploadState.value = ManualUploadUiState(isUploading = true)
            try {
                val message = when (val result = pendingUploadManager.uploadAllUnuploaded()) {
                    ManualUploadResult.NothingToUpload -> "当前没有未上传的晨检记录"
                    is ManualUploadResult.ConfigurationMissing -> "请先在设置中配置平台地址和 API Key"
                    is ManualUploadResult.Finished -> when {
                        result.remainingCount == 0 -> "已成功上传 ${result.uploadedCount} 条晨检记录"
                        result.uploadedCount == 0 -> "上传失败，仍有 ${result.remainingCount} 条记录未上传"
                        else -> "已上传 ${result.uploadedCount} 条，仍有 ${result.remainingCount} 条未上传"
                    }
                }
                _manualUploadState.value = ManualUploadUiState(message = message)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _manualUploadState.value = ManualUploadUiState(message = "上传失败：${e.message ?: "未知错误"}")
            }
        }
    }

    fun consumeManualUploadMessage() {
        _manualUploadState.value = _manualUploadState.value.copy(message = null)
    }

    private data class FilterState(
        val filter: TimeFilter,
        val query: String,
        val handStatus: Set<String>,
        val healthCertStatus: Set<String>,
        val symptomFlags: Set<String>
    )

    private fun toggleSetValue(set: Set<String>, value: String): Set<String> {
        return if (set.contains(value)) set - value else set + value
    }
}

data class ManualUploadUiState(
    val isUploading: Boolean = false,
    val message: String? = null,
)
