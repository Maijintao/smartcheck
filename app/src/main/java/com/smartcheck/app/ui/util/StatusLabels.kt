package com.smartcheck.app.ui.util

import com.smartcheck.app.domain.model.HandStatus
import com.smartcheck.app.domain.model.HealthCertStatus
import com.smartcheck.app.domain.model.SymptomType

fun HandStatus.toChineseLabel(): String = when (this) {
    HandStatus.NORMAL -> "正常"
    HandStatus.ABNORMAL -> "异常"
    HandStatus.NOT_CHECKED -> "未检测"
}

fun HealthCertStatus.toChineseLabel(): String = when (this) {
    HealthCertStatus.VALID -> "有效"
    HealthCertStatus.EXPIRING_SOON -> "即将过期"
    HealthCertStatus.EXPIRED -> "已过期"
}

fun SymptomType.toChineseLabel(): String = when (this) {
    SymptomType.COUGH -> "咳嗽"
    SymptomType.FEVER -> "发热"
    SymptomType.VOMITING -> "呕吐"
    SymptomType.RUNNY_NOSE -> "流涕"
    SymptomType.RASH -> "皮疹"
    SymptomType.HEADACHE -> "头痛"
    SymptomType.FATIGUE -> "乏力"
    SymptomType.SORE_THROAT -> "咽痛"
    SymptomType.DIARRHEA -> "腹泻"
    SymptomType.OTHER -> "其他不适"
}

fun String.toHandStatusChineseLabel(): String = when (trim().uppercase()) {
    "NORMAL", "正常", "合规" -> "正常"
    "ABNORMAL", "异常", "不合格" -> "异常"
    "NOT_CHECKED", "未检测" -> "未检测"
    else -> ifBlank { "未检测" }
}

fun String.toHealthCertChineseLabel(): String = when (trim().uppercase()) {
    "VALID", "有效" -> "有效"
    "EXPIRING_SOON", "临期", "即将过期", "快过期" -> "即将过期"
    "EXPIRED", "过期", "已过期" -> "已过期"
    else -> ifBlank { "未知" }
}

fun String.toSymptomChineseLabels(): String {
    if (isBlank()) return "无"
    return split(",")
        .map { value ->
            runCatching { SymptomType.valueOf(value.trim().uppercase()).toChineseLabel() }
                .getOrElse { value.trim() }
        }
        .filter { it.isNotBlank() }
        .joinToString("、")
        .ifBlank { "无" }
}
