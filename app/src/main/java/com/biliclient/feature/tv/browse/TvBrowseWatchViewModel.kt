package com.biliclient.feature.tv.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.biliclient.data.store.BiliStore
import com.biliclient.feature.tv.home.DanmakuDisplay
import com.biliclient.net.ArchiveRelation
import com.biliclient.net.FeedItem
import com.biliclient.net.FeedOwner
import com.biliclient.net.FeedStat
import com.biliclient.net.Page
import com.biliclient.player.DanmakuItem
import com.biliclient.player.PlayerManager
import com.biliclient.repo.PlaySource
import com.biliclient.repo.DanmakuRepo
import com.biliclient.repo.InteractionRepo
import com.biliclient.repo.RelatedRepo
import com.biliclient.repo.UserRepo
import com.biliclient.repo.VideoRepo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class BrowseWatchUi(
    val feedItems    : List<FeedItem>   = emptyList(),
    val currentIndex : Int             = 0,
    val currentBvid  : String          = "",
    val currentCid   : Long            = 0L,
    val currentAid   : Long            = 0L,
    /** 当前视频的分P列表（pages.size > 1 时显示分P选择按钮）。 */
    val pages        : List<Page>      = emptyList(),
    val acceptQuality: List<Int>       = emptyList(),
    val currentQn    : Int             = 80,
    val isPlaying    : Boolean         = true,
    val loading      : Boolean         = false,
    val danmakuOn    : Boolean         = false,
    val relation     : ArchiveRelation = ArchiveRelation(),
    val toast        : String?         = null
)

