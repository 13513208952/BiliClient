package com.biliclient.feature.video

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.MonetizationOn
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.outlined.Whatshot
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@OptIn(UnstableApi::class, ExperimentalMaterial3Api::class)
@Composable
fun VideoScreen(
    id           : String,
    onBack       : () -> Unit,
    onOpenVideo  : (String) -> Unit,
    onOpenBangumi: (String) -> Unit = {},
    modifier     : Modifier = Modifier,
    vm           : VideoViewModel = hiltViewModel()
) {
    val ui             by vm.ui.collectAsStateWithLifecycle()
    val danmaku        by vm.danmaku.collectAsStateWithLifecycle()
    val danmakuDisplay by vm.danmakuDisplay.collectAsStateWithLifecycle()
    val ctx = LocalContext.current

    var isFullscreen       by rememberFullscreenState()
    var showSettingsSheet  by remember { mutableStateOf(false) }
    var settingsInitialTab by remember { mutableStateOf<Int?>(null) }

    DisposableEffect(Unit) { onDispose { vm.playerManager.pause() } }

    LaunchedEffect(id) { vm.load(id) }
    LaunchedEffect(ui.toast) {
        ui.toast?.let { Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show(); vm.clearToast() }
    }
    LaunchedEffect(ui.redirectToBangumi) {
        ui.redirectToBangumi?.let { onOpenBangumi(it) }
    }

    Column(modifier.fillMaxSize()) {
        BiliPlayerSurface(
            player             = vm.playerManager.player,
            danmaku            = danmaku,
            danmakuOn          = ui.danmakuOn,
            danmakuScale       = danmakuDisplay.scale,
            danmakuAlpha       = danmakuDisplay.alpha,
            danmakuDensity     = danmakuDisplay.density,
            positionMs         = { vm.playerManager.positionMs },
            isFullscreen       = isFullscreen,
            onFullscreenChange = { isFullscreen = it },
            onSettingsClick    = { settingsInitialTab = null; showSettingsSheet = true },
            onBack             = onBack
        )

        if (!isFullscreen) {
            when {
                ui.loading       -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator()
                }
                ui.error != null -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text(ui.error!!)
                }
                else             -> VideoContent(
                    vm                    = vm,
                    ui                    = ui,
                    danmakuCount          = danmaku.size,
                    onOpenVideo           = onOpenVideo,
                    onOpenDanmakuSettings = {
                        settingsInitialTab = TAB_DANMAKU
                        showSettingsSheet  = true
                    }
                )
            }

            ui.favSheet?.let { sheet ->
                val favState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                ModalBottomSheet(onDismissRequest = { vm.closeFavSheet() }, sheetState = favState) {
                    Text("选择收藏夹", fontWeight = FontWeight.Bold, fontSize = 16.sp,
                         modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp))
                    if (sheet.loading) {
                        Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else {
                        LazyColumn(Modifier.fillMaxWidth().navigationBarsPadding()) {
                            items(sheet.folders, key = { it.id }) { folder ->
                                ListItem(
                                    headlineContent   = { Text(folder.title) },
                                    supportingContent = { Text("${folder.mediaCount} 个视频") },
                                    trailingContent   = {
                                        Checkbox(
                                            checked         = folder.checked,
                                            onCheckedChange = { vm.toggleFavFolder(folder.id, it) }
                                        )
                                    },
                                    modifier = Modifier.clickable {
                                        vm.toggleFavFolder(folder.id, !folder.checked)
                                    }
                                )
                            }
                            item { Spacer(Modifier.height(16.dp)) }
                        }
                    }
                }
            }
        }
    }

    if (showSettingsSheet) {
        PlaySettingsPanel(
            isFullscreen     = isFullscreen,
            initialTab       = settingsInitialTab,
            playbackSpeed    = ui.playbackSpeed,
            onSpeedChange    = vm::setSpeed,
            acceptQuality    = ui.acceptQuality,
            currentQn        = ui.currentQn,
            onQualitySelected = vm::switchQuality,
            danmakuOn        = ui.danmakuOn,
            onToggleDanmaku  = vm::toggleDanmaku,
            danmakuScale     = danmakuDisplay.scale,
            danmakuAlpha     = danmakuDisplay.alpha,
            danmakuDensity   = danmakuDisplay.density,
            onDanmakuScale   = vm::setDanmakuTextScale,
            onDanmakuAlpha   = vm::setDanmakuAlpha,
            onDanmakuDensity = vm::setDanmakuDensity,
            onDismiss        = { showSettingsSheet = false }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 视频详情区（非全屏）
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun VideoContent(
    vm                   : VideoViewModel,
    ui                   : VideoViewModel.VideoUi,
    danmakuCount         : Int,
    onOpenVideo          : (String) -> Unit,
    onOpenDanmakuSettings: () -> Unit = {}
) {
    val v   = ui.view ?: return
    val ctx = LocalContext.current
    var replyText by remember { mutableStateOf("") }
    var dmText    by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo }
            .map { info ->
                val last  = info.visibleItemsInfo.lastOrNull()?.index ?: 0
                val total = info.totalItemsCount
                total > 0 && last >= total - 5
            }
            .distinctUntilChanged()
            .collect { nearEnd -> if (nearEnd) vm.loadMoreReplies() }
    }

    LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        item {
            Text(v.title, fontWeight = FontWeight.Bold, fontSize = 17.sp,
                 modifier = Modifier.padding(top = 8.dp))
            Text("播放 ${v.stat.view} · 弹幕 ${v.stat.danmaku}（已载 $danmakuCount）",
                 color = Color.Gray, fontSize = 12.sp,
                 modifier = Modifier.padding(top = 4.dp))
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text("UP：${v.owner.name}", fontSize = 13.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = { vm.follow() }) { Text("关注") }
            }
        }
        // ── 合集选集（外层，ugc_season）────────────────────────────────
        v.ugcSeason?.let { season ->
            val episodes = season.sections
                .flatMap { it.episodes }
                .filter { it.bvid.isNotEmpty() && it.cid > 0 }
            if (episodes.isNotEmpty()) {
                item {
                    Text("合集：${season.title}（${episodes.size}）",
                         fontWeight = FontWeight.Bold, fontSize = 14.sp,
                         modifier = Modifier.padding(top = 6.dp, bottom = 4.dp))
                    LazyRow(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                        items(episodes, key = { it.bvid }) { ep ->
                            val isCurrent = ep.bvid == v.bvid
                            SelectChip(
                                text      = ep.title.ifBlank { "P${ep.page.page}" },
                                selected  = isCurrent,
                                onClick   = { if (!isCurrent) onOpenVideo(ep.bvid) }
                            )
                        }
                    }
                }
            }
        }
        // ── 分P选集（内层，pages）──────────────────────────────────────
        if (v.pages.size > 1) {
            item {
                Text("选集（${v.pages.size}P）",
                     fontWeight = FontWeight.Bold, fontSize = 14.sp,
                     modifier = Modifier.padding(top = 6.dp, bottom = 4.dp))
                LazyRow(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                    items(v.pages, key = { it.cid }) { page ->
                        val isCurrent = page.cid == ui.currentCid
                        SelectChip(
                            text     = page.part.ifBlank { "P${page.page}" },
                            selected = isCurrent,
                            onClick  = { vm.switchPage(page.cid) }
                        )
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceEvenly) {
                ActionIcon(
                    icon       = Icons.Outlined.ThumbUp,
                    activeIcon = Icons.Filled.ThumbUp,
                    label      = if (ui.relation.like) "已赞" else "点赞",
                    active     = ui.relation.like,
                    onClick    = { vm.like() })
                ActionIcon(
                    icon       = Icons.Outlined.MonetizationOn,
                    activeIcon = Icons.Filled.MonetizationOn,
                    label      = "投币",
                    active     = ui.relation.coin > 0,
                    onClick    = { vm.coin() })
                ActionIcon(
                    icon        = Icons.Outlined.StarBorder,
                    activeIcon  = Icons.Filled.Star,
                    label       = if (ui.relation.favorite) "已藏" else "收藏",
                    active      = ui.relation.favorite,
                    onClick     = { vm.favorite() },
                    onLongClick = { vm.openFavSheet() })
                ActionIcon(
                    icon       = Icons.Outlined.Whatshot,
                    activeIcon = Icons.Filled.Whatshot,
                    label      = "三连",
                    active     = ui.relation.like && ui.relation.coin > 0 && ui.relation.favorite,
                    onClick    = { vm.triple() })
                ActionIcon(
                    icon       = Icons.Outlined.FileDownload,
                    activeIcon = Icons.Filled.DownloadDone,
                    label      = "下载",
                    active     = ui.downloadEnqueued,
                    onClick    = { vm.download() })
                ActionIcon(
                    icon    = Icons.Filled.Share,
                    label   = "分享",
                    onClick = {
                        val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(android.content.Intent.EXTRA_TEXT,
                                "${v.title}\nhttps://www.bilibili.com/video/${v.bvid}")
                        }
                        ctx.startActivity(android.content.Intent.createChooser(send, "分享到"))
                    })
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text("弹幕", fontSize = 13.sp)
                Switch(checked = ui.danmakuOn, onCheckedChange = { vm.toggleDanmaku() })
                IconButton(onClick = onOpenDanmakuSettings) {
                    Icon(Icons.Filled.Tune, contentDescription = "弹幕设置")
                }
                Spacer(Modifier.weight(1f))
                OutlinedTextField(value = dmText, onValueChange = { dmText = it },
                    label = { Text("发条弹幕") }, singleLine = true,
                    modifier = Modifier.weight(1f).padding(start = 8.dp))
                TextButton(onClick = { vm.postDanmaku(dmText); dmText = "" }) { Text("发送") }
            }
        }
        item {
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            Text("猜你喜欢", fontWeight = FontWeight.Bold)
        }
        items(ui.related) { r ->
            Text(r.title,
                 modifier = Modifier.fillMaxWidth().clickable { onOpenVideo(r.bvid) }
                     .padding(vertical = 8.dp),
                 fontSize = 14.sp)
        }
        item {
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            Text("评论 ${v.stat.reply}", fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = replyText, onValueChange = { replyText = it },
                    label = { Text("发表评论") }, singleLine = true,
                    modifier = Modifier.weight(1f))
                TextButton(onClick = { vm.sendReply(replyText); replyText = "" }) { Text("发送") }
            }
        }
        items(ui.replies) { rep ->
            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Text(rep.member.uname, fontSize = 12.sp, color = Color.Gray)
                Text(rep.content.message, fontSize = 14.sp)
            }
        }
        item {
            if (ui.loadingMoreReplies) {
                Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            } else if (ui.replyEnd && ui.replies.isNotEmpty()) {
                Box(Modifier.fillMaxWidth().padding(8.dp), Alignment.Center) {
                    Text("没有更多评论了", fontSize = 12.sp, color = Color.Gray)
                }
            }
        }
    }
}

@Composable
private fun SelectChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape    = RoundedCornerShape(8.dp),
        color    = if (selected) Color(0xFFFB7299) else Color(0x14000000),
        modifier = Modifier.clickable(onClick = onClick).widthIn(max = 160.dp)
    ) {
        Text(
            text,
            fontSize   = 12.sp,
            color      = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            maxLines   = 1,
            overflow   = TextOverflow.Ellipsis,
            modifier   = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}
