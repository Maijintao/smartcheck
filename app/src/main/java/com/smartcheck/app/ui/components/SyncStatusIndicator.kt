package com.smartcheck.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartcheck.app.data.sync.SyncEngineStatus
import java.text.SimpleDateFormat
import java.util.*

/**
 * 同步状态指示器组件
 * 显示：🟢已同步 3分钟前 / 🟡同步中... / 🔴同步错误
 */
@Composable
fun SyncStatusIndicator(
    syncState: SyncEngineStatus,
    lastSyncTime: Long? = null,
    modifier: Modifier = Modifier
) {
    val (dotColor, text) = when (syncState) {
        SyncEngineStatus.IDLE -> {
            val timeText = lastSyncTime?.let { formatTimeAgo(it) } ?: "已同步"
            Color(0xFF22C55E) to timeText
        }
        SyncEngineStatus.SYNCING -> Color(0xFFF59E0B) to "同步中..."
        SyncEngineStatus.ERROR -> Color(0xFFEF4444) to "同步错误"
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(dotColor, CircleShape)
        )
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF64748B)
        )
    }
}

private fun formatTimeAgo(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diffMs = now - timestamp
    val diffMin = diffMs / 60_000
    return when {
        diffMin < 1 -> "刚刚同步"
        diffMin < 60 -> "${diffMin}分钟前同步"
        else -> {
            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
            "${sdf.format(Date(timestamp))} 同步"
        }
    }
}
