package com.biliclient.feature.tv.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.biliclient.core.MediaSelect
import com.biliclient.data.store.BiliStore
import com.biliclient.player.PlayerManager
import com.biliclient.repo.PlaySource
import com.biliclient.net.ArchiveRelation
import com.biliclient.net.FeedItem
import com.biliclient.net.FeedOwner
import com.biliclient.net.Page
import com.biliclient.player.DanmakuItem
import com.biliclient.repo.DanmakuRepo
import com.biliclient.repo.FeedRepo
import com.biliclient.repo.InteractionRepo
import com.biliclient.repo.UserRepo
import com.biliclient.repo.VideoRepo
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class TvUi(
    val feedItems: List<FeedItem> = emptyList(),
    val feedLoading: Boolean = false,
    val feedLoadingMore: Boolean = false,
    val freshIdx: Int = 0,
    val currentIndex: Int = 0,
    val currentBvid: String = "",
    val currentAid: Long = 0L,
    val currentCid: Long = 0L,
    /** 当前视频的分P列表（pages.size > 1 时显示分P选择按钮）。 */
    val pages: List<Page> = emptyList(),
    val danmakuOn: Boolean = false,            // TV 模式弹幕默认关
    val relation: ArchiveRelation = ArchiveRelation(),
    val acceptQuality: List<Int> = emptyList(),
    val currentQn: Int = 80,
    val isPlaying: Boolean = true,
    val toast: String? = null,
    val error: String? = null
)

data class DanmakuDisplay(val scale: Float = 1.0f, val alpha: Float = 1.0f)

