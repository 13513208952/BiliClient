package com.biliclient.feature.tv.browse

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.MonetizationOn
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.biliclient.feature.tv.common.BiliPink
import com.biliclient.feature.tv.common.EpisodeSelectorPanel
import com.biliclient.feature.tv.common.QualityLabels
import com.biliclient.feature.tv.common.TvEpisodeBtn
import com.biliclient.feature.tv.common.tvFocusable
import com.biliclient.feature.tv.home.TvSideVideoList
import com.biliclient.net.FeedItem
import com.biliclient.player.DanmakuView
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class BrowsePlayState { BROWSE, FULLSCREEN_WATCH }

@OptIn(UnstableApi::class, ExperimentalFoundationApi::class)
@Composable
fun TvBrowseWatchScreen(
    seedItem          : FeedItem,
    onBack            : () -> Unit,
    onFullscreenChange: (Boolean) -> Unit = {},
    vm                : TvBrowseWatchViewModel = hiltViewModel()
) {
    val ui             by vm.ui.collectAsStateWithLifecycle()
    val danmaku        by vm.danmaku.collectAsStateWithLifecycle()
    val danmakuDisplay by vm.danmakuDisplay.collectAsStateWithLifecycle()

    var playState              by rememberSaveable { mutableStateOf(BrowsePlayState.BROWSE) }
    var backPressedOnce        by remember { mutableStateOf(false) }
    var controlsVisible        by remember { mutableStateOf(false) }
    var danmakuViewRef         by remember { mutableStateOf<DanmakuView?>(null) }
    var controlsInteractionKey by remember { mutableStateOf(0) }
    var menuOpen               by remember { mutableStateOf(false) }   // 倍速/清晰度二级菜单是否展开
    var selectorOpen           by remember { mutableStateOf(false) }   // 分P选集面板是否展开
    var seekJob                by remember { mutableStateOf<Job?>(null) }
    val fullscreenFocusRequester = remember { FocusRequester() }
    val scope                   = rememberCoroutineScope()

    LaunchedEffect(seedItem.bvid) { vm.initWithSeed(seedItem) }

    // 全屏态变化上报给 TvNavHost（用于隐藏顶部 Tab 栏）
    LaunchedEffect(playState) {
        onFullscreenChange(playState == BrowsePlayState.FULLSCREEN_WATCH)
    }
    // 双击退全屏：2s 内无第二次按下则重置确认位
    LaunchedEffect(backPressedOnce) {
        if (backPressedOnce) { delay(2000); backPressedOnce = false }
    }

    DisposableEffect(Unit) { onDispose { vm.playerManager.pause() } }

    LaunchedEffect(danmaku, danmakuViewRef) { danmakuViewRef?.setData(danmaku) }
    LaunchedEffect(Unit) {
        while (true) {
            danmakuViewRef?.updateTime(vm.playerManager.positionMs)
            delay(80)
        }
    }

    LaunchedEffect(ui.toast) {
        if (ui.toast != null) { delay(2500); vm.clearToast() }
    }

    // 控制层无操作自动收起：分P面板打开时完全挂起；二级菜单打开时延长到 10s；其余 3s
    LaunchedEffect(controlsVisible, controlsInteractionKey, menuOpen, selectorOpen) {
        if (controlsVisible && !selectorOpen) { delay(if (menuOpen) 10_000L else 3_000L); controlsVisible = false }
    }

    // 控制层收起时：复位二级菜单标志 + 关闭分P面板 + 把焦点收回全屏 Box
    LaunchedEffect(controlsVisible) {
        if (!controlsVisible) {
            menuOpen     = false
            selectorOpen = false
            if (playState == BrowsePlayState.FULLSCREEN_WATCH) {
                try { fullscreenFocusRequester.requestFocus() } catch (_: Exception) {}
            }
        }
    }

    // 全屏态 Back：分P面板 → 控制层 → 双击退回列表态（继续播放，不暂停）
    BackHandler(enabled = playState == BrowsePlayState.FULLSCREEN_WATCH) {
        if (selectorOpen) {
            selectorOpen = false                 // 先关分P选集面板
        } else if (controlsVisible) {
            controlsVisible = false              // 再关控制层（焦点回收 effect 随后拉回全屏 Box）
        } else if (backPressedOnce) {
            backPressedOnce = false
            playState       = BrowsePlayState.BROWSE
            controlsVisible = false
        } else {
            backPressedOnce = true
            vm.showToast("再次按返回键退出全屏")
        }
    }
    // 列表态 Back：退回浏览网格页
    BackHandler(enabled = playState == BrowsePlayState.BROWSE) { onBack() }

    Box(Modifier.fillMaxSize().background(Color.Black)) {

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

        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory  = { ctx -> DanmakuView(ctx).also { danmakuViewRef = it } },
            update   = { view ->
                view.danmakuEnabled = ui.danmakuOn
                view.textScale      = danmakuDisplay.scale
                view.danmakuAlpha   = (danmakuDisplay.alpha * 255).toInt()
            }
        )

        // ── 层 3：左侧侧栏（BROWSE 态滑入，FULLSCREEN 滑出）────
        AnimatedVisibility(
            visible = playState == BrowsePlayState.BROWSE,
            enter   = slideInHorizontally { -it } + fadeIn(animationSpec = tween(220)),
            exit    = slideOutHorizontally { -it } + fadeOut(animationSpec = tween(220))
        ) {
            TvSideVideoList(
                modifier     = Modifier
                    .fillMaxHeight()
                    .width(216.dp)
                    .background(
                        Brush.horizontalGradient(
                            0.0f to Color(0xE6000000),
                            1.0f to Color(0x33000000)
                        )
                    )
                    .focusGroup(),
                items        = ui.feedItems,
                currentIndex = ui.currentIndex,
                onSelect     = { index ->
                    vm.playAt(index)
                    playState       = BrowsePlayState.FULLSCREEN_WATCH
                    controlsVisible = false
                },
                onLoadMore   = {}
            )
        }

        // ── 层 4：BROWSE 态右侧视频区（点击进全屏）─────────────
        if (playState == BrowsePlayState.BROWSE) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(start = 216.dp)
                    .tvFocusable(onClick = {
                        playState       = BrowsePlayState.FULLSCREEN_WATCH
                        controlsVisible = false
                    })
            )
        }

        // ── 层 5：FULLSCREEN_WATCH 遥控器 + 手势层 + 控制层 ──────
        if (playState == BrowsePlayState.FULLSCREEN_WATCH) {
            // 进入全屏时把焦点锁在本 Box，遥控器 OK 只走 onPreviewKeyEvent（播放/暂停），
            // 不会跑到控制条里的返回按钮上导致误退全屏
            LaunchedEffect(Unit) {
                try { fullscreenFocusRequester.requestFocus() } catch (_: Exception) {}
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .focusRequester(fullscreenFocusRequester)
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
                                else { vm.prev(); controlsInteractionKey++; true }
                            }
                            Key.DirectionDown -> {
                                if (controlsVisible) { controlsInteractionKey++; false }
                                else { vm.next(); controlsInteractionKey++; true }
                            }
                            Key.DirectionLeft -> {
                                if (controlsVisible) { controlsInteractionKey++; false }
                                else {
                                    if (seekJob == null) {
                                        seekRelative(vm, -10_000L)
                                        seekJob = scope.launch {
                                            delay(500L)
                                            while (true) {
                                                val step = (vm.playerManager.durationMs * 0.01).toLong().coerceAtLeast(500L)
                                                seekRelative(vm, -step)
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
                                        seekRelative(vm, 6_000L)
                                        seekJob = scope.launch {
                                            delay(500L)
                                            while (true) {
                                                val step = (vm.playerManager.durationMs * 0.008).toLong().coerceAtLeast(400L)
                                                seekRelative(vm, step)
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
                                    // 不弹出控制条（去除闪现），暂停状态由中央暂停图标指示
                                    true
                                }
                            }
                            Key.Menu -> { controlsVisible = true; controlsInteractionKey++; true }
                            else -> false
                        }
                    }
                    // 鼠标/触控：单击 toggle 控制层（原有行为，不暂停/恢复）
                    // 用 focusable+pointerInput 代替 combinedClickable，避免遥控器 OK 键也触发此回调
                    .focusable()
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = {
                            controlsVisible = !controlsVisible
                            if (controlsVisible) controlsInteractionKey++
                        })
                    }
            ) {
                AnimatedVisibility(visible = controlsVisible, enter = fadeIn(), exit = fadeOut()) {
                    BrowseControlOverlay(
                        vm               = vm,
                        ui               = ui,
                        onBack           = {
                            // 退回列表态：视频视作仍处于焦点，不暂停
                            playState       = BrowsePlayState.BROWSE
                            controlsVisible = false
                        },
                        onInteraction    = { controlsInteractionKey++ },
                        onMenuOpenChange = { menuOpen = it },
                        selectorOpen     = selectorOpen,
                        onSelectorToggle = { selectorOpen = !selectorOpen }
                    )
                }
                // ── 右 1/3 分P选集面板（selectorOpen 时从右侧滑入）──────
                AnimatedVisibility(
                    visible  = selectorOpen,
                    modifier = Modifier.align(Alignment.CenterEnd),
                    enter    = slideInHorizontally(tween(220)) { it } + fadeIn(tween(220)),
                    exit     = slideOutHorizontally(tween(220)) { it } + fadeOut(tween(220))
                ) {
                    EpisodeSelectorPanel(
                        pages      = ui.pages,
                        currentCid = ui.currentCid,
                        onSelect   = { cid ->
                            vm.switchPage(cid)
                            selectorOpen    = false
                            controlsVisible = false
                        },
                        onDismiss  = { selectorOpen = false }
                    )
                }
                // 遥控器 OK 暂停标志：仅暂停时显示，与控制条无关
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
        }

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

// ── 控制覆盖层 ─────────────────────────────────────────────────────

@Composable
private fun BrowseControlOverlay(
    vm               : TvBrowseWatchViewModel,
    ui               : BrowseWatchUi,
    onBack           : () -> Unit,
    onInteraction    : () -> Unit = {},
    onMenuOpenChange : (Boolean) -> Unit = {},
    selectorOpen     : Boolean = false,
    onSelectorToggle : () -> Unit = {}
) {
    // 面板弹出时把焦点放到第一个控制按钮（播放/暂停），确保遥控器方向键能在面板内导航。
    val firstBtnFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos {}                          // 等一帧确保面板节点完成布局再请求焦点
        try { firstBtnFocus.requestFocus() } catch (_: Exception) {}
    }

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
                .tvFocusable(onClick = { onBack(); onInteraction() }, shape = CircleShape)
                .background(Color(0x99000000), CircleShape)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text("浏览", color = Color.White, fontSize = 15.sp)
        }

        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color(0xCC000000))
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                BrowseSeekBar(vm = vm, onInteraction = onInteraction)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    BrowseIconBtn(
                        icon     = if (ui.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        label    = if (ui.isPlaying) "暂停" else "播放",
                        modifier = Modifier.focusRequester(firstBtnFocus),
                        onClick  = { vm.playerManager.playPause(); onInteraction() }
                    )
                    BrowseIconBtn(
                        icon        = if (ui.relation.like) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                        label       = "点赞",
                        tint        = if (ui.relation.like) BiliPink else Color.White,
                        onClick     = { vm.like(); onInteraction() },
                        onLongClick = { vm.triple(); onInteraction() }
                    )
                    BrowseIconBtn(
                        icon    = if (ui.relation.coin > 0) Icons.Filled.MonetizationOn
                                  else Icons.Outlined.MonetizationOn,
                        label   = "投币",
                        tint    = if (ui.relation.coin > 0) BiliPink else Color.White,
                        onClick = { vm.coin(); onInteraction() }
                    )
                    BrowseIconBtn(
                        icon    = if (ui.relation.favorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        label   = "收藏",
                        tint    = if (ui.relation.favorite) BiliPink else Color.White,
                        onClick = { vm.favorite(); onInteraction() }
                    )
                    BrowseIconBtn(
                        icon    = if (ui.danmakuOn) Icons.Filled.Subtitles else Icons.Outlined.SubtitlesOff,
                        label   = "弹幕",
                        tint    = if (ui.danmakuOn) BiliPink else Color.White,
                        onClick = { vm.toggleDanmaku(); onInteraction() }
                    )
                    BrowseSpeedBtn(vm = vm, onInteraction = onInteraction, onMenuOpenChange = onMenuOpenChange)
                    BrowseQualityBtn(vm = vm, ui = ui, onInteraction = onInteraction, onMenuOpenChange = onMenuOpenChange)
                    // 分P按钮：仅多P视频出现，位于画质右侧行尾
                    if (ui.pages.size > 1) {
                        TvEpisodeBtn(
                            pageCount    = ui.pages.size,
                            selectorOpen = selectorOpen,
                            onToggle     = { onSelectorToggle(); onInteraction() }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BrowseIconBtn(
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

@Composable
private fun BrowseSeekBar(vm: TvBrowseWatchViewModel, onInteraction: () -> Unit = {}) {
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
            onValueChange = { frac ->                        // 触控/鼠标：保持立即跳转
                scrubFrac = frac
                vm.playerManager.seekTo((frac * durMs).toLong())
                onInteraction()
            },
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { st ->
                    val f = st.isFocused || st.hasFocus
                    if (f && !focused) scrubFrac = progress  // 刚获焦：预览点从实际进度起步
                    focused = f                               // 失焦：显示回实际进度，视频不跳转
                }
                .onPreviewKeyEvent { ev ->
                    if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (ev.key) {
                        Key.DirectionLeft  -> { scrubFrac = (scrubFrac - 0.01f).coerceIn(0f, 1f); onInteraction(); true }
                        Key.DirectionRight -> { scrubFrac = (scrubFrac + 0.01f).coerceIn(0f, 1f); onInteraction(); true }
                        Key.DirectionCenter, Key.Enter -> {   // OK 键确认跳转
                            vm.playerManager.seekTo((scrubFrac * durMs).toLong()); onInteraction(); true
                        }
                        else -> false                          // 上/下不拦截，焦点正常移出进度条
                    }
                },
            colors = SliderDefaults.colors(thumbColor = BiliPink, activeTrackColor = BiliPink)
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(fmtMs(shownMs), color = Color.White, fontSize = 12.sp)  // 预览态显示目标时间
            Text(fmtMs(durMs),   color = Color.White, fontSize = 12.sp)
        }
    }
}

@Composable
private fun BrowseQualityBtn(
    vm               : TvBrowseWatchViewModel,
    ui               : BrowseWatchUi,
    onInteraction    : () -> Unit = {},
    onMenuOpenChange : (Boolean) -> Unit = {}
) {
    var expanded  by remember { mutableStateOf(false) }
    val itemFocus =  remember { FocusRequester() }

    // 菜单开/关：上报给面板计时器 + 展开时把焦点放到当前选中项
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
                    onClick  = { expanded = false; vm.switchQuality(qn); onInteraction() }
                )
            }
        }
    }
}

