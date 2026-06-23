package com.biliclient.feature.video

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.biliclient.R
import com.biliclient.feature.tv.common.QualityLabels
import com.biliclient.player.DanmakuItem
import com.biliclient.player.DanmakuView
import kotlinx.coroutines.delay
import kotlin.math.abs

// ─────────────────────────────────────────────────────────────────────────────
// 页签常量（internal：同模块其他屏幕可用 settingsInitialTab 直达指定页签）
// ─────────────────────────────────────────────────────────────────────────────
internal const val TAB_SPEED   = 0
internal const val TAB_QUALITY = 1
internal const val TAB_DANMAKU = 2
private val SETTINGS_TABS = listOf("倍速", "清晰度", "弹幕")
/** 整屏宽度对应的快进/快退秒数（固定速率，与视频时长无关）。精细且不快，适合局部回溯/跳过。 */
private const val SECONDS_PER_FULL_WIDTH = 90f

internal fun speedLabel(v: Float): String =
    v.toBigDecimal().stripTrailingZeros().toPlainString() + "×"

internal tailrec fun Context.findActivity(): Activity = when (this) {
    is Activity       -> this
    is ContextWrapper -> baseContext.findActivity()
    else              -> error("Context 无法解析为 Activity")
}

// ─────────────────────────────────────────────────────────────────────────────
// 全屏状态：旋转 + 系统栏沉浸 + BackHandler + 退出还原
// 用法：var isFullscreen by rememberFullscreenState()
// ─────────────────────────────────────────────────────────────────────────────
@Composable
internal fun rememberFullscreenState(): MutableState<Boolean> {
    val state      = remember { mutableStateOf(false) }
    val isFs       = state.value
    val ctx        = LocalContext.current
    val activity   = remember(ctx) { ctx.findActivity() }

    LaunchedEffect(isFs) {
        val ctrl = WindowInsetsControllerCompat(activity.window, activity.window.decorView)
        if (isFs) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            ctrl.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            ctrl.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            ctrl.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    BackHandler(enabled = isFs) { state.value = false }

    DisposableEffect(Unit) {
        onDispose {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            WindowInsetsControllerCompat(activity.window, activity.window.decorView)
                .show(WindowInsetsCompat.Type.systemBars())
        }
    }

    return state
}

// ─────────────────────────────────────────────────────────────────────────────
// 横向滑动手势容器（Android View 层，onInterceptTouchEvent 协作）
//
// 把 PlayerView 作为唯一子 View 包裹在此容器中。
//   • 点击、纵向手势 → 不拦截 → 原样落到子 PlayerView（显隐控制条/全屏/设置/暂停/进度条照常）
//   • 确认横向拖动（|dx|>slop 且 |dx|≥|dy|）→ onInterceptTouchEvent 返回 true，
//     子 View 收到 ACTION_CANCEL，本层 onTouchEvent 接管，计算 seek 预览；松手提交 seek。
//
// 进度条（DefaultTimeBar）保护：
//   ① DefaultTimeBar 自身会调 requestDisallowInterceptTouchEvent(true)，本层自动跳过拦截。
//   ② 兜底：控制条可见时底部 72dp 内横滑不拦截，双重保护进度条拖动不受影响。
// ─────────────────────────────────────────────────────────────────────────────
@UnstableApi
private class SeekGestureLayout(context: Context) : FrameLayout(context) {

    private val slop = ViewConfiguration.get(context).scaledTouchSlop

    /** 内层 PlayerView 引用，用于查询控制条可见性（保护进度条区域）。 */
    var playerView: PlayerView? = null

    /** 当前播放位置提供者（拦截成功瞬间读取，作为 seek 起点）。 */
    var positionProvider: () -> Long = { 0L }

    /** 当前视频时长提供者（计算目标 clamp 上限）。 */
    var durationProvider: () -> Long = { 0L }

    /** 拖动中回调：deltaMs（正=快进，负=快退）, targetMs（目标绝对时间 ms）。 */
    var onPreview: (deltaMs: Long, targetMs: Long) -> Unit = { _, _ -> }

    /** 松手（ACTION_UP）回调：targetMs（确认 seek 到此位置）。 */
    var onCommit: (targetMs: Long) -> Unit = {}

    /** 取消（ACTION_CANCEL）回调。 */
    var onCancel: () -> Unit = {}

    // ── 手势状态（每次 ACTION_DOWN 重置）────────────────────────
    private var downX          = 0f
    private var downY          = 0f
    private var startPos       = 0L
    private var isIntercepting = false
    /** true = 按下点落在进度条保护区，本次手势不允许横滑拦截。 */
    private var guardTimebar   = false

    // ── onInterceptTouchEvent ────────────────────────────────────
    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX          = ev.x
                downY          = ev.y
                isIntercepting = false
                // 进度条保护：控制条可见 + 按下点在底部控制区 → 不拦截此次手势
                val pv = playerView
                guardTimebar = if (pv != null && pv.isControllerFullyVisible) {
                    val guardHeightPx = 72f * resources.displayMetrics.density
                    ev.y > height - guardHeightPx
                } else false
            }
            MotionEvent.ACTION_MOVE -> {
                if (!isIntercepting && !guardTimebar) {
                    val dx    = ev.x - downX
                    val dy    = ev.y - downY
                    val absDx = abs(dx)
                    val absDy = abs(dy)
                    if (absDx > slop || absDy > slop) {
                        if (absDx >= absDy) {
                            // 确认横滑：夺取后续事件，记录 seek 起点
                            isIntercepting = true
                            startPos       = positionProvider()
                            return true
                        }
                        // 纵向：不拦截，放行给子 PlayerView
                    }
                }
            }
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                isIntercepting = false
            }
        }
        return false
    }

    // ── onTouchEvent ─────────────────────────────────────────────
    // 仅在 onInterceptTouchEvent 返回 true 后由系统调用。
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        val totalDx  = ev.x - downX
        val duration = durationProvider().coerceAtLeast(0L)
        val deltaMs  = ((totalDx / width.toFloat()) * SECONDS_PER_FULL_WIDTH * 1000L).toLong()
        val targetMs = (startPos + deltaMs).coerceIn(
            0L, if (duration > 0L) duration else Long.MAX_VALUE
        )
        when (ev.actionMasked) {
            MotionEvent.ACTION_MOVE   -> onPreview(deltaMs, targetMs)
            MotionEvent.ACTION_UP     -> { onCommit(targetMs); isIntercepting = false }
            MotionEvent.ACTION_CANCEL -> { onCancel();         isIntercepting = false }
        }
        return true
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 播放器表面：SeekGestureLayout 包住 PlayerView（进度条/播放暂停/设置齿轮/全屏按钮）
// + DanmakuView 叠加层 + 返回按钮 + 加载转圈 + 拖动预览气泡
// ─────────────────────────────────────────────────────────────────────────────
@UnstableApi
@Composable
internal fun BiliPlayerSurface(
    player             : androidx.media3.common.Player,
    danmaku            : List<DanmakuItem>,
    danmakuOn          : Boolean,
    danmakuScale       : Float,
    danmakuAlpha       : Float,
    danmakuDensity     : Float,
    positionMs         : () -> Long,
    isFullscreen       : Boolean,
    onFullscreenChange : (Boolean) -> Unit,
    epLoading          : Boolean = false,
    onSettingsClick    : () -> Unit,
    onBack             : () -> Unit,
    modifier           : Modifier = Modifier
) {
    val boxMod = if (isFullscreen)
        modifier.fillMaxSize().background(Color.Black)
    else
        modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black)

    var danmakuView  by remember { mutableStateOf<DanmakuView?>(null) }
    // 横向拖动快进/快退预览状态：null = 未拖动；non-null = 正在拖动（值为 deltaMs，正=快进，负=快退）
    var dragDeltaMs  by remember { mutableStateOf<Long?>(null) }
    var dragTargetMs by remember { mutableStateOf(0L) }

    LaunchedEffect(danmaku, danmakuView) { danmakuView?.setData(danmaku) }
    LaunchedEffect(danmakuView) {
        while (true) { danmakuView?.updateTime(positionMs()); delay(80) }
    }

    Box(boxMod) {
        // ── 播放器底层（SeekGestureLayout 包住 Media3 PlayerView）──
        // 手势协作原则（纯增量，不破坏任何已有功能）：
        //   • 点击 / 纵向滑动 → 不拦截 → 原样落到 PlayerView：显隐控制条/全屏/设置/暂停照常。
        //   • 确认横滑 → 拦截，实时更新预览气泡，松手执行 seekTo（一次性生效）。
        //   • 控制条可见时进度条区域（底部 72dp）不拦截，DefaultTimeBar 拖动照常。
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory  = { c ->
                val gl = SeekGestureLayout(c)
                LayoutInflater.from(c).inflate(R.layout.player_view, gl, true)
                gl
            },
            update = { gl ->
                val pv = gl.getChildAt(0) as? PlayerView ?: return@AndroidView
                pv.player = player
                pv.setFullscreenButtonClickListener { enter -> onFullscreenChange(enter) }
                pv.findViewById<android.view.View>(R.id.biliclient_settings)
                    ?.setOnClickListener { onSettingsClick() }
                gl.playerView       = pv
                gl.positionProvider = { player.currentPosition }
                gl.durationProvider = { player.duration }
                gl.onPreview = { deltaMs, targetMs ->
                    dragDeltaMs  = deltaMs
                    dragTargetMs = targetMs
                }
                gl.onCommit = { targetMs ->
                    player.seekTo(targetMs)
                    dragDeltaMs = null
                }
                gl.onCancel = { dragDeltaMs = null }
            }
        )

        // ── 弹幕叠加层（不拦截触摸）─────────────────────────────
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory  = { c -> DanmakuView(c).also { danmakuView = it } },
            update   = { view ->
                view.danmakuEnabled = danmakuOn
                view.textScale      = danmakuScale
                view.danmakuAlpha   = (danmakuAlpha * 255).toInt()
                view.densityFactor  = danmakuDensity
            }
        )

        // ── 返回按钮 ────────────────────────────────────────────
        IconButton(
            onClick  = { if (isFullscreen) onFullscreenChange(false) else onBack() },
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(8.dp)
                .background(Color(0x66000000), CircleShape)
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = Color.White)
        }

        if (epLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color    = Color.White
            )
        }

        // ── 拖动预览气泡（仅拖动态可见，显示目标时间 + ±偏移量）──
        // 无 pointerInput，不遮挡任何触摸事件。
        val currentDelta = dragDeltaMs
        if (currentDelta != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color(0xCC000000), RoundedCornerShape(10.dp))
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text       = formatMs(dragTargetMs),
                        color      = Color.White,
                        fontSize   = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text     = formatDeltaMs(currentDelta),
                        color    = Color(0xFFCCCCCC),
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 紧凑悬浮播放设置面板（Popup 独立窗口，无遮罩，视频始终可见）
// 全屏：右侧抽屉 320dp；竖屏：底部窄条 heightIn(max=380dp)
// 三页签：倍速 / 清晰度 / 弹幕
// ─────────────────────────────────────────────────────────────────────────────
@Composable
internal fun PlaySettingsPanel(
    isFullscreen     : Boolean,
    initialTab       : Int?    = null,
    playbackSpeed    : Float,
    onSpeedChange    : (Float) -> Unit,
    acceptQuality    : List<Int>,
    currentQn        : Int,
    onQualitySelected: (Int) -> Unit,
    danmakuOn        : Boolean,
    onToggleDanmaku  : () -> Unit,
    danmakuScale     : Float,
    danmakuAlpha     : Float,
    danmakuDensity   : Float,
    onDanmakuScale   : (Float) -> Unit,
    onDanmakuAlpha   : (Float) -> Unit,
    onDanmakuDensity : (Float) -> Unit,
    onDismiss        : () -> Unit
) {
    var tabIndex by remember {
        mutableIntStateOf(initialTab ?: if (acceptQuality.isNotEmpty()) TAB_QUALITY else TAB_SPEED)
    }

    Popup(
        alignment        = if (isFullscreen) Alignment.CenterEnd else Alignment.BottomCenter,
        onDismissRequest = onDismiss,
        properties       = PopupProperties(focusable = true)
    ) {
        val panelMod = if (isFullscreen)
            Modifier.width(320.dp).fillMaxHeight()
        else
            Modifier.fillMaxWidth().heightIn(max = 380.dp)

        val panelShape = if (isFullscreen)
            RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)
        else
            RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)

        Surface(
            modifier     = panelMod,
            shape        = panelShape,
            color        = Color(0xF0212121),
            contentColor = Color.White
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SETTINGS_TABS.forEachIndexed { i, tab ->
                        val sel = i == tabIndex
                        Surface(
                            shape    = RoundedCornerShape(20.dp),
                            color    = if (sel) Color(0xFFFB7299) else Color(0x33FFFFFF),
                            modifier = Modifier.clickable { tabIndex = i }
                        ) {
                            Text(
                                tab,
                                modifier   = Modifier.padding(horizontal = 16.dp, vertical = 7.dp),
                                fontSize   = 13.sp,
                                color      = Color.White,
                                fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                when (tabIndex) {
                    TAB_SPEED   -> SettingsSpeedPage(playbackSpeed, onSpeedChange)
                    TAB_QUALITY -> SettingsQualityPage(acceptQuality, currentQn, onQualitySelected, onDismiss)
                    TAB_DANMAKU -> SettingsDanmakuPage(
                        danmakuOn, onToggleDanmaku,
                        danmakuScale, danmakuAlpha, danmakuDensity,
                        onDanmakuScale, onDanmakuAlpha, onDanmakuDensity
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSpeedPage(playbackSpeed: Float, onSpeedChange: (Float) -> Unit) {
    SliderRow(
        label      = "倍速",
        value      = playbackSpeed,
        displayStr = speedLabel(playbackSpeed),
        valueRange = 0.25f..3.0f,
        steps      = 10,
        onChange   = { v -> onSpeedChange(Math.round(v / 0.25f) * 0.25f) }
    )
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun SettingsQualityPage(
    acceptQuality    : List<Int>,
    currentQn        : Int,
    onQualitySelected: (Int) -> Unit,
    onDismiss        : () -> Unit
) {
    if (acceptQuality.isEmpty()) {
        Text("画质信息加载中…", fontSize = 14.sp, color = Color(0xAAFFFFFF),
             modifier = Modifier.padding(vertical = 8.dp))
        return
    }
    Column {
        acceptQuality.forEach { qn ->
            val sel = qn == currentQn
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onQualitySelected(qn); onDismiss() }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    QualityLabels.label(qn),
                    fontSize   = 15.sp,
                    color      = if (sel) Color(0xFFFB7299) else Color.White,
                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                    modifier   = Modifier.weight(1f)
                )
                if (sel) Text("✓", fontSize = 15.sp, color = Color(0xFFFB7299))
            }
            HorizontalDivider(color = Color(0x22FFFFFF))
        }
    }
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun SettingsDanmakuPage(
    danmakuOn       : Boolean,
    onToggleDanmaku : () -> Unit,
    danmakuScale    : Float,
    danmakuAlpha    : Float,
    danmakuDensity  : Float,
    onDanmakuScale  : (Float) -> Unit,
    onDanmakuAlpha  : (Float) -> Unit,
    onDanmakuDensity: (Float) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("弹幕开关", Modifier.weight(1f), fontSize = 14.sp, color = Color.White)
        Switch(checked = danmakuOn, onCheckedChange = { onToggleDanmaku() })
    }
    HorizontalDivider(color = Color(0x22FFFFFF), modifier = Modifier.padding(bottom = 8.dp))
    SliderRow("字号",
        value      = danmakuScale,
        displayStr = "${"%.1f".format(danmakuScale)}×",
        valueRange = 0.5f..2.0f, steps = 14,
        onChange   = onDanmakuScale)
    SliderRow("透明度",
        value      = danmakuAlpha,
        displayStr = "${(danmakuAlpha * 100).toInt()}%",
        valueRange = 0.1f..1.0f, steps = 17,
        onChange   = onDanmakuAlpha)
    SliderRow("密度",
        value      = danmakuDensity,
        displayStr = "${(danmakuDensity * 100).toInt()}%",
        valueRange = 0.1f..1.0f, steps = 17,
        onChange   = onDanmakuDensity)
    Spacer(Modifier.height(4.dp))
}

@Composable
internal fun SliderRow(
    label     : String,
    value     : Float,
    displayStr: String,
    valueRange: ClosedFloatingPointRange<Float>,
    steps     : Int,
    onChange  : (Float) -> Unit
) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(label,      fontSize = 13.sp, modifier = Modifier.weight(0.22f))
        Slider(value = value, onValueChange = onChange,
               valueRange = valueRange, steps = steps,
               modifier = Modifier.weight(0.60f))
        Text(displayStr, fontSize = 12.sp,
             modifier = Modifier.weight(0.18f).padding(start = 6.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 横向拖动预览：时间格式化工具（私有）
// ─────────────────────────────────────────────────────────────────────────────

/** 将毫秒格式化为 `m:ss` 或 `h:mm:ss`（目标时间显示）。 */
private fun formatMs(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** 将毫秒偏移量格式化为 `+N分M秒` / `-N秒` 形式（拖动量提示）。 */
private fun formatDeltaMs(deltaMs: Long): String {
    val sign     = if (deltaMs >= 0) "+" else "-"
    val totalSec = abs(deltaMs) / 1000L
    val m        = totalSec / 60
    val s        = totalSec % 60
    return if (m > 0) "$sign${m}分${s}秒" else "$sign${s}秒"
}

// ─────────────────────────────────────────────────────────────────────────────
// 操作按钮图标（点赞/投币/收藏/三连/下载/分享 通用入口）
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ActionIcon(
    icon       : ImageVector,
    activeIcon : ImageVector      = icon,
    label      : String,
    active     : Boolean          = false,
    onClick    : () -> Unit,
    onLongClick: (() -> Unit)?    = null
) {
    val tint = if (active) Color(0xFFFB7299)
               else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
        Icon(if (active) activeIcon else icon, label, tint = tint,
             modifier = Modifier.size(24.dp))
        Text(label, fontSize = 11.sp, color = tint)
    }
}
