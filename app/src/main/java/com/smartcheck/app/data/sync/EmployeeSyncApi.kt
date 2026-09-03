package com.smartcheck.app.data.sync

import com.smartcheck.app.api.model.*
import com.smartcheck.app.data.repository.SettingsRepository
import com.smartcheck.app.data.upload.PlatformUrlResolver
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.timeout
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

// ==================== 各接口的具体响应包装 ====================

@kotlinx.serialization.Serializable
data class UploadChangesWrapper(
    val code: Int = 0,
    val message: String = "",
    val msg: String = "",
    val data: UploadChangesResponse? = null
) {
    fun errorMessage(): String = message.ifBlank { msg }
}

@kotlinx.serialization.Serializable
data class PullChangesWrapper(
    val code: Int = 0,
    val message: String = "",
    val msg: String = "",
    val data: PullChangesResponse? = null
) {
    fun errorMessage(): String = message.ifBlank { msg }
}

@kotlinx.serialization.Serializable
data class EmployeeDetailWrapper(
    val code: Int = 0,
    val message: String = "",
    val msg: String = "",
    val data: EmployeeDetailResponse? = null
) {
    fun errorMessage(): String = message.ifBlank { msg }
}

@kotlinx.serialization.Serializable
data class SnapshotWrapper(
    val code: Int = 0,
    val message: String = "",
    val msg: String = "",
    val data: SnapshotResponse? = null
) {
    fun errorMessage(): String = message.ifBlank { msg }
}

@Serializable
private data class SyncErrorWrapper(
    val code: Int = 0,
    val message: String = "",
    val msg: String = "",
) {
    fun errorMessage(): String = message.ifBlank { msg }
}

// ==================== 同步 API Client ====================

/**
 * 员工同步 API 客户端
 * 封装协议文档定义的 5 个接口
 */
