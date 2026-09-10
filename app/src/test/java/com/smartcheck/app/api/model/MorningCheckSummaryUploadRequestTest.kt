package com.smartcheck.app.api.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MorningCheckSummaryUploadRequestTest {

    @Test
    fun `request serializes exact summary contract`() {
        val request = MorningCheckSummaryUploadRequest(
            deviceId = "MORNING-001",
            checkDate = "2026-09-09",
            version = 12,
            expectedCount = 20,
            checkedCount = 18,
            unqualifiedCount = 1
        )

        val json = Json.parseToJsonElement(Json.encodeToString(request)).jsonObject

        assertEquals(
            setOf(
                "device_id",
                "check_date",
                "version",
                "expected_count",
                "checked_count",
                "unqualified_count"
            ),
            json.keys
        )
        assertEquals("\"MORNING-001\"", json.getValue("device_id").toString())
        assertEquals("\"2026-09-09\"", json.getValue("check_date").toString())
        assertEquals("12", json.getValue("version").toString())
        assertEquals("20", json.getValue("expected_count").toString())
        assertEquals("18", json.getValue("checked_count").toString())
        assertEquals("1", json.getValue("unqualified_count").toString())
    }

    @Test
    fun `response deserializes summary result`() {
        val response = Json.decodeFromString<MorningCheckSummaryUploadResponse>(
            """
                {
                  "code": 200,
                  "message": "success",
                  "data": {
                    "record_id": "MC-1001-20260909",
                    "check_date": "2026-09-09",
                    "current_version": 12,
                    "result": "UPDATED"
                  }
                }
            """.trimIndent()
        )

        assertTrue(response.isSuccess)
        assertEquals("MC-1001-20260909", response.data?.recordId)
        assertEquals("2026-09-09", response.data?.checkDate)
        assertEquals(12, response.data?.currentVersion)
        assertEquals("UPDATED", response.data?.result)
    }
}
