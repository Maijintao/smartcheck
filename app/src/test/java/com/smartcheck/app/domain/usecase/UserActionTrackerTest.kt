package com.smartcheck.app.domain.usecase

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class UserActionTrackerTest {

    @Before
    fun setup() {
        UserActionTracker.clear()
    }

    @After
    fun tearDown() {
        UserActionTracker.clear()
    }

    // ── track / getRecentLogs ───────────────────────────────────────────────

    @Test
    fun `track 后 getRecentLogs 包含该记录`() {
        UserActionTracker.track(ActionType.LOGIN_SUCCESS, "LoginScreen")

        val logs = UserActionTracker.getRecentLogs()

        assertEquals(1, logs.size)
        assertEquals(ActionType.LOGIN_SUCCESS, logs[0].action)
        assertEquals("LoginScreen", logs[0].screen)
    }

    @Test
    fun `track 多次后 getRecentLogs 按顺序返回`() {
        UserActionTracker.track(ActionType.LOGIN_SUCCESS, "LoginScreen")
        UserActionTracker.track(ActionType.FACE_RECOGNIZED, "CheckScreen")
        UserActionTracker.track(ActionType.RECORD_SUBMITTED, "CheckScreen")

        val logs = UserActionTracker.getRecentLogs()

        assertEquals(3, logs.size)
        assertEquals(ActionType.LOGIN_SUCCESS, logs[0].action)
        assertEquals(ActionType.FACE_RECOGNIZED, logs[1].action)
        assertEquals(ActionType.RECORD_SUBMITTED, logs[2].action)
    }

    @Test
    fun `track 记录 detail 字段`() {
        UserActionTracker.track(ActionType.FACE_RECOGNIZED, "CheckScreen", detail = "userId=42")

        val log = UserActionTracker.getRecentLogs().first()

        assertEquals("userId=42", log.detail)
    }

    @Test
    fun `track 记录 durationMs 字段`() {
        UserActionTracker.track(ActionType.TEMPERATURE_MEASURED, "CheckScreen", durationMs = 350L)

        val log = UserActionTracker.getRecentLogs().first()

        assertEquals(350L, log.durationMs)
    }

    @Test
    fun `track FAILED result 被正确记录`() {
        UserActionTracker.track(ActionType.LOGIN_FAILED, "LoginScreen", result = ActionResult.FAILED)

        val log = UserActionTracker.getRecentLogs().first()

        assertEquals(ActionResult.FAILED, log.result)
    }

    @Test
    fun `track CANCELLED result 被正确记录`() {
        UserActionTracker.track(ActionType.MORNING_CHECK_START, "CheckScreen", result = ActionResult.CANCELLED)

        val log = UserActionTracker.getRecentLogs().first()

        assertEquals(ActionResult.CANCELLED, log.result)
    }

    @Test
    fun `track 记录 timestamp 为正数`() {
        UserActionTracker.track(ActionType.LOGOUT, "SettingsScreen")

        val log = UserActionTracker.getRecentLogs().first()

        assertTrue("timestamp 应大于 0", log.timestamp > 0L)
    }

    // ── getRecentLogs count 限制 ────────────────────────────────────────────

    @Test
    fun `getRecentLogs 默认返回最多 100 条`() {
        repeat(120) { i ->
            UserActionTracker.track(ActionType.RECORDS_VIEWED, "AdminScreen", detail = "i=$i")
        }

        val logs = UserActionTracker.getRecentLogs()

        assertEquals(100, logs.size)
    }

    @Test
    fun `getRecentLogs 按 count 截断并返回最新记录`() {
        repeat(10) { i ->
            UserActionTracker.track(ActionType.RECORDS_VIEWED, "AdminScreen", detail = "i=$i")
        }

        val logs = UserActionTracker.getRecentLogs(count = 3)

        assertEquals(3, logs.size)
        // takeLast(3) 返回最后 3 条
        assertEquals("i=7", logs[0].detail)
        assertEquals("i=8", logs[1].detail)
        assertEquals("i=9", logs[2].detail)
    }

    @Test
    fun `getRecentLogs count 大于总数时返回全部`() {
        UserActionTracker.track(ActionType.LOGOUT, "SettingsScreen")
        UserActionTracker.track(ActionType.LOGIN_SUCCESS, "LoginScreen")

        val logs = UserActionTracker.getRecentLogs(count = 100)

        assertEquals(2, logs.size)
    }

    // ── clear ───────────────────────────────────────────────────────────────

    @Test
    fun `clear 后 getRecentLogs 为空`() {
        UserActionTracker.track(ActionType.LOGIN_SUCCESS, "LoginScreen")
        UserActionTracker.clear()

        assertTrue(UserActionTracker.getRecentLogs().isEmpty())
    }

    // ── trackStart / trackEnd ───────────────────────────────────────────────

    @Test
    fun `trackStart 返回大于 0 的时间戳`() {
        val startTime = UserActionTracker.trackStart(ActionType.MORNING_CHECK_START, "CheckScreen")

        assertTrue(startTime > 0L)
    }

    @Test
    fun `trackEnd 计算 durationMs 并写入日志`() {
        val startTime = System.currentTimeMillis() - 500L

        UserActionTracker.trackEnd(startTime, ActionType.RECORD_SUBMITTED, "CheckScreen", detail = "done")

        val log = UserActionTracker.getRecentLogs().first()
        assertNotNull(log.durationMs)
        assertTrue("duration 应 >= 500ms，实际=${log.durationMs}", log.durationMs!! >= 500L)
        assertEquals("done", log.detail)
    }

    @Test
    fun `trackEnd 默认 result 为 SUCCESS`() {
        val startTime = System.currentTimeMillis()
        UserActionTracker.trackEnd(startTime, ActionType.EMPLOYEE_ADDED, "EmployeeScreen")

        val log = UserActionTracker.getRecentLogs().first()
        assertEquals(ActionResult.SUCCESS, log.result)
    }

    // ── ActionType 枚举完整性 ────────────────────────────────────────────────

    @Test
    fun `所有 ActionType 枚举值都可以被 track`() {
        ActionType.values().forEach { actionType ->
            UserActionTracker.track(actionType, "TestScreen")
        }

        assertEquals(ActionType.values().size, UserActionTracker.getRecentLogs().size)
    }
}
