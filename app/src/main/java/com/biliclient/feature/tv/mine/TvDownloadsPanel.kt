package com.biliclient.feature.tv.mine

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.biliclient.feature.downloads.DownloadsViewModel
import com.biliclient.feature.tv.common.tvFocusable

@Composable
fun TvDownloadsPanel(vm: DownloadsViewModel = hiltViewModel(), onBack: () -> Unit) {
    val tasks by vm.tasks.collectAsStateWithLifecycle(initialValue = emptyList())

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0D0D))
            .padding(horizontal = 40.dp, vertical = 24.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.tvFocusable(onClick = onBack)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = Color.White)
            }
            Spacer(Modifier.width(12.dp))
            Text("我的下载", color = Color.White, fontSize = 22.sp)
        }
        HorizontalDivider(color = Color.White.copy(alpha = 0.1f),
                          modifier = Modifier.padding(vertical = 8.dp))
        if (tasks.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("暂无下载任务", color = Color.White.copy(alpha = 0.6f))
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(tasks) { task ->
                    val pct = if (task.totalBytes > 0)
                        (task.downloadedBytes * 100 / task.totalBytes).toInt() else 0
                    val statusText = when (task.status) {
                        0 -> "排队中"; 1 -> "下载中"; 2 -> "已暂停"
                        3 -> "合流中"; 4 -> "已完成"; 5 -> "失败"
                        else -> "未知"
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .tvFocusable(onClick = { })
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment    = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(task.title, color = Color.White, fontSize = 14.sp,
                                 maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(statusText,
                                 color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
                        }
                        Text(
                            text  = if (task.status == 4) "完成" else "$pct%",
                            color = Color(0xFFFB7299),
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}
