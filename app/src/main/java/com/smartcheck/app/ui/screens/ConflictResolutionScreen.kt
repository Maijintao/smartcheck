package com.smartcheck.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.smartcheck.app.data.sync.ConflictInfo
import com.smartcheck.app.viewmodel.ConflictViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConflictResolutionScreen(
    onNavigateBack: () -> Unit,
    viewModel: ConflictViewModel = hiltViewModel()
) {
    val conflicts by viewModel.conflicts.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadConflicts()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "同步冲突 (${conflicts.size})",
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                conflicts.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "没有待处理的同步冲突",
                            fontSize = 16.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(conflicts) { conflict ->
                            ConflictCard(
                                conflict = conflict,
                                onAcceptRemote = { viewModel.acceptRemote(conflict.employeeId) },
                                onRetryLocal = { viewModel.retryLocal(conflict.employeeId) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConflictCard(
    conflict: ConflictInfo,
    onAcceptRemote: () -> Unit,
    onRetryLocal: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 标题
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = Color(0xFFF59E0B),
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "${conflict.localEmployee.name}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "工号: ${conflict.employeeId}",
                    fontSize = 13.sp,
                    color = Color(0xFF94A3B8)
                )
            }

            // 版本对比
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "本地版本: ${conflict.localVersion}",
                    fontSize = 13.sp,
                    color = Color(0xFF64748B)
                )
                Text(
                    text = "平台版本: ${conflict.remoteVersion}",
                    fontSize = 13.sp,
                    color = Color(0xFF64748B)
                )
            }

            // 远程删除提示
            if (conflict.remoteDeleted) {
                Surface(
                    color = Color(0xFFFFF7ED),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = "该员工已在平台被删除",
                        modifier = Modifier.padding(8.dp),
                        fontSize = 13.sp,
                        color = Color(0xFFEA580C)
                    )
                }
            }

            // 远程数据摘要
            conflict.remoteEmployee?.let { remote ->
                Surface(
                    color = Color(0xFFF8FAFC),
                    shape = MaterialTheme.shapes.small
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text("平台最新数据:", fontSize = 12.sp, color = Color(0xFF94A3B8))
                        Text("姓名: ${remote.name}  状态: ${remote.status}", fontSize = 13.sp)
                        remote.phone?.let { Text("电话: $it", fontSize = 13.sp) }
                        remote.position?.let { Text("职位: $it", fontSize = 13.sp) }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // 操作按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onRetryLocal,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("重提本地修改", fontSize = 13.sp)
                }
                Button(
                    onClick = onAcceptRemote,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("使用平台数据", fontSize = 13.sp)
                }
            }
        }
    }
}
