package com.smartcheck.app.data.upload

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PlatformUrlResolverTest {
    @Test
    fun `resolves Datang legacy execution URL`() {
        assertEquals(
            "https://dt.datanginfo.com/xyc/api/device/morning-check/upload",
            PlatformUrlResolver.morningCheckUploadUrl("https://dt.datanginfo.com/xyc/execution"),
        )
    }

    @Test
    fun `accepts base API and complete endpoint URLs`() {
        assertEquals(
            "https://dt.datanginfo.com/xyc/api/device/morning-check/upload",
            PlatformUrlResolver.morningCheckUploadUrl("https://dt.datanginfo.com/xyc/api"),
        )
        assertEquals(
            "http://192.168.1.2/xyc/api/device/morning-check/upload",
            PlatformUrlResolver.morningCheckUploadUrl(
                "http://192.168.1.2/xyc/api/device/morning-check/upload"
            ),
        )
        assertEquals(
            "http://192.168.1.2/xyc/api/device/morning-check/upload",
            PlatformUrlResolver.morningCheckUploadUrl("http://192.168.1.2/xyc/api/device"),
        )
    }

    @Test
    fun `builds employee endpoints from documented device API base URL`() {
        val baseUrl = "http://1.2.3.4:8000/api/device"

        assertEquals(
            "http://1.2.3.4:8000/api/device/employees/changes",
            PlatformUrlResolver.employeeChangesUrl(baseUrl),
        )
        assertEquals(
            "http://1.2.3.4:8000/api/device/employees/snapshot",
            PlatformUrlResolver.employeeSnapshotUrl(baseUrl),
        )
        assertEquals(
            "http://1.2.3.4:8000/api/device/employees/EMP%20001",
            PlatformUrlResolver.employeeDetailUrl(baseUrl, "EMP 001"),
        )
        assertEquals(
            "http://1.2.3.4:8000/api/device/employees/images/face%2F001",
            PlatformUrlResolver.employeeImageUrl(baseUrl, "face/001"),
        )
    }

    @Test
    fun `keeps root platform URL backward compatible`() {
        assertEquals(
            "http://1.2.3.4:8000/api/device/employees/changes",
            PlatformUrlResolver.employeeChangesUrl("http://1.2.3.4:8000"),
        )
        assertEquals(
            "http://1.2.3.4:8000/api/device/refresh",
            PlatformUrlResolver.heartbeatUrl("http://1.2.3.4:8000/api/device"),
        )
    }

    @Test
    fun `rejects unsupported URL schemes`() {
        assertThrows(IllegalArgumentException::class.java) {
            PlatformUrlResolver.morningCheckUploadUrl("ftp://dt.datanginfo.com/xyc")
        }
    }
}