@HiltViewModel
class TvBrowseWatchViewModel @Inject constructor(
    val playerManager   : PlayerManager,
    private val relatedRepo     : RelatedRepo,
    private val videoRepo       : VideoRepo,
    private val danmakuRepo     : DanmakuRepo,
    private val interactionRepo : InteractionRepo,
    private val userRepo        : UserRepo,
    private val store           : BiliStore
) : ViewModel() {

    private val _ui = MutableStateFlow(BrowseWatchUi())
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
        playerManager.player.addListener(object : androidx.media3.common.Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (_ui.value.currentBvid.isNotEmpty()) {
                    _ui.update { it.copy(isPlaying = isPlaying) }
                }
            }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == androidx.media3.common.Player.STATE_ENDED &&
                    _ui.value.currentBvid.isNotEmpty()
                ) next()
            }
        })
    }

    fun initWithSeed(seed: FeedItem) {
        if (_ui.value.currentBvid == seed.bvid) return
        _ui.value = BrowseWatchUi(feedItems = listOf(seed), loading = true)
        playAt(0)
        viewModelScope.launch(Dispatchers.IO) {
            // 先看入口视频是否属于合集（ugc_season）：是则用合集集列表替代"相关视频"
            val view      = runCatching { videoRepo.view(seed.bvid).data }.getOrNull()
            val ugcSeason = view?.ugcSeason
            if (ugcSeason != null) {
                val episodes = ugcSeason.sections
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
                if (episodes.isNotEmpty()) {
                    val entryIdx = episodes.indexOfFirst { it.bvid == seed.bvid }.coerceAtLeast(0)
                    _ui.update { it.copy(feedItems = episodes, currentIndex = entryIdx, loading = false) }
                    return@launch
                }
            }
            // 非合集：照旧加载"相关视频"列表
            val related = runCatching { relatedRepo.related(seed.bvid) }.getOrElse { emptyList() }
            val relatedItems = related.map { r ->
                FeedItem(
                    id       = r.aid,
                    bvid     = r.bvid,
                    cid      = r.cid,
                    goto     = "av",
                    pic      = r.pic,
                    title    = r.title,
                    duration = r.duration,
                    owner    = FeedOwner(name = r.owner.name),
                    stat     = FeedStat(view = r.stat.view),
                    pubdate  = 0L
                )
            }
            _ui.update { it.copy(
                feedItems = it.feedItems + relatedItems,
                loading   = false
            ) }
        }
    }

    fun playAt(index: Int) {
        val items = _ui.value.feedItems
        if (index < 0 || index >= items.size) return
        val item = items[index]
        _ui.update { it.copy(currentIndex = index, currentBvid = item.bvid) }
        viewModelScope.launch(Dispatchers.IO) {
            val cid = if (item.cid != 0L) item.cid else {
                videoRepo.view(item.bvid).data?.cid ?: return@launch
            }
            // 原 resolvePlay+select+playDash 改用 playWithRecovery，
            // 以便在「连接被拒绝」时自动换 CDN 镜像或切 Android app API。
            val settings   = store.settings()
            val qnPref     = settings.defaultPlayQuality
            val videoCodec = settings.videoCodec
            withContext(Dispatchers.Main) {
                playerManager.onUnrecoverable = {
                    _ui.update { it.copy(toast = "播放源均不可用，请检查网络") }
                }
                playerManager.playWithRecovery(item.bvid, scope = viewModelScope) { tier ->
                    val source = if (tier == 0) PlaySource.WEB else PlaySource.APP
                    val playData = videoRepo.resolvePlay(item.bvid, cid, qn = qnPref, source = source)
                        ?: return@playWithRecovery null
                    val dash = playData.dash ?: return@playWithRecovery null
                    val sel  = videoRepo.select(dash, qnPref, videoCodec)
                        ?: return@playWithRecovery null
                    _ui.update { it.copy(
                        currentCid    = cid,
                        acceptQuality = playData.acceptQuality,
                        currentQn     = sel.video.id,
                        isPlaying     = true
                    ) }
                    PlayerManager.PlayCandidates(
                        videoUrls = listOf(sel.video.baseUrl) + sel.video.backupUrl,
                        audioUrls = listOf(sel.audio?.baseUrl) + (sel.audio?.backupUrl ?: emptyList())
                    )
                }
            }
            val dmList = runCatching { danmakuRepo.fetch(cid, item.duration) }.getOrElse { emptyList() }
            _danmaku.value = dmList
            // 获取 aid + 分P 列表 + 互动关系（照搬首页逻辑）
            val view = runCatching { videoRepo.view(item.bvid).data }.getOrNull()
            if (view != null) {
                _ui.update { it.copy(currentAid = view.aid, pages = view.pages) }
                val rel = runCatching { interactionRepo.relation(item.bvid) }.getOrElse { ArchiveRelation() }
                _ui.update { it.copy(relation = rel) }
            }
        }
    }

    fun next() {
        val idx = _ui.value.currentIndex + 1
        if (idx < _ui.value.feedItems.size) playAt(idx)
    }

    fun prev() {
        val idx = _ui.value.currentIndex - 1
        if (idx >= 0) playAt(idx)
    }

    fun switchQuality(qn: Int) {
        val bvid = _ui.value.currentBvid
        val cid  = _ui.value.currentCid
        if (bvid.isEmpty() || cid == 0L) return
        viewModelScope.launch(Dispatchers.IO) {
            val pos      = withContext(Dispatchers.Main) { playerManager.positionMs }
            val settings = store.settings()
            val playData = videoRepo.resolvePlay(bvid, cid, qn = qn) ?: return@launch
            val dash     = playData.dash ?: return@launch
            val sel      = videoRepo.select(dash, qn, settings.videoCodec) ?: return@launch
            _ui.update { it.copy(currentQn = sel.video.id, acceptQuality = playData.acceptQuality) }
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
            val settings = store.settings()
            val qn       = _ui.value.currentQn
            val playData = videoRepo.resolvePlay(bvid, cid, qn = qn) ?: return@launch
            val dash     = playData.dash ?: return@launch
            val sel      = videoRepo.select(dash, qn, settings.videoCodec) ?: return@launch
            _ui.update { it.copy(
                currentCid    = cid,
                currentQn     = sel.video.id,
                acceptQuality = playData.acceptQuality
            ) }
            withContext(Dispatchers.Main) {
                playerManager.playDash(sel.video.baseUrl, sel.audio?.baseUrl, bvid, startPositionMs = 0)
            }
            val item   = _ui.value.feedItems.getOrNull(_ui.value.currentIndex)
            val dmList = runCatching { danmakuRepo.fetch(cid, item?.duration ?: 0) }.getOrElse { emptyList() }
            _danmaku.value = dmList
        }
    }

    // ── 互动（照搬 TvHomeViewModel） ───────────────────────────────

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
    fun clearToast()           { _ui.update { it.copy(toast = null) } }

    private fun act(block: suspend () -> String) = viewModelScope.launch(Dispatchers.IO) {
        val msg = runCatching { block() }.getOrElse { it.message ?: "错误" }
        _ui.update { it.copy(toast = msg) }
    }

    override fun onCleared() { super.onCleared() }
}
