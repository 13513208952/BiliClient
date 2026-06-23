package com.biliclient.feature.bangumi

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.biliclient.core.MediaSelect
import com.biliclient.data.store.BiliStore
import com.biliclient.download.DownloadRepo
import com.biliclient.download.DownloadService
import com.biliclient.net.ArchiveRelation
import com.biliclient.net.BangumiEp
import com.biliclient.net.SeasonData
import com.biliclient.net.PersistentCookieJar
import com.biliclient.player.DanmakuItem
import com.biliclient.player.PlayerManager
import com.biliclient.repo.BangumiRepo
import com.biliclient.repo.DanmakuRepo
import com.biliclient.repo.InteractionRepo
import com.biliclient.repo.UserRepo
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject



@UnstableApi
@HiltViewModel
class BangumiViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    val playerManager: PlayerManager,
    private val bangumiRepo: BangumiRepo,
    private val danmakuRepo: DanmakuRepo,
    private val userRepo: UserRepo,
    private val downloadRepo: DownloadRepo,
    private val jar: PersistentCookieJar,
    private val interactionRepo: InteractionRepo,
    private val store: BiliStore
) : ViewModel() {

    data class BangumiUi(
        val loading: Boolean = true,
        val error: String? = null,
        val season: SeasonData? = null,
        val currentEpId: Long = 0,
        val currentEp: BangumiEp? = null,
        val epLoading: Boolean = false,
        val followed: Boolean = false,
        val followLoading: Boolean = false,
        val isPlaying: Boolean = true,
        val danmakuOn: Boolean = false,   // TV 模式弹幕默认关
        val toast: String? = null,
        val relation: ArchiveRelation = ArchiveRelation(),
        val acceptQuality: List<Int> = emptyList(),
        val currentQn: Int = 80,
        val playbackSpeed: Float = 1.0f
    )

    data class DanmakuDisplay(
        val scale  : Float = 1.0f,
        val alpha  : Float = 1.0f,
        val density: Float = 1.0f
    )

    private val _ui = MutableStateFlow(BangumiUi())
    val ui = _ui.asStateFlow()

    private val _danmaku = MutableStateFlow<List<DanmakuItem>>(emptyList())
    val danmaku = _danmaku.asStateFlow()

    private val _danmakuDisplay = MutableStateFlow(DanmakuDisplay())
    val danmakuDisplay = _danmakuDisplay.asStateFlow()

    // 播放器状态监听：同步 isPlaying + STATE_ENDED 自动续集
    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _ui.value = _ui.value.copy(isPlaying = isPlaying)
        }
        override fun onPlaybackStateChanged(state: Int) {
            _ui.value = _ui.value.copy(isPlaying = playerManager.player.playWhenReady)
            if (state == Player.STATE_ENDED) nextEp()
        }
    }

    init {
        playerManager.player.addListener(playerListener)
    }

    override fun onCleared() {
        super.onCleared()
        playerManager.player.removeListener(playerListener)
    }

    fun load(id: String) {
        _ui.value = BangumiUi(loading = true)
        _danmaku.value = emptyList()
        viewModelScope.launch(Dispatchers.IO) {
            val s = store.settings()
            _danmakuDisplay.value = DanmakuDisplay(scale = s.danmakuTextScale, alpha = s.danmakuAlpha)
        }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { userRepo.ensureWbiKeys() }
                val season = withContext(Dispatchers.IO) { bangumiRepo.season(id) }
                if (season == null) {
                    _ui.value = BangumiUi(loading = false, error = "番剧加载失败")
                    return@launch
                }
                // 优先从续播点恢复，否则用 ep 入口 id 或第一集
                val resumeEpId = season.userStatus.progress?.lastEpId?.takeIf { it > 0 }
                val entryEpId = if (id.startsWith("ep"))
                    id.removePrefix("ep").toLongOrNull()
                else null
                val epId = resumeEpId
                    ?: entryEpId
                    ?: season.episodes.firstOrNull()?.id
                    ?: 0L
                val followed = season.userStatus.follow == 1
                _ui.value = BangumiUi(
                    loading = false, season = season,
                    currentEpId = epId, followed = followed
                )
                season.episodes.firstOrNull { it.id == epId }?.let { playEp(it) }
            } catch (e: Exception) {
                _ui.value = BangumiUi(loading = false, error = e.message ?: "异常")
            }
        }
    }

    fun selectEp(ep: BangumiEp) {
        _ui.value = _ui.value.copy(currentEpId = ep.id)
        playEp(ep)
    }

    /** 切换到上一集（TV 遥控器上键）。 */
    fun prevEp() {
        val season = _ui.value.season ?: return
        val idx = season.episodes.indexOfFirst { it.id == _ui.value.currentEpId }
        val prev = season.episodes.getOrNull(idx - 1) ?: return
        selectEp(prev)
    }

    /** 切换到下一集（TV 遥控器下键 / STATE_ENDED 自动续集）。 */
    fun nextEp() {
        val season = _ui.value.season ?: return
        val idx = season.episodes.indexOfFirst { it.id == _ui.value.currentEpId }
        val next = season.episodes.getOrNull(idx + 1) ?: return
        selectEp(next)
    }

    private fun playEp(ep: BangumiEp) {
        _ui.value = _ui.value.copy(epLoading = true)
        viewModelScope.launch(Dispatchers.IO) {
            val settings   = store.settings()
            val qnPref     = settings.defaultPlayQuality
            val videoCodec = settings.videoCodec
            val play = bangumiRepo.pgcPlayUrl(ep.bvid, ep.cid, qn = qnPref)
            val sel  = play?.dash?.let { MediaSelect.select(it, qualityPref = qnPref, codecPref = videoCodec) }
            withContext(Dispatchers.Main) {
                _ui.value = _ui.value.copy(epLoading = false)
                if (sel != null && play != null) {
                    _ui.value = _ui.value.copy(
                        currentEp     = ep,
                        acceptQuality = play.acceptQuality,
                        currentQn     = sel.video.id,
                        relation      = ArchiveRelation()
                    )
                    playerManager.playDash(sel.video.baseUrl, sel.audio?.baseUrl, ep.bvid)
                } else {
                    _ui.value = _ui.value.copy(
                        toast = "该视频需要登录拥有 B站大会员的账号以获取播放权限"
                    )
                }
            }
            // 拉取本集弹幕（duration 单位 ms → 秒）
            val durationSec = (ep.duration / 1000L).toInt()
            val dmList = runCatching { danmakuRepo.fetch(ep.cid, durationSec) }.getOrElse { emptyList() }
            _danmaku.value = dmList
            // 拉取互动关系
            if (ep.bvid.isNotEmpty()) {
                val rel = runCatching { interactionRepo.relation(ep.bvid) }.getOrElse { ArchiveRelation() }
                _ui.value = _ui.value.copy(relation = rel)
            }
        }
    }

    /** 切换当前集画质（保留播放进度）。 */
    fun switchEpQuality(qn: Int) {
        val ep = _ui.value.currentEp ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val pos      = withContext(Dispatchers.Main) { playerManager.positionMs }
            val settings = store.settings()
            val play     = bangumiRepo.pgcPlayUrl(ep.bvid, ep.cid, qn = qn) ?: return@launch
            val dash     = play.dash ?: return@launch
            val sel      = MediaSelect.select(dash, qualityPref = qn, codecPref = settings.videoCodec)
                           ?: return@launch
            _ui.value = _ui.value.copy(
                acceptQuality = play.acceptQuality,
                currentQn     = sel.video.id
            )
            withContext(Dispatchers.Main) {
                playerManager.playDash(sel.video.baseUrl, sel.audio?.baseUrl, ep.bvid, startPositionMs = pos)
            }
        }
    }

    fun setSpeed(speed: Float) {
        playerManager.setSpeed(speed)
        _ui.value = _ui.value.copy(playbackSpeed = speed)
    }

    fun like() = act {
        val on = !_ui.value.relation.like
        val r = interactionRepo.like(currentAid(), on)
        if (r.ok) _ui.value = _ui.value.copy(relation = _ui.value.relation.copy(like = on))
        if (r.ok) (if (on) "已点赞" else "已取消") else "失败: ${r.message}"
    }

    fun coin() = act {
        val r = interactionRepo.coin(currentAid(), 1, alsoLike = true)
        if (r.ok) _ui.value = _ui.value.copy(relation = _ui.value.relation.copy(coin = _ui.value.relation.coin + 1))
        if (r.ok) "投币成功" else "投币失败: ${r.message}"
    }

    fun favorite() = act {
        val uid = userRepo.currentUid() ?: return@act "请先登录"
        val first = interactionRepo.favFolders(uid).firstOrNull() ?: return@act "无收藏夹"
        val on = !_ui.value.relation.favorite
        val r = if (on) interactionRepo.favorite(currentAid(), listOf(first.id), emptyList())
                else interactionRepo.favorite(currentAid(), emptyList(), listOf(first.id))
        if (r.ok) _ui.value = _ui.value.copy(relation = _ui.value.relation.copy(favorite = on))
        if (r.ok) (if (on) "已收藏" else "已取消收藏") else "失败: ${r.message}"
    }

    fun triple() = act {
        val r = interactionRepo.triple(currentAid())
        if (r.ok) _ui.value = _ui.value.copy(relation = ArchiveRelation(like = true, coin = 2, favorite = true))
        if (r.ok) "一键三连成功" else "失败: ${r.message}"
    }

    private fun currentAid(): Long = _ui.value.currentEp?.aid ?: 0L

    /** 追番 / 取消追番。需登录（bili_jct 不为空）才能操作。 */
    fun toggleFollow() {
        val season = _ui.value.season ?: return
        if (jar.get("bili_jct").isNullOrEmpty()) {
            _ui.value = _ui.value.copy(toast = "请先登录")
            return
        }
        val isFollowed = _ui.value.followed
        _ui.value = _ui.value.copy(followLoading = true)
        viewModelScope.launch(Dispatchers.IO) {
            val ok = if (isFollowed) {
                bangumiRepo.unfollow(season.seasonId)
            } else {
                bangumiRepo.follow(season.seasonId)
            }
            withContext(Dispatchers.Main) {
                if (ok) {
                    _ui.value = _ui.value.copy(
                        followed = !isFollowed,
                        followLoading = false,
                        toast = if (!isFollowed) "已追番" else "已取消追番"
                    )
                } else {
                    _ui.value = _ui.value.copy(
                        followLoading = false,
                        toast = "操作失败，请稍后重试"
                    )
                }
            }
        }
    }

    fun downloadEp(ep: BangumiEp) = act {
        val season = _ui.value.season ?: return@act "未加载"
        val id = downloadRepo.enqueue(
            bvid = ep.bvid, aid = ep.aid, cid = ep.cid,
            title = buildEpTitle(ep), cover = ep.cover,
            qualityId = 0, codecId = 7,
            epId = ep.id, seasonTitle = season.seasonTitle
        )
        DownloadService.enqueue(appContext, id)
        "已加入下载"
    }

    /** 整季逐集入队（doc 09 §5）。串行入队，由 DownloadService 并发调度。 */
    fun downloadSeason() = act {
        val season = _ui.value.season ?: return@act "未加载"
        if (season.episodes.isEmpty()) return@act "无可下载集数"
        season.episodes.forEach { ep ->
            val id = downloadRepo.enqueue(
                bvid = ep.bvid, aid = ep.aid, cid = ep.cid,
                title = buildEpTitle(ep), cover = ep.cover,
                qualityId = 0, codecId = 7,
                epId = ep.id, seasonTitle = season.seasonTitle
            )
            DownloadService.enqueue(appContext, id)
        }
        "已添加 ${season.episodes.size} 集到下载队列"
    }

    fun switchSeason(seasonId: Long) {
        _ui.value = BangumiUi(loading = true)
        _danmaku.value = emptyList()
        viewModelScope.launch(Dispatchers.IO) {
            val s = store.settings()
            _danmakuDisplay.value = DanmakuDisplay(scale = s.danmakuTextScale, alpha = s.danmakuAlpha)
        }
        viewModelScope.launch {
            try {
                val season = withContext(Dispatchers.IO) { bangumiRepo.season("ss$seasonId") }
                if (season == null) {
                    _ui.value = BangumiUi(loading = false, error = "季度加载失败")
                    return@launch
                }
                val epId = season.episodes.firstOrNull()?.id ?: 0
                val followed = season.userStatus.follow == 1
                _ui.value = BangumiUi(
                    loading = false, season = season,
                    currentEpId = epId, followed = followed
                )
                season.episodes.firstOrNull()?.let { playEp(it) }
            } catch (e: Exception) {
                _ui.value = BangumiUi(loading = false, error = e.message ?: "异常")
            }
        }
    }

    fun setDanmakuTextScale(v: Float) { _danmakuDisplay.value = _danmakuDisplay.value.copy(scale = v) }
    fun setDanmakuAlpha(v: Float)     { _danmakuDisplay.value = _danmakuDisplay.value.copy(alpha = v) }
    fun setDanmakuDensity(v: Float)   { _danmakuDisplay.value = _danmakuDisplay.value.copy(density = v) }

    fun toggleDanmaku() { _ui.value = _ui.value.copy(danmakuOn = !_ui.value.danmakuOn) }

    fun clearToast() { _ui.value = _ui.value.copy(toast = null) }

    private fun buildEpTitle(ep: BangumiEp): String {
        val part = ep.longTitle.takeIf { it.isNotBlank() }
        return if (part != null) "第${ep.title}话 $part" else "第${ep.title}话"
    }

    private fun act(block: suspend () -> String) {
        viewModelScope.launch(Dispatchers.IO) {
            val msg = runCatching { block() }.getOrElse { it.message ?: "异常" }
            _ui.value = _ui.value.copy(toast = msg)
        }
    }
}
