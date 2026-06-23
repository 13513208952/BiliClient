package com.biliclient.feature.tv.bangumi

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdded
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.MonetizationOn
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.SubtitlesOff
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.biliclient.feature.bangumi.BangumiViewModel
import com.biliclient.feature.tv.common.BiliPink
import com.biliclient.feature.tv.common.QualityLabels
import com.biliclient.feature.tv.common.TvSelectorPanel
import com.biliclient.feature.tv.common.tvFocusable
import com.biliclient.player.DanmakuView
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * TV 原生番剧播放页（始终全屏，无 BROWSE 侧栏态）。
 *
 * 遥控器：上/下=切集（prevEp/nextEp）、左/右=seek（短按±10s/+6s、长按连续）、
 *         OK=播放暂停、Menu/单击=弹出控制层；
 * 控制层：返回 + 进度条 + 播放/暂停 + 追番 + 弹幕 + 倍速 + 选集；
 * 右1/3面板：TvSelectorPanel 展示全集列表，遥控器上下选、OK 确认。
 *
 * @param id     番剧 id（ss123 / ep456 格式，与 BangumiViewModel.load 参数相同）。
 * @param onBack 退出页面回调（由 TvNavHost 控制覆盖层 tvBangumiId=null）。
 */
@OptIn(UnstableApi::class, ExperimentalFoundationApi::class)
@Composable
fun TvBangumiScreen(
    id    : String,
    onBack: () -> Unit,
    vm    : BangumiViewModel = hiltViewModel()
) {
    val ui     by vm.ui.collectAsStateWithLifecycle()
    val danmaku by vm.danmaku.collectAsStateWithLifecycle()

    var controlsVisible        by remember { mutableStateOf(false) }
    var selectorOpen           by remember { mutableStateOf(false) }
    var menuOpen               by remember { mutableStateOf(false) }
    var danmakuViewRef         by remember { mutableStateOf<DanmakuView?>(null) }
    var controlsInteractionKey by remember { mutableStateOf(0) }
    var seekJob                by remember { mutableStateOf<Job?>(null) }
    val fullscreenFocus        = remember { FocusRequester() }
    val scope                  = rememberCoroutineScope()

    // 加载番剧（id 变化时重新加载）
    LaunchedEffect(id) { vm.load(id) }

    // 退出时暂停
    DisposableEffect(Unit) { onDispose { vm.playerManager.pause() } }

    // 弹幕数据同步
    LaunchedEffect(danmaku, danmakuViewRef) { danmakuViewRef?.setData(danmaku) }
    LaunchedEffect(Unit) {
        while (true) {
            danmakuViewRef?.updateTime(vm.playerManager.positionMs)
            delay(80)
        }
    }

    // Toast 自动消除
    LaunchedEffect(ui.toast) {
        if (ui.toast != null) { delay(2500); vm.clearToast() }
    }

    // 控制层无操作自动收起：selectorOpen 完全挂起；menuOpen 延长到 10s；其余 3s
    LaunchedEffect(controlsVisible, controlsInteractionKey, menuOpen, selectorOpen) {
        if (controlsVisible && !selectorOpen) {
            delay(if (menuOpen) 10_000L else 3_000L)
            controlsVisible = false
        }
    }

    // 控制层收起时：复位 menuOpen/selectorOpen + 焦点回全屏 Box
    LaunchedEffect(controlsVisible) {
        if (!controlsVisible) {
            menuOpen     = false
            selectorOpen = false
            try { fullscreenFocus.requestFocus() } catch (_: Exception) {}
        }
    }

    // 初始焦点
    LaunchedEffect(Unit) {
        try { fullscreenFocus.requestFocus() } catch (_: Exception) {}
    }

    // Back：面板 → 控制层 → 退出
    BackHandler {
        when {
            selectorOpen    -> selectorOpen = false
            controlsVisible -> controlsVisible = false
            else            -> onBack()
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {

        // 层1：视频
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory  = { ctx ->
                PlayerView(ctx).apply {
                    player        = vm.playerManager.player
                    useController = false
                    setKeepContentOnPlayerReset(true)
                }
            }
        )

        // 层2：弹幕
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory  = { ctx -> DanmakuView(ctx).also { danmakuViewRef = it } },
            update   = { view ->
                view.danmakuEnabled = ui.danmakuOn
                view.textScale      = 1.0f
                view.danmakuAlpha   = 255
            }
        )

        // 层3：遥控器+手势+控制层+选集面板
        Box(
            Modifier
                .fillMaxSize()
                .focusRequester(fullscreenFocus)
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) {
                        if (event.key == Key.DirectionLeft || event.key == Key.DirectionRight) {
                            seekJob?.cancel(); seekJob = null
                        }
                        return@onPreviewKeyEvent false
                    }
                    when (event.key) {
                        Key.DirectionUp -> {
                            if (controlsVisible) { controlsInteractionKey++; false }
                            else { vm.prevEp(); controlsInteractionKey++; true }
                        }
                        Key.DirectionDown -> {
                            if (controlsVisible) { controlsInteractionKey++; false }
                            else { vm.nextEp(); controlsInteractionKey++; true }
                        }
                        Key.DirectionLeft -> {
                            if (controlsVisible) { controlsInteractionKey++; false }
                            else {
                                if (seekJob == null) {
                                    tvBangumiSeekRelative(vm, -10_000L)
                                    seekJob = scope.launch {
                                        delay(500L)
                                        while (true) {
                                            val step = (vm.playerManager.durationMs * 0.01).toLong().coerceAtLeast(500L)
                                            tvBangumiSeekRelative(vm, -step)
                                            delay(80L)
                                        }
                                    }
                                }
                                controlsInteractionKey++; true
                            }
                        }
                        Key.DirectionRight -> {
                            if (controlsVisible) { controlsInteractionKey++; false }
                            else {
                                if (seekJob == null) {
                                    tvBangumiSeekRelative(vm, 6_000L)
                                    seekJob = scope.launch {
                                        delay(500L)
                                        while (true) {
                                            val step = (vm.playerManager.durationMs * 0.008).toLong().coerceAtLeast(400L)
                                            tvBangumiSeekRelative(vm, step)
                                            delay(80L)
                                        }
                                    }
                                }
                                controlsInteractionKey++; true
                            }
                        }
                        Key.DirectionCenter, Key.Enter -> {
                            if (controlsVisible) { controlsInteractionKey++; false }
                            else {
                                if (ui.isPlaying) vm.playerManager.pause() else vm.playerManager.play()
                                true
                            }
                        }
                        Key.Menu -> { controlsVisible = true; controlsInteractionKey++; true }
                        else -> false
                    }
                }
                .focusable()
                .pointerInput(Unit) {
                    detectTapGestures(onTap = {
                        controlsVisible = !controlsVisible
                        if (controlsVisible) controlsInteractionKey++
                    })
                }
        ) {
            // 控制层（淡入淡出）
            AnimatedVisibility(visible = controlsVisible, enter = fadeIn(), exit = fadeOut()) {
                BangumiControlOverlay(
                    vm               = vm,
                    ui               = ui,
                    onBack           = onBack,
                    onInteraction    = { controlsInteractionKey++ },
                    onMenuOpenChange = { menuOpen = it },
                    selectorOpen     = selectorOpen,
                    onSelectorToggle = { selectorOpen = !selectorOpen }
                )
            }

            // 右1/3 选集面板（从右侧滑入，selectorOpen 时展开）
            val season = ui.season
            if (season != null) {
                val currentIdx = season.episodes
                    .indexOfFirst { it.id == ui.currentEpId }
                    .coerceAtLeast(0)
                AnimatedVisibility(
                    visible  = selectorOpen,
                    modifier = Modifier.align(Alignment.CenterEnd),
                    enter    = slideInHorizontally(tween(220)) { it } + fadeIn(tween(220)),
                    exit     = slideOutHorizontally(tween(220)) { it } + fadeOut(tween(220))
                ) {
                    TvSelectorPanel(
                        title        = "选集 (${season.episodes.size}集)",
                        items        = season.episodes,
                        currentIndex = currentIdx,
                        key          = { it.id },
                        tag          = { "第${it.title}话" },
                        label        = { ep ->
                            ep.longTitle.ifBlank { ep.showTitle.ifBlank { "第${ep.title}话" } }
                        },
                        onSelect     = { ep ->
                            vm.selectEp(ep)
                            selectorOpen    = false
                            controlsVisible = false
                        },
                        onDismiss    = { selectorOpen = false }
                    )
                }
            }

            // 中央暂停图标（仅暂停时显示，与控制层无关）
            if (!ui.isPlaying) {
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .background(Color(0x80000000), CircleShape)
                        .padding(14.dp)
                ) {
                    Icon(
                        imageVector        = Icons.Default.PlayArrow,
                        contentDescription = "已暂停",
                        tint               = Color.White,
                        modifier           = Modifier.size(39.dp)
                    )
                }
            }
        }

        // 加载指示（loading / epLoading）
        if (ui.loading || ui.epLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("加载中…", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
            }
        }

        // 错误提示
        ui.error?.let { err ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(err, color = Color.White, fontSize = 16.sp)
            }
        }

        // Toast
        ui.toast?.let { msg ->
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 24.dp)
                    .background(Color(0xCC000000), RoundedCornerShape(8.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(msg, color = Color.White, fontSize = 14.sp)
            }
        }
    }
}

