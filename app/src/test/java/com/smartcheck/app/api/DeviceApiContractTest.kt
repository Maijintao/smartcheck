package com.smartcheck.app.api

import android.content.Context
import com.smartcheck.app.data.db.ApiAccessLogDao
import com.smartcheck.app.data.db.ApiTokenDao
import com.smartcheck.app.data.db.SystemUserDao
import com.smartcheck.app.data.repository.AdminAuthRepository
import com.smartcheck.app.data.repository.RecordRepository
import com.smartcheck.app.data.repository.UserRepository
import com.smartcheck.app.ml.FaceEngine
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.UserIdPrincipal
import io.ktor.server.auth.bearer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceApiContractTest {

    @Test
    fun `device endpoint paths remain stable`() {
        assertEquals(
            listOf(
                "/health",
                "/api/auth/login",
                "/api/users/sync",
                "/api/records",
                "/api/records/sync",
                "/api/records/statistics",
                "/api/records/{id}",
                "/api/records/export",
                "/api/employees",
                "/api/employees/sync",
                "/api/employees/import",
                "/api/employees/upload-photo",
                "/api/employees/upload-cert-photo",
                "/api/employees/clear-all",
                "/api/employees/{employeeId}",
                "/api/images/{filename}",
                "/api/employee-images/{filename}",
                "/api/downloads/{filename}",
            ),
            listOf(
                DeviceApiContract.HEALTH,
                DeviceApiContract.LOGIN,
                DeviceApiContract.USER_SYNC,
                DeviceApiContract.RECORDS,
                DeviceApiContract.RECORD_SYNC,
                DeviceApiContract.RECORD_STATISTICS,
                DeviceApiContract.RECORD_DETAIL,
                DeviceApiContract.RECORD_EXPORT,
                DeviceApiContract.EMPLOYEES,
                DeviceApiContract.EMPLOYEE_SYNC,
                DeviceApiContract.EMPLOYEE_IMPORT,
                DeviceApiContract.EMPLOYEE_UPLOAD_PHOTO,
                DeviceApiContract.EMPLOYEE_UPLOAD_CERT_PHOTO,
                DeviceApiContract.EMPLOYEE_CLEAR_ALL,
                DeviceApiContract.EMPLOYEE_DELETE,
                DeviceApiContract.RECORD_IMAGE,
                DeviceApiContract.EMPLOYEE_IMAGE,
                DeviceApiContract.DOWNLOAD,
            )
        )
        assertEquals("/api/records/1001", DeviceApiContract.recordDetailPath(1001))
        assertEquals("/api/employees/E001", DeviceApiContract.employeeDeletePath("E001"))
    }

    @Test
    fun `health endpoint is public`() = testApplication {
        application { installApi(createApiService()) }

        val response = client.get(DeviceApiContract.HEALTH)

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("\"status\": \"ok\""))
    }

    @Test
    fun `statistics route is protected and is not captured by record id route`() = testApplication {
        val service = createApiService()
        application { installApi(service) }

        assertEquals(
            HttpStatusCode.Unauthorized,
            client.get("${DeviceApiContract.RECORD_STATISTICS}?startDate=2026-08-01&endDate=2026-08-27").status
        )

        val response = client.get(
            "${DeviceApiContract.RECORD_STATISTICS}?startDate=2026-08-01&endDate=2026-08-27"
        ) {
            header(HttpHeaders.Authorization, "Bearer contract-token")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("\"totalCheck\": 0"))
    }

    @Test
    fun `record list rejects invalid pagination`() = testApplication {
        application { installApi(createApiService()) }

        val response = client.get(
            "${DeviceApiContract.RECORDS}?startDate=2026-08-01&endDate=2026-08-27&page=0&pageSize=0"
        ) {
            header(HttpHeaders.Authorization, "Bearer contract-token")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.bodyAsText().contains("\"code\": 1004"))
    }

    @Test
    fun `record list rejects invalid calendar date`() = testApplication {
        application { installApi(createApiService()) }

        val response = client.get(
            "${DeviceApiContract.RECORDS}?startDate=2026-02-30&endDate=2026-03-01"
        ) {
            header(HttpHeaders.Authorization, "Bearer contract-token")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.bodyAsText().contains("\"code\": 1004"))
    }

    private fun Application.installApi(apiService: ApiService) {
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                ignoreUnknownKeys = true
                encodeDefaults = true
            })
        }
        install(Authentication) {
            bearer("auth-jwt") {
                authenticate { credential ->
                    credential.takeIf { it.token == "contract-token" }
                        ?.let { UserIdPrincipal("contract-user") }
                }
            }
        }
        routing {
            apiService.configureRouting(this)
        }
    }

    private fun createApiService(): ApiService {
        val recordRepository = mockk<RecordRepository>(relaxed = true)
        coEvery { recordRepository.getRecordsByTimeRangeSync(any(), any()) } returns emptyList()

        return ApiService(
            context = mockk<Context>(relaxed = true),
            adminAuthRepository = mockk<AdminAuthRepository>(relaxed = true),
            recordRepository = recordRepository,
            userRepository = mockk<UserRepository>(relaxed = true),
            faceEngine = mockk<FaceEngine>(relaxed = true),
            apiTokenDao = mockk<ApiTokenDao>(relaxed = true),
            apiAccessLogDao = mockk<ApiAccessLogDao>(relaxed = true),
            systemUserDao = mockk<SystemUserDao>(relaxed = true),
        )
    }
}
