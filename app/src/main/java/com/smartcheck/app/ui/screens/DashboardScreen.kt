package com.smartcheck.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.smartcheck.app.viewmodel.SettingsViewModel

@Composable
fun DashboardScreen(
    onNavigateCheck: () -> Unit,
    onNavigateEmployees: () -> Unit,
    onNavigateRecords: () -> Unit,
    onNavigateExport: () -> Unit,
    onNavigateSettings: () -> Unit,
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    val canteenName by settingsViewModel.canteenName.collectAsState()
    val adminAvatar by settingsViewModel.adminAvatar.collectAsState()
    val adminName by settingsViewModel.adminName.collectAsState()

    val primaryBlue = Color(0xFF2563EB)
    val primaryLight = Color(0xFFEFF6FF)
    val bgColor = Color(0xFFF1F5F9)
    val textMain = Color(0xFF1E293B)
    val textMuted = Color(0xFF64748B)

    val displayName = if (canteenName.isBlank()) "上海交通大学荔园三食堂" else canteenName
    val userName = if (adminName.isBlank()) "超级管理员" else adminName

    Box(modifier = Modifier.fillMaxSize().background(bgColor)) {
        Row(modifier = Modifier.fillMaxSize()) {
            SidebarPanel(
                modifier = Modifier
                    .width(340.dp)
                    .fillMaxHeight(),
                primaryBlue = primaryBlue,
                primaryLight = primaryLight,
                textMain = textMain,
                textMuted = textMuted,
                locationTitle = displayName,
                onExitClick = onNavigateSettings
            )

            MainPanel(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(24.dp),
                primaryBlue = primaryBlue,
                primaryLight = primaryLight,
                textMain = textMain,
                textMuted = textMuted,
                adminAvatar = adminAvatar,
                userName = userName,
                onNavigateSettings = onNavigateSettings,
                onNavigateCheck = onNavigateCheck,
                onNavigateEmployees = onNavigateEmployees,
                onNavigateRecords = onNavigateRecords,
                onNavigateExport = onNavigateExport
            )
        }
    }
}

@Composable
private fun SidebarPanel(
    modifier: Modifier,
    primaryBlue: Color,
    primaryLight: Color,
    textMain: Color,
    textMuted: Color,
    locationTitle: String,
    onExitClick: () -> Unit
) {
    Surface(
        modifier = modifier,
        color = Color.White,
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 22.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 18.dp),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(primaryLight, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = primaryBlue,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = locationTitle,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = textMain,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "智能晨检终端",
                        fontSize = 13.sp,
                        color = textMuted
                    )
                }
            }

            Divider(color = Color(0xFFE2E8F0))

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "标准晨检流程",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = textMain
            )

            Spacer(modifier = Modifier.height(14.dp))

            MorningCheckStepper(
                primaryBlue = primaryBlue,
                textMain = textMain,
                textMuted = textMuted
            )

            Spacer(modifier = Modifier.weight(1f))

            TextButton(
                onClick = onExitClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Logout,
                        contentDescription = null,
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "退出系统",
                        color = Color(0xFFEF4444),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun MorningCheckStepper(
    primaryBlue: Color,
    textMain: Color,
    textMuted: Color
) {
    val steps = listOf(
        StepItem("刷脸验温", "面部识别与体温检测"),
        StepItem("手部双面识别", "检查手心手背卫生"),
        StepItem("身体不适确认", "确认当日健康状况"),
        StepItem("生成晨检记录", "完成打卡并存档")
    )

    Column {
        steps.forEachIndexed { index, item ->
            val isLast = index == steps.lastIndex

            Row(verticalAlignment = Alignment.Top) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .border(3.dp, primaryBlue, CircleShape)
                            .background(
                                color = if (isLast) primaryBlue else Color.White,
                                shape = CircleShape
                            )
                    )

                    if (!isLast) {
                        Box(
                            modifier = Modifier
                                .width(2.dp)
                                .height(34.dp)
                                .background(Color(0xFFE2E8F0))
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        fontSize = 16.sp,
                        color = textMain,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = item.subtitle,
                        fontSize = 13.sp,
                        color = textMuted
                    )
                }
            }

            if (!isLast) {
                Spacer(modifier = Modifier.height(14.dp))
            }
        }
    }
}