@UnstableApi
@HiltViewModel
class TvHomeViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    val playerManager: PlayerManager,
    private val feedRepo: FeedRepo,
    private val videoRepo: VideoRepo,
    private val danmakuRepo: DanmakuRepo,
    private val interactionRepo: InteractionRepo,
    private val userRepo: UserRepo,
    private val store: BiliStore
) : ViewModel() {

    private val _ui = MutableStateFlow(TvUi())
    val ui = _ui.asStateFlow()

    private val _danmaku = MutableStateFlow<List<DanmakuItem>>(emptyList())
    val danmaku = _danmaku.asStateFlow()

    private val _danmakuDisplay = MutableStateFlow(DanmakuDisplay())
    val danmakuDisplay = _danmakuDisplay.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val s = store.settings()
            _danmakuDisplay.value = DanmakuDisplay(scale = s.danmakuTextScale, alpha = s.danmakuAlpha)
        }
        loadFeed()
        // 监听播放器状态：自动续播 + 同步 isPlaying
        playerManager.player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                _ui.update { it.copy(isPlaying = playerManager.player.playWhenReady) }
                if (state == Player.STATE_ENDED) next()
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _ui.update { it.copy(isPlaying = isPlaying) }
            }
        })
    }

    // ── 推荐流分页 ────────────────────────────────────────────────

    fun loadFeed() {
        _ui.update { it.copy(feedLoading = true, error = null) }
        viewModelScope.launch {
            val items = withContext(Dispatchers.IO) {
                runCatching { feedRepo.rcmd(0) }.getOrElse { emptyList() }
            }
            _ui.update { it.copy(feedLoading = false, feedItems = items, freshIdx = 1) }
            if (items.isNotEmpty()) playAt(0)
        }
    }

    fun loadMoreFeed() {
        val s = _ui.value
        if (s.feedLoadingMore || s.feedLoading) return
        _ui.update { it.copy(feedLoadingMore = true) }
        viewModelScope.launch {
            val more = withContext(Dispatchers.IO) {
                runCatching { feedRepo.rcmd(s.freshIdx) }.getOrElse { emptyList() }
            }
            _ui.update { it.copy(
                feedLoadingMore = false,
                feedItems       = s.feedItems + more,
                freshIdx        = s.freshIdx + 1
            ) }
        }
    }

    // ── 播放 ──────────────────────────────────────────────────────

    /**
     * @param allowCollectionTakeover true 时，若该视频属于合集（ugc_season），用合集集列表替换当前播放列表
     *   （上/下键走合集）。仅显式进入（playExternal=搜索/深链）时为 true；推荐流(loadFeed)为 false，
     *   避免某条推荐视频恰好属于合集就把整个推荐流劫持成该合集。
     */
    fun playAt(index: Int, allowCollectionTakeover: Boolean = false) {
        val items = _ui.value.feedItems
        if (index < 0 || index >= items.size) return
        val item = items[index]
        // 番剧条目（goto=="bangumi"，仅屏蔽关闭时出现）由界面路由到番剧页，不在此 inline 播放
        if (item.isBangumi || item.bvid.isEmpty()) return
        _ui.update { it.copy(currentIndex = index, currentBvid = item.bvid) }
        viewModelScope.launch(Dispatchers.IO) {
            val cid = if (item.cid != 0L) item.cid else {
                videoRepo.view(item.bvid).data?.cid ?: return@launch
            }
            // 原 resolvePlay+select+playDash 改用 playWithRecovery，
            // 以便在「连接被拒绝」时自动换 CDN 镜像或切 Android app API。
            val videoCodec = store.settings().videoCodec
            withContext(Dispatchers.Main) {
                playerManager.onUnrecoverable = {
                    _ui.update { it.copy(toast = "播放源均不可用，请检查网络") }
                }
                playerManager.playWithRecovery(item.bvid, scope = viewModelScope) { tier ->
                    val source = if (tier == 0) PlaySource.WEB else PlaySource.APP
                    val playData = videoRepo.resolvePlay(item.bvid, cid, qn = 80, source = source)
                        ?: return@playWithRecovery null
                    val dash = playData.dash ?: return@playWithRecovery null
                    val sel  = videoRepo.select(dash, MediaSelect.QUALITY_AUTO, videoCodec)
                        ?: return@playWithRecovery null
                    _ui.update { it.copy(
                        currentCid    = cid,
                        acceptQuality = playData.acceptQuality,
                        currentQn     = sel.video.id,
                        currentAid    = 0L
                    ) }
                    PlayerManager.PlayCandidates(
                        videoUrls = listOf(sel.video.baseUrl) + sel.video.backupUrl,
                        audioUrls = listOf(sel.audio?.baseUrl) + (sel.audio?.backupUrl ?: emptyList())
                    )
                }
            }
            val dmList = runCatching { danmakuRepo.fetch(cid, item.duration) }.getOrElse { emptyList() }
            _danmaku.value = dmList
            val view = runCatching { videoRepo.view(item.bvid).data }.getOrNull()
            if (view != null) {
                _ui.update { it.copy(currentAid = view.aid, pages = view.pages) }

                // 检测合集（ugc_season）：把合集集列表灌入播放列表，上/下键天然切集
                // 仅显式进入时允许，避免推荐流被某条合集视频劫持
                val ugcSeason = if (allowCollectionTakeover) view.ugcSeason else null
                if (ugcSeason != null) {
                    val newFeeds = ugcSeason.sections
                        .flatMap { it.episodes }
                        .filter { it.bvid.isNotEmpty() && it.cid > 0 }
                        .map { ep ->
                            FeedItem(
                                id       = ep.aid,
                                bvid     = ep.bvid,
                                cid      = ep.cid,
                                goto     = "av",
                                pic      = ep.arc.pic,
                                title    = ep.title.ifBlank { ep.page.part },
                                duration = ep.page.duration,
                                owner    = FeedOwner()
                            )
                        }
                    if (newFeeds.isNotEmpty()) {
                        val newIdx = newFeeds.indexOfFirst { it.bvid == item.bvid }.coerceAtLeast(0)
                        _ui.update { it.copy(feedItems = newFeeds, currentIndex = newIdx) }
                    }
                }

                val rel = runCatching { interactionRepo.relation(item.bvid) }.getOrElse { ArchiveRelation() }
                _ui.update { it.copy(relation = rel) }
            }
        }
    }

    fun playExternal(item: FeedItem) {
        val items    = _ui.value.feedItems.toMutableList()
        val existing = items.indexOfFirst { it.bvid == item.bvid }
        val index    = if (existing >= 0) existing else {
            items.add(0, item)
            _ui.update { it.copy(feedItems = items) }
            0
        }
        // 显式进入：允许合集接管播放列表
        playAt(index, allowCollectionTakeover = true)
    }

    fun prev() {
        val idx = _ui.value.currentIndex - 1
        if (idx >= 0) playAt(idx)
    }

    fun next() {
        val idx = _ui.value.currentIndex + 1
        if (idx < _ui.value.feedItems.size) {
            playAt(idx)
        } else {
            viewModelScope.launch {
                loadMoreFeedSuspend()
                val newIdx = _ui.value.currentIndex + 1
                if (newIdx < _ui.value.feedItems.size) playAt(newIdx)
            }
        }
    }

    private suspend fun loadMoreFeedSuspend() {
        val s = _ui.value
        val more = withContext(Dispatchers.IO) {
            runCatching { feedRepo.rcmd(s.freshIdx) }.getOrElse { emptyList() }
        }
        _ui.update { it.copy(
            feedItems = s.feedItems + more,
            freshIdx  = s.freshIdx + 1
        ) }
    }

    // ── 清晰度切换 ────────────────────────────────────────────────

    fun switchQuality(qn: Int) {
        val bvid = _ui.value.currentBvid
        val cid  = _ui.value.currentCid
        if (bvid.isEmpty() || cid == 0L) return
        viewModelScope.launch(Dispatchers.IO) {
            val pos      = withContext(Dispatchers.Main) { playerManager.positionMs }
            val playData = videoRepo.resolvePlay(bvid, cid, qn = qn) ?: return@launch
            val dash     = playData.dash ?: return@launch
            val sel      = videoRepo.select(dash, qualityPref = qn, codecPref = store.settings().videoCodec)
                           ?: return@launch
            _ui.update { it.copy(
                currentQn     = sel.video.id,
                acceptQuality = playData.acceptQuality
            ) }
            withContext(Dispatchers.Main) {
                playerManager.playDash(sel.video.baseUrl, sel.audio?.baseUrl, bvid, startPositionMs = pos)
            }
        }
    }

    // ── 分P 切换（复用 switchQuality 的 playerManager.playDash 路径）────
    /** 切换到同 bvid 的指定分P（cid），startPosition=0，防后台播放由单例 PlayerManager 天然保证。 */
    fun switchPage(cid: Long) {
        val bvid = _ui.value.currentBvid
        if (bvid.isEmpty() || cid == 0L) return
        viewModelScope.launch(Dispatchers.IO) {
            val playData = videoRepo.resolvePlay(bvid, cid, qn = _ui.value.currentQn) ?: return@launch
            val dash     = playData.dash ?: return@launch
            val sel      = videoRepo.select(dash,
                               qualityPref = _ui.value.currentQn,
                               codecPref   = store.settings().videoCodec
                           ) ?: return@launch
            _ui.update { it.copy(
                currentCid    = cid,
                currentQn     = sel.video.id,
                acceptQuality = playData.acceptQuality
            ) }
            withContext(Dispatchers.Main) {
                playerManager.playDash(sel.video.baseUrl, sel.audio?.baseUrl, bvid, startPositionMs = 0)
            }
            // 重拉弹幕
            val item    = _ui.value.feedItems.getOrNull(_ui.value.currentIndex)
            val dmList  = runCatching { danmakuRepo.fetch(cid, item?.duration ?: 0) }.getOrElse { emptyList() }
            _danmaku.value = dmList
        }
    }

    // ── 互动（照搬 VideoViewModel.kt:145-172） ────────────────────

    private fun aid() = _ui.value.currentAid

    fun like() = act {
        val on = !_ui.value.relation.like
        val r  = interactionRepo.like(aid(), on)
        if (r.ok) _ui.update { it.copy(relation = it.relation.copy(like = on)) }
        if (r.ok) (if (on) "已点赞" else "已取消") else "失败: ${r.message}"
    }

    fun coin() = act {
        val r = interactionRepo.coin(aid(), 1, alsoLike = true)
        if (r.ok) _ui.update { it.copy(relation = it.relation.copy(coin = it.relation.coin + 1)) }
        if (r.ok) "投币成功" else "投币失败: ${r.message}"
    }

    fun favorite() = act {
        val uid   = userRepo.currentUid() ?: return@act "请先登录"
        val first = interactionRepo.favFolders(uid).firstOrNull() ?: return@act "无收藏夹"
        val on    = !_ui.value.relation.favorite
        val r     = if (on) interactionRepo.favorite(aid(), listOf(first.id), emptyList())
                    else     interactionRepo.favorite(aid(), emptyList(), listOf(first.id))
        if (r.ok) _ui.update { it.copy(relation = it.relation.copy(favorite = on)) }
        if (r.ok) (if (on) "已收藏" else "已取消收藏") else "收藏失败: ${r.message}"
    }

    fun triple() = act {
        val r = interactionRepo.triple(aid())
        if (r.ok) _ui.update { it.copy(
            relation = ArchiveRelation(like = true, coin = it.relation.coin + 1, favorite = true)
        ) }
        if (r.ok) "三连成功！" else "三连失败: ${r.message}"
    }

    fun toggleDanmaku() {
        _ui.update { it.copy(danmakuOn = !it.danmakuOn) }
    }

    fun showToast(msg: String) { _ui.update { it.copy(toast = msg) } }
    fun clearToast() { _ui.update { it.copy(toast = null) } }

    // ── act{} 辅助 ───────────────────────────────────────────────

    private fun act(block: suspend () -> String) = viewModelScope.launch(Dispatchers.IO) {
        val msg = runCatching { block() }.getOrElse { it.message ?: "错误" }
        _ui.update { it.copy(toast = msg) }
    }

    // ── 生命周期 ──────────────────────────────────────────────────

    override fun onCleared() {
        super.onCleared()
        // 单例 PlayerManager，不在 VM 中 release，由 App 级 onDestroy 统一管理
    }
}
