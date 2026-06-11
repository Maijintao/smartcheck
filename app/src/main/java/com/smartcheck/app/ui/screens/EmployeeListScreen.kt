package com.smartcheck.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.drawBehind
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import com.smartcheck.app.utils.FileUtil
import com.smartcheck.app.viewmodel.EmployeeListViewModel

@Composable
fun EmployeeListScreen(
    onNavigateBack: () -> Unit,
    onNavigateEmployeeDetail: (String) -> Unit,
    onNavigateEmployeeNew: () -> Unit,
    onNavigateCloudImport: () -> Unit,
    viewModel: EmployeeListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    val primaryBlue = Color(0xFF2563EB)
    val primaryLight = Color(0xFFEFF6FF)
    val bgMain = Color(0xFFF1F5F9)
    val textMain = Color(0xFF1E293B)
    val textMuted = Color(0xFF64748B)
    val borderColor = Color(0xFFE2E8F0)

    Column(modifier = Modifier.fillMaxSize().background(bgMain)) {
        // 顶部栏
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color.White,
            shadowElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
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
                        text = "员工管理",
                        color = textMain,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                Row(
                    modifier = Modifier.widthIn(max = 560.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    EmployeeSearchField(
                        value = uiState.query,
                        onValueChange = { viewModel.setQuery(it) },
                        primaryBlue = primaryBlue,
                        bgMain = bgMain,
                        textMuted = textMuted,
                        modifier = Modifier
                            .weight(1f)
                            .widthIn(min = 240.dp, max = 360.dp)
                            .height(44.dp)
                    )
                    PillActionButton(
                        text = "批量导入",
                        icon = Icons.Default.CloudUpload,
                        container = primaryLight,
                        contentColor = primaryBlue,
                        onClick = onNavigateCloudImport
                    )
                }
            }
        }

        val items = listOf<EmployeeListViewModel.EmployeeListItem?>(null) + uiState.items

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 200.dp),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(32.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            itemsIndexed(items) { index, employee ->
                if (index == 0) {
                    AddEmployeeCard(
                        onClick = onNavigateEmployeeNew,
                        primaryBlue = primaryBlue,
                        textMuted = textMuted,
                        borderColor = Color(0xFFCBD5E1)
                    )
                } else if (employee != null) {
                    EmployeeCard(
                        employee = employee,
                        onClick = { onNavigateEmployeeDetail(employee.id) },
                        textMain = textMain,
                        textMuted = textMuted
                    )
                }
            }
        }

        if (uiState.totalPages > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                PageNavButton(
                    enabled = uiState.pageIndex > 0,
                    icon = Icons.Default.KeyboardArrowLeft,
                    bgMain = bgMain,
                    borderColor = borderColor,
                    textMain = textMain,
                    textMuted = textMuted,
                    onClick = { viewModel.prevPage() }
                )

                Spacer(modifier = Modifier.width(14.dp))

                Text(
                    text = "第 ${uiState.pageIndex + 1} / ${uiState.totalPages} 页",
                    fontSize = 14.sp,
                    color = textMuted
                )

                Spacer(modifier = Modifier.width(14.dp))

                PageNavButton(
                    enabled = uiState.pageIndex < uiState.totalPages - 1,
                    icon = Icons.Default.KeyboardArrowLeft,
                    bgMain = bgMain,
                    borderColor = borderColor,
                    textMain = textMain,
                    textMuted = textMuted,
                    onClick = { viewModel.nextPage() }
                )
            }
        }
    }
}

