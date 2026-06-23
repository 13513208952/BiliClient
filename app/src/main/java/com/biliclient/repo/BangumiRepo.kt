package com.biliclient.repo

import com.biliclient.net.BiliApi
import com.biliclient.net.PersistentCookieJar
import com.biliclient.net.PlayUrlData
import com.biliclient.net.SeasonData
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 番剧整季解析 + PGC playurl（doc 09）。
 * season(id) 处理 ep/ss/md 三种入口。
 * pgcPlayUrl 直接调 PGC 接口，不走 wbi（doc 09 §4）。
 */
@Singleton
class BangumiRepo @Inject constructor(
    private val api: BiliApi,
    private val jar: PersistentCookieJar
) {
    private fun csrf(): String = jar.get("bili_jct") ?: ""

    /** id: ep123 / ss123 / md123 → SeasonData；失败返回 null。 */
    suspend fun season(id: String): SeasonData? {
        val resp = when {
            id.startsWith("ep") -> runCatching {
                api.pgcSeason(epId = id.removePrefix("ep").toLong())
            }.getOrNull()
            id.startsWith("ss") -> runCatching {
                api.pgcSeason(seasonId = id.removePrefix("ss").toLong())
            }.getOrNull()
            id.startsWith("md") -> {
                val mdResp = runCatching {
                    api.pgcReviewUser(id.removePrefix("md").toLong())
                }.getOrNull()
                val ssId = mdResp?.data?.media?.seasonId ?: return null
                runCatching { api.pgcSeason(seasonId = ssId) }.getOrNull()
            }
            else -> return null
        }
        return if (resp?.code == 0) resp.result else null
    }

    /** PGC playurl（result.dash，不加 wbi，doc 09 §4）。 */
    suspend fun pgcPlayUrl(bvid: String, cid: Long, qn: Int = 80): PlayUrlData? =
        runCatching { api.playUrlPgc(bvid, cid, qn) }.getOrNull()
            ?.let { if (it.code == 0 && it.result?.dash != null) it.result else null }

    /**
     * 追番 / 取消追番（pgc/web/follow/add|del）。
     * csrf 由 jar 自动读取（同 InteractionRepo 模式）。
     * 返回 true 表示操作成功（code==0）。
     */
    suspend fun follow(seasonId: Long): Boolean =
        runCatching { api.pgcFollowAdd(seasonId, csrf()) }.getOrNull()?.code == 0

    suspend fun unfollow(seasonId: Long): Boolean =
        runCatching { api.pgcFollowDel(seasonId, csrf()) }.getOrNull()?.code == 0
}
