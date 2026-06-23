package com.biliclient.repo

import com.biliclient.core.AbConvert
import com.biliclient.core.AppSigner
import com.biliclient.core.MediaSelect
import com.biliclient.net.BiliApi
import com.biliclient.net.BiliResp
import com.biliclient.net.Dash
import com.biliclient.net.PlayUrlData
import com.biliclient.net.ViewData
import javax.inject.Inject
import javax.inject.Singleton

/**
 * playurl API 来源层。
 * WEB = 原 web/PGC 路线（主路线，不变）；
 * APP = Android app 端（备用，仅在 WEB「连接被拒绝」多次失败后由 PlayerManager 恢复机制调用）。
 * 设计修订：对 docs/plan/00 硬约束 §4.2 的有意放宽，APP 层仅作备用。
 */
enum class PlaySource { WEB, APP }

/** 视频详情 + 播放地址（doc 05）。 */
@Singleton
class VideoRepo @Inject constructor(private val api: BiliApi) {

    suspend fun view(bvid: String): BiliResp<ViewData> = api.view(bvid)

    /**
     * 取播放地址（doc 05）。返回含 dash + is_drm 的 PlayUrlData。
     *
     * [source] 默认 [PlaySource.WEB]（现有全部调用点不受影响）：
     *   - WEB：原 web→PGC 自动降级逻辑，完整保留。
     *   - APP：Android app 端（test1 GetDownUrl_Mobile），
     *           用 AppSigner 签名，网页 Cookie 鉴权（无 access_key），
     *           返回结构与 WEB 相同，可直接复用 MediaSelect。
     */
    suspend fun resolvePlay(
        bvid: String,
        cid: Long,
        qn: Int = 80,
        epId: Long? = null,
        source: PlaySource = PlaySource.WEB
    ): PlayUrlData? {
        return when (source) {
            PlaySource.WEB -> {
                // ── 原逻辑，一行不删 ──────────────────────────────────────────
                if (epId == null) {
                    runCatching { api.playUrlWeb(bvid, cid, qn) }.getOrNull()
                        ?.let { if (it.code == 0 && it.data?.dash != null) return it.data }
                }
                runCatching { api.playUrlPgc(bvid, cid, qn) }.getOrNull()
                    ?.let { if (it.code == 0 && it.result?.dash != null) return it.result }
                null
            }
            PlaySource.APP -> {
                // ── Android app 端备用（test1 GetDownUrl_Mobile）────────────
                // fourk=1 与 WEB 层对齐（test1 原值 0），保留 4K 能力。
                val aid = runCatching { AbConvert.bvToAv(bvid).toString() }.getOrElse { "0" }
                val params = mapOf(
                    "actionkey"  to "appkey",
                    "aid"        to aid,
                    "build"      to "5423000",
                    "cid"        to cid.toString(),
                    "device"     to "android",
                    "expire"     to "0",
                    "fnval"      to "4048",
                    "fnver"      to "0",
                    "force_host" to "0",
                    "fourk"      to "1",
                    "mid"        to "0",
                    "mobi_app"   to "android",
                    "npcybs"     to "0",
                    "otype"      to "json",
                    "platform"   to "android",
                    "qn"         to qn.toString()
                )
                val url = "https://app.bilibili.com/x/playurl?${AppSigner.sign(params)}"
                val resp = runCatching { api.playUrlApp(url) }.getOrNull()
                resp?.let { if (it.code == 0 && it.data?.dash != null) it.data else null }
            }
        }
    }

    /** WEB→PGC 自动降级取 DASH（doc 05）。 */
    suspend fun resolveDash(bvid: String, cid: Long, qn: Int = 80, epId: Long? = null): Dash? =
        resolvePlay(bvid, cid, qn, epId)?.dash

    fun select(dash: Dash, qualityPref: Int = MediaSelect.QUALITY_AUTO, codecPref: Int = 7) =
        MediaSelect.select(dash, qualityPref, codecPref)
}
