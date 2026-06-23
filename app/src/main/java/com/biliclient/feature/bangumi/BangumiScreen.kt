package com.biliclient.feature.bangumi

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdded
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material.icons.outlined.MonetizationOn
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.outlined.Whatshot
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.biliclient.feature.video.ActionIcon
import com.biliclient.feature.video.BiliPlayerSurface
import com.biliclient.feature.video.PlaySettingsPanel
import com.biliclient.feature.video.TAB_DANMAKU
import com.biliclient.feature.video.rememberFullscreenState
import com.biliclient.net.BangumiEp

/**
 * 番剧页：播放控件对齐 VideoScreen（BiliPlayerSurface + PlaySettingsPanel）；
 * 详情区保留番剧特色（番剧名/追番/下载/选集/附加内容），操作行使用共享 ActionIcon。
 */
@OptIn(UnstableApi::class)
@Composable
fun BangumiScreen(
    id      : String,
    onBack  : () -> Unit,
    modifier: Modifier = Modifier,
    vm      : BangumiViewModel = hiltViewModel()
) {
    val ui             by vm.ui.collectAsStateWithLifecycle()
    val danmaku        by vm.danmaku.collectAsStateWithLifecycle()
    val danmakuDisplay by vm.danmakuDisplay.collectAsStateWithLifecycle()
    val ctx = LocalContext.current

    var isFullscreen       by rememberFullscreenState()
    var showSettingsSheet  by remember { mutableStateOf(false) }
    var settingsInitialTab by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(id) { vm.load(id) }
    LaunchedEffect(ui.toast) {
        ui.toast?.let { Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show(); vm.clearToast() }
    }
    DisposableEffect(Unit) { onDispose { vm.playerManager.pause() } }

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
            epLoading          = ui.epLoading,
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
                ui.season != null -> BangumiContent(
                    vm                    = vm,
                    ui                    = ui,
                    onOpenDanmakuSettings = {
                        settingsInitialTab = TAB_DANMAKU
                        showSettingsSheet  = true
                    }
                )
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
            onQualitySelected = vm::switchEpQuality,
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

@Composable
private fun BangumiContent(
    vm                   : BangumiViewModel,
    ui                   : BangumiViewModel.BangumiUi,
    onOpenDanmakuSettings: () -> Unit
) {
    val season = ui.season ?: return
    var seasonMenuExpanded by remember { mutableStateOf(false) }
    val accent = MaterialTheme.colorScheme.primary
    val ctx = LocalContext.current

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {

        // ── 标题 ───────────────────────────────────────────
        item {
            Text(
                season.title,
                fontWeight = FontWeight.Bold, fontSize = 17.sp,
                modifier = Modifier.padding(top = 8.dp),
                maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            Text(
                "${bangumiTypeName(season.type)} · ${season.episodes.size} 集",
                color = Color.Gray, fontSize = 12.sp,
                modifier = Modifier.padding(top = 2.dp, bottom = 4.dp)
            )
        }

        // ── 番剧名 + 追番按钮（占 VideoScreen 里"UP名 + 关注"的位置）──
        item {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        season.seasonTitle.ifBlank { season.title },
                        fontSize = 13.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (season.seasons.size > 1) {
                        Box {
                            TextButton(onClick = { seasonMenuExpanded = true }) {
                                Text("换季 ▾", fontSize = 12.sp)
                            }
                            DropdownMenu(
                                expanded = seasonMenuExpanded,
                                onDismissRequest = { seasonMenuExpanded = false }
                            ) {
                                season.seasons.forEach { rel ->
                                    DropdownMenuItem(
                                        text = { Text(rel.seasonTitle) },
                                        onClick = { seasonMenuExpanded = false; vm.switchSeason(rel.seasonId) }
                                    )
                                }
                            }
                        }
                    }
                }
                TextButton(onClick = { vm.toggleFollow() }, enabled = !ui.followLoading) {
                    if (ui.followLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            imageVector = if (ui.followed) Icons.Filled.BookmarkAdded
                                          else Icons.Filled.BookmarkBorder,
                            contentDescription = null,
                            tint = if (ui.followed) accent else Color.Gray,
                            modifier = Modifier.size(18.dp).padding(end = 2.dp)
                        )
                        Text(
                            if (ui.followed) "已追番" else "追番",
                            fontSize = 13.sp,
                            color = if (ui.followed) accent else Color.Gray
                        )
                    }
                }
            }
        }

        // ── 操作行（下载）──────────────────────────────────
        item {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                season.episodes.firstOrNull { it.id == ui.currentEpId }?.let { ep ->
                    OutlinedButton(onClick = { vm.downloadEp(ep) }) { Text("下载本集") }
                }
                Button(onClick = { vm.downloadSeason() }) { Text("下载整季") }
            }
        }

        // ── 操作行（点赞 / 投币 / 收藏 / 三连）────────────────
        item {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
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
                    icon       = Icons.Outlined.StarBorder,
                    activeIcon = Icons.Filled.Star,
                    label      = if (ui.relation.favorite) "已藏" else "收藏",
                    active     = ui.relation.favorite,
                    onClick    = { vm.favorite() })
                ActionIcon(
                    icon       = Icons.Outlined.Whatshot,
                    activeIcon = Icons.Filled.Whatshot,
                    label      = "三连",
                    active     = ui.relation.like && ui.relation.coin > 0 && ui.relation.favorite,
                    onClick    = { vm.triple() })
                ActionIcon(
                    icon    = Icons.Filled.Share,
                    label   = "分享",
                    onClick = {
                        val url = if (ui.currentEpId > 0)
                            "https://www.bilibili.com/bangumi/play/ep${ui.currentEpId}"
                        else
                            "https://www.bilibili.com/bangumi/play/ss${season.seasonId}"
                        val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(android.content.Intent.EXTRA_TEXT, "${season.title}\n$url")
                        }
                        ctx.startActivity(android.content.Intent.createChooser(send, "分享到"))
                    })
            }
        }

        // ── 弹幕控制（开关 + 弹幕设置入口）────────────────────
        item {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("弹幕", fontSize = 13.sp)
                Switch(checked = ui.danmakuOn, onCheckedChange = { vm.toggleDanmaku() })
                IconButton(onClick = onOpenDanmakuSettings) {
                    Icon(Icons.Filled.Tune, contentDescription = "弹幕设置")
                }
                Spacer(Modifier.weight(1f))
            }
        }

        // ── 正片选集（分P式 LazyRow 卡片列表）────────────────
        item {
            Text(
                "选集", fontWeight = FontWeight.Bold, fontSize = 14.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 6.dp)
            )
        }
        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 8.dp)
            ) {
                items(season.episodes) { ep ->
                    EpChip(ep = ep, active = ep.id == ui.currentEpId, onClick = { vm.selectEp(ep) })
                }
            }
        }

        // ── 附加内容（PV / 番外 / 花絮等，剔除无 bvid/cid 章节）──
        val validSections = season.section.filter { sec ->
            sec.episodes.any { it.bvid.isNotEmpty() && it.cid > 0 }
        }
        if (validSections.isNotEmpty()) {
            item {
                Text(
                    "附加内容", fontWeight = FontWeight.Bold, fontSize = 14.sp,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )
            }
            validSections.forEach { sec ->
                val validEps = sec.episodes.filter { it.bvid.isNotEmpty() && it.cid > 0 }
                if (validEps.isNotEmpty()) {
                    item {
                        Text(sec.title, fontSize = 13.sp, color = Color.Gray,
                             modifier = Modifier.padding(bottom = 4.dp))
                    }
                    item {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = 8.dp)
                        ) {
                            items(validEps) { ep ->
                                EpChip(ep = ep, active = ep.id == ui.currentEpId,
                                       onClick = { vm.selectEp(ep) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EpChip(ep: BangumiEp, active: Boolean, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    val displayTitle = ep.showTitle.takeIf { it.isNotBlank() }
        ?: ep.longTitle.takeIf { it.isNotBlank() }
        ?: if (ep.title.all { it.isDigit() }) "第${ep.title}话" else ep.title

    Box(
        Modifier
            .border(
                width = if (active) 2.dp else 1.dp,
                color = if (active) accent else Color.Gray,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text       = displayTitle,
                color      = if (active) accent else Color.Unspecified,
                fontSize   = 13.sp,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                maxLines   = 1, overflow = TextOverflow.Ellipsis
            )
            if (ep.badge.isNotBlank()) {
                Text(ep.badge, fontSize = 10.sp, color = Color(0xFFFB7299))
            }
        }
    }
}

private fun bangumiTypeName(type: Int) = when (type) {
    1 -> "番剧"; 2 -> "电影"; 3 -> "纪录片"; 4 -> "国创"; 5 -> "电视剧"; 7 -> "综艺"; else -> "番剧"
}
