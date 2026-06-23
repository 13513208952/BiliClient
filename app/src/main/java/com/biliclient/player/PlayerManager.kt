package com.biliclient.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

/**
 * Media3/ExoPlayer 封装（doc 05）。
 * B站 DASH 音视频分离：分别用 ProgressiveMediaSource 加载后 MergingMediaSource 合并。
 * 用 OkHttpDataSource 注入 Referer/UA + 复用带 cookie 的 client；MediaCodec 自动硬解（芯片中立）。
 *
 * 恢复机制（新增）：
 *   [playWithRecovery] 路径在遇到「连接被拒绝」(ECONNREFUSED) 时自动重试——
 *   先耗尽 backupUrl 镜像，再重取同层 CDN，再切下一 API 层（WEB→APP），全部失败才通知 [onUnrecoverable]。
 *   仅此一种错误触发；其它错误维持原行为（不干预）。
 *   [playDash] 路径（switchQuality 等主动操作）不受影响。
 */
@UnstableApi
class PlayerManager(
    context: Context,
    private val client: OkHttpClient,
    private val userAgent: String
) {
    val player: ExoPlayer = ExoPlayer.Builder(context).build()

    // ── 候选 URL 组（一次 resolve 返回一组候选，含 baseUrl + backupUrl 镜像） ──
    data class PlayCandidates(val videoUrls: List<String>, val audioUrls: List<String?>)

    /** 全部 API 层均失败时的回调，在主线程调用，供 ViewModel 弹 toast。 */
    var onUnrecoverable: (() -> Unit)? = null

    // ── 内部恢复状态（仅 playWithRecovery 路径使用，playDash 路径无此状态）──
    private var recoveryScope          : CoroutineScope? = null
    private var recoveryResolve        : (suspend (Int) -> PlayCandidates?)? = null
    private var recoveryCandidates     : PlayCandidates? = null
    private var recoveryCandidatesTier = 0      // 当前 recoveryCandidates 来自哪层（0=WEB,1=APP）
    private var recoveryUrlIdx         = 0      // 当前候选列表下标（0=baseUrl,1+=backupUrl 镜像）
    private var appAttempts            = 0      // APP 层已使用次数（首播不计，最多 recoveryMax）
    private var webAttempts            = 0      // WEB 层恢复已使用次数（首播不计，最多 recoveryMax）
    private var nextIsApp              = true   // 下一次 resolve 优先用 APP（首次从 APP 起步）
    private val recoveryMax            = 5      // 每个源各最多 5 次，共最多 10 次
    private var recoveryBvid           = ""
    private var recoveryJob            : Job? = null
    @Volatile private var recovering   = false  // 防止 onPlayerError 重入

    init {
        // 仅对「连接被拒绝」错误触发恢复；其它错误不处理，维持现状
        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                if (!recovering && recoveryScope != null && isConnectionRefused(error)) {
                    recovering = true
                    val lastPos = player.currentPosition.coerceAtLeast(0L)
                    doRecover(lastPos)
                }
            }

            override fun onPlaybackStateChanged(state: Int) {
                // 进入 READY（有画面）时重置恢复标志，允许后续错误重新触发恢复
                if (state == Player.STATE_READY) recovering = false
            }
        })
    }

    // ── 原有公开方法（完整保留，不删一行） ─────────────────────────────────

    /**
     * 直接播放（[switchQuality] 等主动操作用）。
     * 会清除恢复状态，不做自动重试——主动切换出错由用户感知并重试。
     * @param startPositionMs 起播位置（毫秒），默认 0。
     */
    fun playDash(videoUrl: String, audioUrl: String?, bvid: String, startPositionMs: Long = 0L) {
        clearRecovery()
        buildAndPlay(videoUrl, audioUrl, bvid, startPositionMs)
    }

    /** 设置播放速度（0.5 / 0.75 / 1.0 / 1.25 / 1.5 / 2.0）。*/
    fun setSpeed(speed: Float) { player.setPlaybackSpeed(speed) }

    fun pause() { player.playWhenReady = false }
    fun play()  { player.playWhenReady = true }
    val durationMs: Long get() = player.duration.coerceAtLeast(0L)

    fun playPause() { player.playWhenReady = !player.playWhenReady }
    fun seekTo(ms: Long) = player.seekTo(ms)
    val positionMs: Long get() = player.currentPosition
    fun release() = player.release()

    // ── 新增：带连接被拒绝自动恢复的播放入口（自动播放路径用） ──────────────

    /**
     * 启动播放，并在「连接被拒绝」时自动恢复。
     *
     * 恢复优先级：
     *   ① 当前候选列表还有下一个镜像 URL（backupUrl）→ 直接切换；
     *   ② 镜像耗尽但本层重试次数未满 → 重新调用 [resolve] 取新 CDN 节点；
     *   ③ 本层全部耗尽 → tier++ 切换 API 层，重新 [resolve]；
     *   ④ 所有层均不可用 → 调 [onUnrecoverable]（弹 toast，不卡死）。
     *
     * @param resolve 按 tier（0=WEB, 1=APP）返回候选 URL 组；返回 null 表示该层不可用。
     *   在 IO dispatcher 上调用，可直接执行网络请求。
     */
    fun playWithRecovery(
        bvid: String,
        startPositionMs: Long = 0L,
        scope: CoroutineScope,
        resolve: suspend (tier: Int) -> PlayCandidates?
    ) {
        recoveryJob?.cancel()
        recoveryScope              = scope
        recoveryResolve            = resolve
        recoveryCandidates         = null
        recoveryCandidatesTier     = 0
        recoveryUrlIdx             = 0
        appAttempts                = 0
        webAttempts                = 0
        nextIsApp                  = true
        recoveryBvid               = bvid
        recovering                 = false

        recoveryJob = scope.launch(Dispatchers.IO) {
            val candidates = resolve(0)   // 首播固定 WEB（tier=0），不计入 webAttempts
            if (candidates == null || candidates.videoUrls.isEmpty()) {
                withContext(Dispatchers.Main) { onUnrecoverable?.invoke() }
                return@launch
            }
            recoveryCandidates     = candidates
            recoveryCandidatesTier = 0
            recoveryUrlIdx         = 0
            val videoUrl = candidates.videoUrls[0]
            val audioUrl = candidates.audioUrls.getOrNull(0)
            withContext(Dispatchers.Main) {
                buildAndPlay(videoUrl, audioUrl, bvid, startPositionMs)
            }
        }
    }

    // ── 私有辅助 ────────────────────────────────────────────────────────

    /**
     * 构建并启动 ExoPlayer（提取自原 playDash，逻辑完全一致）。
     * 必须在主线程（application looper）调用。
     */
    private fun buildAndPlay(videoUrl: String, audioUrl: String?, bvid: String, startPositionMs: Long) {
        val df = OkHttpDataSource.Factory(client).setDefaultRequestProperties(
            mapOf(
                "Referer"    to "https://www.bilibili.com/video/$bvid",
                "User-Agent" to userAgent
            )
        )
        val videoSrc = ProgressiveMediaSource.Factory(df)
            .createMediaSource(MediaItem.fromUri(videoUrl))
        val source = if (!audioUrl.isNullOrEmpty()) {
            val audioSrc = ProgressiveMediaSource.Factory(df)
                .createMediaSource(MediaItem.fromUri(audioUrl))
            MergingMediaSource(videoSrc, audioSrc)
        } else {
            videoSrc
        }
        player.setMediaSource(source, startPositionMs)
        player.prepare()
        player.playWhenReady = true
    }

    /** 清除全部恢复状态（[playDash] 直接调用时重置，确保不误触恢复逻辑）。 */
    private fun clearRecovery() {
        recoveryJob?.cancel()
        recoveryJob                = null
        recoveryScope              = null
        recoveryResolve            = null
        recoveryCandidates         = null
        recoveryCandidatesTier     = 0
        recoveryUrlIdx             = 0
        appAttempts                = 0
        webAttempts                = 0
        nextIsApp                  = true
        recoveryBvid               = ""
        recovering                 = false
    }

    /**
     * 「连接被拒绝」恢复，在新协程中执行（含 300ms 退避避免狂刷）。
     * [lastPos] 为出错时的播放位置，用于新源从断点续播。
     *
     * 恢复顺序：
     *   ① 优先穷举当前候选列表的 backupUrl 镜像（与原设计一致）；
     *   ② 镜像耗尽后交替 resolve 新源：APP→WEB→APP→WEB…，各累计最多 [recoveryMax] 次；
     *   ③ 两源均耗尽 → [onUnrecoverable]。
     * 镜像试用也计入来源层的次数。
     */
    private fun doRecover(lastPos: Long) {
        val scope   = recoveryScope   ?: return
        val resolve = recoveryResolve ?: return
        val bvid    = recoveryBvid

        recoveryJob?.cancel()
        recoveryJob = scope.launch(Dispatchers.IO) {
            delay(300L)

            // ① 当前候选列表还有下一个 backupUrl 镜像
            val candidates = recoveryCandidates
            if (candidates != null) {
                val nextIdx = recoveryUrlIdx + 1
                if (nextIdx < candidates.videoUrls.size) {
                    recoveryUrlIdx = nextIdx
                    // 镜像消耗计入该层次数
                    if (recoveryCandidatesTier == 1) appAttempts++ else webAttempts++
                    val videoUrl = candidates.videoUrls[nextIdx]
                    val audioUrl = candidates.audioUrls.getOrElse(nextIdx) { candidates.audioUrls.getOrNull(0) }
                    withContext(Dispatchers.Main) { buildAndPlay(videoUrl, audioUrl, bvid, lastPos) }
                    return@launch
                }
            }

            // ② 镜像耗尽，交替 resolve 新源（APP→WEB→APP→WEB…）
            while (true) {
                if (appAttempts >= recoveryMax && webAttempts >= recoveryMax) {
                    withContext(Dispatchers.Main) { onUnrecoverable?.invoke() }
                    return@launch
                }

                // 优先 nextIsApp 指向的源；若已满则用另一侧
                val useApp = when {
                    nextIsApp  && appAttempts < recoveryMax -> true
                    !nextIsApp && webAttempts < recoveryMax -> false
                    appAttempts < recoveryMax               -> true
                    else                                    -> false
                }
                nextIsApp = !useApp
                val tier = if (useApp) 1 else 0
                if (useApp) appAttempts++ else webAttempts++

                val newCandidates = resolve(tier)
                if (newCandidates != null && newCandidates.videoUrls.isNotEmpty()) {
                    recoveryCandidates     = newCandidates
                    recoveryCandidatesTier = tier
                    recoveryUrlIdx         = 0
                    val videoUrl = newCandidates.videoUrls[0]
                    val audioUrl = newCandidates.audioUrls.getOrNull(0)
                    withContext(Dispatchers.Main) { buildAndPlay(videoUrl, audioUrl, bvid, lastPos) }
                    return@launch
                }
                // 该源暂不可用，300ms 后继续
                delay(300L)
            }
        }
    }

    /**
     * 递归遍历异常 cause 链，判断是否为「连接被拒绝」错误。
     * 仅此触发恢复；其它网络错误（超时、403、解码失败等）不干预，留待后续逐个加。
     */
    private fun isConnectionRefused(e: PlaybackException): Boolean {
        var c: Throwable? = e
        while (c != null) {
            if (c is java.net.ConnectException &&
                (c.message?.contains("ECONNREFUSED", ignoreCase = true) == true ||
                 c.message?.contains("refused",      ignoreCase = true) == true)
            ) return true
            c = c.cause
        }
        return false
    }
}