// ── 控制覆盖层 ────────────────────────────────────────────────────────────

@Composable
private fun BangumiControlOverlay(
    vm               : BangumiViewModel,
    ui               : BangumiViewModel.BangumiUi,
    onBack           : () -> Unit,
    onInteraction    : () -> Unit = {},
    onMenuOpenChange : (Boolean) -> Unit = {},
    selectorOpen     : Boolean = false,
    onSelectorToggle : () -> Unit = {}
) {
    val firstBtnFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos {}
        try { firstBtnFocus.requestFocus() } catch (_: Exception) {}
    }

    Box(Modifier.fillMaxSize()) {
        // 返回按钮（左上角）
        Box(
            Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
                .tvFocusable(onClick = { onBack(); onInteraction() }, shape = CircleShape)
                .background(Color(0x99000000), CircleShape)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text("返回", color = Color.White, fontSize = 15.sp)
        }

        // 底部控制条（进度条 + 按钮行）
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color(0xCC000000))
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                BangumiSeekBar(vm = vm, onInteraction = onInteraction)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    // 播放/暂停
                    BangumiIconBtn(
                        icon     = if (ui.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        label    = if (ui.isPlaying) "暂停" else "播放",
                        modifier = Modifier.focusRequester(firstBtnFocus),
                        onClick  = { vm.playerManager.playPause(); onInteraction() }
                    )
                    // 追番
                    BangumiIconBtn(
                        icon    = if (ui.followed) Icons.Filled.BookmarkAdded
                                  else Icons.Default.BookmarkBorder,
                        label   = if (ui.followed) "已追番" else "追番",
                        tint    = if (ui.followed) BiliPink else Color.White,
                        onClick = { vm.toggleFollow(); onInteraction() }
                    )
                    // 点赞（长按三连）
                    BangumiIconBtn(
                        icon        = if (ui.relation.like) Icons.Filled.ThumbUp
                                      else Icons.Outlined.ThumbUp,
                        label       = "点赞",
                        tint        = if (ui.relation.like) BiliPink else Color.White,
                        onClick     = { vm.like(); onInteraction() },
                        onLongClick = { vm.triple(); onInteraction() }
                    )
                    // 投币
                    BangumiIconBtn(
                        icon    = if (ui.relation.coin > 0) Icons.Filled.MonetizationOn
                                  else Icons.Outlined.MonetizationOn,
                        label   = "投币",
                        tint    = if (ui.relation.coin > 0) BiliPink else Color.White,
                        onClick = { vm.coin(); onInteraction() }
                    )
                    // 收藏
                    BangumiIconBtn(
                        icon    = if (ui.relation.favorite) Icons.Filled.Star
                                  else Icons.Outlined.StarBorder,
                        label   = "收藏",
                        tint    = if (ui.relation.favorite) BiliPink else Color.White,
                        onClick = { vm.favorite(); onInteraction() }
                    )
                    // 弹幕
                    BangumiIconBtn(
                        icon    = if (ui.danmakuOn) Icons.Filled.Subtitles
                                  else Icons.Outlined.SubtitlesOff,
                        label   = "弹幕",
                        tint    = if (ui.danmakuOn) BiliPink else Color.White,
                        onClick = { vm.toggleDanmaku(); onInteraction() }
                    )
                    // 倍速
                    BangumiSpeedBtn(vm = vm, ui = ui, onInteraction = onInteraction, onMenuOpenChange = onMenuOpenChange)
                    // 画质（有可选清晰度时显示）
                    if (ui.acceptQuality.isNotEmpty()) {
                        BangumiQualityBtn(vm = vm, ui = ui, onInteraction = onInteraction, onMenuOpenChange = onMenuOpenChange)
                    }
                    // 选集按钮（番剧集数 > 1 时显示；固定显示"选集"而非"分P"）
                    val epCount = ui.season?.episodes?.size ?: 0
                    if (epCount > 1) {
                        BangumiEpisodeBtn(
                            epCount      = epCount,
                            selectorOpen = selectorOpen,
                            onToggle     = { onSelectorToggle(); onInteraction() }
                        )
                    }
                }
            }
        }
    }
}

