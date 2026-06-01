package com.smartcheck.app.api.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * 省平台统一响应包装
 */
@Serializable
data class ProvinceApiResponse<T>(
    val statuCode: Int,
    val info: String,
    val data: T? = null,
    val filter: JsonElement? = null
)

/**
 * 登录请求
 */
@Serializable
data class ProvinceLoginRequest(
    val userId: String,
    val password: String,
    val instrumentNumber: String
)

/**
 * 登录响应数据
 */
@Serializable
data class ProvinceLoginData(
    val token: String,
    val orgId: Int
)

/**
 * 排班人员信息
 */
@Serializable
data class ProvincePersonSchedule(
    val flowId: String,
    val name: String,
    val idCard: String,
    val portraitPhoto: String? = null,
    val position: String? = null,
    val tel: String? = null,
    val healthUrl: String? = null,
    val healthEndTime: String? = null,
    val inspection_type: Int = 0,
    val orgId: Int = 0
)

/**
 * 新增人员请求
 */
@Serializable
data class ProvinceAddPersonRequest(
    val org_id: Int,
    val name: String,
    val phone: String,
    val health_image: String,
    val job_title: String,
    val effective_time: String,
    val id_number: String,
    val face_image: String
)

/**
 * 晨检管理项
 */
@Serializable
data class ProvinceInspectionItem(
    val itemId: Int,
    val itemName: String,
    val alias: String
)

/**
 * 晨检数据上传
 */
@Serializable
data class ProvinceMorningCheckUpload(
    val orgId: Int,
    val personName: String,
    val diningRoomId: String? = null,
    val idCard: String? = null,
    val state: String? = null,
    val temperature: String? = null,
    val health: String,
    val checkDate: String,
    val picture_img: String? = null,
    val picture_back_img: String? = null,
    val scene_img: String? = null,
    val health_certificate_state: Int,
    val risk_type_one: String? = null,
    val picture_recognition_content: String? = null,
    val risk_type_one_details: String? = null
)

/**
 * 获取晨检类型请求
 */
@Serializable
data class ProvinceLedgerConfigRequest(
    val ledger_data_type: String
)
