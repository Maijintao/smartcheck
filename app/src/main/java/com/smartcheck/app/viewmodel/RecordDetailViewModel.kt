package com.smartcheck.app.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartcheck.app.domain.model.HandStatus
import com.smartcheck.app.domain.model.HealthCertStatus
import com.smartcheck.app.domain.model.Record
import com.smartcheck.app.domain.model.SymptomType
import com.smartcheck.app.domain.repository.IRecordRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RecordDetailViewModel @Inject constructor(
    private val recordRepository: IRecordRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val recordId = savedStateHandle.get<String>("id")?.toLongOrNull()

    private val _record = MutableStateFlow<Record?>(null)
    val record: StateFlow<Record?> = _record.asStateFlow()

    init {
        if (recordId != null) {
            viewModelScope.launch {
                recordRepository.getRecordById(recordId).fold(
                    onSuccess = { _record.value = it },
                    onFailure = { _record.value = null }
                )
            }
        }
    }

    fun updateRecord(
        temperature: Float,
        handStatus: String,
        healthCertStatus: String,
        symptomFlags: String,
        remark: String
    ) {
        val current = _record.value ?: return
        
        val isTempNormal = temperature < 37.3f
        val parsedHandStatus = when (handStatus.trim().uppercase()) {
            "NORMAL", "正常", "合规" -> HandStatus.NORMAL
            "ABNORMAL", "异常", "不合格" -> HandStatus.ABNORMAL
            else -> HandStatus.NOT_CHECKED
        }
        val isHandNormal = parsedHandStatus == HandStatus.NORMAL
        val isPassed = isTempNormal && isHandNormal

        val parsedHealthCertStatus = when (healthCertStatus.trim().uppercase()) {
            "EXPIRED", "过期", "已过期" -> HealthCertStatus.EXPIRED
            "EXPIRING_SOON", "临期", "即将过期", "快过期" -> HealthCertStatus.EXPIRING_SOON
            else -> HealthCertStatus.VALID
        }
        val parsedSymptomFlags = symptomFlags.split(",", "，", "、").mapNotNull {
            when (it.trim().uppercase()) {
                "FEVER", "发热", "发烧" -> SymptomType.FEVER
                "VOMITING", "呕吐" -> SymptomType.VOMITING
                "COUGH", "咳嗽" -> SymptomType.COUGH
                "RUNNY_NOSE", "流涕", "流鼻涕" -> SymptomType.RUNNY_NOSE
                "DIARRHEA", "腹泻" -> SymptomType.DIARRHEA
                "RASH", "皮疹" -> SymptomType.RASH
                "FATIGUE", "乏力", "疲劳" -> SymptomType.FATIGUE
                "HEADACHE", "头痛" -> SymptomType.HEADACHE
                "SORE_THROAT", "咽痛", "喉咙痛" -> SymptomType.SORE_THROAT
                "OTHER", "其他", "其他不适" -> SymptomType.OTHER
                else -> null
            }
        }
        
        val updated = current.copy(
            temperature = temperature,
            isTempNormal = isTempNormal,
            isHandNormal = isHandNormal,
            isPassed = isPassed,
            handStatus = parsedHandStatus,
            healthCertStatus = parsedHealthCertStatus,
            symptomFlags = parsedSymptomFlags,
            remark = remark
        )
        viewModelScope.launch {
            recordRepository.updateRecord(updated)
            _record.value = updated
        }
    }
}
