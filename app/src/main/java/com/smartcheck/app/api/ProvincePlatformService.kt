package com.smartcheck.app.api

import com.smartcheck.app.api.model.ProvinceAddPersonRequest
import com.smartcheck.app.api.model.ProvinceApiResponse
import com.smartcheck.app.api.model.ProvinceInspectionItem
import com.smartcheck.app.api.model.ProvinceLedgerConfigRequest
import com.smartcheck.app.api.model.ProvinceLoginData
import com.smartcheck.app.api.model.ProvinceLoginRequest
import com.smartcheck.app.api.model.ProvinceMorningCheckUpload
import com.smartcheck.app.api.model.ProvincePersonSchedule
import com.smartcheck.app.data.repository.SettingsRepository
import com.smartcheck.app.utils.SM4CryptoUtil
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.serializer
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 省平台 API 客户端
 *
 * 封装所有省平台接口调用，自动处理 SM4 加解密。
 *
 * 规则：
 * - POST/PUT/PATCH：请求体加密，响应解密
 * - GET：请求不加密，响应解密
 */
@Singleton
class ProvincePlatformService @Inject constructor(
    private val httpClient: HttpClient,
    private val settingsRepository: SettingsRepository
) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    /** 省平台服务器基础地址 */
    private val baseUrl: String
        get() = settingsRepository.provincePlatformUrl.value.trimEnd('/')

    /** SM4 密钥（16 字节 Hex → ByteArray） */
    private val smKey: ByteArray
        get() = SM4CryptoUtil.hexStringToBytes(settingsRepository.provincePlatformSmKey.value)

    /** SM4 IV（16 字节 Hex → ByteArray） */
    private val smIv: ByteArray
        get() = SM4CryptoUtil.hexStringToBytes(settingsRepository.provincePlatformSmIv.value)

    /** SmKeys 请求头值 */
    private val smKeysHeader: String
        get() = settingsRepository.provincePlatformSmKeysHeader.value

    // ==================== 业务接口 ====================

    /**
     * 登录（POST，请求体加密）
     *
     * @param request 登录请求参数
     * @return 登录响应（含 token 和 orgId）
     */
    suspend fun login(
        request: ProvinceLoginRequest
    ): Result<ProvinceApiResponse<ProvinceLoginData>> {
        return postEncrypt(
            path = "/api/cgibin/chenjianyi/login",
            body = request,
            token = "1" // 登录接口固定 Token
        )
    }

    /**
     * 获取当天排班人员（GET，请求不加密，响应加密）
     *
     * @param orgId 组织 ID
     * @return 排班人员列表
     */
    suspend fun getTodayPersonSchedule(
        orgId: Int
    ): Result<ProvinceApiResponse<List<ProvincePersonSchedule>>> {
        return getDecrypt(
            path = "/api/cgibin/chenjianyi/get_today_person_schedule",
            query = mapOf("orgId" to orgId.toString())
        )
    }

    /**
     * 新增人员（POST，请求体加密）
     *
     * @param personList 人员列表
     * @return 新增结果
     */
    suspend fun addPerson(
        personList: List<ProvinceAddPersonRequest>
    ): Result<ProvinceApiResponse<JsonElement>> {
        return postEncrypt(
            path = "/api/cgibin/chenjianyi/add_job_person",
            body = personList
        )
    }

    /**
     * 获取晨检管理项（GET，响应加密）
     *
     * @param orgId 组织 ID（可选）
     * @return 晨检管理项列表
     */
    suspend fun getInspectionContent(
        orgId: Int? = null
    ): Result<ProvinceApiResponse<List<ProvinceInspectionItem>>> {
        val query = orgId?.let { mapOf("orgId" to it.toString()) } ?: emptyMap()
        return getDecrypt(
            path = "/api/cgibin/chenjianyi/get_inspection_content",
            query = query
        )
    }

    /**
     * 晨检数据上传（POST，请求体加密）
     *
     * @param dataList 晨检数据列表
     * @return 上传结果
     */
    suspend fun uploadMorningCheckData(
        dataList: List<ProvinceMorningCheckUpload>
    ): Result<ProvinceApiResponse<JsonElement>> {
        return postEncrypt(
            path = "/api/cgibin/chenjianyi/update_person_morning_check_data",
            body = dataList
        )
    }

    /**
     * 获取晨检类型（POST，请求体加密）
     *
     * @param type 类型枚举（如 "Egg", "Disease" 等）
     * @return 配置列表
     */
    suspend fun getLedgerItemConfig(
        type: String
    ): Result<ProvinceApiResponse<JsonElement>> {
        return postEncrypt(
            path = "/api/background_fund_supervision/ledger_food_safety/get_ledger_item_config",
            body = ProvinceLedgerConfigRequest(ledger_data_type = type)
        )
    }

    // ==================== 通用加解密请求方法 ====================

    /**
     * POST 请求：请求体加密，响应解密
     */
    private suspend inline fun <reified T : Any, reified R> postEncrypt(
        path: String,
        body: T,
        token: String? = null
    ): Result<R> = withContext(Dispatchers.IO) {
        try {
            // 1. JSON 序列化
            val jsonBody = json.encodeToString(serializer<T>(), body)
            Timber.d("ProvincePlatform POST $path, plaintext: $jsonBody")

            // 2. SM4 加密
            val encryptedBody = SM4CryptoUtil.encrypt(jsonBody, smKey, smIv)
            Timber.d("ProvincePlatform POST $path, encrypted length: ${encryptedBody.length}")

            // 3. 获取认证 Token
            val authToken = token ?: settingsRepository.provincePlatformToken.value

            // 4. 发送请求（请求体为纯 Hex 字符串）
            val response = httpClient.post("$baseUrl$path") {
                contentType(ContentType.Text.Plain)
                header("Token", authToken)
                header("SmKeys", smKeysHeader)
                if (com.smartcheck.app.BuildConfig.DEBUG) {
                    val apisix = settingsRepository.provincePlatformTestApisix.value
                    if (apisix.isNotBlank()) {
                        header("apisix", apisix)
                    }
                }
                setBody(encryptedBody)
            }

            // 5. 解密响应
            decryptResponse<R>(response, path)
        } catch (e: Exception) {
            Timber.e(e, "ProvincePlatform POST $path failed")
            Result.failure(e)
        }
    }

    /**
     * GET 请求：请求不加密，响应解密
     */
    private suspend inline fun <reified R> getDecrypt(
        path: String,
        query: Map<String, String> = emptyMap()
    ): Result<R> = withContext(Dispatchers.IO) {
        try {
            val authToken = settingsRepository.provincePlatformToken.value

            val response = httpClient.get("$baseUrl$path") {
                contentType(ContentType.Application.Json)
                header("Token", authToken)
                header("SmKeys", smKeysHeader)
                if (com.smartcheck.app.BuildConfig.DEBUG) {
                    val apisix = settingsRepository.provincePlatformTestApisix.value
                    if (apisix.isNotBlank()) {
                        header("apisix", apisix)
                    }
                }
                query.forEach { (k, v) -> parameter(k, v) }
            }

            Timber.d("ProvincePlatform GET $path, status: ${response.status}")
            decryptResponse<R>(response, path)
        } catch (e: Exception) {
            Timber.e(e, "ProvincePlatform GET $path failed")
            Result.failure(e)
        }
    }

    /**
     * 解密响应并反序列化
     */
    private suspend inline fun <reified R> decryptResponse(
        response: HttpResponse,
        path: String
    ): Result<R> {
        if (!response.status.isSuccess()) {
            return Result.failure(Exception("HTTP ${response.status}"))
        }

        // 获取响应体的 16 进制密文字符串
        val encryptedText = response.body<String>()
        Timber.d("ProvincePlatform response from $path, encrypted length: ${encryptedText.length}")

        // SM4 解密
        val decryptedJson = SM4CryptoUtil.decrypt(encryptedText, smKey, smIv)
        Timber.d("ProvincePlatform response from $path, decrypted: $decryptedJson")

        // JSON 反序列化
        val result = json.decodeFromString<R>(decryptedJson)
        return Result.success(result)
    }
}
