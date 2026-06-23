package com.biliclient.feature.video

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.biliclient.core.AbConvert
import com.biliclient.core.Diag
import com.biliclient.core.MediaSelect
import com.biliclient.data.store.BiliStore
import com.biliclient.net.ArchiveRelation
import com.biliclient.net.FavFolder
import com.biliclient.net.RelatedItem
import com.biliclient.net.Reply
import com.biliclient.net.ViewData
import com.biliclient.download.DownloadRepo
import com.biliclient.download.DownloadService
import com.biliclient.player.DanmakuItem
import com.biliclient.player.PlayerManager
import com.biliclient.repo.PlaySource
import com.biliclient.repo.DanmakuRepo
import com.biliclient.repo.InteractionRepo
import com.biliclient.repo.RelatedRepo
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

@UnstableApi
@HiltViewModel
class VideoViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    val playerManager: PlayerManager,
    private val videoRepo: VideoRepo,
    private val danmakuRepo: DanmakuRepo,
    private val relatedRepo: RelatedRepo,
    private val interactionRepo: InteractionRepo,
    private val userRepo: UserRepo,
    private val downloadRepo: DownloadRepo,
    private val store: BiliStore
) : ViewModel() {

    data class DanmakuDisplay(
        val scale   : Float = 1.0f,
        val alpha   : Float = 1.0f,
        val density : Float = 1.0f
    )

    data class FavFolderState(val id: Long, val title: String, val mediaCount: Int, val checked: Boolean)
    data class FavSheetUi(val loading: Boolean = true, val folders: List<FavFolderState> = emptyList())

    data class VideoUi(
        val loading: Boolean = true,
        val error: String? = null,
        val view: ViewData? = null,
        val playbackSpeed: Float = 1.0f,
        val danmakuOn: Boolean = true,
        val relation: ArchiveRelation = ArchiveRelation(),
        val related: List<RelatedItem> = emptyList(),
        val replies: List<Reply> = emptyList(),
        val toast: String? = null,
        val redirectToBangumi: String? = null,
        val downloadEnqueued: Boolean = false,
        val replyNext: Long = 0,
        val replyEnd: Boolean = false,
        val loadingMoreReplies: Boolean = false,
        val favSheet: FavSheetUi? = null,
        val acceptQuality: List<Int> = emptyList(),
        val currentQn: Int = 80,
        /** 当前播放的分P cid（用于分P选集高亮；纯单P时即 view.cid）。 */
        val currentCid: Long = 0L
    )

    private val _ui = MutableStateFlow(VideoUi())
    val ui = _ui.asStateFlow()
    private val _danmaku = MutableStateFlow<List<DanmakuItem>>(emptyList())
    val danmaku = _danmaku.asStateFlow()
    private val _danmakuDisplay = MutableStateFlow(DanmakuDisplay())
    val danmakuDisplay = _danmakuDisplay.asStateFlow()

    private fun resolveBvid(id: String): String = when {
        id.startsWith("BV") -> id
        id.startsWith("av") || id.startsWith("AV") -> AbConvert.avToBv(id)
        id.all { it.isDigit() } -> AbConvert.avToBv(id.toLong())
        else -> id
    }

    fun load(id: String) {
        _ui.value = VideoUi(loading = true)
        viewModelScope.launch {
            // 从 DataStore 读取弹幕显示默认值
            viewModelScope.launch(Dispatchers.IO) {
                val s = store.settings()
                _danmakuDisplay.value = DanmakuDisplay(scale = s.danmakuTextScale, alpha = s.danmakuAlpha)
            }
            try {
                val bvid = resolveBvid(id)
                val view = withContext(Dispatchers.IO) {
                    userRepo.ensureWbiKeys()
                    videoRepo.view(bvid)
                }
                val d = view.data
                if (view.code != 0 || d == null) {
                    _ui.value = VideoUi(loading = false, error = "加载失败: ${view.message}")
                    return@launch
                }
                // 番剧重定向检测（doc 09 §2）
                if (d.redirectUrl != null) {
                    val m = Regex("""bangumi/play/(ep\d+|ss\d+)""").find(d.redirectUrl)
                    if (m != null) {
                        _ui.value = VideoUi(loading = false, redirectToBangumi = m.groupValues[1])
                        return@launch
                    }
                }
                _ui.update { it.copy(loading = false, view = d, currentCid = d.cid) }

                // 原 resolvePlay+select+playDash 改用 playWithRecovery，
                // 以便在「连接被拒绝」时自动换 CDN 镜像或切 Android app API。
                withContext(Dispatchers.IO) {
                    val settings   = store.settings()
                    val qnPref     = settings.defaultPlayQuality
                    val videoCodec = settings.videoCodec
                    withContext(Dispatchers.Main) {
                        playerManager.onUnrecoverable = {
                            _ui.update { it.copy(toast = "播放源均不可用，请检查网络") }
                        }
                        playerManager.playWithRecovery(d.bvid, scope = viewModelScope) { tier ->
                            val source = if (tier == 0) PlaySource.WEB else PlaySource.APP
                            val playData = videoRepo.resolvePlay(d.bvid, d.cid, qn = qnPref, source = source)
                                ?: return@playWithRecovery null
                            val dash = playData.dash ?: return@playWithRecovery null
                            val sel  = videoRepo.select(dash, qualityPref = qnPref, codecPref = videoCodec)
                                ?: return@playWithRecovery null
                            _ui.update { it.copy(
                                acceptQuality = playData.acceptQuality,
                                currentQn     = sel.video.id
                            ) }
                            PlayerManager.PlayCandidates(
                                videoUrls = listOf(sel.video.baseUrl) + sel.video.backupUrl,
                                audioUrls = listOf(sel.audio?.baseUrl) + (sel.audio?.backupUrl ?: emptyList())
                            )
                        }
                    }
                }
                viewModelScope.launch(Dispatchers.IO) { _danmaku.value = danmakuRepo.fetch(d.cid, d.duration) }
                viewModelScope.launch(Dispatchers.IO) {
                    val rel = interactionRepo.relation(d.bvid)
                    _ui.update { it.copy(relation = rel) }
                }
                viewModelScope.launch(Dispatchers.IO) {
                    val rel = relatedRepo.related(d.bvid)
                    _ui.update { it.copy(related = rel) }
                }
                viewModelScope.launch(Dispatchers.IO) {
                    val page = interactionRepo.repliesPage(d.aid, next = 0)
                    Diag.d("load firstPage replies=${page.replies?.size} replyNext=${page.cursor.next} replyEnd=${page.cursor.isEnd}")
                    _ui.update { it.copy(
                        replies   = page.replies ?: emptyList(),
                        replyNext = page.cursor.next,
                        replyEnd  = page.cursor.isEnd
                    ) }
                }
            } catch (e: Exception) {
                _ui.value = VideoUi(loading = false, error = e.message ?: "异常")
            }
        }
    }

    fun switchQuality(qn: Int) {
        val bvid = _ui.value.view?.bvid ?: return
        val cid  = _ui.value.view?.cid  ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val pos      = withContext(Dispatchers.Main) { playerManager.positionMs }
            val settings = store.settings()
            val playData = videoRepo.resolvePlay(bvid, cid, qn = qn) ?: return@launch
            val dash     = playData.dash ?: return@launch
            val sel      = videoRepo.select(dash, qualityPref = qn, codecPref = settings.videoCodec)
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

    /**
     * 切换到当前 BV 的指定分P（cid），startPosition=0，复用 switchQuality 的 playDash 路径。
     * 同一单例 PlayerManager，天然保持防后台/防多播。
     */
    fun switchPage(cid: Long) {
        val bvid = _ui.value.view?.bvid ?: return
        if (bvid.isEmpty() || cid == 0L || cid == _ui.value.currentCid) return
        val duration = _ui.value.view?.pages?.firstOrNull { it.cid == cid }?.duration ?: 0
        viewModelScope.launch(Dispatchers.IO) {
            val settings = store.settings()
            val qn       = _ui.value.currentQn
            val playData = videoRepo.resolvePlay(bvid, cid, qn = qn) ?: return@launch
            val dash     = playData.dash ?: return@launch
            val sel      = videoRepo.select(dash, qualityPref = qn, codecPref = settings.videoCodec)
                           ?: return@launch
            _ui.update { it.copy(
                currentCid    = cid,
                currentQn     = sel.video.id,
                acceptQuality = playData.acceptQuality
            ) }
            withContext(Dispatchers.Main) {
                playerManager.playDash(sel.video.baseUrl, sel.audio?.baseUrl, bvid, startPositionMs = 0)
            }
            _danmaku.value = runCatching { danmakuRepo.fetch(cid, duration) }.getOrElse { emptyList() }
        }
    }

    fun setSpeed(speed: Float) {
        playerManager.setSpeed(speed)
        _ui.update { it.copy(playbackSpeed = speed) }
    }

    fun toggleDanmaku() { _ui.update { it.copy(danmakuOn = !it.danmakuOn) } }

    private fun aid() = _ui.value.view?.aid ?: 0
    private fun bvid() = _ui.value.view?.bvid ?: ""

    fun like() = act {
        val on = !_ui.value.relation.like
        val r = interactionRepo.like(aid(), on)
        if (r.ok) _ui.update { it.copy(relation = it.relation.copy(like = on)) }
        if (r.ok) (if (on) "已点赞" else "已取消") else "失败: ${r.message}"
    }

    fun coin() = act {
        val r = interactionRepo.coin(aid(), 1, alsoLike = true)
        if (r.ok) _ui.update { it.copy(relation = it.relation.copy(coin = it.relation.coin + 1)) }
        if (r.ok) "投币成功" else "投币失败: ${r.message}"
    }

    fun favorite() = act {
        val uid = userRepo.currentUid() ?: return@act "请先登录"
        val first = interactionRepo.favFolders(uid).firstOrNull() ?: return@act "无收藏夹"
        val on = !_ui.value.relation.favorite
        val r = if (on) interactionRepo.favorite(aid(), listOf(first.id), emptyList())
        else interactionRepo.favorite(aid(), emptyList(), listOf(first.id))
        if (r.ok) _ui.update { it.copy(relation = it.relation.copy(favorite = on)) }
        if (r.ok) (if (on) "已收藏" else "已取消收藏") else "失败: ${r.message}"
    }

    fun triple() = act {
        val r = interactionRepo.triple(aid())
        if (r.ok) _ui.update { it.copy(relation = ArchiveRelation(like = true, coin = 2, favorite = true)) }
        if (r.ok) "一键三连成功" else "失败: ${r.message}"
    }

    fun follow() = act {
        val mid = _ui.value.view?.owner?.mid ?: return@act "无UP"
        val r = interactionRepo.follow(mid, true)
        if (r.ok) "已关注" else "失败: ${r.message}"
    }

    fun sendReply(msg: String) = act {
        if (msg.isBlank()) return@act "内容为空"
        val r = interactionRepo.addReply(aid(), msg)
        if (r.ok) {
            val page = interactionRepo.repliesPage(aid(), next = 0)
            _ui.update { it.copy(
                replies   = page.replies ?: emptyList(),
                replyNext = page.cursor.next,
                replyEnd  = page.cursor.isEnd
            ) }
        }
        if (r.ok) "评论成功" else "失败: ${r.message}"
    }

    fun loadMoreReplies() {
        val cur = _ui.value
        if (cur.loadingMoreReplies || cur.replyEnd || cur.view == null) return
        _ui.update { it.copy(loadingMoreReplies = true) }
        viewModelScope.launch(Dispatchers.IO) {
            val page       = interactionRepo.repliesPage(cur.view.aid, next = cur.replyNext)
            val newReplies = page.replies ?: emptyList()
            // 防回归兜底：若本页为空或游标未推进（接口异常/末页），直接标记到底
            val noProgress = newReplies.isEmpty() || page.cursor.next == cur.replyNext
            Diag.d("loadMoreReplies replyNext-in=${cur.replyNext} newReplies=${newReplies.size} " +
                   "cursor.next-out=${page.cursor.next} noProgress=$noProgress replyEnd=${page.cursor.isEnd || noProgress}")
            _ui.update { it.copy(
                replies            = it.replies + newReplies,
                replyNext          = page.cursor.next,
                replyEnd           = page.cursor.isEnd || noProgress,
                loadingMoreReplies = false
            ) }
        }
    }

    fun openFavSheet() {
        val aid = aid()
        if (aid == 0L) return
        _ui.update { it.copy(favSheet = FavSheetUi(loading = true)) }
        viewModelScope.launch(Dispatchers.IO) {
            val uid = userRepo.currentUid() ?: run {
                _ui.update { it.copy(toast = "请先登录", favSheet = null) }
                return@launch
            }
            val folders = interactionRepo.favFoldersWithState(uid, aid)
            val states = folders.map { f ->
                FavFolderState(
                    id         = f.id,
                    title      = f.title,
                    mediaCount = f.mediaCount,
                    checked    = f.favState == 1
                )
            }
            _ui.update { it.copy(favSheet = FavSheetUi(loading = false, folders = states)) }
        }
    }

    fun toggleFavFolder(folderId: Long, checked: Boolean) {
        val sheet = _ui.value.favSheet ?: return
        // 乐观更新
        val newFolders = sheet.folders.map { if (it.id == folderId) it.copy(checked = checked) else it }
        _ui.update { it.copy(favSheet = sheet.copy(folders = newFolders)) }
        viewModelScope.launch(Dispatchers.IO) {
            val add = if (checked) listOf(folderId) else emptyList()
            val del = if (checked) emptyList() else listOf(folderId)
            val r = interactionRepo.favorite(aid(), add, del)
            if (!r.ok) {
                // 回滚
                _ui.update { it.copy(
                    favSheet = it.favSheet?.copy(folders = sheet.folders),
                    toast    = "操作失败: ${r.message}"
                ) }
            } else {
                // 同步 relation.favorite
                _ui.update { s ->
                    val anyChecked = s.favSheet?.folders?.any { f -> f.checked } == true
                    s.copy(relation = s.relation.copy(favorite = anyChecked))
                }
            }
        }
    }

    fun closeFavSheet() { _ui.update { it.copy(favSheet = null) } }

    fun setDanmakuTextScale(v: Float) {
        _danmakuDisplay.update { it.copy(scale = v) }
    }

    fun setDanmakuAlpha(v: Float) {
        _danmakuDisplay.update { it.copy(alpha = v) }
    }

    fun setDanmakuDensity(v: Float) {
        _danmakuDisplay.update { it.copy(density = v) }
    }

    /**
     * 下载当前视频（doc 08）：入队 Room + 启动前台 Service。
     * 按 BV 为单位（计划下载模型）：若为分P视频（pages.size>1）则下整个 BV 的全部 P；否则下单 P。
     * 合集不在此批量（合集每集是独立 BV，需各自进入后下载）。
     */
    fun download() = act {
        val v        = _ui.value.view ?: return@act "未加载"
        val settings = store.settings()
        val pages    = v.pages
        if (pages.size > 1) {
            // 分P：逐 P 入队，标题带分P名以便区分
            pages.forEach { p ->
                val partTitle = if (p.part.isNotBlank()) "${v.title} - P${p.page} ${p.part}"
                                else "${v.title} - P${p.page}"
                val id = downloadRepo.enqueue(
                    bvid      = v.bvid,
                    aid       = v.aid,
                    cid       = p.cid,
                    title     = partTitle,
                    cover     = v.pic,
                    qualityId = settings.defaultDownloadQuality,
                    codecId   = settings.videoCodec
                )
                DownloadService.enqueue(appContext, id)
            }
            _ui.update { it.copy(downloadEnqueued = true) }
            "已加入下载（${pages.size}P）"
        } else {
            val id = downloadRepo.enqueue(
                bvid      = v.bvid,
                aid       = v.aid,
                cid       = v.cid,
                title     = v.title,
                cover     = v.pic,
                qualityId = settings.defaultDownloadQuality,
                codecId   = settings.videoCodec
            )
            DownloadService.enqueue(appContext, id)
            _ui.update { it.copy(downloadEnqueued = true) }
            "已加入下载"
        }
    }

    fun postDanmaku(text: String) = act {
        if (text.isBlank()) return@act "弹幕为空"
        val cid = _ui.value.view?.cid ?: return@act "无效"
        val posMs = withContext(Dispatchers.Main) { playerManager.positionMs }.toInt()
        val r = interactionRepo.postDanmaku(cid, bvid(), text, posMs)
        if (r.ok) _danmaku.update { it + DanmakuItem(posMs, 1, text, 16777215, 25) }
        if (r.ok) "已发送" else "失败: ${r.message}"
    }

    private fun act(block: suspend () -> String) {
        viewModelScope.launch(Dispatchers.IO) {
            val msg = runCatching { block() }.getOrElse { it.message ?: "异常" }
            _ui.update { it.copy(toast = msg) }
        }
    }

    fun clearToast() { _ui.update { it.copy(toast = null) } }

    override fun onCleared() { super.onCleared() }
}
