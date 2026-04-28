package com.smartcheck.app.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.content.ContentValues
import android.widget.Toast
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.smartcheck.app.data.db.RecordEntity
import com.smartcheck.app.ui.theme.Dimens
import com.smartcheck.app.viewmodel.ReportExportViewModel
import java.io.File
import java.io.FileOutputStream
import java.io.FileNotFoundException
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun ReportExportScreen(
    onNavigateBack: () -> Unit,
    viewModel: ReportExportViewModel = hiltViewModel()
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val records by viewModel.records.collectAsState()
    var dateFilter by remember { mutableStateOf("") }
    var exporting by remember { mutableStateOf(false) }
    var showDateMenu by remember { mutableStateOf(false) }
    var showCustomDateInput by remember { mutableStateOf(false) }
    val historyItems = remember { mutableStateListOf<ExportHistoryItem>() }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        historyItems.clear()
        historyItems.addAll(loadExportHistory(context))
    }

    val primaryBlue = Color(0xFF2563EB)
    val bgMain = Color(0xFFF1F5F9)
    val textMain = Color(0xFF1E293B)
    val textMuted = Color(0xFF64748B)
    val borderColor = Color(0xFFE2E8F0)
    val infoBg = Color(0xFFE0E7FF)
    val infoText = Color(0xFF3730A3)

    val now = System.currentTimeMillis()
    val todayKey = remember(now) { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(now) }
    val monthKey = remember(now) { SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(now) }

    val rangeText = buildRangeText(records, dateFilter)
    val hint = when {
        dateFilter.isBlank() -> "(全部)"
        Regex("\\d{4}-\\d{2}$").matches(dateFilter.trim()) -> "(本月)"
        Regex("\\d{4}-\\d{2}-\\d{2}$").matches(dateFilter.trim()) -> "(当天)"
        else -> ""
    }
    val dateDisplay = when {
        dateFilter.isBlank() -> "全部记录"
        rangeText != "--" -> "$rangeText $hint".trim()
        else -> dateFilter
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgMain)
    ) {
        Surface(color = Color.White, shadowElevation = 0.dp) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
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
                        text = "数据导出中心",
                        color = textMain,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Divider(color = borderColor)
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Surface(color = infoBg, shape = RoundedCornerShape(8.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = infoText,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "提示：导出的报表将保存至设备本地文件夹。如需转发，请前往系统文件管理器进行分享。",
                        color = infoText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            tint = primaryBlue,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "新建导出任务",
                            color = textMain,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFFF8FAFC))
                                .clickable { showDateMenu = true }
                                .padding(horizontal = 16.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = dateDisplay,
                                    color = textMain,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    tint = textMuted
                                )
                            }

                            DropdownMenu(
                                expanded = showDateMenu,
                                onDismissRequest = { showDateMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("全部") },
                                    onClick = {
                                        showDateMenu = false
                                        showCustomDateInput = false
                                        dateFilter = ""
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("今天") },
                                    onClick = {
                                        showDateMenu = false
                                        showCustomDateInput = false
                                        dateFilter = todayKey
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("本月") },
                                    onClick = {
                                        showDateMenu = false
                                        showCustomDateInput = false
                                        dateFilter = monthKey
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("自定义日期关键字") },
                                    onClick = {
                                        showDateMenu = false
                                        showCustomDateInput = true
                                    }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(18.dp))

                        Button(
                            onClick = {
                                if (exporting) return@Button
                                exporting = true
                                val result = exportRecordsCsv(
                                    context = context,
                                    records = records,
                                    dateFilter = dateFilter
                                )
                                exporting = false
                                if (result != null) {
                                    val item = ExportHistoryItem(
                                        id = System.currentTimeMillis().toString(),
                                        createdAt = System.currentTimeMillis(),
                                        year = SimpleDateFormat("yyyy", Locale.getDefault()).format(System.currentTimeMillis()),
                                        rangeText = buildRangeText(records, dateFilter),
                                        fileName = result.displayName,
                                        uri = result.uri.toString(),
                                        absolutePath = result.absolutePath,
                                        dateFilter = dateFilter
                                    )
                                    historyItems.add(0, item)
                                    writeExportHistory(context, historyItems)
                                    Toast.makeText(context, "已保存到: ${result.absolutePath}", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "导出失败", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier
                                .height(44.dp)
                                .width(180.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = primaryBlue),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudDownload,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "确认导出",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    if (showCustomDateInput) {
                        OutlinedTextField(
                            value = dateFilter,
                            onValueChange = { dateFilter = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("日期关键字 (yyyy-MM 或 yyyy-MM-dd)") }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(0.dp))

            Card(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFFCFCFC))
                            .padding(horizontal = 20.dp, vertical = 16.dp),
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
                            text = "导出记录",
                            color = textMain,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Divider(color = borderColor)

                    Column(modifier = Modifier.fillMaxSize()) {
                        ExportHeaderRow()
                        if (historyItems.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.CloudDownload,
                                        contentDescription = null,
                                        tint = Color(0xFFCBD5E1),
                                        modifier = Modifier.size(80.dp)
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = "暂无导出记录",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = textMain
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "您还没有进行过数据导出，请在上方选择日期后点击导出。",
                                        fontSize = 15.sp,
                                        color = Color(0xFF94A3B8)
                                    )
                                }
                            }
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                itemsIndexed(historyItems) { index, item ->
                                    ExportRow(
                                        index = index + 1,
                                        item = item,
                                        onDownload = {
                                            handleDownload(context, records, historyItems, item)
                                        },
                                        onShare = {
                                            handleShare(context, records, historyItems, item)
                                        }
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

@Composable
private fun ExportHeaderRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF8FAFC))
            .padding(horizontal = Dimens.PaddingNormal, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HeaderCell(text = "序号", width = 80.dp)
        HeaderCell(text = "年份", width = 100.dp)
        HeaderCell(text = "日期范围", width = 200.dp)
        HeaderCell(text = "文件名", width = 220.dp)
        HeaderCell(text = "操作", width = 140.dp)
    }
}

@Composable
private fun ExportRow(
    index: Int,
    item: ExportHistoryItem,
    onDownload: () -> Unit,
    onShare: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = Dimens.PaddingNormal, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BodyCell(text = index.toString(), width = 80.dp)
        BodyCell(text = item.year, width = 100.dp)
        BodyCell(text = item.rangeText, width = 200.dp)
        BodyCell(text = item.fileName, width = 220.dp)
        Row(
            modifier = Modifier.width(140.dp),
            horizontalArrangement = Arrangement.spacedBy(Dimens.PaddingNormal),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "下载",
                color = Color(0xFF2563EB),
                fontSize = Dimens.TextSizeSmall,
                modifier = Modifier.clickable(onClick = onDownload)
            )
            Text(
                text = "转发",
                color = Color(0xFF2563EB),
                fontSize = Dimens.TextSizeSmall,
                modifier = Modifier.clickable(onClick = onShare)
            )
        }
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
private fun BodyCell(text: String, width: Dp) {
    Text(
        text = text,
        modifier = Modifier.width(width),
        color = Color(0xFF111827),
        fontSize = Dimens.TextSizeSmall
    )
}

private fun shareExportFile(context: Context, uri: Uri) {
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(shareIntent, "分享导出文件")
    context.startActivity(chooser)
}

private fun exportRecordsCsv(
    context: Context,
    records: List<RecordEntity>,
    dateFilter: String
): ExportResult? {
    return try {
        val format = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val displayName = "check_report_${format.format(System.currentTimeMillis())}.csv"
        val csvContent = buildCsvContent(records, dateFilter)
        saveToDownloads(context, displayName, csvContent)
    } catch (_: Exception) {
        null
    }
}

private data class ExportResult(
    val uri: Uri,
    val displayName: String,
    val absolutePath: String
)

private fun csvEscape(value: String): String {
    val escaped = value.replace("\"", "\"\"")
    val needsQuote = escaped.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
    return if (needsQuote) "\"$escaped\"" else escaped
}

private fun buildCsvContent(
    records: List<RecordEntity>,
    dateFilter: String
): ByteArray {
    val dateKey = dateFilter.trim()
    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    val filtered = records.filter { record ->
        dateKey.isBlank() || dateFormat.format(record.checkTime).contains(dateKey)
    }

    val header = "姓名,工号,体温,手部情况,健康证状态,身体不适,结果,时间,人脸照片,手心照片,手背照片\n"

    val builder = StringBuilder()
    builder.append('\uFEFF')
    builder.append(header)

    filtered.forEach { record ->
        val row = listOf(
            record.userName,
            record.employeeId,
            String.format(Locale.getDefault(), "%.1f", record.temperature),
            record.handStatus,
            record.healthCertStatus,
            record.symptomFlags,
            if (record.isPassed) "通过" else "未通过",
            timeFormat.format(record.checkTime),
            record.faceImagePath.orEmpty(),
            record.handPalmPath.orEmpty(),
            record.handBackPath.orEmpty()
        ).joinToString(",") { csvEscape(it) }
        builder.append(row).append("\n")
    }

    return builder.toString().toByteArray(Charsets.UTF_8)
}

private fun saveToDownloads(
    context: Context,
    displayName: String,
    content: ByteArray
): ExportResult? {
    val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    val legacyAbsolutePath = File(downloadsDir, displayName).absolutePath
    val displayPath = "${Environment.DIRECTORY_DOWNLOADS}/$displayName"

    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, displayName)
            put(MediaStore.Downloads.MIME_TYPE, "text/csv")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
        context.contentResolver.openOutputStream(uri)?.use { out ->
            out.write(content)
        }
        ExportResult(uri = uri, displayName = displayName, absolutePath = displayPath)
    } else {
        if (!downloadsDir.exists()) downloadsDir.mkdirs()
        val file = File(downloadsDir, displayName)
        FileOutputStream(file).use { out ->
            out.write(content)
        }
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        ExportResult(uri = uri, displayName = displayName, absolutePath = legacyAbsolutePath)
    }
}

