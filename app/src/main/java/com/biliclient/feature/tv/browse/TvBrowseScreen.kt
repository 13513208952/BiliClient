package com.biliclient.feature.tv.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.biliclient.feature.home.HomeViewModel
import com.biliclient.feature.tv.common.tvFocusable
import com.biliclient.net.FeedItem

@Composable
fun TvBrowseScreen(
    vm: HomeViewModel = hiltViewModel(),
    onPlayVideo: (FeedItem) -> Unit = {}
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()

    // 近底加载更多（与手机版逻辑一致）
    LaunchedEffect(gridState) {
        snapshotFlow { gridState.layoutInfo }
            .collect { info ->
                val last  = info.visibleItemsInfo.lastOrNull()?.index ?: 0
                val total = info.totalItemsCount
                if (total > 0 && last >= total - 4) vm.loadMore()
            }
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF0D0D0D))) {
        when {
            ui.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFFFB7299))
            }
            ui.error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(ui.error!!, color = Color.White)
            }
            else -> LazyVerticalGrid(
                columns               = GridCells.Fixed(4),
                state                 = gridState,
                modifier              = Modifier.fillMaxSize().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement   = Arrangement.spacedBy(12.dp)
            ) {
                items(ui.items, key = { it.uiKey }) { item ->
                    TvFeedCard(
                        item    = item,
                        onClick = { onPlayVideo(item) }
                    )
                }
                if (ui.loadingMore) {
                    item(span = { GridItemSpan(4) }) {
                        Box(
                            Modifier.fillMaxWidth().padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(28.dp),
                                color    = Color(0xFFFB7299)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TvFeedCard(item: FeedItem, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .tvFocusable(onClick = onClick, shape = RoundedCornerShape(8.dp))
    ) {
        // 封面（16:9）
        AsyncImage(
            model              = item.pic,
            contentDescription = item.title,
            modifier           = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp)),
            contentScale       = ContentScale.Crop
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text     = item.title,
            color    = Color.White,
            fontSize = 13.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text     = "${item.owner.name}  ${fmtView(item.stat.view)}",
            color    = Color.White.copy(alpha = 0.6f),
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun fmtView(v: Long): String = when {
    v >= 100_000_000 -> "${"%.1f".format(v / 100_000_000.0)}亿"
    v >= 10_000      -> "${"%.1f".format(v / 10_000.0)}万"
    else             -> v.toString()
}
