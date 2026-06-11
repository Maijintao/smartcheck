package com.smartcheck.app.ui.screens

import android.Manifest
import android.graphics.Bitmap
import androidx.camera.core.CameraSelector
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import timber.log.Timber
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.with
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.smartcheck.app.ui.components.DualCameraPreview
import com.smartcheck.app.ui.components.FaceOverlay
import com.smartcheck.app.ui.components.HandOverlay
import com.smartcheck.app.ui.theme.Dimens
import com.smartcheck.app.viewmodel.CheckState
import com.smartcheck.app.utils.FileUtil
import com.smartcheck.app.viewmodel.MainViewModel
import com.smartcheck.app.viewmodel.SettingsViewModel
import com.smartcheck.app.utils.UsbCameraHelper
import kotlinx.coroutines.delay
import android.widget.Toast
import com.smartcheck.sdk.ForeignObjectInfo
import com.smartcheck.sdk.HandInfo
import kotlin.math.max
import kotlin.math.min

@OptIn(ExperimentalPermissionsApi::class, ExperimentalAnimationApi::class)
@Composable
fun HomeScreen(
    onNavigateAdmin: (() -> Unit)? = null,
    onNavigateBackToDashboard: (() -> Unit)? = null,
    viewModel: MainViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val adminName by settingsViewModel.adminName.collectAsState()
    val adminAvatar by settingsViewModel.adminAvatar.collectAsState()
    val handInfos by viewModel.handDetectionState.collectAsState()
    val faceBoxes by viewModel.faceDetectionBoxes.collectAsState()
    val context = LocalContext.current

    var lastFrameWidth by remember { mutableIntStateOf(0) }
    var lastFrameHeight by remember { mutableIntStateOf(0) }
    var previewWidth by remember { mutableIntStateOf(0) }
    var previewHeight by remember { mutableIntStateOf(0) }
    var cameraLensFacing by remember { mutableIntStateOf(-1) }
    var lastFrameBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var lastFrameBitmapUpdatedAt by remember { mutableLongStateOf(0L) }
    var faceSnapshot by remember { mutableStateOf<Bitmap?>(null) }
    var handFrontShot by remember { mutableStateOf<Bitmap?>(null) }
    var handBackShot by remember { mutableStateOf<Bitmap?>(null) }
    var showSuccessOverlay by remember { mutableStateOf(false) }
    var successCountdown by remember { mutableIntStateOf(3) }
    var showTransitionMask by remember { mutableStateOf(false) }

    var showSymptomDialog by remember { mutableStateOf(false) }
    var symptomConfirmed by remember { mutableStateOf(false) }
    var autoNavigateState by remember { mutableStateOf<CheckState?>(null) }
    var cameraInitState by remember { mutableStateOf(com.smartcheck.app.ui.components.CameraInitState.Initializing) }
    var showHandFailDialog by remember { mutableStateOf(false) }
    var lastHandIssueShownAt by remember { mutableLongStateOf(0L) }

    LaunchedEffect(uiState.state) {
        if (uiState.state == CheckState.SYMPTOM_CHECKING) {
            showSymptomDialog = true
            symptomConfirmed = false
        } else {
            showSymptomDialog = false
        }

        if (uiState.state == CheckState.HAND_FAIL && uiState.handHasIssue) {
            val now = System.currentTimeMillis()
            if (now - lastHandIssueShownAt.toLong() > 2500L) {
                showHandFailDialog = true
                lastHandIssueShownAt = now
            }
        }

        when (uiState.state) {
            CheckState.HAND_BACK_CHECKING -> {
                if (handFrontShot == null) {
                    handFrontShot = lastFrameBitmap?.let { createPreviewBitmap(it, maxWidth = 240) }
                }
            }
            CheckState.SYMPTOM_CHECKING, CheckState.HAND_FAIL -> {
                if (uiState.handBackInfos.isNotEmpty()) {
                    if (handBackShot == null) {
                        handBackShot = lastFrameBitmap?.let { createPreviewBitmap(it, maxWidth = 240) }
                    }
                } else if (uiState.handPalmInfos.isNotEmpty()) {
                    if (handFrontShot == null) {
                        handFrontShot = lastFrameBitmap?.let { createPreviewBitmap(it, maxWidth = 240) }
                    }
                } else if (handBackShot == null) {
                    handBackShot = lastFrameBitmap?.let { createPreviewBitmap(it, maxWidth = 240) }
                }
            }
            CheckState.IDLE -> {
                autoNavigateState = null
                showSuccessOverlay = false
                handFrontShot.safeRecycle()
                handBackShot.safeRecycle()
                faceSnapshot.safeRecycle()
                handFrontShot = null
                handBackShot = null
                faceSnapshot = null
            }
            else -> Unit
        }
    }

    LaunchedEffect(uiState.state) {
        if (uiState.state == CheckState.FACE_PASS) {
            lastFrameBitmap?.let { faceSnapshot = createPreviewBitmap(it, maxWidth = 220) }
        }
    }

    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            faceSnapshot.safeRecycle()
            handFrontShot.safeRecycle()
            handBackShot.safeRecycle()
        }
    }

    LaunchedEffect(uiState.state, uiState.isRecordFinalized) {
        val terminalStates = setOf(
            CheckState.ALL_PASS,
            CheckState.HAND_FAIL,
            CheckState.SYMPTOM_FAIL,
            CheckState.TEMP_FAIL
        )
        if (uiState.state !in terminalStates) return@LaunchedEffect
        if (!uiState.isRecordFinalized) return@LaunchedEffect
        if (autoNavigateState == uiState.state) return@LaunchedEffect
        autoNavigateState = uiState.state

        if (uiState.state == CheckState.ALL_PASS) {
            // 晨检通过：显示成功蒙层 + 倒计时3秒，然后自动重置等待下一位
            showSuccessOverlay = true
            for (sec in 3 downTo 1) {
                successCountdown = sec
                delay(1000)
            }
            showSuccessOverlay = false
            // scheduleReset(3000) 已在 MainViewModel.finalizeCheckRecord() 中调用，
            // 倒计时结束时状态会自动切回 IDLE，无需手动导航
        } else {
            delay(1200)
            onNavigateBackToDashboard?.invoke()
        }
    }

    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    LaunchedEffect(Unit) {
        if (!cameraPermissionState.status.isGranted) {
            cameraPermissionState.launchPermissionRequest()
        }
    }

    LaunchedEffect(Unit) {
        val available = context.filesDir.usableSpace
        val threshold = 500L * 1024L * 1024L
        if (available < threshold) {
            FileUtil.clearOldRecords(context, 30)
            Toast.makeText(context, "存储空间不足，已清理30天前记录", Toast.LENGTH_SHORT).show()
        }
    }

    val isHandStage = uiState.state == CheckState.HAND_PALM_CHECKING ||
        uiState.state == CheckState.HAND_BACK_CHECKING

    val preferredCameraId = remember(isHandStage) {
        val vidPidStr = if (isHandStage) "0BDA:D567" else "0BDA:271A"
        val parsed = UsbCameraHelper.parseVidPid(vidPidStr)
        if (parsed != null) {
            val found = UsbCameraHelper.findCameraIdByVidPid(context, parsed.first, parsed.second)
            if (found != null) {
                Timber.i("${if (isHandStage) "Hand" else "Face"} camera resolved by VID/PID: $vidPidStr -> $found")
                found
            } else {
                Timber.w("Camera not found for VID/PID $vidPidStr, falling back to default")
                if (isHandStage) "111" else "109"
            }
        } else {
            if (isHandStage) "111" else "109"
        }
    }
    val isSwitchingCamera = uiState.state == CheckState.FACE_PASS || uiState.state == CheckState.TEMP_MEASURING
    val isMirrored = cameraLensFacing == CameraSelector.LENS_FACING_FRONT ||
        (!isHandStage && cameraLensFacing == CameraSelector.LENS_FACING_EXTERNAL)

    LaunchedEffect(isHandStage, isMirrored) {
        // Analyzer 输出帧通常不是预览镜像坐标，这里固定关闭镜像补偿，
        // 避免用 UI 镜像状态误导手心/手背判定极性。
        viewModel.updateHandFrameMirror(false)
    }

    LaunchedEffect(isHandStage) {
        if (isHandStage) {
            showTransitionMask = true
            delay(1500)
            showTransitionMask = false
        } else {
            showTransitionMask = false
        }
    }

    val statusText = when {
        cameraInitState != com.smartcheck.app.ui.components.CameraInitState.Ready -> if (isHandStage) "正在初始化手部相机..." else "正在初始化人脸相机..."
        showTransitionMask -> "正在切换相机..."
        else -> uiState.message.ifBlank { "请将人脸对准摄像头" }
    }

    val primaryBlue = Color(0xFF3B82F6)
    val primaryDark = Color(0xFF2563EB)
    val bgDark = Color(0xFF0F172A)
    val panelBg = Color.White
    val panelBgAlt = Color(0xFFF8FAFC)
    val textMain = Color(0xFF1E293B)
    val textMuted = Color(0xFF64748B)
    val borderColor = Color(0xFFE2E8F0)

    val success = Color(0xFF10B981)
    val successBg = Color(0xFFDCFCE7)
    val warning = Color(0xFFF59E0B)
    val danger = Color(0xFFEF4444)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgDark)
    ) {
        Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
            Box(
                modifier = Modifier
                    .weight(1.6f)
                    .fillMaxHeight()
                    .background(Color.Black)
            ) {
                if (cameraPermissionState.status.isGranted) {
                DualCameraPreview(
                    modifier = Modifier
                        .fillMaxSize()
                        .onSizeChanged {
                            previewWidth = it.width
                            previewHeight = it.height
                        },
                    cameraType = if (isHandStage) com.smartcheck.app.ui.components.CameraType.HAND else com.smartcheck.app.ui.components.CameraType.FACE,
                    preferredCameraId = preferredCameraId,
                    onFrameAnalyzed = { bitmap ->
                        lastFrameWidth = bitmap.width
                        lastFrameHeight = bitmap.height
                        val now = System.currentTimeMillis()
                        if (now - lastFrameBitmapUpdatedAt >= 300L) {
                            lastFrameBitmap?.safeRecycle()
                            lastFrameBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false)
                            lastFrameBitmapUpdatedAt = now
                        }
                        viewModel.processFrame(bitmap)
                    },
                    onCameraInfo = { _, lensFacing ->
                        cameraLensFacing = lensFacing
                    },
                    onCameraState = { state ->
                        cameraInitState = state
                    }
                )

                // 轻微暗角渐变（camera-feed 背景效果）
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color(0xFF0F172A).copy(alpha = 0.35f)
                                )
                            )
                        )
                )

                if (cameraInitState == com.smartcheck.app.ui.components.CameraInitState.Ready &&
                    !isHandStage &&
                    faceBoxes.isNotEmpty()
                ) {
                    FaceOverlay(
                        faceBoxes = faceBoxes,
                        frameWidth = lastFrameWidth,
                        frameHeight = lastFrameHeight,
                        viewWidth = previewWidth,
                        viewHeight = previewHeight,
                        mirrorX = isMirrored,
                    )
                    Timber.d("[HomeScreen] 画框: faceBoxes=$faceBoxes, frame=(${lastFrameWidth}x${lastFrameHeight}), view=(${previewWidth}x${previewHeight}), mirror=$isMirrored")
                }

                if (cameraInitState == com.smartcheck.app.ui.components.CameraInitState.Ready && isHandStage) {
                    HandOverlay(
                        handInfos = handInfos,
                        frameWidth = lastFrameWidth,
                        frameHeight = lastFrameHeight,
                        viewWidth = previewWidth,
                        viewHeight = previewHeight,
                        contentScale = ContentScale.Fit,
                        mirrorX = isMirrored,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                ScannerFrameOverlay()

                if (cameraInitState == com.smartcheck.app.ui.components.CameraInitState.Error) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.7f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = null,
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(34.dp)
                            )
                            Spacer(modifier = Modifier.height(Dimens.PaddingSmall))
                            Text(
                                text = "相机初始化失败",
                                color = Color.White,
                                fontSize = Dimens.TextSizeNormal,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(Dimens.PaddingSmall))
                            Text(
                                text = "请检查设备或重启",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = Dimens.TextSizeSmall
                            )
                        }
                    }
                } else if (cameraInitState != com.smartcheck.app.ui.components.CameraInitState.Ready) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            androidx.compose.material3.CircularProgressIndicator(
                                color = Color(0xFF3B82F6),
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(34.dp)
                            )
                            Spacer(modifier = Modifier.height(Dimens.PaddingSmall))
                            Text(
                                text = "正在初始化相机...",
                                color = Color.White,
                                fontSize = Dimens.TextSizeNormal,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(Dimens.PaddingSmall))
                            Text(
                                text = if (isHandStage) "即将开始手部识别" else "即将开始人脸识别",
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = Dimens.TextSizeSmall
                            )
                        }
                    }
                }

                if (showTransitionMask && cameraInitState == com.smartcheck.app.ui.components.CameraInitState.Ready) {
                    TransitionMask()
                }

                StatusBadge(
                    text = statusText,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = Dimens.PaddingNormal)
                )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "需要相机权限以进行人脸识别",
                                fontSize = Dimens.TextSizeNormal,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(Dimens.PaddingNormal))
                            Button(
                                onClick = { cameraPermissionState.launchPermissionRequest() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CameraAlt,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = "授予权限", fontSize = Dimens.TextSizeNormal, color = Color.White)
                            }
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(panelBg)
            ) {
                // 顶部用户信息区
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(panelBgAlt)
                        .padding(horizontal = 24.dp, vertical = 22.dp)
                ) {
                    val context = LocalContext.current
                    val faceFile = FileUtil.getRecordImageFile(context, uiState.faceImagePath)?.takeIf { it.exists() }
                    val faceModel = faceFile ?: faceSnapshot

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // 头像 - 蓝色圆形背景
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(Color(0xFFDBEAFE)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (faceModel != null) {
                                AsyncImage(
                                    model = faceModel,
                                    contentDescription = "头像",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = primaryBlue,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            val nameText = uiState.currentUserName.ifBlank { "陌生人" }
                            val badgeText = if (uiState.currentUserId == null) "身份识别中..." else "已识别"

                            Text(
                                text = nameText,
                                color = textMain,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = badgeText,
                                color = textMuted,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 实时体温测定 + 健康证状态 并排卡片
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // 实时体温测定
                        InfoCard(
                            modifier = Modifier.weight(1f),
                            title = "实时体温测定",
                            content = {
                                val isReadingTemp = uiState.currentTemp == 0f || uiState.state == CheckState.TEMP_MEASURING
                                if (isReadingTemp) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        androidx.compose.material3.CircularProgressIndicator(
                                            modifier = Modifier.size(14.dp),
                                            strokeWidth = 2.dp,
                                            color = warning
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "读取中...",
                                            color = warning,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                } else {
                                    val isHigh = uiState.currentTemp >= 37.3f && uiState.currentTemp > 0f
                                    Text(
                                        text = formatTemp(uiState.currentTemp),
                                        color = if (isHigh) danger else success,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        )

                        // 健康证状态
                        InfoCard(
                            modifier = Modifier.weight(1f),
                            title = "健康证状态",
                            content = {
                                val days = uiState.healthCertDaysRemaining
                                val certText = if (days == null) "暂无数据" else formatHealthCert(days)
                                Text(
                                    text = certText,
                                    color = if (days != null && days < 0) danger else textMuted,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        )
                    }
                }

                // 手部卫生检测区域
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 18.dp)
                ) {
                    Text(
                        text = "手部卫生检测",
                        color = textMain,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "请依次完成手心与手背检测",
                        color = textMuted,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    val context = LocalContext.current

                    // 第一步：手心检测
                    HandCheckStepCard(
                        stepTitle = "第一步：手心检测",
                        stepHint = "请以此完成手心与手背检测",
                        isActive = uiState.state == CheckState.HAND_PALM_CHECKING,
                        isDone = uiState.handPalmPath != null,
                        handImagePath = uiState.handPalmPath,
                        handSnapshot = handFrontShot,
                        handInfos = uiState.handPalmInfos,
                        frameWidth = uiState.handPalmFrameWidth ?: lastFrameWidth,
                        frameHeight = uiState.handPalmFrameHeight ?: lastFrameHeight,
                        placeholderTitle = "手心",
                        placeholderHint = "等待拍摄",
                        borderColor = borderColor,
                        panelBgAlt = panelBgAlt,
                        textMain = textMain,
                        textMuted = textMuted,
                        primaryBlue = primaryBlue,
                        primaryDark = primaryDark,
                        success = success,
                        successBg = successBg,
                        danger = danger,
                        onRetake = { viewModel.retakeHandPalm() }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // 第二步：手背检测
                    HandCheckStepCard(
                        stepTitle = "第二步：手背检测",
                        stepHint = "",
                        isActive = uiState.state == CheckState.HAND_BACK_CHECKING,
                        isDone = uiState.handBackPath != null,
                        handImagePath = uiState.handBackPath,
                        handSnapshot = handBackShot,
                        handInfos = uiState.handBackInfos,
                        frameWidth = uiState.handBackFrameWidth ?: lastFrameWidth,
                        frameHeight = uiState.handBackFrameHeight ?: lastFrameHeight,
                        placeholderTitle = "手背",
                        placeholderHint = "等待拍摄",
                        borderColor = borderColor,
                        panelBgAlt = panelBgAlt,
                        textMain = textMain,
                        textMuted = textMuted,
                        primaryBlue = primaryBlue,
                        primaryDark = primaryDark,
                        success = success,
                        successBg = successBg,
                        danger = danger,
                        onRetake = { viewModel.retakeHandBack() }
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(borderColor)
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 18.dp)
                ) {
                    val canSubmit = uiState.state != CheckState.SYMPTOM_CHECKING && uiState.handPalmPath != null && uiState.handBackPath != null
                    val buttonText = when {
                        uiState.isSubmitting -> "提交中..."
                        canSubmit -> "提交并上岗"
                        else -> "检测未完成，无法提交"
                    }

                    Button(
                        onClick = { viewModel.finalizeCheckRecord() },
                        enabled = canSubmit && !uiState.isSubmitting,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(Dimens.ButtonHeight),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = primaryDark,
                            disabledContainerColor = Color(0xFFCBD5E1),
                            disabledContentColor = Color(0xFF64748B)
                        )
                    ) {
                        Text(
                            text = buttonText,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }

    androidx.compose.animation.AnimatedContent(
        targetState = showSymptomDialog,
        transitionSpec = { androidx.compose.animation.fadeIn(tween(200)) with androidx.compose.animation.fadeOut(tween(200)) },
        label = "SymptomDialog"
    ) { visible ->
        if (visible) {
            AlertDialog(
                onDismissRequest = { },
                title = {
                    Text(text = "健康询问", color = primaryDark, fontSize = Dimens.TextSizeLarge)
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(Dimens.PaddingNormal)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = symptomConfirmed,
                                onCheckedChange = { symptomConfirmed = it }
                            )
                            Spacer(modifier = Modifier.width(Dimens.PaddingSmall))
                            Text(
                                text = "我承诺今日无腹泻、咽痛等异常症状",
                                fontSize = Dimens.TextSizeNormal
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showSymptomDialog = false
                            if (symptomConfirmed) {
                                viewModel.submitSymptoms(emptyList())
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = primaryDark)
                    ) {
                        Text(text = "确认", fontSize = Dimens.TextSizeNormal, color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showSymptomDialog = false
                            viewModel.submitSymptoms(listOf("自述异常"))
                        }
                    ) {
                        Text(text = "有异常", fontSize = Dimens.TextSizeNormal, color = MaterialTheme.colorScheme.error)
                    }
                }
            )
        }
    }

    androidx.compose.animation.AnimatedContent(
        targetState = uiState.showDuplicateCheckDialog,
        transitionSpec = { androidx.compose.animation.fadeIn(tween(200)) with androidx.compose.animation.fadeOut(tween(200)) },
        label = "DuplicateCheckDialog"
    ) { visible ->
        if (visible) {
            AlertDialog(
                onDismissRequest = { },
                title = {
                    Text(text = "今日已晨检", color = primaryDark, fontSize = Dimens.TextSizeLarge)
                },
                text = {
                    Text(
                        text = "是否继续晨检",
                        fontSize = Dimens.TextSizeNormal
                    )
                },
                confirmButton = {
                    Button(
                        onClick = { viewModel.continueDuplicateMorningCheck() },
                        colors = ButtonDefaults.buttonColors(containerColor = primaryDark)
                    ) {
                        Text(text = "继续晨检", fontSize = Dimens.TextSizeNormal, color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.cancelDuplicateMorningCheck() }) {
                        Text(
                            text = "取消",
                            fontSize = Dimens.TextSizeNormal,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            )
        }
    }

    androidx.compose.animation.AnimatedContent(
        targetState = showSuccessOverlay,
        transitionSpec = { androidx.compose.animation.fadeIn(tween(220)) with androidx.compose.animation.fadeOut(tween(220)) },
        label = "SuccessOverlay"
    ) { visible ->
        if (visible) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(success.copy(alpha = 0.92f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "✔", color = Color.White, fontSize = Dimens.TextSizeTitle)
                    Spacer(modifier = Modifier.height(Dimens.PaddingSmall))
                    Text(
                        text = "晨检成功，祝您工作愉快",
                        color = Color.White,
                        fontSize = Dimens.TextSizeLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(Dimens.PaddingNormal))
                    Text(
                        text = "${successCountdown} 秒后开始下一位检测",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = Dimens.TextSizeNormal
                    )
                }
            }
        }
    }

    androidx.compose.animation.AnimatedContent(
        targetState = showHandFailDialog,
        transitionSpec = { androidx.compose.animation.fadeIn(tween(200)) with androidx.compose.animation.fadeOut(tween(200)) },
        label = "HandFailDialog"
    ) { visible ->
        if (visible) {
            AlertDialog(
                onDismissRequest = { showHandFailDialog = false },
                title = {
                    Text(text = "发现异物/伤口", color = MaterialTheme.colorScheme.error, fontSize = Dimens.TextSizeLarge)
                },
                text = {
                    val issueSummary = uiState.handDetectionResults.joinToString("，")
                    Text(
                        text = if (issueSummary.isBlank()) "手部检测异常，请人工复核" else "手部检测异常：$issueSummary",
                        fontSize = Dimens.TextSizeNormal
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showHandFailDialog = false
                            // 不退出晨检，让用户可以继续
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text(text = "确认", fontSize = Dimens.TextSizeNormal, color = Color.White)
                    }
                }
            )
        }
    }
}


private fun Bitmap?.safeRecycle() {
    try {
        if (this != null && !isRecycled) {
            recycle()
        }
    } catch (_: Exception) {
    }
}


@Composable
private fun TransitionMask() {
    val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "mask")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.65f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = tween(700),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "maskAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = alpha)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            androidx.compose.material3.CircularProgressIndicator(
                color = Color(0xFF3B82F6),
                strokeWidth = 3.dp,
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.height(Dimens.PaddingSmall))
            Text(
                text = "正在准备手部检测",
                color = Color.White,
                fontSize = Dimens.TextSizeNormal,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun HandGuideOverlay(
    hasDetection: Boolean,
    isCaptured: Boolean,
    modifier: Modifier = Modifier
) {
    val success = Color(0xFF10B981)
    val warning = Color(0xFFF59E0B)

    val targetColor = when {
        isCaptured -> success
        hasDetection -> warning
        else -> Color.White
    }
    val guideColor by animateColorAsState(targetValue = targetColor, animationSpec = tween(220), label = "handGuide")
    val guideAlpha by animateFloatAsState(
        targetValue = if (isCaptured) 0.9f else 0.55f,
        animationSpec = tween(180),
        label = "handGuideAlpha"
    )

    androidx.compose.foundation.Canvas(modifier = modifier) {
        val stroke = Stroke(
            width = 3.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(
                floatArrayOf(10.dp.toPx(), 8.dp.toPx())
            )
        )
        val guideWidth = size.width * 0.36f
        val guideHeight = size.height * 0.52f
        val left = (size.width - guideWidth) / 2f
        val top = (size.height - guideHeight) / 2f
        val palmHeight = guideHeight * 0.55f
        val fingerHeight = guideHeight * 0.35f
        val fingerWidth = guideWidth * 0.16f
        val gap = guideWidth * 0.03f

        val color = guideColor.copy(alpha = guideAlpha)

        drawRoundRect(
            color = color,
            topLeft = androidx.compose.ui.geometry.Offset(left + guideWidth * 0.2f, top + fingerHeight),
            size = androidx.compose.ui.geometry.Size(guideWidth * 0.6f, palmHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(18.dp.toPx()),
            style = stroke
        )

        val fingerTop = top
        val fingerBaseLeft = left + guideWidth * 0.22f
        for (i in 0 until 4) {
            val fx = fingerBaseLeft + i * (fingerWidth + gap)
            drawRoundRect(
                color = color,
                topLeft = androidx.compose.ui.geometry.Offset(fx, fingerTop),
                size = androidx.compose.ui.geometry.Size(fingerWidth, fingerHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx()),
                style = stroke
            )
        }

        drawRoundRect(
            color = color,
            topLeft = androidx.compose.ui.geometry.Offset(left, top + fingerHeight + palmHeight * 0.25f),
            size = androidx.compose.ui.geometry.Size(guideWidth * 0.18f, palmHeight * 0.35f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx()),
            style = stroke
        )

        if (isCaptured) {
            drawRect(color = success.copy(alpha = 0.08f))
        }
    }
}

@Composable
private fun HandForeignObjectOverlay(
    handInfos: List<HandInfo>,
    modifier: Modifier = Modifier
) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val strokeWidth = 3.dp.toPx()
        val renderWidth = size.width
        val renderHeight = size.height

        fun mapBox(
            box: android.graphics.RectF,
            cropLeft: Float,
            cropTop: Float,
            scale: Float,
            offsetX: Float,
            offsetY: Float
        ): android.graphics.RectF {
            val left = offsetX + (cropLeft + min(box.left, box.right)) * scale
            val top = offsetY + (cropTop + min(box.top, box.bottom)) * scale
            val right = offsetX + (cropLeft + max(box.left, box.right)) * scale
            val bottom = offsetY + (cropTop + max(box.top, box.bottom)) * scale
            return android.graphics.RectF(left, top, right, bottom)
        }

        handInfos.forEach { hand ->
            val cropScaleFactor = 1.5f
            val box = hand.box
            val cropWidth = box.width() * cropScaleFactor
            val cropHeight = box.height() * cropScaleFactor
            val cropLeft = max(0f, box.centerX() - cropWidth / 2f)
            val cropTop = max(0f, box.centerY() - cropHeight / 2f)

            val scale = min(
                renderWidth / cropWidth.coerceAtLeast(1f),
                renderHeight / cropHeight.coerceAtLeast(1f)
            )
            val offsetX = (renderWidth - cropWidth * scale) / 2f
            val offsetY = (renderHeight - cropHeight * scale) / 2f

            val foreignObjects: List<ForeignObjectInfo> = if (hand.foreignObjects.isNotEmpty()) {
                hand.foreignObjects
            } else if (hand.hasForeignObject && hand.keyPoints.size >= 2) {
                val tl = hand.keyPoints[0]
                val br = hand.keyPoints[1]
                listOf(
                    ForeignObjectInfo(
                        box = android.graphics.RectF(tl.x, tl.y, br.x, br.y),
                        score = hand.score,
                        label = hand.label
                    )
                )
            } else {
                emptyList()
            }

            foreignObjects.forEach { fo ->
                val mapped = mapBox(fo.box, cropLeft, cropTop, scale, offsetX, offsetY)
                drawRect(
                    color = Color(0xFFEF4444),
                    topLeft = androidx.compose.ui.geometry.Offset(mapped.left, mapped.top),
                    size = androidx.compose.ui.geometry.Size(mapped.width(), mapped.height()),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth)
                )
            }
        }
    }
}

@Composable
private fun HandResultOverlay(
    handInfos: List<HandInfo>,
    frameWidth: Int,
    frameHeight: Int,
    modifier: Modifier = Modifier
) {
    if (frameWidth <= 0 || frameHeight <= 0) return
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val strokeWidth = 3.dp.toPx()
        val renderWidth = size.width
        val renderHeight = size.height

        val scaleX = renderWidth / frameWidth.toFloat().coerceAtLeast(1f)
        val scaleY = renderHeight / frameHeight.toFloat().coerceAtLeast(1f)
        val scale = min(scaleX, scaleY)
        val offsetX = (renderWidth - frameWidth * scale) / 2f
        val offsetY = (renderHeight - frameHeight * scale) / 2f

        fun mapBox(box: android.graphics.RectF): android.graphics.RectF {
            val left = offsetX + min(box.left, box.right) * scale
            val top = offsetY + min(box.top, box.bottom) * scale
            val right = offsetX + max(box.left, box.right) * scale
            val bottom = offsetY + max(box.top, box.bottom) * scale
            return android.graphics.RectF(left, top, right, bottom)
        }

        handInfos.forEach { hand ->
            val handBox = mapBox(hand.box)
            drawRect(
                color = if (hand.hasForeignObject) Color(0xFFEF4444) else Color(0xFF10B981),
                topLeft = androidx.compose.ui.geometry.Offset(handBox.left, handBox.top),
                size = androidx.compose.ui.geometry.Size(handBox.width(), handBox.height()),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth)
            )

            val foreignObjects: List<ForeignObjectInfo> = if (hand.foreignObjects.isNotEmpty()) {
                hand.foreignObjects
            } else if (hand.hasForeignObject && hand.keyPoints.size >= 2) {
                val tl = hand.keyPoints[0]
                val br = hand.keyPoints[1]
                listOf(
                    ForeignObjectInfo(
                        box = android.graphics.RectF(tl.x, tl.y, br.x, br.y),
                        score = hand.score,
                        label = hand.label
                    )
                )
            } else {
                emptyList()
            }

            foreignObjects.forEach { fo ->
                val mapped = mapBox(fo.box)
                drawRect(
                    color = Color(0xFFEF4444),
                    topLeft = androidx.compose.ui.geometry.Offset(mapped.left, mapped.top),
                    size = androidx.compose.ui.geometry.Size(mapped.width(), mapped.height()),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth)
                )
            }
        }
    }
}

@Composable
private fun ScannerFrameOverlay() {
    val primaryBlue = Color(0xFF3B82F6)
    val cornerLength = 40.dp
    val cornerStroke = 3.dp

    val infinite = androidx.compose.animation.core.rememberInfiniteTransition(label = "reticle")
    val scanProgress by infinite.animateFloat(
        initialValue = 0.05f,
        targetValue = 0.95f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = tween(2500),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "scan"
    )

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(modifier = Modifier.size(width = 320.dp, height = 380.dp)) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .size(cornerLength)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(cornerStroke)
                        .background(primaryBlue)
                )
                Box(
                    modifier = Modifier
                        .width(cornerStroke)
                        .fillMaxHeight()
                        .background(primaryBlue)
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(cornerLength)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(cornerStroke)
                        .background(primaryBlue)
                )
                Box(
                    modifier = Modifier
                        .width(cornerStroke)
                        .fillMaxHeight()
                        .background(primaryBlue)
                        .align(Alignment.TopEnd)
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .size(cornerLength)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(cornerStroke)
                        .background(primaryBlue)
                        .align(Alignment.BottomStart)
                )
                Box(
                    modifier = Modifier
                        .width(cornerStroke)
                        .fillMaxHeight()
                        .background(primaryBlue)
                        .align(Alignment.BottomStart)
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(cornerLength)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(cornerStroke)
                        .background(primaryBlue)
                        .align(Alignment.BottomEnd)
                )
                Box(
                    modifier = Modifier
                        .width(cornerStroke)
                        .fillMaxHeight()
                        .background(primaryBlue)
                        .align(Alignment.BottomEnd)
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .align(Alignment.TopStart)
                    .offset(y = (380.dp - 2.dp) * scanProgress)
                    .background(primaryBlue)
            )
        }
    }
}

