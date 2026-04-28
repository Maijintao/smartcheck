package com.smartcheck.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import com.smartcheck.app.data.db.RecordEntity
import com.smartcheck.app.domain.model.HandStatus
import com.smartcheck.app.domain.model.HealthCertStatus
import com.smartcheck.app.ui.theme.Dimens
import com.smartcheck.app.utils.FileUtil
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RecordDetailDialog(
    record: RecordEntity,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }

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

    val isTempNormal = record.isTempNormal
    val tempValue = String.format(Locale.getDefault(), "%.1f", record.temperature)
    val tempLabel = if (isTempNormal) "体温正常" else "体温异常"

    val healthStatus = runCatching {
        HealthCertStatus.valueOf(record.healthCertStatus)
    }.getOrNull()

    val healthBadge = when (healthStatus) {
        HealthCertStatus.VALID -> Triple(successBg, success, "有效 (VALID)")
        HealthCertStatus.EXPIRING_SOON -> Triple(warningBg, warning, "临期 (EXPIRING_SOON)")
        HealthCertStatus.EXPIRED -> Triple(dangerBg, danger, "过期 (EXPIRED)")
        null -> Triple(primaryLight, primaryBlue, if (record.healthCertStatus.isBlank()) "未知" else "未知 (${record.healthCertStatus})")
    }

    val symptomDisplay = record.symptomFlags.ifBlank { "无" }

    val handStatus = runCatching {
        HandStatus.valueOf(record.handStatus)
    }.getOrNull() ?: HandStatus.NOT_CHECKED

    val handText = when (handStatus) {
        HandStatus.NORMAL -> "合规 (NORMAL)"
        HandStatus.ABNORMAL -> "异常 (ABNORMAL)"
        HandStatus.NOT_CHECKED -> "未检测 (NOT_CHECKED)"
    }

    val handColor = when (handStatus) {
        HandStatus.NORMAL -> success
        HandStatus.ABNORMAL -> danger
        HandStatus.NOT_CHECKED -> warning
    }

    val passBadge = if (record.isPassed) {
        Triple(successBg, success, "通过")
    } else {
        Triple(dangerBg, danger, "不合格")
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        AnimatedVisibility(
            visible = true,
            enter = fadeIn() + scaleIn(initialScale = 0.96f)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(0.88f),
                shape = RoundedCornerShape(16.dp),
                color = bgMain
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Surface(color = cardBg, shadowElevation = 0.dp) {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp, vertical = 18.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "晨检记录详情",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = textMain,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "检测时间: ${dateFormat.format(Date(record.checkTime))}",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = textMuted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    IconButton(onClick = onDismiss) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "关闭",
                                            tint = textMuted
                                        )
                                    }
                                }
                            }

                            Divider(color = borderColor)
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(bgMain)
                            .padding(24.dp),
                        horizontalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(5f)
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(18.dp)
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

                                    val faceFile = FileUtil.getRecordImageFile(context, record.faceImagePath)
                                    val faceGradient = Brush.linearGradient(
                                        listOf(Color(0xFFE2E8F0), Color(0xFFCBD5E1))
                                    )
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(300.dp)
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
                                            .height(160.dp)
                                            .background(bgMain)
                                    ) {
                                        val backFile = FileUtil.getRecordImageFile(context, record.handBackPath)
                                        val palmFile = FileUtil.getRecordImageFile(context, record.handPalmPath)

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
                                                                tint = textMuted,
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
                                                                tint = textMuted,
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
                            verticalArrangement = Arrangement.spacedBy(18.dp)
                        ) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = cardBg),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(20.dp),
                                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(108.dp)
                                            .border(6.dp, if (isTempNormal) successBg else dangerBg, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                text = tempValue,
                                                fontSize = 34.sp,
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

                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                                            InfoItem(label = "姓名", value = record.userName, textMain = textMain, textMuted = textMuted, modifier = Modifier.weight(1f))
                                            InfoItem(label = "员工编号 (工号)", value = record.employeeId.ifBlank { "--" }, textMain = textMain, textMuted = textMuted, modifier = Modifier.weight(1f))
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.Top) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(text = "健康证状态", fontSize = 13.sp, color = textMuted)
                                                Spacer(modifier = Modifier.height(6.dp))
                                                StatusBadge(text = healthBadge.third, bg = healthBadge.first, fg = healthBadge.second)
                                            }
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(text = "本次判定", fontSize = 13.sp, color = textMuted)
                                                Spacer(modifier = Modifier.height(6.dp))
                                                StatusBadge(text = passBadge.third, bg = passBadge.first, fg = passBadge.second)
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
                                            text = "检测详情核验",
                                            color = textMain,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    Divider(color = borderColor)

                                    DetailRow(
                                        label = "AI 手部卫生判定",
                                        value = handText,
                                        valueColor = handColor,
                                        borderColor = borderColor
                                    )

                                    DetailRow(
                                        label = "身体不适申报",
                                        value = symptomDisplay,
                                        valueColor = if (symptomDisplay == "无") success else danger,
                                        borderColor = borderColor
                                    )

                                    val remarkText = record.remark.ifBlank { "暂无备注说明" }
                                    DetailRow(
                                        label = "管理员备注",
                                        value = remarkText,
                                        valueColor = if (record.remark.isBlank()) textMuted else textMain,
                                        borderColor = borderColor,
                                        showDivider = false
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(0.dp))
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
            fontSize = 16.sp,
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
