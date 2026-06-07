package com.smartcheck.app.domain.model

enum class ErrorCode(val code: String, val message: String) {
    ER001("ER001", "参数验证失败"),
    ER002("ER002", "数据不存在"),
    ER003("ER003", "数据重复"),
    
    FA101("FA101", "未检测到人脸"),
    FA102("FA102", "人脸特征提取失败"),
    FA103("FA103", "人脸比对失败"),
    FA104("FA104", "人脸识别超时"),
    
    HD201("HD201", "未检测到手部"),
    HD202("HD202", "手部检测失败"),
    HD203("HD203", "手部图像保存失败"),
    
    TM301("TM301", "测温模块未就绪"),
    TM302("TM302", "测温数据异常"),
    TM303("TM303", "测温超时"),
    
    DB401("DB401", "数据库操作失败"),
    DB402("DB402", "数据保存失败"),
    
    ST501("ST501", "存储空间不足"),
    ST502("ST502", "文件访问失败"),
    
    HW601("HW601", "硬件初始化失败"),
    HW602("HW602", "串口通信失败"),
}

sealed class AppError : Throwable() {
    val errorCode: String? get() = null
    val errorMessage: String? get() = null

    object NotFound : AppError() {
        override val message: String? = "数据不存在"
    }
    data class ValidationError(val field: String, val errorMsg: String) : AppError() {
        override val message: String? = errorMsg
    }
    data class DuplicateError(val field: String, val value: String) : AppError() {
        override val message: String? = "$field 已存在: $value"
    }
    data class Unauthorized(val reason: String = "未授权") : AppError() {
        override val message: String? = reason
    }
    object Forbidden : AppError() {
        override val message: String? = "禁止访问"
    }

    object HardwareNotReady : AppError() {
        override val message: String? = "硬件未就绪"
    }
    data class HardwareError(val device: String, val detail: String) : AppError() {
        override val message: String? = "$device 错误: $detail"
    }

    object ModelNotLoaded : AppError() {
        override val message: String? = "模型未加载"
    }
    data class DetectionError(val type: String, val errorMsg: String) : AppError() {
        override val message: String? = errorMsg
    }
    object NoTargetDetected : AppError() {
        override val message: String? = "未检测到目标"
    }
    data class StorageError(val errorMsg: String) : AppError() {
        override val message: String? = errorMsg
    }

    data class UnknownError(val errorMsg: String) : AppError() {
        override val message: String? = errorMsg
    }

    override fun toString(): String = message ?: javaClass.simpleName
}
