package com.smartcheck.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.smartcheck.app.ui.theme.Dimens
import com.smartcheck.app.viewmodel.CloudImportViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmployeeCloudImportScreen(
    onNavigateBack: () -> Unit,
    viewModel: CloudImportViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    val displayedPageIndex = uiState.pageIndex + 1
    var pageIndexText by remember(uiState.pageIndex) {
        mutableStateOf(displayedPageIndex.toString())
    }

    val primaryBlue = Color(0xFF2563EB)
    val primaryLight = Color(0xFFEFF6FF)
    val bgMain = Color(0xFFF1F5F9)
    val cardBg = Color.White
    val textMain = Color(0xFF1E293B)
    val textMuted = Color(0xFF64748B)
    val borderColor = Color(0xFFE2E8F0)
    val inputBg = Color(0xFFF8FAFC)
    val danger = Color(0xFFEF4444)

    LaunchedEffect(uiState.importSuccess) {
        if (uiState.importSuccess) {
            // 导入成功后会弹出对话框，这里不需要额外处理
        }
    }

    Scaffold(
        topBar = {
            Surface(color = cardBg, shadowElevation = 0.dp) {
                Column {
                    TopAppBar(
                        title = {
                            Text(
                                text = "从云端同步员工",
                                color = textMain,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        },
                        navigationIcon = {
                            Box(
                                modifier = Modifier
                                    .padding(start = 16.dp)
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
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = cardBg,
                            titleContentColor = textMain,
                            navigationIconContentColor = textMain
                        )
                    )
                    Divider(color = borderColor)
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(bgMain),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(
                modifier = Modifier
                    .widthIn(max = 760.dp)
                    .fillMaxWidth()
                    .padding(32.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            tint = primaryBlue,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "接口参数配置",
                            fontSize = 17.sp,
                            color = textMain,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Divider(color = borderColor)

                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(
                            text = "设备编码 (SN)",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = textMuted
                        )
                        TextField(
                            value = uiState.deviceSn,
                            onValueChange = { viewModel.setDeviceSn(it) },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("请输入绑定的云端设备编码 (yg_sn)", fontSize = 14.sp) },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = inputBg,
                                disabledContainerColor = inputBg,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                disabledIndicatorColor = Color.Transparent,
                                cursorColor = primaryBlue,
                                focusedTextColor = textMain,
                                unfocusedTextColor = textMain
                            ),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "获取页码",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = textMuted
                            )
                            TextField(
                                value = pageIndexText,
                                onValueChange = {
                                    val sanitized = it.filter(Char::isDigit)
                                    pageIndexText = sanitized

                                    val displayIndex = sanitized.toIntOrNull() ?: return@TextField
                                    viewModel.setPageIndex((displayIndex - 1).coerceAtLeast(0))
                                },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("1", fontSize = 14.sp) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.White,
                                    unfocusedContainerColor = inputBg,
                                    disabledContainerColor = inputBg,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    disabledIndicatorColor = Color.Transparent,
                                    cursorColor = primaryBlue,
                                    focusedTextColor = textMain,
                                    unfocusedTextColor = textMain
                                ),
                                shape = RoundedCornerShape(10.dp)
                            )
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "每页获取条数 (1-100)",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = textMuted
                            )
                            TextField(
                                value = uiState.pageSize,
                                onValueChange = {
                                    val sanitized = it.filter(Char::isDigit)
                                    viewModel.setPageSize(sanitized)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("50", fontSize = 14.sp) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.White,
                                    unfocusedContainerColor = inputBg,
                                    disabledContainerColor = inputBg,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    disabledIndicatorColor = Color.Transparent,
                                    cursorColor = primaryBlue,
                                    focusedTextColor = textMain,
                                    unfocusedTextColor = textMain
                                ),
                                shape = RoundedCornerShape(10.dp)
                            )
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Button(
                            onClick = { viewModel.fetchEmployees() },
                            enabled = uiState.deviceSn.isNotBlank() && !uiState.isLoading,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = primaryBlue,
                                disabledContainerColor = Color(0xFF93C5FD)
                            ),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.CloudDownload, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("获取员工信息", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // 加载状态
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier
                        .widthIn(max = 760.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = primaryBlue)
                }
            }

            // 错误信息
            uiState.error?.let { error ->
                Surface(
                    modifier = Modifier
                        .widthIn(max = 760.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp),
                    color = Color(0xFFFFF1F2),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = error,
                            color = danger,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        TextButton(onClick = { viewModel.clearError() }) {
                            Text(text = "关闭", color = textMain)
                        }
                    }
                }
            }

            val previewShape = RoundedCornerShape(16.dp)
            Surface(
                modifier = Modifier
                    .widthIn(max = 760.dp)
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 32.dp)
                    .drawBehind {
                        drawRoundRect(
                            color = Color(0xFFCBD5E1),
                            cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx()),
                            style = Stroke(
                                width = 2.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(
                                    floatArrayOf(10.dp.toPx(), 6.dp.toPx())
                                )
                            )
                        )
                    },
                color = Color.White.copy(alpha = 0.5f),
                shape = previewShape
            ) {
                if (uiState.employees.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        if (uiState.isLoading) {
                            CircularProgressIndicator(color = primaryBlue)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "正在获取员工信息…",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF94A3B8)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                tint = Color(0xFFCBD5E1),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "配置参数并点击获取，同步数据将在此处显示",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "共 ${uiState.total} 条记录，当前 ${uiState.employees.size} 条",
                                color = textMuted,
                                fontSize = 14.sp
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                TextButton(onClick = { viewModel.selectAll(true) }) {
                                    Text("全选", color = primaryBlue)
                                }
                                TextButton(onClick = { viewModel.selectAll(false) }) {
                                    Text("取消", color = primaryBlue)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(uiState.employees) { employee ->
                                EmployeeCloudItem(
                                    employee = employee,
                                    primaryBlue = primaryBlue,
                                    primaryLight = primaryLight,
                                    borderColor = borderColor,
                                    textMain = textMain,
                                    textMuted = textMuted,
                                    onToggle = { viewModel.toggleEmployeeSelection(employee.employeeId) }
                                )
                            }
                        }

                        val selectedCount = uiState.employees.count { it.selected }
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { viewModel.importSelectedEmployees() },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = selectedCount > 0 && !uiState.isLoading,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = primaryBlue,
                                disabledContainerColor = Color(0xFF93C5FD)
                            ),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "导入已选员工 ($selectedCount)",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        // 导入结果对话框
        if (uiState.importSuccess && uiState.importResult != null) {
            AlertDialog(
                onDismissRequest = { 
                    viewModel.clearImportResult()
                    onNavigateBack()
                },
                title = { Text("导入完成") },
                text = {
                    Column {
                        Text("总数：${uiState.importResult?.total}")
                        Text("成功：${uiState.importResult?.success}")
                        Text("失败：${uiState.importResult?.failed}")
                    }
                },
                confirmButton = {
                    TextButton(onClick = { 
                        viewModel.clearImportResult()
                        onNavigateBack()
                    }) {
                        Text("确定")
                    }
                }
            )
        }
    }
}

