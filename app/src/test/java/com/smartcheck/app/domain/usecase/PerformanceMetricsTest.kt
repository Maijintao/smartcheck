package com.smartcheck.app.domain.usecase

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PerformanceMetricsTest {

    @Before
    fun setup() {
        PerformanceMetrics.clear()
    }

    @After
    fun tearDown() {
        PerformanceMetrics.clear()
    }

    // ── PerformanceMetrics ──────────────────────────────────────────────────

    @Test
    fun `recordDuration 记录后 getStats 返回非 null`() {
        PerformanceMetrics.recordDuration("face_recognition", 120)

        assertNotNull(PerformanceMetrics.getStats("face_recognition"))
    }

    @Test
    fun `recordDuration 多次记录后 getStats count 正确`() {
        PerformanceMetrics.recordDuration("op", 100)
        PerformanceMetrics.recordDuration("op", 200)
        PerformanceMetrics.recordDuration("op", 300)

        val stats = PerformanceMetrics.getStats("op")!!
        assertEquals(3, stats.count)
    }

    @Test
    fun `getStats 未记录的操作返回 null`() {
        assertNull(PerformanceMetrics.getStats("nonexistent_operation"))
    }

    @Test
    fun `getAllStats 包含所有已记录的操作`() {
        PerformanceMetrics.recordDuration("op_a", 50)
        PerformanceMetrics.recordDuration("op_b", 80)

        val all = PerformanceMetrics.getAllStats()

        assertTrue(all.containsKey("op_a"))
        assertTrue(all.containsKey("op_b"))
        assertEquals(2, all.size)
    }

    @Test
    fun `clear 后 getStats 返回 null`() {
        PerformanceMetrics.recordDuration("op", 100)
        PerformanceMetrics.clear()

        assertNull(PerformanceMetrics.getStats("op"))
    }

    @Test
    fun `clear 后 getAllStats 返回空 Map`() {
        PerformanceMetrics.recordDuration("op", 100)
        PerformanceMetrics.clear()

        assertTrue(PerformanceMetrics.getAllStats().isEmpty())
    }

    @Test
    fun `recordDurationIfSlow 也会记录样本到 getStats`() {
        PerformanceMetrics.recordDurationIfSlow("slow_op", 2000, thresholdMs = 1000)

        val stats = PerformanceMetrics.getStats("slow_op")
        assertNotNull(stats)
        assertEquals(1, stats!!.count)
    }

    // ── MetricRecord ────────────────────────────────────────────────────────

    @Test
    fun `MetricRecord 空样本时 getStats 返回全零`() {
        val record = MetricRecord("test")

        val stats = record.getStats()

        assertEquals(0, stats.count)
        assertEquals(0L, stats.avg)
        assertEquals(0L, stats.min)
        assertEquals(0L, stats.max)
    }

    @Test
    fun `MetricRecord 单个样本时 avg min max 均等于该值`() {
        val record = MetricRecord("test")
        record.addSample(150)

        val stats = record.getStats()

        assertEquals(1, stats.count)
        assertEquals(150L, stats.avg)
        assertEquals(150L, stats.min)
        assertEquals(150L, stats.max)
    }

    @Test
    fun `MetricRecord 多个样本时 min max avg 计算正确`() {
        val record = MetricRecord("test")
        record.addSample(100)
        record.addSample(200)
        record.addSample(300)

        val stats = record.getStats()

        assertEquals(3, stats.count)
        assertEquals(100L, stats.min)
        assertEquals(300L, stats.max)
        assertEquals(200L, stats.avg)  // (100+200+300)/3 = 200
    }

    @Test
    fun `MetricRecord p50 计算正确`() {
        val record = MetricRecord("test")
        // 插入 1..5，中值 = 3
        (1L..5L).forEach { record.addSample(it * 100) }

        val stats = record.getStats()

        assertEquals(300L, stats.p50)
    }

    @Test
    fun `MetricRecord 超过 1000 条样本时 count 不超过 1000`() {
        val record = MetricRecord("test")
        repeat(1100) { record.addSample(it.toLong()) }

        val stats = record.getStats()

        assertEquals(1000, stats.count)
    }

    @Test
    fun `MetricRecord 超过 1000 条时最旧样本被丢弃`() {
        val record = MetricRecord("test")
        // 添加 1001 个样本，值从 0 到 1000
        repeat(1001) { record.addSample(it.toLong()) }

        val stats = record.getStats()

        // 0 应已被丢弃，min 应为 1
        assertEquals(1L, stats.min)
    }
}
