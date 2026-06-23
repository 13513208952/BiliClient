package com.biliclient.feature.tv.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.biliclient.feature.tv.common.tvFocusable
import com.biliclient.net.SearchBangumiItem
import com.biliclient.net.SearchVideoItem

@Composable
fun TvSearchResultArea(
    vm            : TvSearchViewModel,
    onPlayVideo   : (SearchVideoItem) -> Unit,
    onPlayBangumi : (String) -> Unit,
    modifier      : Modifier = Modifier
) {
    val ui      by vm.ui.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()

    val showHistory  = ui.inputText.isBlank() && ui.results.isEmpty() && ui.bangumiResults.isEmpty() && !ui.searching
    val showSuggests = ui.inputText.isNotBlank() && ui.suggests.isNotEmpty() && ui.results.isEmpty() && ui.bangumiResults.isEmpty() && !ui.searching
    val showResults  = ui.results.isNotEmpty() || ui.bangumiResults.isNotEmpty() || ui.searching

    Box(modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
        when {
            showHistory  -> HistoryPanel(
                history    = history,
                onSelect   = { vm.historySearch(it) },
                onClearAll = { vm.clearHistory() }
            )
            showSuggests -> SuggestPanel(
                suggests = ui.suggests,
                onSelect = { vm.onInputTextChange(it); vm.search() }
            )
            showResults  -> ResultPanel(
                bangumiResults = ui.bangumiResults,
                results        = ui.results,
                searching      = ui.searching,
                loadingMore    = ui.loadingMore,
                noMore         = ui.noMore,
                onLoadMore     = { vm.loadMore() },
                onPlayVideo    = onPlayVideo,
                onPlayBangumi  = onPlayBangumi
            )
            else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("输入关键词开始搜索", color = Color.White.copy(alpha = 0.4f), fontSize = 16.sp)
            }
        }
    }
}

@Composable
private fun HistoryPanel(
    history   : List<String>,
    onSelect  : (String) -> Unit,
    onClearAll: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Text("最近搜索", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
            if (history.isNotEmpty()) {
                Box(
                    Modifier
                        .tvFocusable(onClick = onClearAll, shape = RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("清空", color = Color(0xFFFB7299), fontSize = 13.sp)
                }
            }
        }
        if (history.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("暂无搜索历史", color = Color.White.copy(alpha = 0.3f), fontSize = 14.sp)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(history) { kw ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .tvFocusable(onClick = { onSelect(kw) }, shape = RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.History,
                            contentDescription = null,
                            tint     = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(kw, color = Color.White, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun SuggestPanel(
    suggests: List<String>,
    onSelect: (String) -> Unit
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        items(suggests) { s ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .tvFocusable(onClick = { onSelect(s) }, shape = RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    tint     = Color(0xFFFB7299),
                    modifier = Modifier.size(16.dp)
                )
                Text(s, color = Color.White, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun ResultPanel(
    bangumiResults : List<SearchBangumiItem>,
    results        : List<SearchVideoItem>,
    searching      : Boolean,
    loadingMore    : Boolean,
    noMore         : Boolean,
    onLoadMore     : () -> Unit,
    onPlayVideo    : (SearchVideoItem) -> Unit,
    onPlayBangumi  : (String) -> Unit
) {
    val listState = rememberLazyListState()
    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total       = listState.layoutInfo.totalItemsCount
            !loadingMore && !noMore && total > 0 && lastVisible >= total - 3
        }
    }
    LaunchedEffect(shouldLoadMore) { if (shouldLoadMore) onLoadMore() }

    if (searching && results.isEmpty() && bangumiResults.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color(0xFFFB7299), modifier = Modifier.size(40.dp))
        }
        return
    }

    LazyColumn(
        state               = listState,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier            = Modifier.fillMaxSize()
    ) {
        // 番剧结果置顶（不受屏蔽开关影响）
        if (bangumiResults.isNotEmpty()) {
            item {
                Text(
                    "番剧", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                )
            }
            items(bangumiResults, key = { "bgm_${it.uiKey}" }) { bgm ->
                SearchBangumiCard(item = bgm, onClick = { onPlayBangumi("ss${bgm.seasonId}") })
            }
            if (results.isNotEmpty()) {
                item {
                    Text(
                        "视频", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp,
                        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                    )
                }
            }
        }
        items(results, key = { it.uiKey }) { item ->
            SearchVideoCard(item = item, onClick = { onPlayVideo(item) })
        }
        if (loadingMore) {
            item {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFFFB7299), modifier = Modifier.size(28.dp))
                }
            }
        }
        if (noMore && results.isNotEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    Text("没有更多了", color = Color.White.copy(alpha = 0.3f), fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun SearchBangumiCard(item: SearchBangumiItem, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .tvFocusable(onClick = onClick, shape = RoundedCornerShape(8.dp))
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment     = Alignment.Top
    ) {
        AsyncImage(
            model              = item.coverHttps(),
            contentDescription = null,
            contentScale       = ContentScale.Crop,
            modifier           = Modifier
                .width(80.dp)
                .aspectRatio(3f / 4f)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF2A2A2A))
        )
        Column(
            Modifier.weight(1f).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text       = item.cleanTitle(),
                color      = Color.White,
                fontSize   = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines   = 2,
                overflow   = TextOverflow.Ellipsis
            )
            if (item.badge.isNotBlank()) {
                Text(item.badge, color = Color(0xFFFB7299), fontSize = 11.sp)
            }
            val scoreText = item.mediaScore?.let { "评分 ${"%.1f".format(it.score)}" } ?: ""
            val epText    = if (item.epSize > 0) "${item.epSize} 集" else ""
            val info = listOf(scoreText, epText).filter { it.isNotBlank() }.joinToString("  ")
            if (info.isNotBlank()) {
                Text(info, color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun SearchVideoCard(item: SearchVideoItem, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .tvFocusable(onClick = onClick, shape = RoundedCornerShape(8.dp))
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment     = Alignment.Top
    ) {
        AsyncImage(
            model             = item.picHttps(),
            contentDescription = null,
            contentScale      = ContentScale.Crop,
            modifier          = Modifier
                .width(140.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF2A2A2A))
        )
        Column(
            Modifier.weight(1f).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text       = item.cleanTitle(),
                color      = Color.White,
                fontSize   = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines   = 2,
                overflow   = TextOverflow.Ellipsis
            )
            Text(
                text     = item.author,
                color    = Color.White.copy(alpha = 0.6f),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (item.duration.isNotBlank()) {
                Text(
                    text     = item.duration,
                    color    = Color.White.copy(alpha = 0.4f),
                    fontSize = 11.sp
                )
            }
        }
    }
}
