package com.smartcheck.app.domain.usecase

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import com.smartcheck.app.domain.model.HealthCertStatus
import com.smartcheck.app.domain.model.HandStatus
import com.smartcheck.app.domain.model.Record
import com.smartcheck.app.domain.model.SymptomType
import com.smartcheck.app.domain.model.User
import com.smartcheck.app.domain.repository.IRecordRepository
import com.smartcheck.app.domain.repository.ITemperatureService
import com.smartcheck.app.domain.repository.IUserRepository
import com.smartcheck.app.domain.repository.IVoiceService
import com.smartcheck.sdk.HandInfo
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class MorningCheckUseCaseTest {

    private lateinit var userRepository: IUserRepository
    private lateinit var recordRepository: IRecordRepository
    private lateinit var temperatureService: ITemperatureService
    private lateinit var voiceService: IVoiceService
    private lateinit var useCase: MorningCheckUseCase

    @Before
    fun setup() {
        userRepository = mockk(relaxed = true)
        recordRepository = mockk(relaxed = true)
        temperatureService = mockk(relaxed = true)
        voiceService = mockk(relaxed = true)
        
        useCase = MorningCheckUseCase(
            userRepository = userRepository,
            recordRepository = recordRepository,
            temperatureService = temperatureService,
            voiceService = voiceService
        )
    }

    @Test
    fun `onFaceRecognized returns result with user info when found`() = runTest {
        // Given
        val userId = 1L
        val userName = "张三"
        val confidence = 0.95f
        val expectedUser = User(
            id = userId,
            name = userName,
            employeeId = "EMP001",
            healthCertEndDate = System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000
        )
        coEvery { userRepository.getUserById(userId) } returns Result.success(expectedUser)

        // When
        val result = useCase.onFaceRecognized(userId, userName, confidence)

        // Then
        assertEquals(userId, result.userId)
        assertEquals(userName, result.userName)
        assertEquals(confidence, result.faceConfidence)
        assertEquals("欢迎，$userName", result.message)
        coVerify(exactly = 0) { voiceService.speak(any()) }
    }

    @Test
    fun `onFaceRecognized speaks health cert warning when expiring soon`() = runTest {
        // Given
        val userId = 1L
        val userName = "张三"
        val confidence = 0.95f
        val expectedUser = User(
            id = userId,
            name = userName,
            employeeId = "EMP001",
            healthCertEndDate = System.currentTimeMillis() + 5L * 24 * 60 * 60 * 1000
        )
        coEvery { userRepository.getUserById(userId) } returns Result.success(expectedUser)

        // When
        val result = useCase.onFaceRecognized(userId, userName, confidence)

        // Then
        assertTrue(result.healthCertDaysRemaining!! < 7)
        coVerify { voiceService.speak("健康证即将到期") }
    }

    @Test
    fun `checkHealthCert returns valid status`() = runTest {
        // Given
        val user = User(
            id = 1L,
            name = "张三",
            employeeId = "EMP001",
            healthCertEndDate = System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000
        )

        // When
        val result = useCase.checkHealthCert(user)

        // Then
        assertTrue(result.isSuccess)
    }

    @Test
    fun `speak calls voiceService`() {
        // Given
        val message = "测试消息"

        // When
        useCase.speak(message)

        // Then
        coVerify { voiceService.speak(message) }
    }

    @Test
    fun `speakSuccess calls voiceService with welcome message`() {
        // When
        useCase.speakSuccess()

        // Then
        coVerify { voiceService.speak("欢迎") }
    }

    @Test
    fun `speakHealthCertWarning calls voiceService`() {
        // When
        useCase.speakHealthCertWarning()

        // Then
        coVerify { voiceService.speak("健康证即将到期") }
    }

    // ── checkHealthCert ─────────────────────────────────────────────────────

    @Test
    fun `checkHealthCert 健康证有效时返回 VALID`() = runTest {
        // Given
        val user = User(
            id = 1L,
            name = "张三",
            employeeId = "EMP001",
            healthCertEndDate = System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000
        )

        // When
        val result = useCase.checkHealthCert(user)

        // Then
        assertTrue(result.isSuccess)
        assertEquals(HealthCertStatus.VALID, result.getOrNull())
    }

    @Test
    fun `checkHealthCert 健康证即将过期时返回 EXPIRING_SOON`() = runTest {
        // Given
        val user = User(
            id = 1L,
            name = "张三",
            employeeId = "EMP001",
            healthCertEndDate = System.currentTimeMillis() + 3L * 24 * 60 * 60 * 1000
        )

        // When
        val result = useCase.checkHealthCert(user)

        // Then
        assertTrue(result.isSuccess)
        assertEquals(HealthCertStatus.EXPIRING_SOON, result.getOrNull())
    }

    @Test
    fun `checkHealthCert 健康证已过期时返回 EXPIRED`() = runTest {
        // Given
        val user = User(
            id = 1L,
            name = "张三",
            employeeId = "EMP001",
            healthCertEndDate = System.currentTimeMillis() - 5L * 24 * 60 * 60 * 1000
        )

        // When
        val result = useCase.checkHealthCert(user)

        // Then
        assertTrue(result.isSuccess)
        assertEquals(HealthCertStatus.EXPIRED, result.getOrNull())
    }

    // ── calculateIsPassed ───────────────────────────────────────────────────

    @Test
    fun `calculateIsPassed 全部条件正常时返回 true`() {
        // Given
        val handResult = HandCheckResult(palmNormal = true, backNormal = true, palmImagePath = null, backImagePath = null)

        // When
        val result = useCase.calculateIsPassed(
            isTempNormal = true,
            handCheckResult = handResult,
            healthCertStatus = HealthCertStatus.VALID,
            symptoms = emptyList()
        )

        // Then
        assertTrue(result)
    }

    @Test
    fun `calculateIsPassed 温度异常时返回 false`() {
        // Given
        val handResult = HandCheckResult(palmNormal = true, backNormal = true, palmImagePath = null, backImagePath = null)

        // When
        val result = useCase.calculateIsPassed(
            isTempNormal = false,
            handCheckResult = handResult,
            healthCertStatus = HealthCertStatus.VALID,
            symptoms = emptyList()
        )

        // Then
        assertFalse(result)
    }

    @Test
    fun `calculateIsPassed 手掌异常时返回 false`() {
        // Given
        val handResult = HandCheckResult(palmNormal = false, backNormal = true, palmImagePath = null, backImagePath = null)

        // When
        val result = useCase.calculateIsPassed(
            isTempNormal = true,
            handCheckResult = handResult,
            healthCertStatus = HealthCertStatus.VALID,
            symptoms = emptyList()
        )

        // Then
        assertFalse(result)
    }

    @Test
    fun `calculateIsPassed 手背异常时返回 false`() {
        // Given
        val handResult = HandCheckResult(palmNormal = true, backNormal = false, palmImagePath = null, backImagePath = null)

        // When
        val result = useCase.calculateIsPassed(
            isTempNormal = true,
            handCheckResult = handResult,
            healthCertStatus = HealthCertStatus.VALID,
            symptoms = emptyList()
        )

        // Then
        assertFalse(result)
    }

    @Test
    fun `calculateIsPassed 健康证已过期时返回 false`() {
        // Given
        val handResult = HandCheckResult(palmNormal = true, backNormal = true, palmImagePath = null, backImagePath = null)

        // When
        val result = useCase.calculateIsPassed(
            isTempNormal = true,
            handCheckResult = handResult,
            healthCertStatus = HealthCertStatus.EXPIRED,
            symptoms = emptyList()
        )

        // Then
        assertFalse(result)
    }

    @Test
    fun `calculateIsPassed 有发烧症状时返回 false`() {
        // Given
        val handResult = HandCheckResult(palmNormal = true, backNormal = true, palmImagePath = null, backImagePath = null)

        // When
        val result = useCase.calculateIsPassed(
            isTempNormal = true,
            handCheckResult = handResult,
            healthCertStatus = HealthCertStatus.VALID,
            symptoms = listOf(SymptomType.FEVER)
        )

        // Then
        assertFalse(result)
    }

    @Test
    fun `calculateIsPassed 健康证即将过期但其他正常时返回 true`() {
        // EXPIRING_SOON 不阻断晨检
        val handResult = HandCheckResult(palmNormal = true, backNormal = true, palmImagePath = null, backImagePath = null)

        val result = useCase.calculateIsPassed(
            isTempNormal = true,
            handCheckResult = handResult,
            healthCertStatus = HealthCertStatus.EXPIRING_SOON,
            symptoms = emptyList()
        )

        assertTrue(result)
    }

    // ── saveRecord ──────────────────────────────────────────────────────────

    @Test
    fun `saveRecord 正常时调用 repository 并返回带 id 的 Record`() = runTest {
        // Given
        val handResult = HandCheckResult(palmNormal = true, backNormal = true, palmImagePath = null, backImagePath = null)
        coEvery { recordRepository.saveRecord(any()) } returns Result.success(42L)

        // When
        val result = useCase.saveRecord(
            userId = 1L,
            userName = "张三",
            employeeId = "E001",
            temperature = 36.5f,
            isTempNormal = true,
            handCheckResult = handResult,
            symptoms = emptyList(),
            healthCertStatus = HealthCertStatus.VALID
        )

        // Then
        assertTrue(result.isSuccess)
        assertEquals(42L, result.getOrNull()?.id)
        coVerify { recordRepository.saveRecord(any()) }
    }

    @Test
    fun `saveRecord repository 失败时返回 failure`() = runTest {
        // Given
        val handResult = HandCheckResult(palmNormal = true, backNormal = true, palmImagePath = null, backImagePath = null)
        coEvery { recordRepository.saveRecord(any()) } returns Result.failure(Exception("DB error"))

        // When
        val result = useCase.saveRecord(
            userId = 1L,
            userName = "张三",
            employeeId = "E001",
            temperature = 36.5f,
            isTempNormal = true,
            handCheckResult = handResult,
            symptoms = emptyList(),
            healthCertStatus = HealthCertStatus.VALID
        )

        // Then
        assertTrue(result.isFailure)
    }

    // ── processSymptomSubmission ────────────────────────────────────────────

    @Test
    fun `processSymptomSubmission 无症状时返回 isAllPass true`() {
        // When
        val result = useCase.processSymptomSubmission(emptyList())

        // Then
        assertTrue(result.isAllPass)
        assertFalse(result.hasFever)
        coVerify { voiceService.speak("无不适症状") }
    }

    @Test
    fun `processSymptomSubmission 含发烧时返回 isAllPass false 且 hasFever true`() {
        // When
        val result = useCase.processSymptomSubmission(listOf("发烧"))

        // Then
        assertFalse(result.isAllPass)
        assertTrue(result.hasFever)
        coVerify { voiceService.speak("有发烧症状，禁止上岗") }
    }

    @Test
    fun `processSymptomSubmission 含其他症状时返回 isAllPass false 且 hasFever false`() {
        // When
        val result = useCase.processSymptomSubmission(listOf("咳嗽"))

        // Then
        assertFalse(result.isAllPass)
        assertFalse(result.hasFever)
        coVerify { voiceService.speak("症状已记录") }
    }

    @Test
    fun `processSymptomSubmission 仅含空白字符串时视为无症状`() {
        // When
        val result = useCase.processSymptomSubmission(listOf("  ", ""))

        // Then
        assertTrue(result.isAllPass)
    }

    // ── analyzeHandDetectionResults ─────────────────────────────────────────

    @Test
    fun `analyzeHandDetectionResults 无检测结果时返回 isPassing true`() {
        // When
        val analysis = useCase.analyzeHandDetectionResults(emptyList())

        // Then
        assertFalse(analysis.hasIssue)
        assertTrue(analysis.isPassing)
        assertTrue(analysis.issues.isEmpty())
    }

    @Test
    fun `analyzeHandDetectionResults 有异物时返回 hasIssue true`() {
        // Given
        val handInfo = mockk<HandInfo>()
        every { handInfo.hasForeignObject } returns true
        every { handInfo.label } returns "ring"

        // When
        val analysis = useCase.analyzeHandDetectionResults(listOf(handInfo))

        // Then
        assertTrue(analysis.hasIssue)
        assertFalse(analysis.isPassing)
        assertTrue(analysis.issues.contains("ring"))
    }

    @Test
    fun `analyzeHandDetectionResults 无异物时返回 hasIssue false`() {
        // Given
        val handInfo = mockk<HandInfo>()
        every { handInfo.hasForeignObject } returns false
        every { handInfo.label } returns "normal"

        // When
        val analysis = useCase.analyzeHandDetectionResults(listOf(handInfo))

        // Then
        assertFalse(analysis.hasIssue)
        assertTrue(analysis.isPassing)
    }

    // ── calculateHealthCertStatus ───────────────────────────────────────────

    @Test
    fun `calculateHealthCertStatus remainingDays 为 null 时返回 VALID`() {
        assertEquals(HealthCertStatus.VALID, useCase.calculateHealthCertStatus(null))
    }

    @Test
    fun `calculateHealthCertStatus 负数天数返回 EXPIRED`() {
        assertEquals(HealthCertStatus.EXPIRED, useCase.calculateHealthCertStatus(-1))
    }

    @Test
    fun `calculateHealthCertStatus 小于 7 天返回 EXPIRING_SOON`() {
        assertEquals(HealthCertStatus.EXPIRING_SOON, useCase.calculateHealthCertStatus(3))
    }

    @Test
    fun `calculateHealthCertStatus 大于等于 7 天返回 VALID`() {
        assertEquals(HealthCertStatus.VALID, useCase.calculateHealthCertStatus(30))
    }

    // ── submitSymptoms ──────────────────────────────────────────────────────

    @Test
    fun `submitSymptoms 含 FEVER 时 shouldBlockWork 为 true`() = runTest {
        // When
        val result = useCase.submitSymptoms(listOf(SymptomType.FEVER, SymptomType.COUGH))

        // Then
        assertTrue(result.hasFever)
        assertTrue(result.shouldBlockWork)
        coVerify { voiceService.speak("有发烧症状，禁止上岗") }
    }

    @Test
    fun `submitSymptoms 不含 FEVER 时 shouldBlockWork 为 false`() = runTest {
        // When
        val result = useCase.submitSymptoms(listOf(SymptomType.COUGH))

        // Then
        assertFalse(result.hasFever)
        assertFalse(result.shouldBlockWork)
        assertTrue(result.hasOtherSymptoms)
        coVerify { voiceService.speak("症状已记录") }
    }

    @Test
    fun `submitSymptoms 无任何症状时 hasOtherSymptoms 为 false`() = runTest {
        // When
        val result = useCase.submitSymptoms(emptyList())

        // Then
        assertFalse(result.hasFever)
        assertFalse(result.hasOtherSymptoms)
        assertFalse(result.shouldBlockWork)
        coVerify { voiceService.speak("无不适症状") }
    }

    // ── speak helpers ───────────────────────────────────────────────────────

    @Test
    fun `speakHealthCertExpired 调用 voiceService`() {
        useCase.speakHealthCertExpired()

        coVerify { voiceService.speak("健康证已过期") }
    }

    @Test
    fun `speakTemperatureNormal 调用 voiceService`() {
        useCase.speakTemperatureNormal()

        coVerify { voiceService.speak("体温正常，请准备手部检测") }
    }

    @Test
    fun `speakHandCheckPass 调用 voiceService`() {
        useCase.speakHandCheckPass()

        coVerify { voiceService.speak("请回答健康询问") }
    }

    @Test
    fun `speakHandCheckFail 调用 voiceService`() {
        useCase.speakHandCheckFail()

        coVerify { voiceService.speak("手部有异物") }
    }

    @Test
    fun `speakAllPass 调用 voiceService`() {
        useCase.speakAllPass()

        coVerify { voiceService.speak("晨检成功，祝您工作愉快") }
    }
}
