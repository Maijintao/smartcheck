package com.smartcheck.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.graphics.vector.ImageVector
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

    val displayName = if (canteenName.isBlank()) "紫马科技" else canteenName
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
                userName = userName,
                adminAvatar = adminAvatar,
                onSettingsClick = onNavigateSettings,
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
    userName: String,
    adminAvatar: String,
    onSettingsClick: () -> Unit,
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
            // 机构名
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp),
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

            Spacer(modifier = Modifier.height(14.dp))

            // 设置 + 超级管理员标签
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    color = primaryLight,
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .clickable(onClick = onSettingsClick)
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            tint = primaryBlue,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "设置",
                            fontSize = 14.sp,
                            color = primaryBlue,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Surface(
                    color = primaryLight,
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = primaryBlue,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = userName,
                            fontSize = 14.sp,
                            color = primaryBlue,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "标准晨检流程",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = textMain
            )

            Spacer(modifier = Modifier.height(16.dp))

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
                        text = "退出登录",
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
        StepItem("刷脸验温", "面部识别与体温检测", Icons.Default.VerifiedUser),
        StepItem("手部双面识别", "检查手心手背卫生", Icons.Default.Badge),
        StepItem("身体不适确认", "确认当日健康状况", Icons.Default.Assessment),
        StepItem("生成晨检记录", "完成打卡并存档", Icons.Default.ListAlt)
    )

    Column {
        steps.forEachIndexed { index, item ->
            val isLast = index == steps.lastIndex
            val stepNumber = index + 1

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFF8FAFC))
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 蓝色圆形图标
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(primaryBlue, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        fontSize = 15.sp,
                        color = textMain,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = item.subtitle,
                        fontSize = 12.sp,
                        color = textMuted
                    )
                }

                // 编号
                Text(
                    text = "0$stepNumber",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = primaryBlue.copy(alpha = 0.15f)
                )
            }

            if (!isLast) {
                Spacer(modifier = Modifier.height(10.dp))
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
    onNavigateCheck: () -> Unit,
    onNavigateEmployees: () -> Unit,
    onNavigateRecords: () -> Unit,
    onNavigateExport: () -> Unit
) {
    // 功能卡片区域 - 2x2网格
    FeatureCardLayout(
        modifier = modifier.fillMaxSize(),
        primaryBlue = primaryBlue,
        textMain = textMain,
        textMuted = textMuted,
        onNavigateCheck = onNavigateCheck,
        onNavigateEmployees = onNavigateEmployees,
        onNavigateRecords = onNavigateRecords,
        onNavigateExport = onNavigateExport
    )
}

@Composable
private fun FeatureCardLayout(
    modifier: Modifier,
    primaryBlue: Color,
    textMain: Color,
    textMuted: Color,
    onNavigateCheck: () -> Unit,
    onNavigateEmployees: () -> Unit,
    onNavigateRecords: () -> Unit,
    onNavigateExport: () -> Unit
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // 第一行：大图(我要晨检) + 小图(员工管理)
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            PrimaryFeatureCard(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                primaryBlue = primaryBlue,
                onClick = onNavigateCheck
            )

            SmallFeatureCard(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                label = "员工管理",
                description = "录入与管理员工面部及基本信息",
                icon = Icons.Default.Badge,
                iconBg = Color(0xFFE0E7FF),
                iconTint = Color(0xFF4F46E5),
                textMain = textMain,
                textMuted = textMuted,
                onClick = onNavigateEmployees
            )
        }

        // 第二行：小图(晨检记录) + 小图(报表导出)
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            SmallFeatureCard(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                label = "晨检记录",
                description = "查看历史打卡详情与异常状态",
                icon = Icons.Default.ListAlt,
                iconBg = Color(0xFFDCFCE7),
                iconTint = Color(0xFF16A34A),
                textMain = textMain,
                textMuted = textMuted,
                onClick = onNavigateRecords
            )

            SmallFeatureCard(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                label = "报表导出",
                description = "生成并导出每日/每周统计报表",
                icon = Icons.Default.Assessment,
                iconBg = Color(0xFFFEF08A),
                iconTint = Color(0xFFCA8A04),
                textMain = textMain,
                textMuted = textMuted,
                onClick = onNavigateExport
            )
        }
    }
}

@Composable
private fun PrimaryFeatureCard(
    modifier: Modifier = Modifier,
    primaryBlue: Color,
    onClick: () -> Unit
) {
    val gradientBrush = Brush.linearGradient(
        colors = listOf(Color(0xFF3B82F6), Color(0xFF60A5FA)),
        start = androidx.compose.ui.geometry.Offset(0f, 0f),
        end = androidx.compose.ui.geometry.Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(gradientBrush)
                .padding(32.dp)
        ) {
            // 盾牌装饰图标在右下
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .align(Alignment.BottomEnd)
                    .offset(x = 10.dp, y = 10.dp)
                    .background(
                        color = Color.White.copy(alpha = 0.15f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.VerifiedUser,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.4f),
                    modifier = Modifier.size(56.dp)
                )
            }

            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Top
            ) {
                Text(
                    text = "我要晨检",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "开始每日员工健康打卡",
                    fontSize = 16.sp,
                    color = Color.White.copy(alpha = 0.85f)
                )
            }
        }
    }
}

@Composable
private fun SmallFeatureCard(
    modifier: Modifier = Modifier,
    label: String,
    description: String,
    icon: ImageVector,
    iconBg: Color,
    iconTint: Color,
    textMain: Color,
    textMuted: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(28.dp)
        ) {
            // 图标装饰在右下
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .align(Alignment.BottomEnd)
                    .offset(x = 10.dp, y = 10.dp)
                    .background(
                        color = iconBg.copy(alpha = 0.5f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint.copy(alpha = 0.4f),
                    modifier = Modifier.size(48.dp)
                )
            }

            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Top
            ) {
                Text(
                    text = label,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = textMain
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = description,
                    fontSize = 15.sp,
                    color = textMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private data class StepItem(
    val title: String,
    val subtitle: String,
    val icon: ImageVector
)
