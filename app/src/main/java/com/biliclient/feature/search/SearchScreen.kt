package com.biliclient.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.biliclient.core.Route
import com.biliclient.net.SearchBangumiItem
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * 搜索页（doc 10 §5）：搜索框 + 联想下拉 + 搜索历史 + 结果列表。
 * URL/BV/AV/ep/ss 直跳对应页（03 篇 UrlRouter）。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    onBack: (() -> Unit)? = null,
    onOpenVideo: (String) -> Unit,
    onOpenBangumi: (String) -> Unit,
    modifier: Modifier = Modifier,
    vm: SearchViewModel = hiltViewModel()
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    fun doRoute() {
        if (input.isBlank()) return
        vm.resolve(input) { route ->
            when (route) {
                is Route.Video -> onOpenVideo(route.id)
                is Route.Bangumi -> onOpenBangumi(route.id)
                else -> {}  // already handled in vm.resolve → search()
            }
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo }
            .map { info ->
                val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
                val total = info.totalItemsCount
                total > 0 && last >= total - 4
            }
            .distinctUntilChanged()
            .collect { nearEnd -> if (nearEnd) vm.loadMore() }
    }

    Column(modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it; vm.onInputChange(it) },
                    placeholder = { Text("搜索 B站", fontSize = 14.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(end = 4.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { doRoute() }),
                    trailingIcon = {
                        if (input.isNotEmpty()) {
                            IconButton(onClick = { input = ""; vm.onInputChange("") }) {
                                Icon(Icons.Default.Clear, null)
                            }
                        }
                    }
                )
            },
            navigationIcon = {
                if (onBack != null) {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) }
                }
            },
            actions = {
                IconButton(onClick = { doRoute() }) { Icon(Icons.Default.Search, null) }
            }
        )

        // 联想下拉
        if (ui.suggests.isNotEmpty()) {
            Column(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)
                    .padding(vertical = 4.dp)
            ) {
                ui.suggests.forEach { s ->
                    Text(
                        s, fontSize = 14.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { input = s; vm.search(s) }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                    Divider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }

        // 搜索结果 or 历史
        when {
            ui.searching -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            ui.results.isNotEmpty() || ui.bangumiResults.isNotEmpty() -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                // 番剧结果置顶（不受屏蔽开关影响）
                if (ui.bangumiResults.isNotEmpty()) {
                    item {
                        Text(
                            "番剧", fontWeight = FontWeight.Bold, fontSize = 13.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                    items(ui.bangumiResults, key = { "bgm_${it.uiKey}" }) { bgm ->
                        BangumiResultRow(item = bgm, onClick = { onOpenBangumi("ss${bgm.seasonId}") })
                        Divider(modifier = Modifier.padding(horizontal = 12.dp))
                    }
                    if (ui.results.isNotEmpty()) {
                        item {
                            Text(
                                "视频", fontWeight = FontWeight.Bold, fontSize = 13.sp,
                                color = Color.Gray,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
                items(ui.results, key = { it.uiKey }) { item ->
                    SearchResultRow(item = item, onClick = { onOpenVideo(item.bvid) })
                    Divider(modifier = Modifier.padding(horizontal = 12.dp))
                }
                if (ui.loadingMore) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }
            else -> Column(Modifier.fillMaxWidth().padding(16.dp)) {
                if (history.isNotEmpty()) {
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("搜索历史", fontSize = 14.sp, color = Color.Gray)
                        TextButton(onClick = { vm.clearHistory() }) { Text("清空", fontSize = 12.sp) }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        history.forEach { h ->
                            SuggestionChip(
                                onClick = { input = h.keyword; vm.search(h.keyword) },
                                label = { Text(h.keyword, fontSize = 13.sp) }
                            )
                        }
                    }
                } else {
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        Text("输入关键词或 BV/AV/番剧号搜索", color = Color.Gray, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun BangumiResultRow(item: SearchBangumiItem, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        AsyncImage(
            model = item.coverHttps(),
            contentDescription = null,
            modifier = Modifier.width(90.dp).aspectRatio(3f / 4f)
                .clip(RoundedCornerShape(6.dp)).background(Color(0xFFE0E0E0))
        )
        Column(Modifier.padding(start = 10.dp).weight(1f)) {
            Text(item.cleanTitle(), fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Medium)
            if (item.badge.isNotBlank()) {
                Text(item.badge, fontSize = 10.sp, color = Color(0xFFFB7299),
                    modifier = Modifier.padding(top = 2.dp))
            }
            val scoreText = item.mediaScore?.let { "评分 ${"%.1f".format(it.score)}" } ?: ""
            val epText = if (item.epSize > 0) "${item.epSize} 集" else ""
            val info = listOf(scoreText, epText).filter { it.isNotBlank() }.joinToString("  ")
            if (info.isNotBlank()) {
                Text(info, fontSize = 11.sp, color = Color.Gray, modifier = Modifier.padding(top = 4.dp))
            }
            if (item.isFollow == 1) {
                Text("已追番", fontSize = 10.sp, color = Color(0xFFFB7299),
                    modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}

@Composable
private fun SearchResultRow(item: com.biliclient.net.SearchVideoItem, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        AsyncImage(
            model = item.picHttps(),
            contentDescription = null,
            modifier = Modifier.width(120.dp).aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(6.dp)).background(Color(0xFFE0E0E0))
        )
        Column(Modifier.padding(start = 10.dp).weight(1f)) {
            Text(item.cleanTitle(), fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(item.author, fontSize = 11.sp, color = Color.Gray, modifier = Modifier.padding(top = 4.dp))
            Text("播放 ${item.play}  时长 ${item.duration}", fontSize = 10.sp, color = Color.Gray)
        }
    }
}