@Composable
private fun MainPanel(
    modifier: Modifier,
    primaryBlue: Color,
    primaryLight: Color,
    textMain: Color,
    textMuted: Color,
    adminAvatar: String,
    userName: String,
    onNavigateSettings: () -> Unit,
    onNavigateCheck: () -> Unit,
    onNavigateEmployees: () -> Unit,
    onNavigateRecords: () -> Unit,
    onNavigateExport: () -> Unit
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateSettings) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "设置",
                    tint = textMuted
                )
            }

            Surface(
                color = Color.White,
                shadowElevation = 2.dp,
                shape = RoundedCornerShape(30.dp)
            ) {
                Row(
                    modifier = Modifier
                        .padding(start = 10.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (adminAvatar.isNotBlank()) {
                        AsyncImage(
                            model = adminAvatar,
                            contentDescription = null,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(primaryLight),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(primaryLight, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = primaryBlue,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = userName,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = textMain,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 180.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        FeatureGrid(
            modifier = Modifier.fillMaxSize(),
            primaryBlue = primaryBlue,
            textMain = textMain,
            textMuted = textMuted,
            onNavigateCheck = onNavigateCheck,
            onNavigateEmployees = onNavigateEmployees,
            onNavigateRecords = onNavigateRecords,
            onNavigateExport = onNavigateExport
        )
    }
}

@Composable
private fun FeatureGrid(
    modifier: Modifier,
    primaryBlue: Color,
    textMain: Color,
    textMuted: Color,
    onNavigateCheck: () -> Unit,
    onNavigateEmployees: () -> Unit,
    onNavigateRecords: () -> Unit,
    onNavigateExport: () -> Unit
) {
    val entries = listOf(
        FeatureEntry(
            label = "我要晨检",
            description = "开始每日员工健康打卡",
            icon = Icons.Default.VerifiedUser,
            kind = FeatureKind.Primary,
            onClick = onNavigateCheck
        ),
        FeatureEntry(
            label = "员工管理",
            description = "录入与管理员工面部及基本信息",
            icon = Icons.Default.Badge,
            kind = FeatureKind.Staff,
            onClick = onNavigateEmployees
        ),
        FeatureEntry(
            label = "晨检记录",
            description = "查看历史打卡详情与异常状态",
            icon = Icons.Default.ListAlt,
            kind = FeatureKind.Record,
            onClick = onNavigateRecords
        ),
        FeatureEntry(
            label = "报表导出",
            description = "生成并导出每日/每周统计报表",
            icon = Icons.Default.Assessment,
            kind = FeatureKind.Report,
            onClick = onNavigateExport
        )
    )

    BoxWithConstraints(modifier = modifier) {
        val itemHeight = (maxHeight - 24.dp) / 2

        LazyVerticalGrid(
            modifier = Modifier.fillMaxSize(),
            columns = GridCells.Fixed(2),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            items(entries) { entry ->
                FeatureCard(
                    entry = entry,
                    primaryBlue = primaryBlue,
                    textMain = textMain,
                    textMuted = textMuted,
                    modifier = Modifier.height(itemHeight)
                )
            }
        }
    }
}

@Composable
private fun FeatureCard(
    entry: FeatureEntry,
    primaryBlue: Color,
    textMain: Color,
    textMuted: Color,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(20.dp)

    val container = when (entry.kind) {
        FeatureKind.Primary -> null
        else -> Color.White
    }

    val iconWrap = when (entry.kind) {
        FeatureKind.Primary -> Color.White.copy(alpha = 0.20f)
        FeatureKind.Staff -> Color(0xFFE0E7FF)
        FeatureKind.Record -> Color(0xFFDCFCE7)
        FeatureKind.Report -> Color(0xFFFEF08A)
    }

    val iconTint = when (entry.kind) {
        FeatureKind.Primary -> Color.White
        FeatureKind.Staff -> Color(0xFF4F46E5)
        FeatureKind.Record -> Color(0xFF16A34A)
        FeatureKind.Report -> Color(0xFFCA8A04)
    }

    val titleColor = if (entry.kind == FeatureKind.Primary) Color.White else textMain
    val descColor = if (entry.kind == FeatureKind.Primary) Color.White else textMuted

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = entry.onClick),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = container ?: Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    if (entry.kind == FeatureKind.Primary) {
                        Brush.linearGradient(listOf(primaryBlue, Color(0xFF3B82F6)))
                    } else {
                        Brush.linearGradient(listOf(Color.White, Color.White))
                    }
                )
                .padding(horizontal = 22.dp, vertical = 22.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(150.dp)
                    .align(Alignment.BottomEnd)
                    .offset(x = 26.dp, y = 26.dp)
                    .background(
                        brush = if (entry.kind == FeatureKind.Primary) {
                            Brush.radialGradient(
                                colors = listOf(Color.White.copy(alpha = 0.12f), Color.Transparent)
                            )
                        } else {
                            Brush.linearGradient(
                                colors = listOf(Color.Black.copy(alpha = 0.03f), Color.Transparent)
                            )
                        },
                        shape = CircleShape
                    )
            )

            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(iconWrap, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = entry.icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = entry.label,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = titleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = entry.description,
                    fontSize = 15.sp,
                    color = descColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private data class StepItem(
    val title: String,
    val subtitle: String
)

private enum class FeatureKind {
    Primary,
    Staff,
    Record,
    Report
}

private data class FeatureEntry(
    val label: String,
    val description: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val kind: FeatureKind,
    val onClick: () -> Unit
)