@Singleton
class EmployeeSyncApi @Inject constructor(
    private val client: HttpClient,
    private val settingsRepository: SettingsRepository
) {
    companion object {
        private const val TAG = "EmployeeSyncApi"
        private const val TIMEOUT_NO_IMAGE = 15_000L   // 不含图片的接口
        private const val TIMEOUT_WITH_IMAGE = 60_000L // 含图片/快照的接口
    }

    private val apiKey: String
        get() = settingsRepository.apiKey.value

    private fun isConfigured(): Boolean =
        settingsRepository.platformUrl.value.isNotBlank() && apiKey.isNotBlank()

    /**
     * §7 POST /employees/changes — 上传员工变更
     */
    suspend fun uploadChanges(request: UploadChangesRequest): Result<UploadChangesResponse> {
        if (!isConfigured()) return Result.failure(Exception("平台地址或API Key未配置"))
        return withContext(Dispatchers.IO) {
            try {
                val response = client.post(
                    PlatformUrlResolver.employeeChangesUrl(settingsRepository.platformUrl.value)
                ) {
                    header("api-key", apiKey)
                    contentType(ContentType.Application.Json)
                    timeout { requestTimeoutMillis = TIMEOUT_WITH_IMAGE }
                    setBody(request)
                }
                checkHttpError(response, "uploadChanges")?.let { return@withContext it }
                val wrapper: UploadChangesWrapper = response.body()
                checkBizCode(wrapper.code, wrapper.errorMessage(), "uploadChanges")
                    ?.let { return@withContext it }
                val data = wrapper.data
                    ?: return@withContext Result.failure(Exception("响应 data 为空"))
                Result.success(data)
            } catch (e: Exception) {
                Timber.e(e, "$TAG: uploadChanges failed")
                Result.failure(e)
            }
        }
    }

    /**
     * §8 GET /employees/changes — 增量拉取员工变化
     */
    suspend fun pullChanges(afterCursor: Long, limit: Int = 100): Result<PullChangesResponse> {
        if (!isConfigured()) return Result.failure(Exception("平台地址或API Key未配置"))
        return withContext(Dispatchers.IO) {
            try {
                val response = client.get(
                    PlatformUrlResolver.employeeChangesUrl(settingsRepository.platformUrl.value)
                ) {
                    header("api-key", apiKey)
                    parameter("after_cursor", afterCursor)
                    parameter("limit", limit)
                    timeout { requestTimeoutMillis = TIMEOUT_NO_IMAGE }
                }
                checkHttpError(response, "pullChanges")?.let { return@withContext it }
                val wrapper: PullChangesWrapper = response.body()
                checkBizCode(wrapper.code, wrapper.errorMessage(), "pullChanges")
                    ?.let { return@withContext it }
                val data = wrapper.data
                    ?: return@withContext Result.failure(Exception("响应 data 为空"))
                Result.success(data)
            } catch (e: Exception) {
                Timber.e(e, "$TAG: pullChanges failed")
                Result.failure(e)
            }
        }
    }

    /**
     * §8.4 GET /employees/{employeeId} — 查询单个员工最新状态
     */
    suspend fun getEmployee(employeeId: String): Result<EmployeeDetailResponse> {
        if (!isConfigured()) return Result.failure(Exception("平台地址或API Key未配置"))
        return withContext(Dispatchers.IO) {
            try {
                val response = client.get(
                    PlatformUrlResolver.employeeDetailUrl(
                        settingsRepository.platformUrl.value,
                        employeeId,
                    )
                ) {
                    header("api-key", apiKey)
                    timeout { requestTimeoutMillis = TIMEOUT_NO_IMAGE }
                }
                checkHttpError(response, "getEmployee")?.let { return@withContext it }
                val wrapper: EmployeeDetailWrapper = response.body()
                checkBizCode(wrapper.code, wrapper.errorMessage(), "getEmployee")
                    ?.let { return@withContext it }
                val data = wrapper.data
                    ?: return@withContext Result.failure(Exception("响应 data 为空"))
                Result.success(data)
            } catch (e: Exception) {
                Timber.e(e, "$TAG: getEmployee failed")
                Result.failure(e)
            }
        }
    }

    /**
     * §9 GET /employees/snapshot — 拉取完整员工快照
     */
    suspend fun getSnapshot(): Result<SnapshotResponse> {
        if (!isConfigured()) return Result.failure(Exception("平台地址或API Key未配置"))
        return withContext(Dispatchers.IO) {
            try {
                val response = client.get(
                    PlatformUrlResolver.employeeSnapshotUrl(settingsRepository.platformUrl.value)
                ) {
                    header("api-key", apiKey)
                    timeout { requestTimeoutMillis = TIMEOUT_WITH_IMAGE }
                }
                checkHttpError(response, "getSnapshot")?.let { return@withContext it }
                val wrapper: SnapshotWrapper = response.body()
                checkBizCode(wrapper.code, wrapper.errorMessage(), "getSnapshot")
                    ?.let { return@withContext it }
                val data = wrapper.data
                    ?: return@withContext Result.failure(Exception("响应 data 为空"))
                Result.success(data)
            } catch (e: Exception) {
                Timber.e(e, "$TAG: getSnapshot failed")
                Result.failure(e)
            }
        }
    }

    /**
     * §10 GET /employees/images/{fileId} — 下载员工图片
     * @return 图片原始字节数组
     */
    suspend fun downloadImage(fileId: String): Result<ByteArray> {
        if (!isConfigured()) return Result.failure(Exception("平台地址或API Key未配置"))
        return withContext(Dispatchers.IO) {
            try {
                val response = client.get(
                    PlatformUrlResolver.employeeImageUrl(
                        settingsRepository.platformUrl.value,
                        fileId,
                    )
                ) {
                    header("api-key", apiKey)
                    timeout { requestTimeoutMillis = TIMEOUT_WITH_IMAGE }
                }
                checkHttpError(response, "downloadImage")?.let { return@withContext it }

                // 图片接口返回二进制数据，检查 Content-Type 判断是否错误响应
                val contentType = response.contentType()?.toString() ?: ""
                if (contentType.contains("json")) {
                    // 服务端返回了 JSON 错误而非图片
                    val errorText = response.bodyAsText()
                    Timber.w("$TAG: downloadImage 返回 JSON 错误: $errorText")
                    return@withContext Result.failure(Exception("图片下载失败: 服务端返回错误"))
                }

                Result.success(response.readBytes())
            } catch (e: Exception) {
                Timber.e(e, "$TAG: downloadImage failed")
                Result.failure(e)
            }
        }
    }

    // ==================== 内部工具 ====================

    private suspend fun checkHttpError(
        response: HttpResponse,
        methodName: String
    ): Result<Nothing>? {
        if (response.status.isSuccess()) return null
        val httpStatus = response.status.value
        val error = parseSyncApiException(httpStatus, response.bodyAsText())
        Timber.w(
            "$TAG: $methodName HTTP error $httpStatus, businessCode=${error.errorCode}: ${error.message}"
        )
        return Result.failure(error)
    }

    private fun checkBizCode(
        code: Int,
        message: String,
        methodName: String
    ): Result<Nothing>? {
        if (code == 200) return null
        Timber.w("$TAG: $methodName 业务错误 code=$code: $message")
        return Result.failure(SyncApiException(code, message, httpStatus = 200))
    }
}

internal fun parseSyncApiException(httpStatus: Int, responseBody: String): SyncApiException {
    val wrapper = runCatching {
        Json { ignoreUnknownKeys = true }.decodeFromString(SyncErrorWrapper.serializer(), responseBody)
    }.getOrNull()
    val businessCode = wrapper?.code?.takeIf { it != 0 } ?: httpStatus
    val message = wrapper?.errorMessage()?.takeIf { it.isNotBlank() } ?: "HTTP $httpStatus"
    return SyncApiException(businessCode, message, httpStatus)
}

/**
 * 同步 API 异常
 */
class SyncApiException(
    val errorCode: Int,
    override val message: String,
    val httpStatus: Int? = null,
) :
    Exception("SyncAPI [$errorCode]: $message")
