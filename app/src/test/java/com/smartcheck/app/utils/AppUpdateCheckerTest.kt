package com.smartcheck.app.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