@Composable
private fun AddEmployeeCard(
    onClick: () -> Unit,
    primaryBlue: Color,
    textMuted: Color,
    borderColor: Color
) {
    val interactionSource = remember { MutableInteractionSource() }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = rememberRipple(),
                onClick = onClick
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .dashedBorder(2.dp, borderColor, 16.dp)
                .background(Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // 蓝色圆形+按钮
                Surface(
                    color = primaryBlue,
                    shape = CircleShape,
                    shadowElevation = 2.dp
                ) {
                    Box(
                        modifier = Modifier.size(64.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "新增员工",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "新增员工",
                    color = textMuted,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun EmployeeCard(
    employee: EmployeeListViewModel.EmployeeListItem,
    onClick: () -> Unit,
    textMain: Color,
    textMuted: Color
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val interactionSource = remember { MutableInteractionSource() }

    val status = certStatus(employee.daysRemaining)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = rememberRipple(),
                onClick = onClick
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            val photoGradient = Brush.linearGradient(listOf(Color(0xFFE0E7FF), Color(0xFFC7D2FE)))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.65f)
                    .background(photoGradient)
            ) {
                val facePath = employee.faceImagePath
                if (!facePath.isNullOrBlank()) {
                    val imageFile = FileUtil.getRecordImageFile(context, facePath)
                    val request = ImageRequest.Builder(context)
                        .data(imageFile)
                        .crossfade(true)
                        .build()

                    SubcomposeAsyncImage(
                        model = request,
                        contentDescription = employee.name,
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
                                        tint = Color.White.copy(alpha = 0.80f),
                                        modifier = Modifier.size(88.dp)
                                    )
                                }
                            }
                            else -> SubcomposeAsyncImageContent()
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.80f),
                            modifier = Modifier.size(88.dp)
                        )
                    }
                }

                // 剩余天数标签
                Box(
                    modifier = Modifier
                        .padding(10.dp)
                        .align(Alignment.BottomCenter)
                        .clip(RoundedCornerShape(20.dp))
                        .background(status.bg)
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = status.text,
                        color = status.fg,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.35f)
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = employee.name,
                    color = textMain,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private data class CertStatus(
    val text: String,
    val fg: Color,
    val bg: Color
)

private fun certStatus(daysRemaining: Int): CertStatus {
    val success = Color(0xFF10B981)
    val successBg = Color(0xFFDCFCE7)
    val warning = Color(0xFFF59E0B)
    val warningBg = Color(0xFFFEF3C7)
    val danger = Color(0xFFEF4444)
    val dangerBg = Color(0xFFFEE2E2)

    return when {
        daysRemaining < 0 -> CertStatus(
            text = "已过期 ${kotlin.math.abs(daysRemaining)} 天",
            fg = danger,
            bg = dangerBg
        )
        daysRemaining < 7 -> CertStatus(
            text = "剩余 $daysRemaining 天",
            fg = warning,
            bg = warningBg
        )
        else -> CertStatus(
            text = "剩余 $daysRemaining 天",
            fg = success,
            bg = successBg
        )
    }
}

@Composable
private fun PillActionButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    container: Color,
    contentColor: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(container)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = text,
                color = contentColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun EmployeeSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    primaryBlue: Color,
    bgMain: Color,
    textMuted: Color,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(20.dp)
    val textMain = Color(0xFF1E293B)

    Box(
        modifier = modifier
            .clip(shape)
            .background(bgMain)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = textMuted,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))

            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                cursorBrush = SolidColor(primaryBlue),
                textStyle = TextStyle(
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                    color = textMain
                ),
                decorationBox = { innerTextField ->
                    Box(modifier = Modifier.fillMaxWidth()) {
                        if (value.isBlank()) {
                            Text(
                                text = "搜索姓名/工号",
                                fontSize = 14.sp,
                                color = textMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        innerTextField()
                    }
                }
            )
        }
    }
}

@Composable
private fun PageNavButton(
    enabled: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    bgMain: Color,
    borderColor: Color,
    textMain: Color,
    textMuted: Color,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(shape)
            .background(if (enabled) Color.White else bgMain)
            .drawBehind {
                drawRoundRect(
                    color = borderColor,
                    cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx()),
                    style = Stroke(width = 1.dp.toPx())
                )
            }
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) textMain else textMuted
        )
    }
}

private fun Modifier.dashedBorder(
    width: Dp,
    color: Color,
    cornerRadius: Dp,
    on: Dp = 8.dp,
    off: Dp = 8.dp
): Modifier {
    return drawBehind {
        val strokeWidth = width.toPx()
        val radius = cornerRadius.toPx()
        drawRoundRect(
            color = color,
            style = Stroke(
                width = strokeWidth,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(on.toPx(), off.toPx()), 0f)
            ),
            cornerRadius = CornerRadius(radius, radius)
        )
    }
}