// ── 选集按钮（番剧专用，显示"选集"而非"分P"）────────────────────────────

@Composable
private fun BangumiEpisodeBtn(epCount: Int, selectorOpen: Boolean, onToggle: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .tvFocusable(onClick = onToggle, shape = RoundedCornerShape(6.dp))
            .padding(8.dp)
    ) {
        Icon(
            Icons.Default.PlaylistPlay,
            contentDescription = "选集",
            tint     = if (selectorOpen) BiliPink else Color.White,
            modifier = Modifier.size(32.dp)
        )
        Text("选集 ($epCount)", color = if (selectorOpen) BiliPink else Color.White, fontSize = 11.sp)
    }
}

// ── 进度条 ───────────────────────────────────────────────────────────────

@Composable
private fun BangumiSeekBar(vm: BangumiViewModel, onInteraction: () -> Unit = {}) {
    var posMs    by remember { mutableStateOf(0L) }
    var durMs    by remember { mutableStateOf(1L) }
    var focused  by remember { mutableStateOf(false) }
    var scrubFrac by remember { mutableStateOf(0f) }

    LaunchedEffect(Unit) {
        while (true) {
            posMs = vm.playerManager.positionMs
            val d = vm.playerManager.player.duration
            durMs = if (d > 0) d else 1L
            delay(1000)
        }
    }

    val progress = (posMs.toFloat() / durMs).coerceIn(0f, 1f)
    val shown    = if (focused) scrubFrac else progress
    val shownMs  = if (focused) (scrubFrac * durMs).toLong() else posMs

    Column(Modifier.fillMaxWidth()) {
        Slider(
            value         = shown,
            onValueChange = { frac ->
                scrubFrac = frac
                vm.playerManager.seekTo((frac * durMs).toLong())
                onInteraction()
            },
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { st ->
                    val f = st.isFocused || st.hasFocus
                    if (f && !focused) scrubFrac = progress
                    focused = f
                }
                .onPreviewKeyEvent { ev ->
                    if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (ev.key) {
                        Key.DirectionLeft  -> { scrubFrac = (scrubFrac - 0.01f).coerceIn(0f, 1f); onInteraction(); true }
                        Key.DirectionRight -> { scrubFrac = (scrubFrac + 0.01f).coerceIn(0f, 1f); onInteraction(); true }
                        Key.DirectionCenter, Key.Enter -> {
                            vm.playerManager.seekTo((scrubFrac * durMs).toLong()); onInteraction(); true
                        }
                        else -> false
                    }
                },
            colors = SliderDefaults.colors(thumbColor = BiliPink, activeTrackColor = BiliPink)
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(tvBangumiFormatMs(shownMs), color = Color.White, fontSize = 12.sp)
            Text(tvBangumiFormatMs(durMs),   color = Color.White, fontSize = 12.sp)
        }
    }
}

