package com.biliclient.feature.downloads

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.biliclient.data.db.DownloadEntity
import com.biliclient.download.DownloadController
import kotlinx.coroutines.launch

/** 下载管理（doc 08 §4 / doc 12 §3.5）：TabRow[下载中|已完成] + 缩略图/标题/清晰度/进度/速度/操作。 */
@Composable
fun DownloadsScreen(
    modifier: Modifier = Modifier,
    vm: DownloadsViewModel = hiltViewModel()
) {
    val tasks by vm.tasks.collectAsStateWithLifecycle(initialValue = emptyList())
    val progress by vm.progress.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState { 2 }
    val scope = rememberCoroutineScope()

    Column(modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = pagerState.currentPage) {
            listOf("下载中", "已完成").forEachIndexed { i, label ->
                Tab(
                    selected = pagerState.currentPage == i,
                    onClick = { scope.launch { pagerState.animateScrollToPage(i) } },
                    text = { Text(label) }
                )
            }
        }

        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            when (page) {
                0 -> {
                    val active = tasks.filter { it.status in 0..3 }
                    val failed = tasks.filter { it.status == 5 }
                    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
                        items(active, key = { it.id }) { t ->
                            DownloadingRow(
                                t = t,
                                prog = progress[t.id],
                                onPause = { vm.pause(t.id) },
                                onResume = { vm.resume(t.id) },
                                onCancel = { vm.cancel(t.id) }
                            )
                        }
                        if (failed.isNotEmpty()) {
                            item {
                                Text(
                                    "失败 (${failed.size})",
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }
                            items(failed, key = { it.id }) { t ->
                                FailedRow(t) { vm.retry(t.id) }
                            }
                        }
                        if (active.isEmpty() && failed.isEmpty()) {
                            item {
                                Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) {
                                    Text("暂无下载任务", color = Color.Gray)
                                }
                            }
                        }
                    }
                }
                else -> {
                    val done = tasks.filter { it.status == 4 }
                    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
                        items(done, key = { it.id }) { t -> CompletedRow(t) }
                        if (done.isEmpty()) {
                            item {
                                Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) {
                                    Text("暂无已完成下载", color = Color.Gray)
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
private fun DownloadingRow(
    t: DownloadEntity,
    prog: DownloadController.Progress?,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit
) {
    val done = prog?.done ?: t.downloadedBytes
    val total = prog?.total ?: t.totalBytes
    val speedBps = prog?.speedBps ?: 0L
    val frac = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else 0f

    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.Top
    ) {
        AsyncImage(
            model = t.cover,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(width = 80.dp, height = 54.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t.title, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f), maxLines = 1)
                Spacer(Modifier.width(6.dp))
                Text(qualityLabel(t.qualityId), fontSize = 11.sp, color = Color.Gray)
            }
            LinearProgressIndicator(
                progress = { frac },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                val statusText = when (t.status) {
                    0 -> "排队中"; 1 -> "下载中"; 2 -> "已暂停"; 3 -> "合流中"; else -> ""
                }
                val sizeText = "${done / 1024 / 1024}MB / ${total / 1024 / 1024}MB"
                val speedText = fmtSpeed(speedBps)
                Text(
                    "$statusText  $sizeText${if (speedText.isNotEmpty()) "  $speedText" else ""}",
                    fontSize = 11.sp, color = Color.Gray, modifier = Modifier.weight(1f)
                )
                if (t.status == 2) {
                    TextButton(onClick = onResume) { Text("继续", fontSize = 12.sp) }
                } else {
                    TextButton(onClick = onPause) { Text("暂停", fontSize = 12.sp) }
                }
                TextButton(onClick = onCancel) { Text("取消", fontSize = 12.sp) }
            }
        }
    }
}

@Composable
private fun FailedRow(t: DownloadEntity, onRetry: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = t.cover,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(width = 80.dp, height = 54.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(t.title, fontSize = 13.sp, maxLines = 1)
            Text(
                "失败：${t.errorType ?: "未知错误"}",
                fontSize = 11.sp, color = Color.Red
            )
        }
        TextButton(onClick = onRetry) { Text("重试", fontSize = 12.sp) }
    }
}

@Composable
private fun CompletedRow(t: DownloadEntity) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = t.cover,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(width = 80.dp, height = 54.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(t.title, fontSize = 13.sp, maxLines = 1)
            Text(
                "${qualityLabel(t.qualityId)} · 已保存至相册 Movies/BiliClient",
                fontSize = 11.sp, color = Color.Gray
            )
        }
    }
}

private fun qualityLabel(id: Int): String = when (id) {
    16 -> "360P"; 32 -> "480P"; 64 -> "720P"; 74 -> "720P60"
    80 -> "1080P"; 112 -> "1080P+"; 116 -> "1080P60"; 120 -> "4K"
    125 -> "HDR"; 126 -> "杜比"; 127 -> "8K"; else -> "自动"
}

private fun fmtSpeed(bps: Long): String = when {
    bps >= 1024 * 1024 -> "${"%.1f".format(bps.toDouble() / 1024 / 1024)} MB/s"
    bps >= 1024 -> "${bps / 1024} KB/s"
    bps > 0 -> "$bps B/s"
    else -> ""
}
