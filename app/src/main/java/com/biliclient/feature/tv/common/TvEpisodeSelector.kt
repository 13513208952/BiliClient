package com.biliclient.feature.tv.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.biliclient.net.Page

/** 分P选集按钮（画质右侧行尾，pages.size > 1 时显示）。 */
@Composable
fun TvEpisodeBtn(
    pageCount    : Int,
    selectorOpen : Boolean,
    onToggle     : () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .tvFocusable(onClick = onToggle, shape = RoundedCornerShape(6.dp))
            .padding(8.dp)
    ) {
        Icon(
            Icons.Default.PlaylistPlay,
            contentDescription = "分P",
            tint     = if (selectorOpen) BiliPink else Color.White,
            modifier = Modifier.size(32.dp)
        )
        Text("分P ($pageCount)", color = if (selectorOpen) BiliPink else Color.White, fontSize = 11.sp)
    }
}

/**
 * 泛型右1/3选集面板，分P与番剧（episodes）共用。
 *
 * 调用方应在 BoxScope 中把此 Composable 包裹进
 * `AnimatedVisibility(modifier = Modifier.align(Alignment.CenterEnd), ...)`，
 * 以实现正确的右对齐 + 滑入动画。
 *
 * @param title        标题栏文字（如"选集 (4P)"、"选集 (179集)"）。
 * @param items        集合元素列表。
 * @param currentIndex 当前选中项的下标。
 * @param key          LazyColumn 的 key，同类型需全局唯一（通常用 id/cid）。
 * @param tag          左侧短标签（如 "P1"、"第1话"），固定宽度 36dp。
 * @param label        主标题文字（最多2行，省略号裁剪）。
 * @param onSelect     用户选中某项时回调。
 * @param onDismiss    点击"关闭"或 Back 时回调（由调用方控制 selectorOpen）。
 */
@Composable
fun <T> TvSelectorPanel(
    title        : String,
    items        : List<T>,
    currentIndex : Int,
    key          : (T) -> Any,
    tag          : (T) -> String,
    label        : (T) -> String,
    onSelect     : (T) -> Unit,
    onDismiss    : () -> Unit
) {
    val listState = rememberLazyListState()
    val focusList = remember(items.size) { items.map { FocusRequester() } }

    // 面板展开时把焦点定位到当前项（复用 TvQualityBtn 的 withFrameNanos 模式）
    LaunchedEffect(Unit) {
        withFrameNanos {}
        try { focusList.getOrNull(currentIndex)?.requestFocus() } catch (_: Exception) {}
        if (currentIndex > 0) listState.scrollToItem(currentIndex)
    }

    // AnimatedVisibility 的 align(Alignment.CenterEnd) 由调用方在 BoxScope 设置，
    // 此处仅负责面板自身宽高与样式（260dp 固定宽 ≈ 屏宽 1/3）
    Box(
        Modifier
            .fillMaxHeight()
            .width(260.dp)
            .background(
                Color(0xDD111111),
                RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp)
            )
            .padding(vertical = 12.dp)
    ) {
        Column(Modifier.fillMaxSize()) {
            // 标题行
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(title, color = Color.White, fontSize = 14.sp)
                Box(
                    Modifier
                        .tvFocusable(onClick = onDismiss, shape = RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("关闭", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                }
            }
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                itemsIndexed(items, key = { _, item -> key(item) }) { idx, item ->
                    val isCurrent = idx == currentIndex
                    var focused   by remember { mutableStateOf(false) }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .then(
                                if (idx < focusList.size) Modifier.focusRequester(focusList[idx])
                                else Modifier
                            )
                            .onFocusChanged { focused = it.isFocused }
                            .background(
                                when {
                                    focused   -> BiliPink.copy(alpha = 0.30f)
                                    isCurrent -> BiliPink.copy(alpha = 0.15f)
                                    else      -> Color.Transparent
                                }
                            )
                            .tvFocusable(onClick = { onSelect(item) })
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text     = tag(item),
                            color    = if (isCurrent) BiliPink else Color.White.copy(alpha = 0.5f),
                            fontSize = 11.sp,
                            modifier = Modifier.width(36.dp)
                        )
                        Text(
                            text     = label(item),
                            color    = if (isCurrent) BiliPink else Color.White,
                            fontSize = 13.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

/**
 * 右1/3分P选集面板（[TvSelectorPanel] 的薄封装，外部调用点签名不变）。
 *
 * 调用方应在 BoxScope 中把此 Composable 包裹进
 * `AnimatedVisibility(modifier = Modifier.align(Alignment.CenterEnd), ...)`。
 */
@Composable
fun EpisodeSelectorPanel(
    pages      : List<Page>,
    currentCid : Long,
    onSelect   : (Long) -> Unit,
    onDismiss  : () -> Unit
) {
    val currentIndex = pages.indexOfFirst { it.cid == currentCid }.coerceAtLeast(0)
    TvSelectorPanel(
        title        = "选集 (${pages.size}P)",
        items        = pages,
        currentIndex = currentIndex,
        key          = { it.cid },
        tag          = { "P${it.page}" },
        label        = { it.part.ifBlank { "第${it.page}P" } },
        onSelect     = { onSelect(it.cid) },
        onDismiss    = onDismiss
    )
}