private fun formatTemp(temp: Float): String {
    return if (temp == 0f) "读取中..." else "%.1f°C".format(temp)
}

@Composable
private fun StatusBadge(
    text: String,
    modifier: Modifier = Modifier
) {
    val primaryBlue = Color(0xFF3B82F6)
    val pillBg = Color(0xFF0F172A).copy(alpha = 0.60f)

    Surface(
        color = pillBg,
        shape = RoundedCornerShape(30.dp),
        tonalElevation = 0.dp,
        modifier = modifier.border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(30.dp))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = primaryBlue,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = text,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun PlaceholderHandTile(
    title: String,
    hint: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFE5E7EB)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Color(0xFF374151))
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = hint, fontSize = Dimens.TextSizeSmall, color = Color(0xFF6B7280))
        }
    }
}

private fun formatHealthCert(days: Int?): String {
    return when {
        days == null -> "--"
        days < 0 -> "已过期 ${kotlin.math.abs(days)} 天"
        else -> "剩余 ${days} 天"
    }
}

private fun createPreviewBitmap(source: Bitmap, maxWidth: Int = 320): Bitmap {
    if (source.isRecycled) return source
    if (source.width <= maxWidth) return source
    val ratio = source.height.toFloat() / source.width.toFloat()
    val targetHeight = (maxWidth * ratio).toInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(source, maxWidth, targetHeight, true)
}

