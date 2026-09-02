package com.smartcheck.app.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateCheckerTest {

    @Test
    fun `version names compare by numeric components`() {
        assertEquals(1, compareVersionNames("3.5", "1.0.22"))
        assertEquals(1, compareVersionNames("1.0.22", "1.0.21"))
        assertEquals(0, compareVersionNames("V1.0.22", "1.0.22"))
        assertEquals(-1, compareVersionNames("1.0.9", "1.0.10"))
    }

    @Test
    fun `invalid version names cannot be compared`() {
        assertNull(compareVersionNames("latest", "1.0.22"))
        assertNull(compareVersionNames("1.0.22", "latest"))
    }

    @Test
    fun `signature comparison uses certificate content instead of array identity`() {
        val current = byteArrayOf(1, 2, 3)
        val archive = byteArrayOf(1, 2, 3)

        assertFalse(current === archive)
        assertTrue(haveMatchingSignatures(listOf(current), listOf(archive)))
    }

    @Test
    fun `signature comparison rejects different or missing certificates`() {
        assertFalse(
            haveMatchingSignatures(
                listOf(byteArrayOf(1, 2, 3)),
                listOf(byteArrayOf(1, 2, 4)),
            )
        )
        assertFalse(haveMatchingSignatures(emptyList(), emptyList()))
        assertFalse(haveMatchingSignatures(listOf(byteArrayOf(1)), emptyList()))
    }

    @Test
    fun `signature comparison ignores signer ordering`() {
        val first = byteArrayOf(1, 2, 3)
        val second = byteArrayOf(4, 5, 6)

        assertTrue(
            haveMatchingSignatures(
                listOf(first, second),
                listOf(second.copyOf(), first.copyOf()),
            )
        )
    }
}
