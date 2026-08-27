package com.smartcheck.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import com.smartcheck.app.domain.model.HealthCertStatus
import com.smartcheck.app.domain.model.HandStatus
import com.smartcheck.app.ui.theme.Dimens
import com.smartcheck.app.ui.util.toChineseLabel
import com.smartcheck.app.utils.FileUtil
import com.smartcheck.app.viewmodel.RecordDetailViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordDetailScreen(
    onNavigateBack: () -> Unit,
    viewModel: RecordDetailViewModel = hiltViewModel()
) {
    val record by viewModel.record.collectAsState()
    val context = LocalContext.current
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }
    
    var isEditing by remember { mutableStateOf(false) }
    var temperature by remember { mutableStateOf("") }
    var handStatus by remember { mutableStateOf("") }
    var healthCertStatus by remember { mutableStateOf("") }
    var symptomFlags by remember { mutableStateOf("") }
    var remark by remember { mutableStateOf("") }

    LaunchedEffect(record) {
        val value = record ?: return@LaunchedEffect
        temperature = value.temperature.toString()
        handStatus = value.handStatus.toChineseLabel()
        healthCertStatus = value.healthCertStatus.toChineseLabel()
        symptomFlags = value.symptomFlags.joinToString("、") { it.toChineseLabel() }
        remark = value.remark
    }

    val primaryBlue = Color(0xFF2563EB)
    val primaryLight = Color(0xFFEFF6FF)
    val bgMain = Color(0xFFF1F5F9)
    val cardBg = Color.White
    val textMain = Color(0xFF1E293B)
    val textMuted = Color(0xFF64748B)
    val borderColor = Color(0xFFE2E8F0)

    val success = Color(0xFF10B981)
    val successBg = Color(0xFFDCFCE7)
    val danger = Color(0xFFEF4444)
    val dangerBg = Color(0xFFFEE2E2)
    val warning = Color(0xFFF59E0B)
    val warningBg = Color(0xFFFEF3C7)

    Scaffold(
        topBar = {
            Surface(color = cardBg, shadowElevation = 0.dp) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp, vertical = 18.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(bgMain)
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
                                text = "晨检记录详情",
                                color = textMain,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        record?.let {
                            Text(
                                text = "检测时间: ${dateFormat.format(Date(it.checkTime))}",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = textMuted
                            )
                        }
                    }

                    Divider(color = borderColor)
                }
            }
        }
    ) { paddingValues ->
        val currentRecord = record
        if (currentRecord == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text("记录不存在")
            }
        } else {
            val isTempNormal = currentRecord.temperature < 37.3f
            val tempValue = String.format(Locale.getDefault(), "%.1f", currentRecord.temperature)
            val tempLabel = if (isTempNormal) "体温正常" else "体温异常"

            val healthBadge = when (currentRecord.healthCertStatus) {
                HealthCertStatus.VALID -> Triple(successBg, success, "有效")
                HealthCertStatus.EXPIRING_SOON -> Triple(warningBg, warning, "即将过期")
                HealthCertStatus.EXPIRED -> Triple(dangerBg, danger, "已过期")
                HealthCertStatus.NOT_PROVIDED -> Triple(dangerBg, danger, "未录入")
                HealthCertStatus.REVOKED -> Triple(dangerBg, danger, "已吊销")
                HealthCertStatus.NOT_CHECKED -> Triple(warningBg, warning, "未检查")
                HealthCertStatus.UNKNOWN -> Triple(warningBg, warning, "未知")
            }

            val symptomDisplay = if (currentRecord.symptomFlags.isEmpty()) {
                "无"
            } else {
                currentRecord.symptomFlags.joinToString("、") { it.toChineseLabel() }
            }

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(bgMain)
                    .padding(32.dp),
                horizontalArrangement = Arrangement.spacedBy(32.dp)
            ) {
                Column(
                    modifier = Modifier
                        .weight(5f)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = cardBg),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFF8FAFC))
                                    .padding(horizontal = 18.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CameraAlt,
                                    contentDescription = null,
                                    tint = primaryBlue,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "面部抓拍",
                                    color = textMain,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Divider(color = borderColor)

                            val faceFile = FileUtil.getRecordImageFile(context, currentRecord.faceImagePath)
                            val faceGradient = Brush.linearGradient(
                                listOf(Color(0xFFE2E8F0), Color(0xFFCBD5E1))
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(340.dp)
                                    .background(faceGradient),
                                contentAlignment = Alignment.Center
                            ) {
                                SubcomposeAsyncImage(
                                    model = faceFile,
                                    contentDescription = "人脸抓拍",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                ) {
                                    when (painter.state) {
                                        is coil.compose.AsyncImagePainter.State.Loading,
                                        is coil.compose.AsyncImagePainter.State.Error -> {
                                            Box(
                                                modifier = Modifier.fillMaxSize(),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Person,
                                                    contentDescription = null,
                                                    tint = Color(0xFF94A3B8),
                                                    modifier = Modifier.size(64.dp)
                                                )
                                            }
                                        }

                                        else -> SubcomposeAsyncImageContent()
                                    }
                                }
                            }
                        }
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = cardBg),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFF8FAFC))
                                    .padding(horizontal = 18.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Description,
                                    contentDescription = null,
                                    tint = primaryBlue,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "手部抓拍",
                                    color = textMain,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Divider(color = borderColor)

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp)
                                    .background(bgMain)
                            ) {
                                val backFile = FileUtil.getRecordImageFile(context, currentRecord.handBackPath)
                                val palmFile = FileUtil.getRecordImageFile(context, currentRecord.handPalmPath)

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxSize()
                                        .background(Color(0xFFE2E8F0))
                                ) {
                                    SubcomposeAsyncImage(
                                        model = backFile,
                                        contentDescription = "手背检测",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    ) {
                                        when (painter.state) {
                                            is coil.compose.AsyncImagePainter.State.Loading,
                                            is coil.compose.AsyncImagePainter.State.Error -> {
                                                Box(
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.PhotoLibrary,
                                                        contentDescription = null,
                                                        tint = Color(0xFF64748B),
                                                        modifier = Modifier.size(52.dp)
                                                    )
                                                }
                                            }

                                            else -> SubcomposeAsyncImageContent()
                                        }
                                    }
                                    Surface(
                                        modifier = Modifier
                                            .align(Alignment.BottomStart)
                                            .padding(10.dp),
                                        color = Color.Black.copy(alpha = 0.60f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = "手背检测",
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxSize()
                                        .background(Color(0xFFCBD5E1))
                                ) {
                                    SubcomposeAsyncImage(
                                        model = palmFile,
                                        contentDescription = "手心检测",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    ) {
                                        when (painter.state) {
                                            is coil.compose.AsyncImagePainter.State.Loading,
                                            is coil.compose.AsyncImagePainter.State.Error -> {
                                                Box(
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.PhotoLibrary,
                                                        contentDescription = null,
                                                        tint = Color(0xFF64748B),
                                                        modifier = Modifier.size(52.dp)
                                                    )
                                                }
                                            }

                                            else -> SubcomposeAsyncImageContent()
                                        }
                                    }
                                    Surface(
                                        modifier = Modifier
                                            .align(Alignment.BottomStart)
                                            .padding(10.dp),
                                        color = Color.Black.copy(alpha = 0.60f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = "手心检测",
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(6f)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = cardBg),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(24.dp),
                            horizontalArrangement = Arrangement.spacedBy(24.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(120.dp)
                                    .clip(CircleShape)
                                    .border(6.dp, if (isTempNormal) successBg else dangerBg, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = tempValue,
                                        fontSize = 36.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isTempNormal) success else danger
                                    )
                                    Text(
                                        text = tempLabel,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isTempNormal) success else danger
                                    )
                                }
                            }

                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                                    InfoItem(label = "姓名", value = currentRecord.userName, textMain = textMain, textMuted = textMuted, modifier = Modifier.weight(1f))
                                    InfoItem(label = "员工编号 (工号)", value = currentRecord.employeeId.ifBlank { "--" }, textMain = textMain, textMuted = textMuted, modifier = Modifier.weight(1f))
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.Top) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = "健康证状态", fontSize = 13.sp, color = textMuted)
                                        Spacer(modifier = Modifier.height(6.dp))
                                        StatusBadge(
                                            text = healthBadge.third,
                                            bg = healthBadge.first,
                                            fg = healthBadge.second
                                        )
                                    }
                                    InfoItem(label = "身体不适申报", value = symptomDisplay, textMain = textMain, textMuted = textMuted, modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = cardBg),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFF8FAFC))
                                    .padding(horizontal = 18.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Description,
                                        contentDescription = null,
                                        tint = primaryBlue,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "检测详情核验",
                                        color = textMain,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                Button(
                                    onClick = {
                                        if (isEditing) {
                                            temperature = currentRecord.temperature.toString()
                                            handStatus = currentRecord.handStatus.toChineseLabel()
                                            healthCertStatus = currentRecord.healthCertStatus.toChineseLabel()
                                            symptomFlags = currentRecord.symptomFlags.joinToString("、") { it.toChineseLabel() }
                                            remark = currentRecord.remark
                                            isEditing = false
                                        } else {
                                            isEditing = true
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = primaryLight),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = ButtonDefaults.ContentPadding
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = null,
                                        tint = primaryBlue,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (isEditing) "取消修改" else "人工复核 / 修改",
                                        color = primaryBlue,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                            Divider(color = borderColor)

                            val handText = currentRecord.handStatus.toChineseLabel()

                            val handColor = when (currentRecord.handStatus) {
                                HandStatus.NORMAL -> success
                                HandStatus.ABNORMAL -> danger
                                HandStatus.NOT_CHECKED -> warning
                            }

                            DetailRow(
                                label = "AI 手部卫生判定",
                                value = handText,
                                valueColor = handColor,
                                borderColor = borderColor
                            )

                            val remarkText = currentRecord.remark.ifBlank { "暂无备注说明" }
                            DetailRow(
                                label = "管理员备注",
                                value = remarkText,
                                valueColor = if (currentRecord.remark.isBlank()) textMuted else textMain,
                                borderColor = borderColor,
                                showDivider = false
                            )

                            if (isEditing) {
                                Divider(color = borderColor)

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(18.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    EditField(label = "体温", value = temperature, enabled = true) { temperature = it }
                                    EditField(label = "手部情况（正常/异常/未检测）", value = handStatus, enabled = true) { handStatus = it }
                                    EditField(label = "健康证状态（有效/即将过期/已过期）", value = healthCertStatus, enabled = true) { healthCertStatus = it }
                                    EditField(label = "身体不适（顿号或逗号分隔）", value = symptomFlags, enabled = true) { symptomFlags = it }
                                    EditField(label = "备注", value = remark, enabled = true) { remark = it }

                                    Button(
                                        modifier = Modifier.fillMaxWidth(),
                                        onClick = {
                                            viewModel.updateRecord(
                                                temperature = temperature.toFloatOrNull() ?: 0f,
                                                handStatus = handStatus,
                                                healthCertStatus = healthCertStatus,
                                                symptomFlags = symptomFlags,
                                                remark = remark
                                            )
                                            isEditing = false
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = primaryBlue),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text(
                                            text = "保存修改",
                                            color = Color.White,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoItem(
    label: String,
    value: String,
    textMain: Color,
    textMuted: Color,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = value,
            fontSize = 17.sp,
            color = textMain,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun StatusBadge(
    text: String,
    bg: Color,
    fg: Color
) {
    Surface(color = bg, shape = RoundedCornerShape(8.dp)) {
        Text(
            text = text,
            color = fg,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    valueColor: Color,
    borderColor: Color,
    showDivider: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 15.sp,
            color = Color(0xFF64748B),
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = value,
            fontSize = 15.sp,
            color = valueColor,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
    if (showDivider) {
        Divider(color = borderColor)
    }
}

@Composable
private fun EditField(
    label: String,
    value: String,
    enabled: Boolean,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label, fontSize = Dimens.TextSizeSmall) },
        singleLine = true,
        enabled = enabled,
        textStyle = MaterialTheme.typography.bodyMedium
    )
}