private data class ExportHistoryItem(
    val id: String,
    val createdAt: Long,
    val year: String,
    val rangeText: String,
    val fileName: String,
    val uri: String,
    val absolutePath: String,
    val dateFilter: String
)

private fun buildRangeText(records: List<RecordEntity>, dateFilter: String): String {
    val dateKey = dateFilter.trim()
    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val filtered = records.filter { record ->
        dateKey.isBlank() || dateFormat.format(record.checkTime).contains(dateKey)
    }
    if (filtered.isEmpty()) return "--"
    val sorted = filtered.sortedBy { it.checkTime }
    val start = dateFormat.format(sorted.first().checkTime)
    val end = dateFormat.format(sorted.last().checkTime)
    return "$start - $end"
}

private fun handleDownload(
    context: Context,
    records: List<RecordEntity>,
    history: List<ExportHistoryItem>,
    item: ExportHistoryItem
) {
    val existing = resolveHistoryUri(context, item)
    if (existing != null) {
        Toast.makeText(context, "已保存到: ${item.absolutePath}", Toast.LENGTH_SHORT).show()
        return
    }
    val result = exportRecordsCsv(context, records, item.dateFilter)
    if (result != null) {
        val updated = item.copy(
            uri = result.uri.toString(),
            absolutePath = result.absolutePath
        )
        writeExportHistory(context, history.map { if (it.id == item.id) updated else it })
        Toast.makeText(context, "已保存到: ${result.absolutePath}", Toast.LENGTH_SHORT).show()
    } else {
        Toast.makeText(context, "导出失败", Toast.LENGTH_SHORT).show()
    }
}