@Composable
private fun InfoCard(
    modifier: Modifier = Modifier,
    title: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) {
        Text(
            text = title,
            color = Color(0xFF64748B),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun HandCheckStepCard(
    stepTitle: String,
    stepHint: String,
    isActive: Boolean,
    isDone: Boolean,
    handImagePath: String?,
    handSnapshot: Bitmap?,
    handInfos: List<com.smartcheck.sdk.HandInfo>,
    frameWidth: Int,
    frameHeight: Int,
    placeholderTitle: String,
    placeholderHint: String,
    borderColor: Color,
    panelBgAlt: Color,
    textMain: Color,
    textMuted: Color,
    primaryBlue: Color,
    primaryDark: Color,
    success: Color,
    successBg: Color,
    danger: Color,
    onRetake: () -> Unit
) {
    val context = LocalContext.current
    val statusText = when {
        isActive -> "待开始"
        isDone -> "已完成"
        else -> "待开始"
    }
    val chipBg = when {
        isActive -> primaryBlue.copy(alpha = 0.12f)
        isDone -> successBg
        else -> Color(0xFFF1F5F9)
    }
    val chipText = when {
        isActive -> primaryDark
        isDone -> success
        else -> textMuted
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(panelBgAlt)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = stepTitle,
                    color = textMain,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (stepHint.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stepHint,
                        color = textMuted,
                        fontSize = 12.sp
                    )
                }
            }
            Surface(color = chipBg, shape = RoundedCornerShape(999.dp)) {
                Text(
                    text = statusText,
                    color = chipText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(120.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(borderColor)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = rememberRipple(bounded = true)
                    ) { onRetake() }
            ) {
                val imageFile = FileUtil.getRecordImageFile(context, handImagePath)?.takeIf { it.exists() }
                val imageModel = imageFile ?: handSnapshot
                if (imageModel != null) {
                    AsyncImage(
                        model = imageModel,
                        contentDescription = placeholderTitle,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    PlaceholderHandTile(
                        title = placeholderTitle,
                        hint = placeholderHint,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                if (imageModel != null && handInfos.isNotEmpty()) {
                    HandResultOverlay(
                        handInfos = handInfos,
                        frameWidth = frameWidth,
                        frameHeight = frameHeight,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            val hasIssue = handInfos.any { it.hasForeignObject }
            val issueSummary = handInfos.flatMap { info ->
                if (info.foreignObjects.isNotEmpty()) {
                    info.foreignObjects.map { it.label }
                } else if (info.hasForeignObject) {
                    listOf(info.label)
                } else {
                    emptyList()
                }
            }.distinct().joinToString("，")

            if (hasIssue) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = null,
                        tint = danger,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = issueSummary.ifBlank { "异常" },
                        color = danger,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            } else {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = success,
                    modifier = Modifier.size(30.dp)
                )
            }
        }
    }
}
