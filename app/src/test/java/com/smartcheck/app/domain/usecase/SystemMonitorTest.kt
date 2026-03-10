package com.smartcheck.app.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SystemMonitorTest {

    @Before
    fun resetState() {
        // 将所有可变状态重置为默认值
        SystemMonitor.updateAiModelState(false)
        SystemMonitor.updateCameraState(false)
        SystemMonitor.updateTemperatureState(false)
        SystemMonitor.updateRecordCount(0)
    }

    // ── updateAiModelState ──────────────────────────────────────────────────

    @Test
    fun `updateAiModelState true 时 systemState aiModelLoaded 为 true`() {
        SystemMonitor.updateAiModelState(true)

        assertTrue(SystemMonitor.systemState.value.aiModelLoaded)
    }

    @Test
    fun `updateAiModelState false 时 systemState aiModelLoaded 为 false`() {
        SystemMonitor.updateAiModelState(true)
        SystemMonitor.updateAiModelState(false)

        assertFalse(SystemMonitor.systemState.value.aiModelLoaded)
    }

    // ── updateCameraState ───────────────────────────────────────────────────

    @Test
    fun `updateCameraState true 时 systemState cameraReady 为 true`() {
        SystemMonitor.updateCameraState(true)

        assertTrue(SystemMonitor.systemState.value.cameraReady)
    }

    @Test
    fun `updateCameraState false 时 systemState cameraReady 为 false`() {
        SystemMonitor.updateCameraState(true)
        SystemMonitor.updateCameraState(false)

        assertFalse(SystemMonitor.systemState.value.cameraReady)
    }

    // ── updateTemperatureState ──────────────────────────────────────────────

    @Test
    fun `updateTemperatureState true 时 systemState temperatureReady 为 true`() {
        SystemMonitor.updateTemperatureState(true)

        assertTrue(SystemMonitor.systemState.value.temperatureReady)
    }

    @Test
    fun `updateTemperatureState false 时 systemState temperatureReady 为 false`() {
        SystemMonitor.updateTemperatureState(true)
        SystemMonitor.updateTemperatureState(false)

        assertFalse(SystemMonitor.systemState.value.temperatureReady)
    }

    // ── updateRecordCount ───────────────────────────────────────────────────

    @Test
    fun `updateRecordCount 正确更新 recordCount`() {
        SystemMonitor.updateRecordCount(42)

        assertEquals(42, SystemMonitor.systemState.value.recordCount)
    }

    @Test
    fun `updateRecordCount 多次更新取最新值`() {
        SystemMonitor.updateRecordCount(10)
        SystemMonitor.updateRecordCount(20)
        SystemMonitor.updateRecordCount(5)

        assertEquals(5, SystemMonitor.systemState.value.recordCount)
    }

    // ── 状态独立性 ──────────────────────────────────────────────────────────

    @Test
    fun `更新 aiModelLoaded 不影响其他字段`() {
        // 先设置其他字段
        SystemMonitor.updateCameraState(true)
        SystemMonitor.updateTemperatureState(true)
        SystemMonitor.updateRecordCount(7)

        // 只更新 aiModelLoaded
        SystemMonitor.updateAiModelState(true)

        val state = SystemMonitor.systemState.value
        assertTrue(state.aiModelLoaded)
        assertTrue("cameraReady 不应被影响", state.cameraReady)
        assertTrue("temperatureReady 不应被影响", state.temperatureReady)
        assertEquals("recordCount 不应被影响", 7, state.recordCount)
    }

    @Test
    fun `更新 recordCount 不影响其他布尔字段`() {
        SystemMonitor.updateAiModelState(true)
        SystemMonitor.updateCameraState(true)

        SystemMonitor.updateRecordCount(99)

        val state = SystemMonitor.systemState.value
        assertTrue("aiModelLoaded 不应被影响", state.aiModelLoaded)
        assertTrue("cameraReady 不应被影响", state.cameraReady)
        assertEquals(99, state.recordCount)
    }

    // ── SystemState 数据类默认值 ────────────────────────────────────────────

    @Test
    fun `SystemState 默认值全部为零值`() {
        val state = SystemState()

        assertEquals(0f, state.memoryUsage)
        assertEquals(0L, state.availableStorage)
        assertEquals(0, state.recordCount)
        assertFalse(state.aiModelLoaded)
        assertFalse(state.cameraReady)
        assertFalse(state.temperatureReady)
        assertEquals(0L, state.lastHealthCheck)
    }
}
