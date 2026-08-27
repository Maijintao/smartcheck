package com.smartcheck.app.ui.screens

import android.app.DatePickerDialog
import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.smartcheck.app.ui.components.CameraCaptureDialog
import com.smartcheck.app.ui.theme.Dimens
import com.smartcheck.app.viewmodel.EmployeeDetailViewModel
import com.smartcheck.app.viewmodel.SettingsViewModel
import com.smartcheck.app.utils.UsbCameraHelper
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.collectLatest

@Composable
fun EmployeeDetailScreen(
    onNavigateBack: () -> Unit,
    viewModel: EmployeeDetailViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val user by viewModel.user.collectAsState()
    val faceBitmap by viewModel.faceBitmap.collectAsState()
    val certBitmap by viewModel.certBitmap.collectAsState()

    val faceCameraId = remember {
        val parsed = UsbCameraHelper.parseVidPid("0BDA:271A")
        if (parsed != null) {
            UsbCameraHelper.findCameraIdByVidPid(context, parsed.first, parsed.second)
        } else null
    } ?: "109"

    val handCameraId = remember {
        val parsed = UsbCameraHelper.parseVidPid("0BDA:D567")
        if (parsed != null) {
            UsbCameraHelper.findCameraIdByVidPid(context, parsed.first, parsed.second)
        } else null
    } ?: "111"

    var name by remember { mutableStateOf("") }
    var employeeId by remember { mutableStateOf("") }
    var idCard by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var position by remember { mutableStateOf("") }
    var department by remember { mutableStateOf("") }
    var certStart by remember { mutableStateOf<LocalDate?>(null) }
    var certEnd by remember { mutableStateOf<LocalDate?>(null) }
    var healthCertImagePath by remember { mutableStateOf("") }

    var showFaceCamera by remember { mutableStateOf(false) }
    var showCertCamera by remember { mutableStateOf(false) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(user) {
        val value = user ?: return@LaunchedEffect
        name = value.name
        employeeId = value.employeeId
        idCard = value.idCardNumber
        phone = value.phone
        position = value.position
        department = value.department
        certStart = value.healthCertStartDate?.let { millisToLocalDate(it) }
        certEnd = value.healthCertEndDate?.let { millisToLocalDate(it) }
        healthCertImagePath = value.healthCertImagePath
    }

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is EmployeeDetailViewModel.UiEvent.Saved -> onNavigateBack()
                is EmployeeDetailViewModel.UiEvent.Error -> {
                    // TODO: show toast/snackbar
                }
            }
        }
    }

    if (showStartPicker) {
        LaunchedEffect(Unit) {
            showStartPicker = false
            showDatePicker(context) { date ->
                certStart = date
            }
        }
    }

    if (showEndPicker) {
        LaunchedEffect(Unit) {
            showEndPicker = false
            showDatePicker(context) { date ->
                certEnd = date
            }
        }
    }

    val primaryBlue = Color(0xFF2563EB)
    val primaryLight = Color(0xFFEFF6FF)
    val bgMain = Color(0xFFF8FAFC)
    val inputBg = Color(0xFFF1F5F9)
    val textMain = Color(0xFF1E293B)
    val textMuted = Color(0xFF64748B)
    val borderColor = Color(0xFFE2E8F0)
    val danger = Color(0xFFEF4444)

    Column(modifier = Modifier.fillMaxSize().background(bgMain)) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color.White,
            shadowElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(inputBg)
                        .clickable(onClick = onNavigateBack),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowLeft,
                        contentDescription = "返回",
                        tint = textMain
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = if (user == null) "新增员工" else "编辑员工",
                    color = textMain,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(32.dp)
                    .padding(bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                if (errorMessage != null) {
                    Surface(color = Color(0xFFFFF1F2), shape = RoundedCornerShape(12.dp)) {
                        Text(
                            text = errorMessage!!,
                            color = danger,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Badge,
                                    contentDescription = null,
                                    tint = primaryBlue,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "员工基本信息",
                                    color = textMain,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(borderColor)
                            )

                            FormRow(label = "姓名", required = true) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
            FormRow(label = "编号", required = true) {
                OutlinedTextField(
                    value = employeeId,
                    onValueChange = { employeeId = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
            FormRow(label = "身份证") {
                OutlinedTextField(
                    value = idCard,
                    onValueChange = { idCard = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
            FormRow(label = "手机") {
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
            FormRow(label = "职位") {
                OutlinedTextField(
                    value = position,
                    onValueChange = { position = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
            FormRow(label = "部门") {
                OutlinedTextField(
                    value = department,
                    onValueChange = { department = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
                        }
                    }

                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.VerifiedUser,
                                    contentDescription = null,
                                    tint = primaryBlue,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "特征与资质录入",
                                    color = textMain,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(borderColor)
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                CaptureBox(
                                    title = "录入人脸",
                                    subtitle = "点击调用摄像头",
                                    bitmap = faceBitmap,
                                    onClick = { showFaceCamera = true },
                                    primaryBlue = primaryBlue,
                                    primaryLight = primaryLight,
                                    inputBg = inputBg,
                                    borderColor = Color(0xFFCBD5E1),
                                    modifier = Modifier.weight(1f),
                                    required = true
                                )
                                CaptureBox(
                                    title = "健康证拍照",
                                    subtitle = "拍摄实体证件",
                                    bitmap = certBitmap,
                                    onClick = { showCertCamera = true },
                                    primaryBlue = primaryBlue,
                                    primaryLight = primaryLight,
                                    inputBg = inputBg,
                                    borderColor = Color(0xFFCBD5E1),
                                    modifier = Modifier.weight(1f),
                                    required = true
                                )
                            }

                            FormRow(label = "健康证起始日期", required = true) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showStartPicker = true }
                ) {
                    OutlinedTextField(
                        value = certStart?.toString().orEmpty(),
                        onValueChange = { },
                        modifier = Modifier.fillMaxWidth(),
                        readOnly = true,
                        enabled = false,
                        singleLine = true
                    )
                }
            }
            FormRow(label = "健康证到期日期", required = true) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showEndPicker = true }
                ) {
                    OutlinedTextField(
                        value = certEnd?.toString().orEmpty(),
                        onValueChange = { },
                        modifier = Modifier.fillMaxWidth(),
                        readOnly = true,
                        enabled = false,
                        singleLine = true
                    )
                }
            }
                        }
                    }
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color.White.copy(alpha = 0.92f),
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp, vertical = 18.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onNavigateBack,
                    colors = ButtonDefaults.buttonColors(containerColor = inputBg)
                ) {
                    Text(text = "取消", color = textMain)
                }

                Spacer(modifier = Modifier.width(12.dp))

                if (user != null) {
                    Button(
                        onClick = { viewModel.deleteUser() },
                        colors = ButtonDefaults.buttonColors(containerColor = danger)
                    ) {
                        Text(text = "删除员工", color = Color.White)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                }

                Button(
                    onClick = {
                        val missing = mutableListOf<String>()
                        if (name.isBlank()) missing.add("姓名")
                        if (employeeId.isBlank()) missing.add("编号")
                        if (faceBitmap == null) missing.add("人脸照片")
                        if (certBitmap == null && healthCertImagePath.isBlank()) missing.add("健康证照片")
                        if (certStart == null) missing.add("健康证起始日期")
                        if (certEnd == null) missing.add("健康证到期日期")

                        if (missing.isNotEmpty()) {
                            errorMessage = "请填写必填项：${missing.joinToString("、")}"
                            return@Button
                        }
                        errorMessage = null

                        if (user == null) {
                            viewModel.saveEmployee(
                                name = name,
                                employeeId = employeeId,
                                idCardNumber = idCard,
                                phone = phone,
                                position = position,
                                department = department,
                                healthCertStartDate = certStart?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli(),
                                healthCertEndDate = certEnd?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli(),
                                onSuccess = onNavigateBack
                            )
                        } else {
                            viewModel.updateUser(
                                name = name,
                                employeeId = employeeId,
                                idCardNumber = idCard,
                                phone = phone,
                                position = position,
                                department = department,
                                healthCertImagePath = healthCertImagePath,
                                healthCertStartDate = certStart?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli(),
                                healthCertEndDate = certEnd?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
                            )
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = primaryBlue)
                ) {
                    Text(
                        text = if (user == null) "保存并提交" else "保存修改",
                        color = Color.White
                    )
                }
            }
        }
    }

    if (showFaceCamera) {
        CameraCaptureDialog(
            cameraId = faceCameraId,
            isFaceCamera = true,
            onCapture = {
                viewModel.updateFaceBitmap(it)
                showFaceCamera = false
            },
            onDismiss = { showFaceCamera = false }
        )
    }

    if (showCertCamera) {
        DisposableEffect(Unit) {
            viewModel.startHealthCertCaptureLight()
            onDispose { viewModel.stopHealthCertCaptureLight() }
        }
        CameraCaptureDialog(
            cameraId = handCameraId,
            isFaceCamera = false,
            onCapture = {
                viewModel.updateCertBitmap(it)
                showCertCamera = false
            },
            onDismiss = { showCertCamera = false }
        )
    }
}

@Composable
private fun FormRow(label: String, required: Boolean = false, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        RequiredLabel(label = label, required = required)
        Spacer(modifier = Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun RequiredLabel(label: String, required: Boolean) {
    val text = buildAnnotatedString {
        append(label)
        if (required) {
            append(" ")
            withStyle(
                style = SpanStyle(
                    color = Color(0xFFE53935),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            ) {
                append("*")
            }
        }
    }
    Text(
        text = text,
        color = Color(0xFF64748B),
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun CaptureBox(
    title: String,
    subtitle: String,
    bitmap: Bitmap?,
    onClick: () -> Unit,
    primaryBlue: Color,
    primaryLight: Color,
    inputBg: Color,
    borderColor: Color,
    modifier: Modifier = Modifier,
    required: Boolean = false
) {
    val shape = RoundedCornerShape(16.dp)
    val isFace = title.contains("人脸")

    Column(modifier = modifier) {
        RequiredLabel(label = title, required = required)
        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 180.dp)
                .clip(shape)
                .background(inputBg)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val stroke = Stroke(
                    width = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(
                        floatArrayOf(10.dp.toPx(), 6.dp.toPx())
                    )
                )

                drawRoundRect(
                    color = borderColor,
                    style = stroke,
                    cornerRadius = CornerRadius(16.dp.toPx())
                )

                if (isFace) {
                    val inset = 15.dp.toPx()
                    val corner = 20.dp.toPx()
                    val w = size.width
                    val h = size.height
                    val cStroke = Stroke(width = 2.dp.toPx())

                    drawLine(
                        color = primaryBlue,
                        start = androidx.compose.ui.geometry.Offset(inset, inset),
                        end = androidx.compose.ui.geometry.Offset(inset + corner, inset),
                        strokeWidth = cStroke.width
                    )
                    drawLine(
                        color = primaryBlue,
                        start = androidx.compose.ui.geometry.Offset(inset, inset),
                        end = androidx.compose.ui.geometry.Offset(inset, inset + corner),
                        strokeWidth = cStroke.width
                    )

                    drawLine(
                        color = primaryBlue,
                        start = androidx.compose.ui.geometry.Offset(w - inset, h - inset),
                        end = androidx.compose.ui.geometry.Offset(w - inset - corner, h - inset),
                        strokeWidth = cStroke.width
                    )
                    drawLine(
                        color = primaryBlue,
                        start = androidx.compose.ui.geometry.Offset(w - inset, h - inset),
                        end = androidx.compose.ui.geometry.Offset(w - inset, h - inset - corner),
                        strokeWidth = cStroke.width
                    )
                }
            }

            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = null,
                            tint = primaryBlue
                        )
                    }
                    Text(
                        text = subtitle,
                        fontSize = 13.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
            }
        }
    }
}

private fun showDatePicker(
    context: android.content.Context,
    onDateSelected: (LocalDate) -> Unit
) {
    val now = LocalDate.now()
    DatePickerDialog(
        context,
        { _, year: Int, month: Int, day: Int ->
            onDateSelected(LocalDate.of(year, month + 1, day))
        },
        now.year,
        now.monthValue - 1,
        now.dayOfMonth
    ).show()
}

private fun millisToLocalDate(millis: Long): LocalDate {
    return java.time.Instant.ofEpochMilli(millis)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
}
