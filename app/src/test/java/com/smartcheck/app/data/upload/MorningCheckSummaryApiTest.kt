package com.smartcheck.app.data.upload

import com.smartcheck.app.api.PlatformApiContract
import com.smartcheck.app.api.model.MorningCheckSummaryUploadRequest
import com.smartcheck.app.data.repository.SettingsRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MorningCheckSummaryApiTest {

    @Test
    fun `upload uses summary path api key and exact payload`() = runTest {
        lateinit var captured: HttpRequestData
        val api = createApi { request ->
            captured = request
            """
                {
                  "code": 200,
                  "message": "success",
                  "data": {
                    "record_id": "MC-1001-20260909",
                    "check_date": "2026-09-09",
                    "current_version": 1,
                    "result": "CREATED"
                  }
                }
            """.trimIndent()
        }

        val result = api.upload(request())

        assertTrue(result.isSuccess)
        assertEquals(HttpMethod.Post, captured.method)
        assertEquals(
            "/api/device/morning-check/summary/upload",
            PlatformApiContract.MORNING_CHECK_SUMMARY_UPLOAD_PATH
        )
        assertEquals("/api/device/morning-check/summary/upload", captured.url.encodedPath)
        assertEquals("contract-api-key", captured.headers["api-key"])
        assertEquals(ContentType.Application.Json, captured.body.contentType)
        val body = (captured.body as TextContent).text
        assertTrue(body.contains("\"device_id\":\"MORNING-001\""))
        assertTrue(body.contains("\"expected_count\":20"))
        assertTrue(body.contains("\"checked_count\":18"))
        assertTrue(body.contains("\"unqualified_count\":1"))
    }

    @Test
    fun `server error is retryable and forbidden is permanent`() = runTest {
        val retryable = createApi(status = 500) {
            """{"code":50000,"message":"server error","data":null}"""
        }.upload(request()).exceptionOrNull()
        val permanent = createApi(status = 403) {
            """{"code":40301,"message":"not authorized","data":null}"""
        }.upload(request()).exceptionOrNull()

        assertTrue(retryable is RetryableSummaryUploadException)
        assertTrue(permanent is PermanentSummaryUploadException)
        assertEquals(403, (permanent as PermanentSummaryUploadException).httpStatus)
        assertEquals(40301, permanent.code)
    }

    @Test
    fun `retryable business codes are retried even with http 200`() = runTest {
        val serverError = createApi {
            """{"code":50000,"message":"server error","data":null}"""
        }.upload(request()).exceptionOrNull()
        val rateLimited = createApi {
            """{"code":42900,"message":"too many requests","data":null}"""
        }.upload(request()).exceptionOrNull()
        val unauthorized = createApi {
            """{"code":40100,"message":"invalid api key","data":null}"""
        }.upload(request()).exceptionOrNull()

        assertTrue(serverError is RetryableSummaryUploadException)
        assertTrue(rateLimited is RetryableSummaryUploadException)
        assertTrue(unauthorized is PermanentSummaryUploadException)
    }

    @Test
    fun `success response must contain matching identity and valid version`() = runTest {
        val blankRecordId = createApi {
            successResponse(recordId = "", currentVersion = 1, result = "CREATED")
        }.upload(request()).exceptionOrNull()
        val staleWithoutNewerVersion = createApi {
            successResponse(recordId = "MC-1", currentVersion = 1, result = "STALE")
        }.upload(request()).exceptionOrNull()

        assertTrue(blankRecordId is PermanentSummaryUploadException)
        assertTrue(staleWithoutNewerVersion is PermanentSummaryUploadException)
    }

    @Test
    fun `future business date is rejected before upload`() = runTest {
        val error = createApi {
            error("HTTP request must not be sent for a future date")
        }.upload(
            request().copy(checkDate = MorningCheckSummaryCalculator.currentDate().plusDays(1).toString())
        ).exceptionOrNull()

        assertTrue(error is PermanentSummaryUploadException)
        assertTrue(error?.message?.contains("不得晚于") == true)
    }

    private fun createApi(
        status: Int = 200,
        responseBody: (HttpRequestData) -> String
    ): MorningCheckSummaryApi {
        val settingsRepository = mockk<SettingsRepository>()
        every { settingsRepository.platformUrl } returns MutableStateFlow("https://platform.example.com/")
        every { settingsRepository.apiKey } returns MutableStateFlow("contract-api-key")
        val client = HttpClient(MockEngine { request ->
            respond(
                content = responseBody(request),
                status = io.ktor.http.HttpStatusCode.fromValue(status),
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    encodeDefaults = true
                })
            }
        }
        return MorningCheckSummaryApi(client, settingsRepository)
    }

    private fun request() = MorningCheckSummaryUploadRequest(
        deviceId = "MORNING-001",
        checkDate = "2026-09-09",
        version = 1,
        expectedCount = 20,
        checkedCount = 18,
        unqualifiedCount = 1
    )

    private fun successResponse(
        recordId: String,
        currentVersion: Int,
        result: String
    ): String = """
        {
          "code": 200,
          "message": "success",
          "data": {
            "record_id": "$recordId",
            "check_date": "2026-09-09",
            "current_version": $currentVersion,
            "result": "$result"
          }
        }
    """.trimIndent()
}
