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
    }

    @Test
    fun `rejects unsupported URL schemes`() {
        assertThrows(IllegalArgumentException::class.java) {
            PlatformUrlResolver.morningCheckUploadUrl("ftp://dt.datanginfo.com/xyc")
        }
    }
}
