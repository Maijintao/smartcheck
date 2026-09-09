package com.smartcheck.app.data.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class EmployeeSyncRepositoryTest {

    @Test
    fun `未提供新图片时保留平台已有图片`() {
        assertEquals(
            "KEEP",
            determineEmployeeImageAction(
                newPath = null,
                oldSha256 = "existing-sha256",
                newSha256 = null,
            ),
        )
    }

    @Test
    fun `新图片内容变化时替换平台图片`() {
        assertEquals(
            "REPLACE",
            determineEmployeeImageAction(
                newPath = "new-face.jpg",
                oldSha256 = "old-sha256",
                newSha256 = "new-sha256",
            ),
        )
    }
}
