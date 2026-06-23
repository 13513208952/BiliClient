package com.biliclient.feature.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.hilt.navigation.compose.hiltViewModel
import com.biliclient.feature.tv.common.tvEdgeAware
import com.biliclient.feature.tv.home.TvHomeViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.biliclient.feature.tv.browse.TvBrowseScreen
import com.biliclient.feature.tv.browse.TvBrowseWatchScreen
import com.biliclient.net.FeedItem
import com.biliclient.feature.tv.common.tvFocusable
import com.biliclient.feature.tv.bangumi.TvBangumiScreen
import com.biliclient.feature.tv.desktop.TvDesktopScreen
import com.biliclient.feature.tv.home.TvHomeScreen
import com.biliclient.feature.tv.mine.TvDownloadsPanel
import com.biliclient.feature.tv.mine.TvFavPanel
import com.biliclient.feature.tv.mine.TvHistoryPanel
import com.biliclient.feature.tv.mine.TvMineScreen
import com.biliclient.feature.tv.mine.TvSettingsPanel
import com.biliclient.feature.tv.search.TvSearchPanel

private sealed class TvDetail {
    object Settings  : TvDetail()
    object Fav       : TvDetail()
    object History   : TvDetail()
    object Downloads : TvDetail()
}

private enum class TvTab(val label: String, val icon: ImageVector) {
    HOME   ("首页", Icons.Default.Home),
    BROWSE ("浏览", Icons.Default.VideoLibrary),
    DESKTOP("桌面", Icons.Default.Apps),
    MINE   ("我的", Icons.Default.Person),
    SEARCH ("搜索", Icons.Default.Search)
}

