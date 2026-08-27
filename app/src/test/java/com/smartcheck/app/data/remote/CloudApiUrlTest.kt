package com.smartcheck.app.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudApiUrlTest {

    @Test
    fun `normalizeBaseUrl removes whitespace and trailing slash`() {
        val result = CloudApiUrl.normalizeBaseUrl("  https://example.com/api/  ")

        assertEquals("https://example.com/api", result.getOrThrow())
    }

    @Test
    fun `normalizeBaseUrl supports IP port and base path`() {
        val result = CloudApiUrl.normalizeBaseUrl("http://192.168.1.10:8080/api")

        assertEquals("http://192.168.1.10:8080/api", result.getOrThrow())
    }

    @Test
    fun `normalizeBaseUrl rejects missing scheme`() {
        assertTrue(CloudApiUrl.normalizeBaseUrl("example.com/api").isFailure)
    }

    @Test
    fun `normalizeBaseUrl rejects query and fragment`() {
        assertTrue(CloudApiUrl.normalizeBaseUrl("https://example.com/api?token=1").isFailure)
        assertTrue(CloudApiUrl.normalizeBaseUrl("https://example.com/api#section").isFailure)
    }

    @Test
    fun `buildUrl joins base path and endpoint once`() {
        val url = CloudApiUrl.buildUrl(
            "https://example.com/api/",
            "/wosapi/YGCJRobotOpenApi/PageStaff"
        )

        assertEquals(
            "https://example.com/api/wosapi/YGCJRobotOpenApi/PageStaff",
            url
        )
    }

    @Test
    fun `resolveUrl keeps absolute image URL`() {
        val url = CloudApiUrl.resolveUrl(
            CloudApiUrl.DEFAULT_BASE_URL,
            "https://cdn.example.com/image.jpg"
        )

        assertEquals("https://cdn.example.com/image.jpg", url)
    }

    @Test
    fun `resolveUrl joins relative image URL with configured base`() {
        val url = CloudApiUrl.resolveUrl(
            "https://example.com/api",
            "/uploads/image.jpg"
        )

        assertEquals("https://example.com/api/uploads/image.jpg", url)
    }
}