// ── 图标按钮 ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BangumiIconBtn(
    icon       : ImageVector,
    label      : String,
    tint       : Color = Color.White,
    modifier   : Modifier = Modifier,
    onClick    : () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .tvFocusable(onClick = onClick, onLongClick = onLongClick, shape = CircleShape)
            .padding(8.dp)
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(32.dp))
        Text(label, color = Color.White, fontSize = 11.sp)
    }
}

// ── 倍速按钮 ─────────────────────────────────────────────────────────────

@Composable
private fun BangumiSpeedBtn(
    vm               : BangumiViewModel,
    ui               : BangumiViewModel.BangumiUi,
    onInteraction    : () -> Unit = {},
    onMenuOpenChange : (Boolean) -> Unit = {}
) {
    var expanded  by remember { mutableStateOf(false) }
    val itemFocus =  remember { FocusRequester() }

    LaunchedEffect(expanded) {
        onMenuOpenChange(expanded)
        if (expanded) {
            withFrameNanos {}
            try { itemFocus.requestFocus() } catch (_: Exception) {}
        }
    }

    Box {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .tvFocusable(onClick = { expanded = true; onInteraction() }, shape = RoundedCornerShape(6.dp))
                .padding(8.dp)
        ) {
            Icon(Icons.Default.FastForward, contentDescription = "速度",
                 tint = Color.White, modifier = Modifier.size(32.dp))
            Text(QualityLabels.speedLabel(ui.playbackSpeed), color = Color.White, fontSize = 11.sp)
        }
        DropdownMenu(
            expanded         = expanded,
            onDismissRequest = { expanded = false },
            modifier         = Modifier.background(Color(0xDD111111))
        ) {
            val list     = QualityLabels.SPEEDS
            val focusIdx = list.indexOf(ui.playbackSpeed).let { if (it >= 0) it else 0 }
            list.forEachIndexed { i, speed ->
                var itemFocused by remember { mutableStateOf(false) }
                DropdownMenuItem(
                    text     = {
                        Text(QualityLabels.speedLabel(speed),
                             color = if (!itemFocused && speed == ui.playbackSpeed) BiliPink else Color.White)
                    },
                    modifier = (if (i == focusIdx) Modifier.focusRequester(itemFocus) else Modifier)
                        .onFocusChanged { itemFocused = it.isFocused }
                        .background(if (itemFocused) BiliPink.copy(alpha = 0.30f) else Color.Transparent),
                    onClick  = {
                        expanded = false
                        vm.setSpeed(speed)
                        onInteraction()
                    }
                )
            }
        }
    }
}

