package com.smartcheck.app.viewmodel

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartcheck.app.data.repository.HardwareRepository
import com.smartcheck.app.data.repository.SettingsRepository
import com.smartcheck.app.data.upload.PendingUploadManager
import com.smartcheck.app.data.upload.RecordUploadReporter
import com.smartcheck.app.domain.model.toEntity
import com.smartcheck.app.domain.model.HandStatus
import com.smartcheck.app.domain.model.HealthCertStatus
import com.smartcheck.app.domain.model.Record
import com.smartcheck.app.domain.model.SymptomType
import com.smartcheck.app.domain.repository.IRecordRepository
import com.smartcheck.app.domain.repository.IUserRepository
import com.smartcheck.app.domain.repository.IVoiceService
import com.smartcheck.app.domain.usecase.HandCheckResult
import com.smartcheck.app.domain.usecase.ImageStorageUseCase
import com.smartcheck.app.domain.usecase.MorningCheckUseCase
import com.smartcheck.app.domain.usecase.PerformanceMetrics
import com.smartcheck.app.domain.usecase.ActionType
import com.smartcheck.app.domain.usecase.UserActionTracker
import com.smartcheck.app.ml.FaceEngine
import com.smartcheck.app.ml.SeetaFaceEngine
import com.smartcheck.sdk.face.FaceSdk
import com.smartcheck.sdk.HandDetector
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import kotlin.math.ceil
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * 主 ViewModel - 晨检状态机核心逻辑
 * 
 * 注意：此 ViewModel 仍包含大量业务逻辑，待逐步迁移到 UseCase 层
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val faceEngine: FaceEngine,
    private val hardwareRepository: HardwareRepository,
    private val voiceService: IVoiceService,
    private val recordUploadReporter: RecordUploadReporter,
    private val settingsRepository: SettingsRepository,
    private val userRepository: IUserRepository,
    private val recordRepository: IRecordRepository,
    private val morningCheckUseCase: MorningCheckUseCase,
    private val imageStorageUseCase: ImageStorageUseCase,
    private val pendingUploadManager: PendingUploadManager,
    private val appScope: CoroutineScope
) : ViewModel() {

    companion object {
        private const val REQUIRED_HAND_COUNT = 2
        private const val REQUIRED_HAND_STABLE_FRAMES = 2
        private const val HAND_SIDE_UNKNOWN_EPS = 0.02f
        private const val HAND_STAGE_COOLDOWN_MS = 1200L
        private const val HAND_BOX_IOU_MAX = 0.55f
        private const val OVERWRITE_WINDOW_MS = 2 * 60 * 60 * 1000L
    }

    private enum class HandSide {
        PALM,
        BACK,
        UNKNOWN,
    }

    private enum class LightStage {
        FACE,
        HAND,
        OFF,
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()
    
    // 手部检测结果数据流 (用于可视化)
    private val _handDetectionState = MutableStateFlow<List<com.smartcheck.sdk.HandInfo>>(emptyList())
    val handDetectionState: StateFlow<List<com.smartcheck.sdk.HandInfo>> = _handDetectionState.asStateFlow()

    private val _isHandDetecting = MutableStateFlow(false)
    val isHandDetecting: StateFlow<Boolean> = _isHandDetecting.asStateFlow()

    private val _faceDetectionBoxes = MutableStateFlow<List<Rect>>(emptyList())
    val faceDetectionBoxes: StateFlow<List<Rect>> = _faceDetectionBoxes.asStateFlow()

    data class PerfMetrics(
        val faceDuration: Duration = Duration.ZERO,
        val handDuration: Duration = Duration.ZERO,
        val tempDuration: Duration = Duration.ZERO
    )

    private val _perfMetrics = MutableStateFlow(PerfMetrics())
    val perfMetrics: StateFlow<PerfMetrics> = _perfMetrics.asStateFlow()
    
    private var tempMeasureJob: Job? = null
    private var resetJob: Job? = null

    private var handDetectionJob: Job? = null
    private val handDetectionSeq = AtomicInteger(0)

    private val isHandStepProcessing = AtomicBoolean(false)
    private var handOkFrames = 0
    private var handStepStartAt = 0L
    private var handCooldownJob: Job? = null
    private var autoSubmitJob: Job? = null
    private var handFrameMirrored: Boolean = false
    private var isRetaking: Boolean = false

    private var currentFacePath: String? = null
    private var currentPalmPath: String? = null
    private var currentBackPath: String? = null
    private var currentFaceBitmap: Bitmap? = null
    private var currentPalmBitmap: Bitmap? = null
    private var currentBackBitmap: Bitmap? = null
    private var lastFaceFrameAt: Long = 0L
    private var lastFaceDetectAt: Long = 0L
    private var faceDetectJob: Job? = null
    private var faceSaveJob: Job? = null
    private var palmSaveJob: Job? = null
    private var backSaveJob: Job? = null
    
    // 人脸跟踪状态
    private var lastTrackingId: Int = -1
    private var stableFramesCount: Int = 0
    private val requiredStableFrames: Int = 3 // 连续3帧稳定才识别
    private var lastRecognizedUserId: Long? = null // 上次识别成功的用户ID，避免重复识别
    private var lastUnknownTrackingId: Int = -1
    private var lastUnknownVoiceAt: Long = 0L
    private var currentLightStage: LightStage = LightStage.OFF

    private val minFreeBytes = 200L * 1024L * 1024L
    private val faceGuideText = "请将人脸对准摄像头"

    private val isRockchip: Boolean = run {
        val hardware = Build.HARDWARE.lowercase()
        val board = Build.BOARD.lowercase()
        val product = Build.PRODUCT.lowercase()
        hardware.contains("rk") || board.contains("rk") || product.contains("rk")
    }
    
    init {
        Timber.tag("MainViewModel").d("MainViewModel initialized")
        hardwareRepository.init()
        turnAllLightsOff(force = true)
        voiceService.setEnabled(settingsRepository.isVoiceEnabled())
        announceFaceGuide(force = true)
        viewModelScope.launch {
            settingsRepository.voiceEnabled.collect { enabled ->
                voiceService.setEnabled(enabled)
            }
        }
    }
    
    /**
     * 状态转换辅助函数，自动记录日志
     */
    private fun transitionTo(newState: CheckState, update: (UiState) -> UiState = { it.copy(state = newState) }) {
        val oldState = _uiState.value.state
        if (oldState != newState) {
            MorningCheckLogger.logStateTransition(oldState, newState)
        }
        _uiState.update(update)
    }

    private fun announceFaceGuide(force: Boolean = false) {
        val current = _uiState.value
        val shouldResetUi =
            current.message != faceGuideText ||
                current.currentUserId != null ||
                current.currentUserName.isNotBlank() ||
                current.faceConfidence != 0f

        if (shouldResetUi) {
            _uiState.update {
                it.copy(
                    message = faceGuideText,
                    currentUserId = null,
                    currentUserName = "",
                    faceConfidence = 0f
                )
            }
        }

        if (force || shouldResetUi) {
            voiceService.speak(faceGuideText)
        }
    }

    fun processFrame(frame: Bitmap) {
        val now = System.currentTimeMillis()
        if (_uiState.value.state == CheckState.IDLE) {
            if (now - lastFaceFrameAt >= 300L) {
                lastFaceFrameAt = now
                currentFaceBitmap.safeRecycle()
                currentFaceBitmap = frame.copy(Bitmap.Config.ARGB_8888, false)
            }
        } else {
            if (faceDetectJob?.isActive == true) {
                faceDetectJob?.cancel()
            }
        }
        when (_uiState.value.state) {
            CheckState.IDLE -> processCameraFrame(frame)
            CheckState.HAND_PALM_CHECKING, CheckState.HAND_BACK_CHECKING -> processHandStepFrame(frame)
            else -> Unit
        }
    }

    fun updateHandFrameMirror(mirrored: Boolean) {
        handFrameMirrored = mirrored
    }
    
    /**
     * 处理实时手部检测帧
     */
    fun processHandDetection(frame: Bitmap) {
        // Real-time frames come in frequently. If we cancel and restart on each frame,
        // detection may never finish and UI will stay in "detecting" state forever.
        // Instead, drop frames while a detection is in-flight.
        if (handDetectionJob?.isActive == true) return

        handDetectionJob = viewModelScope.launch {
            _isHandDetecting.value = true
            try {
                val startAt = SystemClock.elapsedRealtime()
                val results = withContext(Dispatchers.Default) {
                    HandDetector.detect(frame)
                }
                val elapsed = SystemClock.elapsedRealtime() - startAt
                _perfMetrics.update { it.copy(handDuration = elapsed.milliseconds) }
                _handDetectionState.value = results
            } catch (e: CancellationException) {
                // Normal control path (e.g. screen leaving).
            } catch (e: Exception) {
                Timber.tag("MainViewModel").e(e, "Hand detection error")
            } finally {
                _isHandDetecting.value = false
            }
        }
    }

    fun clearHandDetection() {
        handDetectionJob?.cancel()
        _handDetectionState.value = emptyList()
        _isHandDetecting.value = false
    }
    
    /**
     * 处理摄像头帧（人脸识别）
     */
    fun processCameraFrame(frame: Bitmap) {
        if (_uiState.value.state != CheckState.IDLE) {
            _faceDetectionBoxes.value = emptyList()
            return
        }
        val now = SystemClock.elapsedRealtime()
        if (now - lastFaceDetectAt < 150L) {
            frame.safeRecycle()
            return
        }
        if (faceDetectJob?.isActive == true) {
            frame.safeRecycle()
            return
        }
        lastFaceDetectAt = now

        faceDetectJob = viewModelScope.launch {
            val safeBitmap = frame
            try {
                // 1. 使用跟踪获取人脸位置
                val trackedFaces = withContext(Dispatchers.Default) {
                    FaceSdk.track(safeBitmap)
                }
                
                // 2. 更新框（即使没识别也画框）
                if (trackedFaces.isNotEmpty()) {
                    val bestFace = trackedFaces.first()
                    Timber.d("[人脸跟踪] 框位置: left=${bestFace.box.left}, top=${bestFace.box.top}, right=${bestFace.box.right}, bottom=${bestFace.box.bottom}")
                    _faceDetectionBoxes.value = listOf(
                        Rect(
                            bestFace.box.left.toInt(),
                            bestFace.box.top.toInt(),
                            bestFace.box.right.toInt(),
                            bestFace.box.bottom.toInt()
                        )
                    )
                    
                    // 3. 跟踪逻辑：判断是否是同一个人
                    val currentTrackingId = bestFace.id
                    
                    if (currentTrackingId == lastTrackingId) {
                        // 同一跟踪ID，增加稳定计数
                        stableFramesCount++
                        Timber.d("[人脸跟踪] 跟踪ID=$currentTrackingId, 稳定帧数=$stableFramesCount/$requiredStableFrames")
                        
                        // 4. 连续稳定且未识别过此人脸时才识别
                        if (stableFramesCount >= requiredStableFrames && 
                            lastRecognizedUserId == null) {
                            // 每次请求人脸识别前都开人脸灯。
                            requestFaceRecognitionLight(force = false)

                            // 进行识别
                            val result = withContext(NonCancellable) {
                                faceEngine.detectAndRecognize(safeBitmap)
                            }
                            
                            if (result != null && result.userId != null) {
                                lastRecognizedUserId = result.userId
                                val userName = result.userName?.takeIf { it.isNotBlank() } ?: "未知用户"
                                onFaceRecognized(result.userId, userName, result.confidence)
                            } else if (result != null) {
                                _uiState.update {
                                    it.copy(
                                        currentUserId = null,
                                        currentUserName = "陌生人",
                                        faceConfidence = result.confidence
                                    )
                                }
                                val currentNow = SystemClock.elapsedRealtime()
                                if (currentTrackingId != lastUnknownTrackingId || currentNow - lastUnknownVoiceAt > 3000L) {
                                    voiceService.speak("人脸未录入")
                                    lastUnknownTrackingId = currentTrackingId
                                    lastUnknownVoiceAt = currentNow
                                }
                            }
                        }
                    } else {
                        // 换人，重置跟踪状态
                        if (lastTrackingId != -1) {
                            Timber.d("[人脸跟踪] 换人: $lastTrackingId -> $currentTrackingId")
                        }
                        lastTrackingId = currentTrackingId
                        stableFramesCount = 1
                        lastRecognizedUserId = null
                        announceFaceGuide()
                    }
                } else {
                    // 没检测到人脸，重置跟踪状态
                    _faceDetectionBoxes.value = emptyList()
                    if (lastTrackingId != -1) {
                        Timber.d("[人脸跟踪] 丢失目标，重置跟踪")
                    }
                    lastTrackingId = -1
                    stableFramesCount = 0
                    lastRecognizedUserId = null
                    lastUnknownTrackingId = -1
                    announceFaceGuide()
                }
                
            } catch (e: CancellationException) {
                // Normal control flow
            } catch (e: Exception) {
                Timber.tag("MainViewModel").e(e, "Face tracking error")
                _faceDetectionBoxes.value = emptyList()
            } finally {
                frame.safeRecycle()
            }
        }
    }
    
    /**
     * 人脸识别成功回调
     */
    private fun onFaceRecognized(userId: Long, userName: String, confidence: Float) {
        if (_uiState.value.state != CheckState.IDLE) return
        Timber.tag("MainViewModel").d("Face recognized: $userName (confidence: $confidence)")

        // 人脸识别成功后再灭灯，避免识别请求过程中闪烁。
        finishFaceRecognitionLight()

        viewModelScope.launch {
            proceedAfterFaceRecognized(userId, userName, confidence)
        }
    }

    private suspend fun proceedAfterFaceRecognized(userId: Long, userName: String, confidence: Float) {
        val startTime = System.currentTimeMillis()
        val result = morningCheckUseCase.onFaceRecognized(userId, userName, confidence)

        _uiState.update {
            it.copy(
                state = if (result.isAllowedToContinue) CheckState.FACE_PASS else CheckState.HEALTH_CERT_EXPIRED,
                currentUserId = result.userId,
                currentUserName = result.userName,
                healthCertEndDate = result.healthCertEndDate,
                healthCertDaysRemaining = result.healthCertDaysRemaining,
                faceConfidence = result.faceConfidence,
                message = result.message,
                faceImagePath = result.userFaceImagePath,
                showDuplicateCheckDialog = false
            )
        }

        PerformanceMetrics.recordDuration("face_recognition", System.currentTimeMillis() - startTime)
        UserActionTracker.track(ActionType.FACE_RECOGNIZED, "MainScreen", "userId=$userId, name=$userName")

        if (!result.isAllowedToContinue) {
            hardwareRepository.beep("error")
            turnAllLightsOff()
            scheduleReset(5000)
            return
        }

        val currentUser = userRepository.getUserById(userId).getOrNull()
        val employeeId = currentUser?.employeeId?.trim().orEmpty()

        val latestByEmployee = if (employeeId.isNotBlank()) {
            val resultByEmployee = recordRepository.getTodayRecordByEmployeeId(employeeId)
            if (resultByEmployee.isFailure) {
                Timber.tag("MainViewModel").w(
                    resultByEmployee.exceptionOrNull(),
                    "Failed to check today's record by employeeId"
                )
            }
            resultByEmployee.getOrNull()
        } else {
            null
        }

        val latestByUserIdResult = recordRepository.getTodayRecordByUser(userId)
        if (latestByUserIdResult.isFailure) {
            Timber.tag("MainViewModel").w(
                latestByUserIdResult.exceptionOrNull(),
                "Failed to check today's record by userId"
            )
        }
        val latestByUserId = latestByUserIdResult.getOrNull()

        val latestTodayRecord = listOfNotNull(latestByEmployee, latestByUserId)
            .maxByOrNull { it.checkTime }

        val now = System.currentTimeMillis()
        val withinOverwriteWindow = latestTodayRecord != null &&
            now - latestTodayRecord.checkTime <= OVERWRITE_WINDOW_MS
        if (withinOverwriteWindow) {
            morningCheckUseCase.speakAlreadyCheckedToday()
            _uiState.update {
                it.copy(
                    showDuplicateCheckDialog = true,
                    message = "今日已晨检，是否继续晨检？"
                )
            }
            return
        }

        morningCheckUseCase.speakSuccess()
        continueMorningCheckFlow()
    }

    fun continueDuplicateMorningCheck() {
        val state = _uiState.value
        if (!state.showDuplicateCheckDialog || state.state != CheckState.FACE_PASS) return

        _uiState.update {
            it.copy(
                showDuplicateCheckDialog = false,
                message = "准备开始晨检..."
            )
        }

        viewModelScope.launch {
            continueMorningCheckFlow()
        }
    }

    fun cancelDuplicateMorningCheck() {
        if (!_uiState.value.showDuplicateCheckDialog) return
        reset()
    }

    private suspend fun continueMorningCheckFlow() {
        if (_uiState.value.state == CheckState.IDLE) return

        faceSaveJob?.cancel()
        faceSaveJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val bitmap = currentFaceBitmap ?: return@launch
                val saveResult = imageStorageUseCase.saveFaceImage(bitmap)
                saveResult.onSuccess { name ->
                    currentFacePath = name
                    _uiState.update { it.copy(faceImagePath = name) }
                }.onFailure {
                    _uiState.update { it.copy(message = "照片保存失败") }
                }
                bitmap.recycle()
                currentFaceBitmap = null
            } catch (e: Exception) {
                Timber.tag("MainViewModel").e(e, "Failed to save face snapshot")
            }
        }

        hardwareRepository.beep("success")
        delay(1000)
        startTemperatureMeasure()
    }
    
    /**
     * 开始测温
     */
    private fun startTemperatureMeasure() {
        Timber.tag("MainViewModel").d("Starting temperature measurement")
        
        _uiState.update {
            it.copy(
                state = CheckState.TEMP_MEASURING,
                message = "正在测温，请稍候..."
            )
        }
        
        voiceService.speak("正在测温")
        
        tempMeasureJob?.cancel()
        tempMeasureJob = viewModelScope.launch {
            val result = morningCheckUseCase.measureTemperatureFlow(3)
            result.onSuccess { tempResult ->
                _uiState.update { it.copy(currentTemp = tempResult.temperature) }
                if (tempResult.isNormal) {
                    onTemperatureNormal(tempResult.temperature)
                } else {
                    onTemperatureAbnormal(tempResult.temperature)
                }
            }.onFailure {
                _uiState.update { it.copy(message = "测温失败") }
                turnAllLightsOff()
                scheduleReset(3000)
            }
        }
    }
    
    /**
     * 体温正常
     */
    private fun onTemperatureNormal(temp: Float) {
        Timber.tag("MainViewModel").d("Temperature normal: $temp")

        _uiState.update {
            it.copy(
                state = CheckState.TEMP_MEASURING,
                message = "体温正常，准备手部检测"
            )
        }
        hardwareRepository.beep("success")
        morningCheckUseCase.speakTemperatureNormal()
        startHandPalmCheck()
    }
    
    /**
     * 体温异常
     */
    private fun onTemperatureAbnormal(temp: Float) {
        Timber.tag("MainViewModel").w("Temperature abnormal: $temp")
        
        _uiState.update {
            it.copy(
                state = CheckState.TEMP_FAIL,
                message = "体温异常：${String.format("%.1f", temp)}°C，请复测"
            )
        }
        
        hardwareRepository.beep("error")
        turnAllLightsOff()
        morningCheckUseCase.speakTemperatureAbnormal(temp)
        
        saveCheckRecord(isPassed = false, isTempNormal = false, isHandNormal = false)
        
        scheduleReset(5000)
    }
    
    /**
     * 执行手部检测
     */
    private fun performHandCheck() {
        Timber.tag("MainViewModel").d("Performing hand check")
        startHandPalmCheck()
    }
    
    /**
     * 手检通过 - 全正常情况，等待用户确认后保存记录
     */
    private fun onHandCheckPass() {
        Timber.tag("MainViewModel").d("Hand check passed - waiting for confirmation")

        _handDetectionState.value = emptyList()
        
        hardwareRepository.beep("success")
        morningCheckUseCase.speakAllPass()

        _uiState.update {
            it.copy(
                state = CheckState.ALL_PASS,
                message = "晨检通过！",
                symptomFlags = ""
            )
        }
        
        UserActionTracker.track(ActionType.HAND_CHECK_COMPLETED, "MainScreen", "result=pass")
    }
    
    /**
     * 手检失败
     */
    private fun onHandCheckFail(issues: List<String>) {
        Timber.tag("MainViewModel").w("Hand check failed: $issues")
        
        _uiState.update {
            it.copy(
                state = CheckState.HAND_FAIL,
                message = "手部检测不合格：${issues.joinToString(", ")}",
                handDetectionResults = issues,
                handHasIssue = true
            )
        }
        
        hardwareRepository.beep("error")
        morningCheckUseCase.speakHandCheckFail()
    }

    fun submitSymptoms(symptoms: List<String>) {
        if (_uiState.value.state != CheckState.SYMPTOM_CHECKING) return

        // 暂存症状到 UiState，供 finalizeCheckRecord 写入记录
        _uiState.update { it.copy(submittedSymptoms = symptoms) }

        val result = morningCheckUseCase.processSymptomSubmission(symptoms)
        if (result.isAllPass) {
            onAllPass(remark = "无")
        } else if (result.hasFever) {
            onFeverBlocked(symptoms)
        } else {
            onSymptomFail(symptoms)
        }
    }

    private fun onAllPass(remark: String) {
        _uiState.update {
            it.copy(
                state = CheckState.ALL_PASS,
                message = "晨检通过！",
                symptomFlags = remark,
                autoSubmitRemainingSec = null
            )
        }

        hardwareRepository.beep("success")
        turnAllLightsOff()
        morningCheckUseCase.speakAllPass()
        hardwareRepository.openDoor()
    }

    @Suppress("UNUSED_PARAMETER")
    fun confirmHandFront(issues: List<String>) = Unit

    @Suppress("UNUSED_PARAMETER")
    fun confirmHandBack(issues: List<String>) = Unit

    private fun onSymptomFail(symptoms: List<String>) {
        val summary = symptoms.joinToString(", ")
        _uiState.update {
            it.copy(
                state = CheckState.SYMPTOM_FAIL,
                message = "有不适症状：$summary",
                symptomFlags = summary
            )
        }

        hardwareRepository.beep("error")
        turnAllLightsOff()
        morningCheckUseCase.speakSymptomFail()

        finalizeCheckRecord()
    }

    private fun onFeverBlocked(symptoms: List<String>) {
        val summary = symptoms.joinToString(", ")
        _uiState.update {
            it.copy(
                state = CheckState.SYMPTOM_FAIL,
                message = "有发烧症状，禁止上岗",
                symptomFlags = summary
            )
        }

        hardwareRepository.beep("error")
        turnAllLightsOff()
        // 语音已在 processSymptomSubmission 中播报"有发烧症状，禁止上岗"

        finalizeCheckRecord(remark = "发烧症状，禁止上岗")
    }

    fun finalizeCheckRecord(remark: String = "") {
        val state = _uiState.value
        if (state.isSubmitting || state.isRecordFinalized) return
        if (state.currentUserId == null) return
        if (state.state == CheckState.IDLE) return

        // 允许在任何状态下提交（只要有手心手背照片）
        // 根据检查结果决定是否通过
        // 防御性校验：必须确实完成过手心+手背的有效检测，才允许判为通过（避免空结果被当作正常）
        val isPassed = state.state == CheckState.ALL_PASS && state.palmChecked && state.backChecked
        // 体温：只看体温阶段是否异常，手部异常不影响体温判定
        val isTempNormal = state.state != CheckState.TEMP_FAIL

        _uiState.update { it.copy(isSubmitting = true) }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                faceSaveJob?.join()
                palmSaveJob?.join()
                backSaveJob?.join()

                val userResult = userRepository.getUserById(state.currentUserId)
                val user = userResult.getOrNull()

                val healthCertStatus = when {
                    state.healthCertDaysRemaining == null -> HealthCertStatus.VALID
                    state.healthCertDaysRemaining < 0 -> HealthCertStatus.EXPIRED
                    state.healthCertDaysRemaining < 7 -> HealthCertStatus.EXPIRING_SOON
                    else -> HealthCertStatus.VALID
                }

                // 手掌/手背独立判定
                val handCheckResult = HandCheckResult(
                    palmNormal = !state.palmHasIssue,
                    backNormal = !state.backHasIssue,
                    palmImagePath = currentPalmPath,
                    backImagePath = currentBackPath
                )

                val actualUserName = state.currentUserName.takeIf { it.isNotBlank() }
                    ?: user?.name?.takeIf { it.isNotBlank() }
                    ?: "未知用户"
                Timber.tag("MainViewModel").d("saveRecord userName: state='${state.currentUserName}', db='${user?.name}', final='$actualUserName'")

                // 解析用户提交的症状到 SymptomType 枚举
                val symptoms = state.submittedSymptoms.mapNotNull { parseSymptomType(it) }

                val result = morningCheckUseCase.saveRecord(
                    userId = state.currentUserId,
                    userName = actualUserName,
                    employeeId = user?.employeeId?.trim().orEmpty(),
                    temperature = state.currentTemp,
                    isTempNormal = isTempNormal,
                    handCheckResult = handCheckResult,
                    symptoms = symptoms,
                    healthCertStatus = healthCertStatus,
                    faceImagePath = currentFacePath,
                    palmImagePath = currentPalmPath,
                    backImagePath = currentBackPath
                )

                result.onSuccess { savedRecord ->
                    val finalRecord = if (remark.isNotBlank()) savedRecord.copy(remark = remark) else savedRecord
                    // 如果有 remark，更新数据库中的记录
                    if (remark.isNotBlank()) {
                        recordRepository.saveRecord(finalRecord)
                    }
                    Timber.tag("MainViewModel").d("Record saved: $finalRecord")
                    runCatching {
                        recordUploadReporter.upload(finalRecord.toEntity())
                    }.onFailure { e ->
                        Timber.tag("MainViewModel").e(e, "Failed to upload record")
                    }

                    // 入队等待上传（支持离线队列）
                    pendingUploadManager.enqueue(finalRecord.id)
                }.onFailure { e ->
                    Timber.tag("MainViewModel").e(e, "Failed to save record")
                }

                _uiState.update { it.copy(isRecordFinalized = true) }
                scheduleReset(if (isPassed) 3000 else 5000)
            } catch (e: Exception) {
                Timber.tag("MainViewModel").e(e, "Failed to finalize record")
            } finally {
                _uiState.update { it.copy(isSubmitting = false) }
            }
        }
    }

    fun retakeFace() {
        reset()
    }

    fun retakeHandPalm() {
        val state = _uiState.value
        if (state.currentUserId == null) return
        if (state.isSubmitting) return
        isRetaking = true
        handOkFrames = 0
        handStepStartAt = System.currentTimeMillis()
        handCooldownJob?.cancel()
        // 只清除手掌相关状态，保留手背
        currentPalmPath = null
        currentPalmBitmap?.safeRecycle()
        currentPalmBitmap = null
        _uiState.update {
            it.copy(
                state = CheckState.HAND_PALM_CHECKING,
                message = "请同时伸出两只手心",
                handPalmPath = null,
                handPalmInfos = emptyList(),
                handPalmFrameWidth = null,
                handPalmFrameHeight = null,
                palmHasIssue = false,
                palmChecked = false,
                handHasIssue = it.backHasIssue,
                handDetectionResults = if (it.backHasIssue) it.handDetectionResults else emptyList()
            )
        }
        voiceService.speak("请同时伸出两只手心")
    }

    fun retakeHandBack() {
        val state = _uiState.value
        if (state.currentUserId == null) return
        if (state.isSubmitting) return
        isRetaking = true
        handOkFrames = 0
        handStepStartAt = System.currentTimeMillis()
        handCooldownJob?.cancel()
        // 只清除手背相关状态，保留手掌
        currentBackPath = null
        currentBackBitmap?.safeRecycle()
        currentBackBitmap = null
        _uiState.update {
            it.copy(
                state = CheckState.HAND_BACK_CHECKING,
                message = "请同时伸出两只手背",
                handBackPath = null,
                handBackInfos = emptyList(),
                handBackFrameWidth = null,
                handBackFrameHeight = null,
                backHasIssue = false,
                backChecked = false,
                handHasIssue = it.palmHasIssue,
                handDetectionResults = if (it.palmHasIssue) it.handDetectionResults else emptyList()
            )
        }
        voiceService.speak("请同时伸出两只手背")
    }

    private fun startHandPalmCheck() {
        handOkFrames = 0
        handStepStartAt = System.currentTimeMillis()
        _handDetectionState.value = emptyList()
        currentPalmPath = null
        currentBackPath = null
        handCooldownJob?.cancel()
        _faceDetectionBoxes.value = emptyList()

        ensureHandLightOn()

        _uiState.update {
            it.copy(
                state = CheckState.HAND_PALM_CHECKING,
                message = "请同时伸出两只手心",
                handPalmPath = null,
                handBackPath = null,
                handPalmInfos = emptyList(),
                handBackInfos = emptyList(),
                palmChecked = false,
                backChecked = false
            )
        }

        voiceService.speak("请同时伸出两只手心")
    }

    private fun startHandBackCheck() {
        handOkFrames = 0
        handStepStartAt = System.currentTimeMillis()
        _handDetectionState.value = emptyList()
        handCooldownJob?.cancel()

        ensureHandLightOn()

        _uiState.update {
            it.copy(
                state = CheckState.HAND_BACK_CHECKING,
                message = "请同时伸出两只手背"
            )
        }

        voiceService.speak("请同时伸出两只手背")
    }

    private fun processHandStepFrame(frame: Bitmap) {
        val state = _uiState.value.state
        if (state != CheckState.HAND_PALM_CHECKING && state != CheckState.HAND_BACK_CHECKING) return
        if (handCooldownJob?.isActive == true) return
        if (!isHandStepProcessing.compareAndSet(false, true)) return

        viewModelScope.launch {
            try {
                val now = System.currentTimeMillis()
                if (!isRockchip) {
                    if (now - handStepStartAt >= 500L) {
                        if (state == CheckState.HAND_PALM_CHECKING) {
                            captureHandPalmAndCooldown(frame, emptyList())
                        } else {
                            captureHandBackAndFinish(frame, emptyList())
                        }
                    }
                    return@launch
                }

                val results = withContext(Dispatchers.Default) {
                    HandDetector.detect(frame)
                }
                _handDetectionState.value = results

                val stageHands = selectDistinctHands(results)

                if (stageHands.size < REQUIRED_HAND_COUNT) {
                    handOkFrames = 0
                    _uiState.update {
                        it.copy(
                            message = if (state == CheckState.HAND_PALM_CHECKING) {
                                "请同时伸出两只手心"
                            } else {
                                "请同时伸出两只手背"
                            }
                        )
                    }
                    return@launch
                }

                val expectedSide = if (state == CheckState.HAND_PALM_CHECKING) {
                    HandSide.PALM
                } else {
                    HandSide.BACK
                }
                if (!isExpectedHandSide(stageHands, expectedSide)) {
                    handOkFrames = 0
                    _uiState.update {
                        it.copy(
                            message = if (state == CheckState.HAND_PALM_CHECKING) {
                                "请同时伸出两只手心"
                            } else {
                                "请同时伸出两只手背"
                            }
                        )
                    }
                    return@launch
                }

                val hasForeignObject = results.any { it.hasForeignObject }
                // 使用阶段特定的异常标记，避免手掌/手背互相影响
                val stageHasIssue = when (state) {
                    CheckState.HAND_PALM_CHECKING -> _uiState.value.palmHasIssue
                    CheckState.HAND_BACK_CHECKING -> _uiState.value.backHasIssue
                    else -> false
                }
                val hasIssueSoFar = hasForeignObject || stageHasIssue
                if (hasIssueSoFar) {
                    val issues = if (hasForeignObject) {
                        results.filter { it.hasForeignObject }.map { it.label }.ifEmpty { results.map { it.label } }
                    } else {
                        _uiState.value.handDetectionResults
                    }
                    _uiState.update {
                        if (state == CheckState.HAND_PALM_CHECKING) {
                            it.copy(
                                handPalmInfos = stageHands,
                                handPalmFrameWidth = frame.width,
                                handPalmFrameHeight = frame.height,
                                palmHasIssue = true,
                                handHasIssue = true,
                                handDetectionResults = issues
                            )
                        } else {
                            it.copy(
                                handBackInfos = stageHands,
                                handBackFrameWidth = frame.width,
                                handBackFrameHeight = frame.height,
                                backHasIssue = true,
                                handHasIssue = true,
                                handDetectionResults = issues
                            )
                        }
                    }
                    if (state == CheckState.HAND_PALM_CHECKING) {
                        captureHandPalmAndCooldown(frame, stageHands, isIssue = true)
                    } else {
                        captureHandBackAndFinish(frame, stageHands, issues, isIssue = true)
                    }
                    return@launch
                }

                handOkFrames++
                if (handOkFrames >= REQUIRED_HAND_STABLE_FRAMES) {
                    if (state == CheckState.HAND_PALM_CHECKING) {
                        captureHandPalmAndCooldown(frame, stageHands)
                    } else {
                        val issues = _uiState.value.handDetectionResults
                        // 只看手背自身的异常标记，不受手掌影响
                        val hasPriorIssue = _uiState.value.backHasIssue
                        captureHandBackAndFinish(frame, stageHands, issues, isIssue = hasPriorIssue)
                    }
                }
            } catch (e: Exception) {
                Timber.tag("MainViewModel").e(e, "Hand step check error")
                onHandCheckFail(listOf("检测异常"))
            } finally {
                isHandStepProcessing.set(false)
                frame.safeRecycle()
            }
        }
    }
    
    /**
     * 保存晨检记录
     */
    private fun saveCheckRecord(
        isPassed: Boolean,
        isTempNormal: Boolean,
        isHandNormal: Boolean,
        remark: String = ""
    ) {
        val state = _uiState.value
        if (state.currentUserId == null) return

        _uiState.update { it.copy(isSubmitting = true) }

        val startTime = System.currentTimeMillis()

        viewModelScope.launch(Dispatchers.IO) {
            try {
                faceSaveJob?.join()
                palmSaveJob?.join()
                backSaveJob?.join()
                val userResult = userRepository.getUserById(state.currentUserId)
                val user = userResult.getOrNull()

                val healthCertStatus = when {
                    state.healthCertDaysRemaining == null -> HealthCertStatus.VALID
                    state.healthCertDaysRemaining < 0 -> HealthCertStatus.EXPIRED
                    state.healthCertDaysRemaining < 7 -> HealthCertStatus.EXPIRING_SOON
                    else -> HealthCertStatus.VALID
                }

                val actualUserName = state.currentUserName.takeIf { it.isNotBlank() }
                    ?: user?.name?.takeIf { it.isNotBlank() }
                    ?: "未知用户"
                Timber.tag("MainViewModel").d("saveCheckRecord userName: state='${state.currentUserName}', db='${user?.name}', final='$actualUserName'")

                val record = Record(
                    userId = state.currentUserId,
                    userName = actualUserName,
                    employeeId = user?.employeeId?.trim().orEmpty(),
                    temperature = state.currentTemp,
                    isTempNormal = isTempNormal,
                    isHandNormal = isHandNormal,
                    isPassed = isPassed,
                    handStatus = when {
                        currentPalmPath == null && currentBackPath == null -> HandStatus.NOT_CHECKED
                        isHandNormal -> HandStatus.NORMAL
                        else -> HandStatus.ABNORMAL
                    },
                    healthCertStatus = healthCertStatus,
                    symptomFlags = emptyList(),
                    faceImagePath = currentFacePath,
                    handPalmPath = currentPalmPath,
                    handBackPath = currentBackPath,
                    remark = remark
                )
                val saveResult = recordRepository.saveRecord(record)
                val recordId = saveResult.getOrNull() ?: 0L
                val savedRecord = record.copy(id = recordId)
                Timber.tag("MainViewModel").d("Record saved: $savedRecord")

                val duration = System.currentTimeMillis() - startTime
                PerformanceMetrics.recordDuration("record_save", duration)
                UserActionTracker.track(
                    ActionType.RECORD_SUBMITTED,
                    "MainScreen",
                    "userId=${state.currentUserId}, passed=$isPassed",
                    durationMs = duration
                )

                try {
                    withContext(Dispatchers.IO) {
                        recordUploadReporter.upload(savedRecord.toEntity())
                    }
                } catch (e: Exception) {
                    Timber.tag("MainViewModel").e(e, "Failed to upload record")
                }

                // 入队等待上传（支持离线队列）
                pendingUploadManager.enqueue(savedRecord.id)
            } catch (e: Exception) {
                Timber.tag("MainViewModel").e(e, "Failed to save record")
            } finally {
                _uiState.update { it.copy(isSubmitting = false) }
            }
        }
    }

    /**
     * 将中文症状字符串映射到 SymptomType 枚举
     */
    private fun parseSymptomType(symptom: String): SymptomType? = when {
        symptom.contains("发烧") || symptom.contains("发热") -> SymptomType.FEVER
        symptom.contains("咳嗽") -> SymptomType.COUGH
        symptom.contains("头痛") -> SymptomType.HEADACHE
        symptom.contains("疲劳") || symptom.contains("乏力") -> SymptomType.FATIGUE
        symptom.contains("咽痛") || symptom.contains("喉咙") -> SymptomType.SORE_THROAT
        symptom.contains("腹泻") -> SymptomType.DIARRHEA
        else -> SymptomType.OTHER
    }

    /**
     * 定时重置状态机
     */
    private fun scheduleReset(delayMs: Long) {
        resetJob?.cancel()
        resetJob = viewModelScope.launch {
            delay(delayMs)
            reset()
        }
    }
    
    /**
     * 重置状态机到 IDLE
     */
    fun reset() {
        Timber.tag("MainViewModel").d("Resetting state machine")

        tempMeasureJob?.cancel()
        resetJob?.cancel()
        handCooldownJob?.cancel()
        autoSubmitJob?.cancel()
        turnAllLightsOff(force = true)
        
        _uiState.update {
            UiState(
                state = CheckState.IDLE,
                message = faceGuideText
            )
        }
        voiceService.speak(faceGuideText)

        _handDetectionState.value = emptyList()
        _faceDetectionBoxes.value = emptyList()
        handOkFrames = 0
        handStepStartAt = 0L
        lastFaceFrameAt = 0L
        isRetaking = false
        currentFacePath = null
        currentPalmPath = null
        currentBackPath = null
        
        // 重置人脸跟踪状态
        lastTrackingId = -1
        stableFramesCount = 0
        lastRecognizedUserId = null
        lastUnknownTrackingId = -1
        lastUnknownVoiceAt = 0L
        currentFaceBitmap.safeRecycle()
        currentPalmBitmap.safeRecycle()
        currentBackBitmap.safeRecycle()
        currentFaceBitmap = null
        currentPalmBitmap = null
        currentBackBitmap = null
        faceSaveJob?.cancel()
        palmSaveJob?.cancel()
        backSaveJob?.cancel()
        faceSaveJob = null
        palmSaveJob = null
        backSaveJob = null
    }

    private fun captureHandPalmAndCooldown(
        frame: Bitmap,
        infos: List<com.smartcheck.sdk.HandInfo>,
        isIssue: Boolean = false
    ) {
        if (isIssue) {
            hardwareRepository.beep("error")
            _uiState.update {
                it.copy(
                    state = CheckState.HAND_FAIL,
                    message = "手部检测不合格",
                    palmHasIssue = true,
                    handHasIssue = true
                )
            }
        } else if (infos.isNotEmpty()) {
            // 仅当本帧确实检测到手才视为有效：空结果不代表「正常」，不得清除标记/置完成位
            hardwareRepository.beep("success")
            // 检测通过，清除手掌异常标记并标记本侧已完成有效检测
            _uiState.update {
                it.copy(
                    palmHasIssue = false,
                    palmChecked = true,
                    handHasIssue = it.backHasIssue
                )
            }
        }
        if (currentPalmBitmap == null) {
            val snapshot = frame.copy(Bitmap.Config.ARGB_8888, false)
            currentPalmBitmap = snapshot
            _uiState.update {
                it.copy(
                    handPalmInfos = infos,
                    handPalmFrameWidth = frame.width,
                    handPalmFrameHeight = frame.height,
                    handCapturePulse = true
                )
            }
            viewModelScope.launch {
                delay(180)
                _uiState.update { it.copy(handCapturePulse = false) }
            }
            palmSaveJob?.cancel()
            palmSaveJob = viewModelScope.launch(Dispatchers.IO) {
                try {
                    val bitmap = currentPalmBitmap ?: return@launch
                    val result = imageStorageUseCase.savePalmImage(bitmap)
                    result.onSuccess { name ->
                        currentPalmPath = name
                        _uiState.update { it.copy(handPalmPath = name) }
                    }.onFailure {
                        _uiState.update { it.copy(message = "照片保存失败") }
                    }
                    bitmap.recycle()
                    currentPalmBitmap = null
                } catch (e: Exception) {
                    Timber.tag("MainViewModel").e(e, "Failed to save hand palm snapshot")
                }
            }
        }

        handCooldownJob?.cancel()
        handCooldownJob = viewModelScope.launch {
            delay(HAND_STAGE_COOLDOWN_MS)
            if (isRetaking) {
                // 复检：评估整体状态
                isRetaking = false
                evaluateHandStateAfterRetake()
            } else {
                startHandBackCheck()
            }
        }
    }

    private fun captureHandBackAndFinish(
        frame: Bitmap,
        infos: List<com.smartcheck.sdk.HandInfo>,
        issues: List<String> = emptyList(),
        isIssue: Boolean = false
    ) {
        if (isIssue) {
            hardwareRepository.beep("error")
            _uiState.update {
                it.copy(
                    state = CheckState.HAND_FAIL,
                    message = "手部检测不合格",
                    backHasIssue = true,
                    handHasIssue = true,
                    handDetectionResults = if (issues.isNotEmpty()) issues else it.handDetectionResults,
                )
            }
        } else if (infos.isNotEmpty()) {
            // 仅当本帧确实检测到手才视为有效：空结果不代表「正常」，不得清除标记/置完成位
            hardwareRepository.beep("success")
            // 检测通过，清除手背异常标记并标记本侧已完成有效检测
            _uiState.update {
                it.copy(
                    backHasIssue = false,
                    backChecked = true,
                    handHasIssue = it.palmHasIssue
                )
            }
        }
        if (currentBackBitmap == null) {
            val snapshot = frame.copy(Bitmap.Config.ARGB_8888, false)
            currentBackBitmap = snapshot
            _uiState.update {
                it.copy(
                    handBackInfos = infos,
                    handBackFrameWidth = frame.width,
                    handBackFrameHeight = frame.height,
                    handCapturePulse = true
                )
            }
            viewModelScope.launch {
                delay(180)
                _uiState.update { it.copy(handCapturePulse = false) }
            }
            backSaveJob?.cancel()
            backSaveJob = viewModelScope.launch(Dispatchers.IO) {
                try {
                    val bitmap = currentBackBitmap ?: return@launch
                    val result = imageStorageUseCase.saveBackImage(bitmap)
                    result.onSuccess { name ->
                        currentBackPath = name
                        _uiState.update { it.copy(handBackPath = name) }
                    }.onFailure {
                        _uiState.update { it.copy(message = "照片保存失败") }
                    }
                    bitmap.recycle()
                    currentBackBitmap = null
                } catch (e: Exception) {
                    Timber.tag("MainViewModel").e(e, "Failed to save hand back snapshot")
                }
            }
        }
        if (isRetaking) {
            // 复检：评估整体状态
            isRetaking = false
            evaluateHandStateAfterRetake()
        } else if (isIssue) {
            _uiState.update {
                it.copy(
                    state = CheckState.SYMPTOM_CHECKING,
                    message = "手部检测异常，请人工复核",
                    autoSubmitRemainingSec = null
                )
            }
        } else {
            startAutoSubmitCountdown()
        }
    }

    /**
     * 复检后评估手部整体状态
     * 手掌和手背都已完成有效检测且无异常 → 通过；任一有异常 → SYMPTOM_CHECKING；
     * 任一侧尚未完成有效检测 → 回到该侧继续检测（空结果不允许判通过）
     */
    private fun evaluateHandStateAfterRetake() {
        val state = _uiState.value
        // 完成度校验：任一侧未完成有效检测（含重拍打断流程、空结果）时，回到该侧重新检测
        if (!state.palmChecked) {
            resumeHandPalmCheck()
            return
        }
        if (!state.backChecked) {
            resumeHandBackCheck()
            return
        }

        val anyIssue = state.palmHasIssue || state.backHasIssue
        if (anyIssue) {
            _uiState.update {
                it.copy(
                    state = CheckState.SYMPTOM_CHECKING,
                    message = "手部检测异常，请人工复核",
                    handHasIssue = true,
                    autoSubmitRemainingSec = null
                )
            }
        } else {
            // 手掌手背都通过
            _handDetectionState.value = emptyList()
            hardwareRepository.beep("success")
            morningCheckUseCase.speakAllPass()
            _uiState.update {
                it.copy(
                    state = CheckState.ALL_PASS,
                    message = "晨检通过！",
                    handHasIssue = false,
                    symptomFlags = ""
                )
            }
            UserActionTracker.track(ActionType.HAND_CHECK_COMPLETED, "MainScreen", "result=pass")
        }
    }

    /**
     * 复检模式下回到手心检测（保留手背已完成状态，不重置 isRetaking）
     */
    private fun resumeHandPalmCheck() {
        isRetaking = true
        handOkFrames = 0
        handStepStartAt = System.currentTimeMillis()
        handCooldownJob?.cancel()
        ensureHandLightOn()
        _uiState.update {
            it.copy(
                state = CheckState.HAND_PALM_CHECKING,
                message = "请同时伸出两只手心"
            )
        }
        voiceService.speak("请同时伸出两只手心")
    }

    /**
     * 复检模式下回到手背检测（保留手心已完成状态，不重置 isRetaking）
     */
    private fun resumeHandBackCheck() {
        isRetaking = true
        handOkFrames = 0
        handStepStartAt = System.currentTimeMillis()
        handCooldownJob?.cancel()
        ensureHandLightOn()
        _uiState.update {
            it.copy(
                state = CheckState.HAND_BACK_CHECKING,
                message = "请同时伸出两只手背"
            )
        }
        voiceService.speak("请同时伸出两只手背")
    }

    private fun startAutoSubmitCountdown() {
        autoSubmitJob?.cancel()
        val totalSec = 3
        _uiState.update {
            it.copy(
                state = CheckState.AUTO_SUBMITTING,
                message = "即将自动提交",
                autoSubmitRemainingSec = totalSec,
                autoSubmitTotalSec = totalSec
            )
        }
        autoSubmitJob = viewModelScope.launch {
            for (sec in totalSec downTo 1) {
                _uiState.update { it.copy(autoSubmitRemainingSec = sec) }
                delay(1000)
            }
            autoSubmitJob = null
            onAllPass(remark = "无")
            finalizeCheckRecord()
        }
    }

    private fun calcRemainingDays(endAt: Long): Int {
        val now = System.currentTimeMillis()
        val diffMs = endAt - now
        return ceil(diffMs / (24f * 60f * 60f * 1000f)).toInt()
    }
    
    override fun onCleared() {
        super.onCleared()
        tempMeasureJob?.cancel()
        resetJob?.cancel()
        handCooldownJob?.cancel()
        autoSubmitJob?.cancel()
        turnAllLightsOff(force = true)
        hardwareRepository.release()
        Timber.tag("MainViewModel").d("MainViewModel cleared")
    }

    private fun ensureFaceLightOn(force: Boolean = false) {
        if (!force && currentLightStage == LightStage.FACE) return
        requestFaceRecognitionLight(force)
    }

    private fun ensureHandLightOn(force: Boolean = false) {
        if (!force && currentLightStage == LightStage.HAND) return
        val handOnOk = hardwareRepository.turnOnHandLight()
        if (handOnOk) {
            hardwareRepository.turnOffFaceLight()
        }
        currentLightStage = if (handOnOk) LightStage.HAND else LightStage.OFF
        if (!handOnOk) {
            Timber.tag("MainViewModel").w("Failed to switch to HAND light stage")
        }
    }

    private fun turnAllLightsOff(force: Boolean = false) {
        if (!force && currentLightStage == LightStage.OFF) return
        hardwareRepository.turnOffFaceLight()
        hardwareRepository.turnOffHandLight()
        hardwareRepository.turnOffAllLights()
        currentLightStage = LightStage.OFF
    }

    private fun requestFaceRecognitionLight(force: Boolean = false) {
        if (!force && currentLightStage == LightStage.FACE) return
        val faceOnOk = hardwareRepository.turnOnFaceLight()
        if (faceOnOk) {
            hardwareRepository.turnOffHandLight()
        }
        currentLightStage = if (faceOnOk) LightStage.FACE else LightStage.OFF
        if (!faceOnOk) {
            Timber.tag("MainViewModel").w("Failed to switch to FACE light stage before recognition request")
        }
    }

    private fun finishFaceRecognitionLight() {
        hardwareRepository.turnOffFaceLight()
        currentLightStage = LightStage.OFF
    }

    private fun isExpectedHandSide(
        infos: List<com.smartcheck.sdk.HandInfo>,
        expected: HandSide,
    ): Boolean {
        val observedSide = classifyHandSide(infos)
        return observedSide != HandSide.UNKNOWN && observedSide == expected
    }

    private fun classifyHandSide(infos: List<com.smartcheck.sdk.HandInfo>): HandSide {
        if (infos.size < REQUIRED_HAND_COUNT) return HandSide.UNKNOWN

        val twoHands = infos.sortedBy { (it.box.left + it.box.right) / 2f }

        if (twoHands.size < REQUIRED_HAND_COUNT) return HandSide.UNKNOWN

        val left = twoHands[0]
        val right = twoHands[1]
        if (left.keyPoints.size <= 20 || right.keyPoints.size <= 20) return HandSide.UNKNOWN

        // MediaPipe 常用几何法：使用 wrist(0), index_mcp(5), pinky_mcp(17)
        // 先逐手判断，再要求两只手方向一致（都手心或都手背）。
        val leftCross = palmCrossSign(left)
        val rightCross = palmCrossSign(right)

        // 左右手在同一姿态下叉积天然相反，按屏幕左右归一化后应同号。
        val leftNorm = leftCross
        val rightNorm = -rightCross

        var leftSide = when {
            leftNorm > HAND_SIDE_UNKNOWN_EPS -> HandSide.PALM
            leftNorm < -HAND_SIDE_UNKNOWN_EPS -> HandSide.BACK
            else -> HandSide.UNKNOWN
        }
        var rightSide = when {
            rightNorm > HAND_SIDE_UNKNOWN_EPS -> HandSide.PALM
            rightNorm < -HAND_SIDE_UNKNOWN_EPS -> HandSide.BACK
            else -> HandSide.UNKNOWN
        }

        if (handFrameMirrored) {
            leftSide = when (leftSide) {
                HandSide.PALM -> HandSide.BACK
                HandSide.BACK -> HandSide.PALM
                HandSide.UNKNOWN -> HandSide.UNKNOWN
            }
            rightSide = when (rightSide) {
                HandSide.PALM -> HandSide.BACK
                HandSide.BACK -> HandSide.PALM
                HandSide.UNKNOWN -> HandSide.UNKNOWN
            }
        }

        val side = when {
            leftSide == HandSide.PALM && rightSide == HandSide.PALM -> HandSide.PALM
            leftSide == HandSide.BACK && rightSide == HandSide.BACK -> HandSide.BACK
            else -> HandSide.UNKNOWN
        }

        if (side != HandSide.UNKNOWN) {
            Timber.tag("MainViewModel").d(
                "[HandSide-MP] leftCross=%.4f rightCross=%.4f leftNorm=%.4f rightNorm=%.4f leftSide=%s rightSide=%s mirrored=%s side=%s",
                leftCross,
                rightCross,
                leftNorm,
                rightNorm,
                leftSide.name,
                rightSide.name,
                handFrameMirrored,
                side.name
            )
        }

        return side
    }

    private fun selectDistinctHands(
        infos: List<com.smartcheck.sdk.HandInfo>
    ): List<com.smartcheck.sdk.HandInfo> {
        val sorted = infos.sortedByDescending { it.score }
        val selected = mutableListOf<com.smartcheck.sdk.HandInfo>()
        for (info in sorted) {
            val isDuplicate = selected.any { iou(it, info) > HAND_BOX_IOU_MAX }
            if (!isDuplicate) {
                selected += info
            }
            if (selected.size >= REQUIRED_HAND_COUNT) {
                break
            }
        }
        return selected.sortedBy { (it.box.left + it.box.right) / 2f }
    }

    private fun iou(a: com.smartcheck.sdk.HandInfo, b: com.smartcheck.sdk.HandInfo): Float {
        val left = kotlin.math.max(a.box.left, b.box.left)
        val top = kotlin.math.max(a.box.top, b.box.top)
        val right = kotlin.math.min(a.box.right, b.box.right)
        val bottom = kotlin.math.min(a.box.bottom, b.box.bottom)
        val interW = (right - left).coerceAtLeast(0f)
        val interH = (bottom - top).coerceAtLeast(0f)
        val inter = interW * interH
        val areaA = (a.box.right - a.box.left).coerceAtLeast(0f) * (a.box.bottom - a.box.top).coerceAtLeast(0f)
        val areaB = (b.box.right - b.box.left).coerceAtLeast(0f) * (b.box.bottom - b.box.top).coerceAtLeast(0f)
        val union = areaA + areaB - inter
        return if (union <= 0f) 0f else inter / union
    }

    private fun palmCrossSign(info: com.smartcheck.sdk.HandInfo): Float {
        val kp = info.keyPoints
        if (kp.size <= 17) return 0f

        val wrist = kp[0]
        val indexMcp = kp[5]
        val pinkyMcp = kp[17]

        val v1x = indexMcp.x - wrist.x
        val v1y = indexMcp.y - wrist.y
        val v2x = pinkyMcp.x - wrist.x
        val v2y = pinkyMcp.y - wrist.y

        val cross = v1x * v2y - v1y * v2x
        val boxW = (info.box.right - info.box.left).coerceAtLeast(1f)
        val boxH = (info.box.bottom - info.box.top).coerceAtLeast(1f)
        return cross / (boxW * boxH)
    }

    private fun Bitmap?.safeRecycle() {
        try {
            if (this != null && !isRecycled) {
                recycle()
            }
        } catch (e: Exception) {
            Timber.tag("MainViewModel").w(e, "Bitmap recycle failed")
        }
    }
}
