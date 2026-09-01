package com.smartcheck.app.api.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MorningCheckUploadRequestTest {

    @Test
    fun `request serializes all morning check fields`() {
        val request = MorningCheckUploadRequest(
            deviceId = "DEVICE001",
            timestamp = 1_716_259_200_000,
            employees = listOf(
                MorningCheckEmployee(
                    employeeId = "E001",
                    name = "Test User",
                    recordId = 1001,
                    recordUuid = "123e4567-e89b-42d3-a456-426614174000",
                    userId = 1,
                    temperature = 36.5f,
                    isTempNormal = true,
                    isHandNormal = false,
                    isPassed = false,
                    handStatus = "ABNORMAL",
                    hasForeignObject = true,
                    handAbnormalTypes = listOf("ring"),
                    healthCertStatus = "VALID",
                    symptomFlags = listOf("COUGH"),
                    remark = "Hand check failed",
                    photo = "face-base64",
                    handPalmPhoto = "palm-base64",
                    handBackPhoto = "back-base64"
                )
            )
        )

        val json = Json.parseToJsonElement(Json.encodeToString(request)).jsonObject
        val employee = json.getValue("employees").jsonArray.single().jsonObject

        assertEquals(setOf("device_id", "timestamp", "employees"), json.keys)
        assertEquals(
            setOf(
                "employee_id",
                "name",
                "record_id",
                "record_uuid",
                "user_id",
                "temperature",
                "is_temp_normal",
                "is_hand_normal",
                "is_passed",
                "hand_status",
                "has_foreign_object",
                "hand_abnormal_types",
                "health_cert_status",
                "symptom_flags",
                "remark",
                "photo",
                "hand_palm_photo",
                "hand_back_photo",
            ),
            employee.keys,
        )
        assertEquals("\"E001\"", employee.getValue("employee_id").toString())
        assertEquals("1001", employee.getValue("record_id").toString())
        assertEquals(
            "\"123e4567-e89b-42d3-a456-426614174000\"",
            employee.getValue("record_uuid").toString()
        )
        assertEquals("1", employee.getValue("user_id").toString())
        assertTrue(employee.getValue("is_temp_normal").toString().toBoolean())
        assertFalse(employee.getValue("is_hand_normal").toString().toBoolean())
        assertFalse(employee.getValue("is_passed").toString().toBoolean())
        assertEquals("\"ABNORMAL\"", employee.getValue("hand_status").toString())
        assertTrue(employee.getValue("has_foreign_object").toString().toBoolean())
        assertEquals("[\"ring\"]", employee.getValue("hand_abnormal_types").toString())
        assertEquals("\"VALID\"", employee.getValue("health_cert_status").toString())
        assertEquals("[\"COUGH\"]", employee.getValue("symptom_flags").toString())
        assertEquals("\"Hand check failed\"", employee.getValue("remark").toString())
        assertEquals("\"face-base64\"", employee.getValue("photo").toString())
        assertEquals("\"palm-base64\"", employee.getValue("hand_palm_photo").toString())
        assertEquals("\"back-base64\"", employee.getValue("hand_back_photo").toString())
    }

    @Test
    fun `request includes required nullable image fields`() {
        val request = MorningCheckUploadRequest(
            deviceId = "DEVICE001",
            timestamp = 1_716_259_200_000,
            employees = listOf(
                MorningCheckEmployee(
                    employeeId = "E001",
                    name = "Test User",
                    recordId = 1001,
                    recordUuid = "123e4567-e89b-42d3-a456-426614174000",
                    userId = 1,
                    temperature = 36.5f,
                    isTempNormal = true,
                    isHandNormal = null,
                    isPassed = false,
                    handStatus = "NOT_CHECKED",
                    hasForeignObject = null,
                    handAbnormalTypes = emptyList(),
                    healthCertStatus = "VALID",
                    symptomFlags = emptyList(),
                    remark = "",
                    photo = null,
                    handPalmPhoto = null,
                    handBackPhoto = null,
                )
            )
        )

        val employee = Json.parseToJsonElement(Json.encodeToString(request))
            .jsonObject.getValue("employees").jsonArray.single().jsonObject

        assertEquals("null", employee.getValue("is_hand_normal").toString())
        assertEquals("null", employee.getValue("has_foreign_object").toString())
        assertEquals("null", employee.getValue("photo").toString())
        assertEquals("null", employee.getValue("hand_palm_photo").toString())
        assertEquals("null", employee.getValue("hand_back_photo").toString())
    }

    @Test
    fun `response deserializes recordIds array`() {
        val response = Json.decodeFromString<MorningCheckUploadResponse>(
            """{"code":200,"message":"success","data":{"recordIds":["REC001"],"processTime":120,"warnings":[],"capture_image_url":"/face.jpg","hand_palm_image_url":"/palm.jpg","hand_back_image_url":null},"request_id":"REQ001"}"""
        )

        assertTrue(response.isSuccess)
        assertEquals(listOf("REC001"), response.data?.recordIds)
        assertEquals(120, response.data?.processTime)
        assertEquals(emptyList<String>(), response.data?.warnings)
        assertEquals("/face.jpg", response.data?.captureImageUrl)
        assertEquals("/palm.jpg", response.data?.handPalmImageUrl)
        assertNull(response.data?.handBackImageUrl)
        assertEquals("REQ001", response.requestId)
    }

    @Test
    fun `response tolerates optional tracking fields missing`() {
        val response = Json.decodeFromString<MorningCheckUploadResponse>(
            """{"code":200,"message":"success","data":{"recordIds":["REC001"],"processTime":120,"warnings":[]}}"""
        )

        assertTrue(response.isSuccess)
        assertEquals(listOf("REC001"), response.data?.recordIds)
        assertNull(response.data?.captureImageUrl)
        assertNull(response.data?.handPalmImageUrl)
        assertNull(response.data?.handBackImageUrl)
        assertNull(response.requestId)
    }
}