@Composable
fun TvNavHost(uiScale: Float = 1.0f) {
    val origDensity = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(
            density   = origDensity.density   * uiScale,
            fontScale = origDensity.fontScale * uiScale
        )
    ) {

    var selectedTab by remember { mutableStateOf(TvTab.HOME) }
    val tabFocusRequesters = remember { TvTab.entries.associateWith { FocusRequester() } }
    var homeFullscreen   by remember { mutableStateOf(false) }
    var browseFullscreen by remember { mutableStateOf(false) }
    var tvDetail by remember { mutableStateOf<TvDetail?>(null) }
    var pendingPlay by remember { mutableStateOf<FeedItem?>(null) }
    var browseWatchItem by remember { mutableStateOf<FeedItem?>(null) }
    var tvBangumiId by remember { mutableStateOf<String?>(null) }
    var exitConfirmPending by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val homeVm: TvHomeViewModel = hiltViewModel()
    val activity = LocalContext.current as? android.app.Activity

    fun switchTab(target: TvTab) {
        homeVm.playerManager.pause()
        selectedTab = target
        scope.launch {
            delay(50)
            try { tabFocusRequesters[target]!!.requestFocus() } catch (_: Exception) { }
        }
    }

    // 5s 确认窗口自动关闭（doc 06 §3.2）
    LaunchedEffect(exitConfirmPending) {
        if (exitConfirmPending) {
            delay(5000)
            exitConfirmPending = false
        }
    }

    // 子页关闭（先注册，优先级较低）
    BackHandler(enabled = tvDetail != null) { tvDetail = null }

    // 顶级返回首页/双击退出（后注册，优先级更高）—— doc 06 §3.2
    // browseWatchItem != null 时由 TvBrowseWatchScreen 内的 BackHandler 先消费（doc 07 §3）
    BackHandler(enabled = tvDetail == null && !homeFullscreen && browseWatchItem == null && tvBangumiId == null) {
        when {
            selectedTab != TvTab.HOME -> {
                switchTab(TvTab.HOME)
                exitConfirmPending = true
            }
            exitConfirmPending -> {
                exitConfirmPending = false
                activity?.finish()
            }
            else -> {
                exitConfirmPending = true
            }
        }
    }

    LaunchedEffect(Unit) {
        try { tabFocusRequesters[TvTab.HOME]!!.requestFocus() } catch (_: Exception) { }
    }

    val tabBarVisible = !(selectedTab == TvTab.HOME && homeFullscreen) &&
        !(selectedTab == TvTab.BROWSE && browseWatchItem != null && browseFullscreen) &&
        (tvDetail == null) &&
        (tvBangumiId == null)

    Column(Modifier.fillMaxSize().background(Color(0xFF0D0D0D))) {

        // ── 顶部 Tab 行（全屏时自动隐藏）──────────────────────
        AnimatedVisibility(
            visible = tabBarVisible,
            enter   = slideInVertically { -it } + fadeIn(animationSpec = tween(250)),
            exit    = slideOutVertically { -it } + fadeOut(animationSpec = tween(250))
        ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(Color(0xFF1A1A1A))
                .focusGroup()
                .padding(horizontal = 24.dp),
            verticalAlignment    = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TvTab.entries.forEach { tab ->
                val isSelected = tab == selectedTab
                Row(
                    modifier = Modifier
                        .focusRequester(tabFocusRequesters[tab]!!)
                        .padding(horizontal = 4.dp, vertical = 4.dp)
                        .background(
                            color = if (isSelected) Color(0xFFFB7299).copy(alpha = 0.2f)
                                    else Color.Transparent,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .tvFocusable(
                            onClick = { switchTab(tab) },
                            shape   = RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector        = tab.icon,
                        contentDescription = tab.label,
                        tint               = if (isSelected) Color(0xFFFB7299) else Color.White,
                        modifier           = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text     = tab.label,
                        color    = if (isSelected) Color(0xFFFB7299) else Color.White,
                        fontSize = 15.sp
                    )
                }
            }
        }
        } // end AnimatedVisibility (tab bar)

        // ── 内容区域 ────────────────────────────────────────────
        Box(
            Modifier
                .fillMaxSize()
                .tvEdgeAware(
                    enabled     = !homeFullscreen && !browseFullscreen,
                    onLeftEdge  = TvTab.entries.getOrNull(selectedTab.ordinal - 1)
                                      ?.let { prevTab -> { switchTab(prevTab) } },
                    onRightEdge = TvTab.entries.getOrNull(selectedTab.ordinal + 1)
                                      ?.let { nextTab -> { switchTab(nextTab) } }
                )
        ) {
            when (selectedTab) {
                TvTab.HOME    -> TvHomeScreen(
                    onFullscreenChange    = { homeFullscreen = it },
                    pendingPlay           = pendingPlay,
                    onPendingPlayConsumed = { pendingPlay = null },
                    onOpenBangumi         = { id -> tvBangumiId = id }
                )
                TvTab.BROWSE  -> {
                    if (browseWatchItem != null) {
                        TvBrowseWatchScreen(
                            seedItem           = browseWatchItem!!,
                            onFullscreenChange = { browseFullscreen = it },
                            onBack             = {
                                homeVm.playerManager.pause()
                                browseWatchItem  = null
                                browseFullscreen = false
                            }
                        )
                    } else {
                        TvBrowseScreen(
                            onPlayVideo = { item -> browseWatchItem = item }
                        )
                    }
                }
                TvTab.DESKTOP -> TvDesktopScreen()
                TvTab.MINE    -> TvMineScreen(
                    onSettings      = { tvDetail = TvDetail.Settings },
                    onFav           = { tvDetail = TvDetail.Fav },
                    onHistory       = { tvDetail = TvDetail.History },
                    onDownloads     = { tvDetail = TvDetail.Downloads },
                    onSwitchToPhone = { }
                )
                TvTab.SEARCH  -> TvSearchPanel(
                    onPlayVideo = { item ->
                        pendingPlay = item
                        selectedTab = TvTab.HOME
                    },
                    onPlayBangumi = { id ->
                        tvBangumiId = id
                    }
                )
            }
            // ── 子页面覆盖层（覆盖整个内容区）───────────────────────
            if (tvDetail != null) {
                val detail = tvDetail
                Box(Modifier.fillMaxSize().background(Color(0xFF0D0D0D))) {
                    when (detail) {
                        TvDetail.Settings  -> TvSettingsPanel(onBack = { tvDetail = null })
                        TvDetail.Fav       -> TvFavPanel(onBack = { tvDetail = null })
                        TvDetail.History   -> TvHistoryPanel(onBack = { tvDetail = null })
                        TvDetail.Downloads -> TvDownloadsPanel(onBack = { tvDetail = null })
                        null               -> { }
                    }
                }
            }

            // ── 番剧全屏覆盖层（TV 原生播放页，从搜索/feed 进入）────────────────
            if (tvBangumiId != null) {
                Box(Modifier.fillMaxSize().background(Color(0xFF0D0D0D))) {
                    TvBangumiScreen(
                        id    = tvBangumiId!!,
                        onBack = {
                            homeVm.playerManager.pause()
                            tvBangumiId = null
                        }
                    )
                }
            }

            // 退出确认提示（doc 06 §3.3）
            if (exitConfirmPending) {
                Box(
                    modifier         = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Box(
                        Modifier
                            .padding(top = 72.dp)
                            .background(Color(0xCC000000), RoundedCornerShape(8.dp))
                            .padding(horizontal = 20.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text     = "再按一次返回键退出 App",
                            color    = Color.White,
                            fontSize = 15.sp
                        )
                    }
                }
            }
        }
    }
    } // end CompositionLocalProvider
}
