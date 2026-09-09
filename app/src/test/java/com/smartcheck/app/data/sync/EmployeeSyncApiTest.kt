package com.smartcheck.app.data.sync

import com.smartcheck.app.api.model.ImageUploadPayload
import com.smartcheck.app.api.model.SyncImageAction
import com.smartcheck.app.api.model.SyncOperation
import com.smartcheck.app.api.model.SyncOperationType
import com.smartcheck.app.api.model.UploadChangesRequest
import com.smartcheck.app.api.model.UploadEmployee
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmployeeSyncApiTest {

    @Test
    fun `非成功HTTP响应保留平台业务错误码`() {
        val error = parseSyncApiException(
            httpStatus = 410,
            responseBody = """{"code":41010,"message":"cursor expired","data":null}""",
        )

        assertEquals(410, error.httpStatus)
        assertEquals(41010, error.errorCode)
        assertEquals("cursor expired", error.message)
    }

    @Test
    fun `无效错误响应回退到HTTP状态码`() {
        val error = parseSyncApiException(httpStatus = 503, responseBody = "not-json")

        assertEquals(503, error.httpStatus)
        assertEquals(503, error.errorCode)
        assertEquals("HTTP 503", error.message)
    }

    @Test
    fun `只重试限流服务端和网络错误`() {
        assertEquals(true, isRetryableSyncFailure(null))
        assertEquals(true, isRetryableSyncFailure(SyncApiException(42900, "too many", 429)))
        assertEquals(true, isRetryableSyncFailure(SyncApiException(50001, "server error", 500)))
        assertEquals(false, isRetryableSyncFailure(SyncApiException(40011, "invalid employee", 400)))
        assertEquals(false, isRetryableSyncFailure(SyncApiException(40100, "invalid api key", 401)))
    }

    @Test
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    fun `上传JSON保留协议必填空值且DELETE不传employee`() {
        val request = UploadChangesRequest(
            deviceId = "DEVICE-001",
            batchId = "batch-001",
            timestamp = 1_000,
            operations = listOf(
                SyncOperation(
                    operationId = "operation-upsert",
                    type = SyncOperationType.UPSERT,
                    employeeId = "EMP-001",
                    expectedVersion = null,
                    employee = UploadEmployee(
                        name = "张三",
                        faceImage = ImageUploadPayload(SyncImageAction.KEEP),
                    ),
                ),
                SyncOperation(
                    operationId = "operation-delete",
                    type = SyncOperationType.DELETE,
                    employeeId = "EMP-002",
                    expectedVersion = 8,
                ),
            ),
        )
        val json = Json {
            encodeDefaults = true
            explicitNulls = true
        }

        val operations = json.parseToJsonElement(json.encodeToString(request))
            .jsonObject.getValue("operations")
            .let { it as kotlinx.serialization.json.JsonArray }
        val upsert = operations[0].jsonObject
        val employee = upsert.getValue("employee").jsonObject
        val delete = operations[1].jsonObject

        assertTrue("expected_version" in upsert)
        assertTrue("id_card_number" in employee)
        assertTrue("phone" in employee)
        assertTrue("position" in employee)
        assertTrue("department" in employee)
        assertTrue("health_certificate" in employee)
        assertFalse("employee" in delete)
    }
}
