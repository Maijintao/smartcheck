package com.smartcheck.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UserTest {

    private val DAY_MS = 24L * 60 * 60 * 1000

    // ── getHealthCertStatus ─────────────────────────────────────────────────

    @Test
    fun `getHealthCertStatus 健康证到期日为 null 时返回 EXPIRED`() {
        val user = User(name = "张三", employeeId = "E001", healthCertEndDate = null)

        assertEquals(HealthCertStatus.EXPIRED, user.getHealthCertStatus())
    }

    @Test
    fun `getHealthCertStatus 健康证剩余超过 7 天返回 VALID`() {
        val endDate = System.currentTimeMillis() + 30L * DAY_MS
        val user = User(name = "张三", employeeId = "E001", healthCertEndDate = endDate)

        assertEquals(HealthCertStatus.VALID, user.getHealthCertStatus())
    }

    @Test
    fun `getHealthCertStatus 健康证剩余 8 天返回 VALID`() {
        // 8 > 7, 边界之上一天
        val endDate = System.currentTimeMillis() + 8L * DAY_MS + 60_000
        val user = User(name = "张三", employeeId = "E001", healthCertEndDate = endDate)

        assertEquals(HealthCertStatus.VALID, user.getHealthCertStatus())
    }

    @Test
    fun `getHealthCertStatus 健康证剩余 3 天返回 EXPIRING_SOON`() {
        val endDate = System.currentTimeMillis() + 3L * DAY_MS
        val user = User(name = "张三", employeeId = "E001", healthCertEndDate = endDate)

        assertEquals(HealthCertStatus.EXPIRING_SOON, user.getHealthCertStatus())
    }

    @Test
    fun `getHealthCertStatus 健康证剩余 7 天恰好在边界返回 EXPIRING_SOON`() {
        // daysRemaining = 7, 7 <= 7 => EXPIRING_SOON
        val endDate = System.currentTimeMillis() + 7L * DAY_MS
        val user = User(name = "张三", employeeId = "E001", healthCertEndDate = endDate)

        assertEquals(HealthCertStatus.EXPIRING_SOON, user.getHealthCertStatus())
    }

    @Test
    fun `getHealthCertStatus 健康证今天到期剩余 0 天返回 EXPIRING_SOON`() {
        // daysRemaining = 0, 0 <= 7 => EXPIRING_SOON (当天还未到期)
        val endDate = System.currentTimeMillis() + 30_000 // 30秒后
        val user = User(name = "张三", employeeId = "E001", healthCertEndDate = endDate)

        assertEquals(HealthCertStatus.EXPIRING_SOON, user.getHealthCertStatus())
    }

    @Test
    fun `getHealthCertStatus 健康证已过期返回 EXPIRED`() {
        val endDate = System.currentTimeMillis() - 5L * DAY_MS
        val user = User(name = "张三", employeeId = "E001", healthCertEndDate = endDate)

        assertEquals(HealthCertStatus.EXPIRED, user.getHealthCertStatus())
    }

    @Test
    fun `getHealthCertStatus 健康证昨天过期返回 EXPIRED`() {
        val endDate = System.currentTimeMillis() - 1L * DAY_MS - 60_000
        val user = User(name = "张三", employeeId = "E001", healthCertEndDate = endDate)

        assertEquals(HealthCertStatus.EXPIRED, user.getHealthCertStatus())
    }

    // ── getHealthCertDaysRemaining ──────────────────────────────────────────

    @Test
    fun `getHealthCertDaysRemaining 到期日为 null 时返回 null`() {
        val user = User(name = "张三", employeeId = "E001", healthCertEndDate = null)

        assertNull(user.getHealthCertDaysRemaining())
    }

    @Test
    fun `getHealthCertDaysRemaining 正确计算未来天数`() {
        val endDate = System.currentTimeMillis() + 30L * DAY_MS
        val user = User(name = "张三", employeeId = "E001", healthCertEndDate = endDate)

        val days = user.getHealthCertDaysRemaining()

        assertNotNull(days)
        // 允许因毫秒差导致的±1天误差
        assertTrue("剩余天数应约为 30，实际为 $days", days!! in 29L..30L)
    }

    @Test
    fun `getHealthCertDaysRemaining 健康证已过期时返回负数`() {
        val endDate = System.currentTimeMillis() - 5L * DAY_MS
        val user = User(name = "张三", employeeId = "E001", healthCertEndDate = endDate)

        val days = user.getHealthCertDaysRemaining()

        assertNotNull(days)
        assertTrue("过期时剩余天数应为负数，实际为 $days", days!! < 0)
    }

    // ── equals / hashCode 自定义实现 ────────────────────────────────────────

    @Test
    fun `id 和 employeeId 相同的两个 User 应 equals`() {
        val user1 = User(id = 1L, name = "张三", employeeId = "E001")
        val user2 = User(id = 1L, name = "张三", employeeId = "E001")

        assertEquals(user1, user2)
    }

    @Test
    fun `faceEmbedding 相同的两个 User 应 equals`() {
        val embedding = byteArrayOf(1, 2, 3)
        val user1 = User(id = 1L, name = "张三", employeeId = "E001", faceEmbedding = embedding)
        val user2 = User(id = 1L, name = "张三", employeeId = "E001", faceEmbedding = embedding.copyOf())

        assertEquals(user1, user2)
    }
}