// ── 画质按钮 ─────────────────────────────────────────────────────────────

@Composable
private fun BangumiQualityBtn(
    vm               : BangumiViewModel,
    ui               : BangumiViewModel.BangumiUi,
    onInteraction    : () -> Unit = {},
    onMenuOpenChange : (Boolean) -> Unit = {}
) {
    var expanded  by remember { mutableStateOf(false) }
    val itemFocus =  remember { FocusRequester() }

    LaunchedEffect(expanded) {
        onMenuOpenChange(expanded)
        if (expanded) {
            withFrameNanos {}
            try { itemFocus.requestFocus() } catch (_: Exception) {}
        }
    }

    Box {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .tvFocusable(onClick = { expanded = true; onInteraction() }, shape = RoundedCornerShape(6.dp))
                .padding(8.dp)
        ) {
            Icon(Icons.Default.Tune, contentDescription = "清晰度",
                 tint = Color.White, modifier = Modifier.size(32.dp))
            Text(QualityLabels.label(ui.currentQn), color = Color.White, fontSize = 11.sp)
        }
        DropdownMenu(
            expanded         = expanded,
            onDismissRequest = { expanded = false },
            modifier         = Modifier.background(Color(0xDD111111))
        ) {
            val list     = ui.acceptQuality
            val focusIdx = list.indexOf(ui.currentQn).let { if (it >= 0) it else 0 }
            list.forEachIndexed { i, qn ->
                var itemFocused by remember { mutableStateOf(false) }
                DropdownMenuItem(
                    text     = {
                        Text(QualityLabels.label(qn),
                             color = if (!itemFocused && qn == ui.currentQn) BiliPink else Color.White)
                    },
                    modifier = (if (i == focusIdx) Modifier.focusRequester(itemFocus) else Modifier)
                        .onFocusChanged { itemFocused = it.isFocused }
                        .background(if (itemFocused) BiliPink.copy(alpha = 0.30f) else Color.Transparent),
                    onClick  = {
                        expanded = false
                        vm.switchEpQuality(qn)
                        onInteraction()
                    }
                )
            }
        }
    }
}

// ── 工具函数 ──────────────────────────────────────────────────────────────

private fun tvBangumiSeekRelative(vm: BangumiViewModel, deltaMs: Long) {
    val dur = vm.playerManager.durationMs
    if (dur <= 0L) return
    val newPos = (vm.playerManager.positionMs + deltaMs).coerceIn(0L, dur)
    vm.playerManager.seekTo(newPos)
    if (newPos <= 0L || newPos >= dur) vm.playerManager.pause()
}

private fun tvBangumiFormatMs(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}
