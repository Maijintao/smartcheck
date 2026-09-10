package com.smartcheck.app.data.upload

import com.smartcheck.app.api.model.MorningCheckSummaryUploadRequest
import com.smartcheck.app.api.model.MorningCheckSummaryUploadResponse
import com.smartcheck.app.data.repository.SettingsRepository
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.net.URI
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MorningCheckSummaryApi @Inject constructor(
    private val httpClient: HttpClient,
    private val settingsRepository: SettingsRepository
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    suspend fun upload(
        request: MorningCheckSummaryUploadRequest
    ): Result<MorningCheckSummaryUploadResponse> {
        return try {
            validateRequest(request)
            val configuredPlatformUrl = settingsRepository.platformUrl.value
            val apiKey = settingsRepository.apiKey.value
            if (configuredPlatformUrl.isBlank() || apiKey.isBlank()) {
                return Result.failure(PermanentSummaryUploadException("平台地址或API Key未配置"))
            }
            if (!runCatching { URI(configuredPlatformUrl.trim()).scheme.equals("https", ignoreCase = true) }
                    .getOrDefault(false)
            ) {
                return Result.failure(PermanentSummaryUploadException("晨检人数汇总上报地址必须使用HTTPS"))
            }
            val url = runCatching {
                PlatformUrlResolver.morningCheckSummaryUploadUrl(configuredPlatformUrl)
            }.getOrElse { error ->
                return Result.failure(
                    PermanentSummaryUploadException(
                        error.message ?: "平台地址格式无效",
                        cause = error
                    )
                )
            }

            val response = httpClient.post(url) {
                contentType(ContentType.Application.Json)
                header("api-key", apiKey)
                setBody(json.encodeToString(request))
            }
            val responseText = response.bodyAsText()
            val responseBody = runCatching {
                json.decodeFromString<MorningCheckSummaryUploadResponse>(responseText)
            }.getOrNull()

            if (response.status.value == 200 && responseBody?.isSuccess == true) {
                val data = responseBody.data
                    ?: return Result.failure(PermanentSummaryUploadException("平台成功响应缺少data"))
                if (data.recordId.isBlank()) {
                    return Result.failure(PermanentSummaryUploadException("平台成功响应缺少record_id"))
                }
                if (data.checkDate != request.checkDate) {
                    return Result.failure(PermanentSummaryUploadException("平台成功响应中的check_date不匹配"))
                }
                if (data.result !in SUCCESS_RESULTS) {
                    return Result.failure(PermanentSummaryUploadException("平台返回未知result: ${data.result}"))
                }
                val versionValid = if (data.result == "STALE") {
                    data.currentVersion > request.version
                } else {
                    data.currentVersion >= request.version
                }
                if (!versionValid) {
                    return Result.failure(PermanentSummaryUploadException("平台成功响应中的current_version无效"))
                }
                Result.success(responseBody)
            } else {
                val message = responseBody?.message
                    ?.takeIf { it.isNotBlank() }
                    ?: responseText.take(2_000).ifBlank { "HTTP ${response.status.value}" }
                val code = responseBody?.code
                if (response.status.value == 408 ||
                    response.status.value == 429 ||
                    response.status.value >= 500 ||
                    code == 42900 ||
                    (code != null && code in 50000..59999)
                ) {
                    val retryAfterSeconds = response.headers[HttpHeaders.RetryAfter]?.toLongOrNull()
                    Result.failure(
                        RetryableSummaryUploadException(
                            message = message,
                            retryAfterSeconds = retryAfterSeconds
                        )
                    )
                } else {
                    Result.failure(
                        PermanentSummaryUploadException(
                            message = message,
                            httpStatus = response.status.value,
                            code = code
                        )
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: SerializationException) {
            Result.failure(PermanentSummaryUploadException("晨检人数汇总请求序列化失败", cause = e))
        } catch (e: IllegalArgumentException) {
            Result.failure(PermanentSummaryUploadException(e.message ?: "晨检人数汇总参数不合法", cause = e))
        } catch (e: Exception) {
            Timber.w(e, "Morning check summary upload network error")
            Result.failure(RetryableSummaryUploadException("晨检人数汇总上报网络异常", cause = e))
        }
    }

    private fun validateRequest(request: MorningCheckSummaryUploadRequest) {
        require(request.deviceId.isNotBlank() && request.deviceId.length <= 64) {
            "device_id不能为空且最长64字符"
        }
        val checkDate = runCatching { LocalDate.parse(request.checkDate) }
            .getOrElse { throw IllegalArgumentException("check_date必须为YYYY-MM-DD") }
        require(checkDate <= MorningCheckSummaryCalculator.currentDate()) {
            "check_date不得晚于北京时间当前日期"
        }
        require(request.version >= 1) { "version必须从1开始" }
        require(request.expectedCount >= 0) { "expected_count不能为负数" }
        require(request.checkedCount in 0..request.expectedCount) {
            "checked_count必须介于0和expected_count之间"
        }
        require(request.unqualifiedCount in 0..request.checkedCount) {
            "unqualified_count必须介于0和checked_count之间"
        }
    }

    companion object {
        private val SUCCESS_RESULTS = setOf("CREATED", "UPDATED", "DUPLICATE", "STALE")
    }
}

sealed class SummaryUploadException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

class RetryableSummaryUploadException(
    message: String,
    val retryAfterSeconds: Long? = null,
    cause: Throwable? = null
) : SummaryUploadException(message, cause)

class PermanentSummaryUploadException(
    message: String,
    val httpStatus: Int? = null,
    val code: Int? = null,
    cause: Throwable? = null
) : SummaryUploadException(message, cause)
