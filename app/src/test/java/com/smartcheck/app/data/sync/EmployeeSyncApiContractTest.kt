package com.smartcheck.app.data.sync

import com.smartcheck.app.api.PlatformApiContract
import com.smartcheck.app.api.model.PullChangesResponse
import com.smartcheck.app.api.model.SyncOperation
import com.smartcheck.app.api.model.SyncOperationType
import com.smartcheck.app.api.model.UploadChangesRequest
import com.smartcheck.app.data.repository.SettingsRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
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

class EmployeeSyncApiContractTest {

    @Test
    fun `platform endpoint paths remain stable`() {
        assertEquals("/api/device/refresh", PlatformApiContract.HEARTBEAT_PATH)
        assertEquals(
            "/api/device/morning-check/upload",
            PlatformApiContract.MORNING_CHECK_UPLOAD_PATH
        )
        assertEquals("/api/device/employees/changes", PlatformApiContract.EMPLOYEE_CHANGES_PATH)
        assertEquals(
            "/api/device/employees/E001",
            PlatformApiContract.employeeDetailPath("E001")
        )
        assertEquals("/api/device/employees/snapshot", PlatformApiContract.EMPLOYEE_SNAPSHOT_PATH)
        assertEquals(
            "/api/device/employees/images/face-E001-v4",
            PlatformApiContract.employeeImagePath("face-E001-v4")
        )
    }

    @Test
    fun `upload changes uses implemented path header and JSON contract`() = runTest {
        lateinit var captured: HttpRequestData
        val api = createApi { request ->
            captured = request
            """
                {
                  "code": 200,
                  "message": "success",
                  "data": {
                    "batch_id": "batch-1",
                    "accepted": 0,
                    "duplicates": 1,
                    "conflicts": 0,
                    "rejected": 0,
                    "server_cursor": 12,
                    "results": [{
                      "operation_id": "op-1",
                      "employee_id": "E001",
                      "status": "DUPLICATE",
                      "employee_version": 3,
                      "cursor": 12
                    }]
                  }
                }
            """.trimIndent()
        }

        val result = api.uploadChanges(
            UploadChangesRequest(
                deviceId = "DEVICE001",
                batchId = "batch-1",
                timestamp = 1_785_168_000_000,
                operations = listOf(
                    SyncOperation(
                        operationId = "op-1",
                        type = SyncOperationType.DELETE,
                        employeeId = "E001",
                        expectedVersion = 3
                    )
                )
            )
        )

        assertTrue(result.isSuccess)
        assertEquals(HttpMethod.Post, captured.method)
        assertEquals("/api/device/employees/changes", captured.url.encodedPath)
        assertEquals("contract-api-key", captured.headers["api-key"])
        assertEquals(ContentType.Application.Json, captured.body.contentType)
        assertEquals("batch-1", result.getOrThrow().batchId)
        assertEquals(1, result.getOrThrow().duplicates)
    }

    @Test
    fun `pull changes sends cursor and limit and accepts msg fallback`() = runTest {
        lateinit var captured: HttpRequestData
        val api = createApi { request ->
            captured = request
            """
                {
                  "code": 200,
                  "msg": "success",
                  "data": {
                    "changes": [],
                    "next_cursor": 105,
                    "has_more": false,
                    "server_time": 1785168000000
                  }
                }
            """.trimIndent()
        }

        val result: Result<PullChangesResponse> = api.pullChanges(afterCursor = 100, limit = 20)

        assertTrue(result.isSuccess)
        assertEquals(HttpMethod.Get, captured.method)
        assertEquals("/api/device/employees/changes", captured.url.encodedPath)
        assertEquals("100", captured.url.parameters["after_cursor"])
        assertEquals("20", captured.url.parameters["limit"])
        assertEquals("contract-api-key", captured.headers["api-key"])
        assertEquals(105, result.getOrThrow().nextCursor)
    }

    @Test
    fun `read endpoints match implemented paths`() = runTest {
        val paths = mutableListOf<String>()
        val api = createApi { request ->
            paths += request.url.encodedPath
            when (request.url.encodedPath) {
                "/api/device/employees/E001" -> """
                    {
                      "code": 200,
                      "message": "success",
                      "data": {
                        "employee_id": "E001",
                        "deleted": true,
                        "version": 4,
                        "employee": null
                      }
                    }
                """.trimIndent()
                "/api/device/employees/snapshot" -> """
                    {
                      "code": 200,
                      "message": "success",
                      "data": {
                        "employees": [],
                        "total": 0,
                        "cursor": 4,
                        "server_time": 1785168000000
                      }
                    }
                """.trimIndent()
                else -> error("Unexpected path: ${request.url.encodedPath}")
            }
        }

        assertTrue(api.getEmployee("E001").isSuccess)
        assertTrue(api.getSnapshot().isSuccess)
        assertEquals(
            listOf(
                "/api/device/employees/E001",
                "/api/device/employees/snapshot"
            ),
            paths
        )
    }

    private fun createApi(responseBody: (HttpRequestData) -> String): EmployeeSyncApi {
        val settingsRepository = mockk<SettingsRepository>()
        every { settingsRepository.platformUrl } returns MutableStateFlow("https://platform.example.com/")
        every { settingsRepository.apiKey } returns MutableStateFlow("contract-api-key")

        val engine = MockEngine { request ->
            respond(
                content = responseBody(request),
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                    encodeDefaults = true
                })
            }
            install(HttpTimeout)
        }
        return EmployeeSyncApi(client, settingsRepository)
    }
}