private fun handleShare(
    context: Context,
    records: List<RecordEntity>,
    history: List<ExportHistoryItem>,
    item: ExportHistoryItem
) {
    val existing = resolveHistoryUri(context, item)
    if (existing != null) {
        shareExportFile(context, existing)
        return
    }
    val result = exportRecordsCsv(context, records, item.dateFilter)
    if (result != null) {
        val updated = item.copy(
            uri = result.uri.toString(),
            absolutePath = result.absolutePath
        )
        writeExportHistory(context, history.map { if (it.id == item.id) updated else it })
        shareExportFile(context, result.uri)
    } else {
        Toast.makeText(context, "导出失败", Toast.LENGTH_SHORT).show()
    }
}

private fun resolveHistoryUri(context: Context, item: ExportHistoryItem): Uri? {
    return try {
        val uri = Uri.parse(item.uri)
        context.contentResolver.openInputStream(uri)?.close()
        uri
    } catch (_: FileNotFoundException) {
        null
    } catch (_: Exception) {
        null
    }
}

private fun historyFile(context: Context): File {
    return File(context.filesDir, "export_history.csv")
}

private fun loadExportHistory(context: Context): List<ExportHistoryItem> {
    val file = historyFile(context)
    if (!file.exists()) return emptyList()
    return runCatching {
        file.readLines().mapNotNull { line ->
            val parts = line.split("|")
            if (parts.size < 8) return@mapNotNull null
            ExportHistoryItem(
                id = parts[0],
                createdAt = parts[1].toLongOrNull() ?: 0L,
                year = parts[2],
                rangeText = parts[3],
                fileName = parts[4],
                uri = parts[5],
                absolutePath = parts[6],
                dateFilter = parts[7]
            )
        }
    }.getOrDefault(emptyList())
}

private fun writeExportHistory(context: Context, items: List<ExportHistoryItem>) {
    val file = historyFile(context)
    val content = buildString {
        items.forEach { item ->
            append(
                listOf(
                    item.id,
                    item.createdAt.toString(),
                    item.year,
                    item.rangeText,
                    item.fileName,
                    item.uri,
                    item.absolutePath,
                    item.dateFilter
                ).joinToString("|")
            )
            append("\n")
        }
    }
    file.writeText(content)
}
