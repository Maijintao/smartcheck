package com.smartcheck.app.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.smartcheck.app.data.db.RecordEntity
import com.smartcheck.app.ui.components.RecordDetailDialog
import com.smartcheck.app.ui.theme.Dimens
import com.smartcheck.app.viewmodel.RecordsViewModel
import com.smartcheck.app.viewmodel.RecordsViewModel.TimeFilter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AdminScreen(
    onNavigateBack: () -> Unit,
    onNavigateExport: (() -> Unit)? = null,
    onNavigateRecordDetail: ((Long) -> Unit)? = null,
    viewModel: RecordsViewModel = hiltViewModel()
) {
    val records by viewModel.records.collectAsState()
    val query by viewModel.query.collectAsState()
    var selectedTimeFilter by remember { mutableStateOf(TimeFilter.TODAY) }
    var statusFilter by remember { mutableStateOf("") }
    var selectedRecord by remember { mutableStateOf<RecordEntity?>(null) }

    // 同步时间筛选
    LaunchedEffect(selectedTimeFilter) {
        viewModel.setTimeFilter(selectedTimeFilter)
    }

    // 同步状态筛选
    LaunchedEffect(statusFilter) {
        val statusValue = when (statusFilter) {
            "通过" -> "NORMAL"
            "不合格" -> "ABNORMAL"
            else -> ""
        }
        viewModel.setHandStatusFilter(statusValue)
    }

    // 时间筛选选项
    val timeFilterOptions = listOf(
        "今天" to TimeFilter.TODAY,
        "本周" to TimeFilter.WEEK,
        "本月" to TimeFilter.MONTH,
        "全部" to TimeFilter.ALL
    )

    // 状态筛选选项
    val statusOptions = listOf("全部", "通过", "不合格")

    val primaryBlue = Color(0xFF2563EB)
    val primaryLight = Color(0xFFEFF6FF)
    val bgMain = Color(0xFFF1F5F9)
    val textMain = Color(0xFF1E293B)
    val textMuted = Color(0xFF64748B)
    val borderColor = Color(0xFFE2E8F0)
    val success = Color(0xFF10B981)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgMain)
    ) {
        // 顶部栏
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color.White,
            shadowElevation = 2.dp
        ) {
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
                            text = "晨检记录",
                            color = textMain,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    if (onNavigateExport != null) {
                        Button(
                            onClick = onNavigateExport,
                            colors = ButtonDefaults.buttonColors(containerColor = success),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(text = "导出记录报表", color = Color.White, fontSize = 14.sp)
                        }
                    }
                }
                Divider(color = borderColor)
            }
        }

        // 筛选栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 日期筛选
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("日期：", fontSize = 15.sp, color = textMain, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.width(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    timeFilterOptions.forEach { (label, filter) ->
                        val isSelected = selectedTimeFilter == filter
                        FilterChip(
                            text = label,
                            isSelected = isSelected,
                            primaryBlue = primaryBlue,
                            textMuted = textMuted,
                            onClick = { selectedTimeFilter = filter }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // 状态筛选
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("状态：", fontSize = 15.sp, color = textMain, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.width(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    statusOptions.forEach { option ->
                        val isSelected = (option == "全部" && statusFilter.isEmpty()) || statusFilter == option
                        FilterChip(
                            text = option,
                            isSelected = isSelected,
                            primaryBlue = primaryBlue,
                            textMuted = textMuted,
                            onClick = {
                                statusFilter = if (option == "全部") "" else option
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // 搜索框
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::setQuery,
                modifier = Modifier
                    .width(240.dp)
                    .height(44.dp),
                singleLine = true,
                placeholder = { Text("搜索姓名/工号", fontSize = 14.sp, color = textMuted) },
                trailingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = textMuted,
                        modifier = Modifier.size(18.dp)
                    )
                },
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp)
            )
        }

        // 表格区域
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
        ) {
            TableHeader()
            if (records.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFFF9FAFB)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "暂无记录",
                        color = Color(0xFF6B7280),
                        fontSize = Dimens.TextSizeNormal
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(records) { record ->
                        RecordTableRow(
                            record = record,
                            onView = {
                                selectedRecord = record
                            },
                            onEdit = {
                                onNavigateRecordDetail?.invoke(record.id)
                            },
                            onClick = { selectedRecord = record }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }

    selectedRecord?.let { record ->
        RecordDetailDialog(
            record = record,
            onDismiss = { selectedRecord = null }
        )
    }
}

@Composable
private fun FilterChip(
    text: String,
    isSelected: Boolean,
    primaryBlue: Color,
    textMuted: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (isSelected) primaryBlue else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            color = if (isSelected) Color.White else textMuted,
            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
        )
    }
}

@Composable
private fun TableHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF8FAFC))
            .padding(vertical = 12.dp, horizontal = Dimens.PaddingNormal),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HeaderCell(text = "日期", width = 180.dp)
        HeaderCell(text = "姓名", width = 120.dp)
        HeaderCell(text = "体温", width = 90.dp)
        HeaderCell(text = "手部情况", width = 120.dp)
        HeaderCell(text = "其他身体不适", width = 180.dp)
        HeaderCell(text = "操作", width = 140.dp)
    }
}

@Composable
private fun HeaderCell(text: String, width: Dp) {
    Text(
        text = text,
        modifier = Modifier.width(width),
        color = Color(0xFF6B7280),
        fontSize = Dimens.TextSizeSmall,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun RecordTableRow(
    record: RecordEntity,
    onView: () -> Unit,
    onEdit: () -> Unit,
    onClick: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    val tempHigh = record.temperature >= 37.3f
    val handIssue = record.handStatus.contains("异常") || !record.isHandNormal
    val warningRow = tempHigh || handIssue
    val background = if (warningRow) Color(0xFFFFF1F1) else Color.White
    val textColor = if (warningRow) MaterialTheme.colorScheme.error else Color(0xFF111827)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = Dimens.PaddingNormal),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BodyCell(text = dateFormat.format(Date(record.checkTime)), width = 180.dp, color = textColor)
        BodyCell(text = record.userName, width = 120.dp, color = textColor)
        BodyCell(text = "%.1f°C".format(record.temperature), width = 90.dp, color = textColor)
        BodyCell(text = record.handStatus.ifBlank { "--" }, width = 120.dp, color = textColor)
        BodyCell(text = record.symptomFlags.ifBlank { "无" }, width = 180.dp, color = textColor)
        Row(
            modifier = Modifier.width(140.dp),
            horizontalArrangement = Arrangement.spacedBy(Dimens.PaddingNormal),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "查看",
                color = Color(0xFF2563EB),
                fontSize = Dimens.TextSizeSmall,
                modifier = Modifier.clickable(onClick = onView)
            )
            Text(
                text = "编辑",
                color = Color(0xFF2563EB),
                fontSize = Dimens.TextSizeSmall,
                modifier = Modifier.clickable(onClick = onEdit)
            )
        }
    }
}

@Composable
private fun BodyCell(text: String, width: Dp, color: Color) {
    Text(
        text = text,
        modifier = Modifier.width(width),
        color = color,
        fontSize = Dimens.TextSizeSmall
    )
}