@Composable
private fun EmployeeCloudItem(
    employee: CloudImportViewModel.CloudEmployeeItem,
    primaryBlue: Color,
    primaryLight: Color,
    borderColor: Color,
    textMain: Color,
    textMuted: Color,
    onToggle: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(14.dp)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(
                interactionSource = interactionSource,
                indication = rememberRipple(),
                onClick = onToggle
            )
            .drawBehind {
                drawRoundRect(
                    color = borderColor,
                    cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx()),
                    style = Stroke(width = 1.dp.toPx())
                )
            },
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = if (employee.selected) primaryLight else Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (employee.selected) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                    contentDescription = if (employee.selected) "取消选择" else "选择",
                    tint = if (employee.selected) primaryBlue else Color(0xFF94A3B8)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = employee.name.ifBlank { "未命名" },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = textMain,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = "工号：${employee.employeeId}",
                    color = textMuted,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (employee.phone.isNotBlank() || employee.position.isNotBlank() || employee.healthCertCode.isNotBlank()) {
                    val extras = buildList {
                        if (employee.phone.isNotBlank()) add("手机：${employee.phone}")
                        if (employee.position.isNotBlank()) add("职位：${employee.position}")
                        if (employee.healthCertCode.isNotBlank()) add("健康证：${employee.healthCertCode}")
                    }.joinToString("  ·  ")

                    Text(
                        text = extras,
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
