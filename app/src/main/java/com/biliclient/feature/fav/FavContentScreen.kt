package com.biliclient.feature.fav

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** 收藏夹内容页（doc 10 §3）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavContentScreen(
    onBack: () -> Unit,
    onOpenVideo: (String) -> Unit,
    modifier: Modifier = Modifier,
    vm: FavContentViewModel = hiltViewModel()
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo }
            .map { info ->
                val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
                val total = info.totalItemsCount
                total > 0 && last >= total - 3
            }
            .distinctUntilChanged()
            .collect { nearEnd -> if (nearEnd) vm.loadMore() }
    }

    Column(modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(ui.title.ifBlank { "收藏夹" }) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } }
        )
        when {
            ui.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            ui.error != null -> Box(Modifier.fillMaxSize(), Alignment.Center) { Text(ui.error!!) }
            else -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                items(ui.items, key = { it.uiKey }) { media ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onOpenVideo(media.bvid) }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        AsyncImage(
                            model = media.cover,
                            contentDescription = null,
                            modifier = Modifier
                                .width(120.dp).aspectRatio(16f / 9f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFFE0E0E0))
                        )
                        Column(Modifier.padding(start = 10.dp).weight(1f)) {
                            Text(media.title, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(media.upper.name, fontSize = 11.sp, color = Color.Gray,
                                modifier = Modifier.padding(top = 4.dp))
                            Text("播放 ${media.cntInfo.play}", fontSize = 11.sp, color = Color.Gray)
                        }
                    }
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
        }
    }
}