@Composable
private fun BrowseSpeedBtn(
    vm               : TvBrowseWatchViewModel,
    onInteraction    : () -> Unit = {},
    onMenuOpenChange : (Boolean) -> Unit = {}
) {
    var expanded     by remember { mutableStateOf(false) }
    var currentSpeed by remember { mutableStateOf(1.0f) }
    val itemFocus    =  remember { FocusRequester() }

    // 菜单开/关：上报给面板计时器 + 展开时把焦点放到当前选中项
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
            Text(QualityLabels.speedLabel(currentSpeed), color = Color.White, fontSize = 11.sp)
        }
        DropdownMenu(
            expanded         = expanded,
            onDismissRequest = { expanded = false },
            modifier         = Modifier.background(Color(0xDD111111))
        ) {
            val list     = QualityLabels.SPEEDS
            val focusIdx = list.indexOf(currentSpeed).let { if (it >= 0) it else 0 }
            list.forEachIndexed { i, speed ->
                var itemFocused by remember { mutableStateOf(false) }
                DropdownMenuItem(
                    text     = {
                        Text(QualityLabels.speedLabel(speed),
                             color = if (!itemFocused && speed == currentSpeed) BiliPink else Color.White)
                    },
                    modifier = (if (i == focusIdx) Modifier.focusRequester(itemFocus) else Modifier)
                        .onFocusChanged { itemFocused = it.isFocused }
                        .background(if (itemFocused) BiliPink.copy(alpha = 0.30f) else Color.Transparent),
                    onClick  = {
                        expanded = false
                        currentSpeed = speed
                        vm.playerManager.setSpeed(speed)
                        onInteraction()
                    }
                )
            }
        }
    }
}

// ── 工具函数 ──────────────────────────────────────────────────────

private fun seekRelative(vm: TvBrowseWatchViewModel, deltaMs: Long) {
    val dur = vm.playerManager.durationMs
    if (dur <= 0L) return
    val newPos = (vm.playerManager.positionMs + deltaMs).coerceIn(0L, dur)
    vm.playerManager.seekTo(newPos)
    if (newPos <= 0L || newPos >= dur) vm.playerManager.pause()
}

private fun fmtMs(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}
