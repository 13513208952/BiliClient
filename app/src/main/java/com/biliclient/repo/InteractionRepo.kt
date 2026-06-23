package com.biliclient.repo

import com.biliclient.core.Diag
import com.biliclient.net.ActionResult
import com.biliclient.net.ArchiveRelation
import com.biliclient.net.BiliApi
import com.biliclient.net.BiliResp
import com.biliclient.net.FavFolder
import com.biliclient.net.PersistentCookieJar
import com.biliclient.net.Reply
import com.biliclient.net.ReplyPage
import kotlinx.serialization.json.JsonElement
import javax.inject.Inject
import javax.inject.Singleton

/** 互动（点赞/投币/收藏/三连/评论/发弹幕/关注，doc 07）。所有写操作带 csrf=bili_jct。 */
@Singleton
class InteractionRepo @Inject constructor(
    private val api: BiliApi,
    private val jar: PersistentCookieJar
) {
    private fun csrf(): String = jar.get("bili_jct") ?: ""
    private fun BiliResp<JsonElement>.toAction() = ActionResult(code, message)

    suspend fun relation(bvid: String): ArchiveRelation =
        runCatching { api.archiveRelation(bvid).data }.getOrNull() ?: ArchiveRelation()

    suspend fun replies(aid: Long): List<Reply> =
        runCatching { api.replies(oid = aid).data?.replies }.getOrNull() ?: emptyList()

    suspend fun like(aid: Long, on: Boolean) = api.like(aid, if (on) 1 else 2, csrf()).toAction()

    suspend fun coin(aid: Long, multiply: Int, alsoLike: Boolean) =
        api.coin(aid, multiply, if (alsoLike) 1 else 0, csrf()).toAction()

    suspend fun favorite(aid: Long, add: List<Long>, del: List<Long>) =
        api.favDeal(aid, 2, add.joinToString(","), del.joinToString(","), csrf()).toAction()

    suspend fun favFolders(upMid: Long) =
        runCatching { api.favFolders(upMid).data?.list }.getOrNull() ?: emptyList()

    suspend fun favFoldersWithState(uid: Long, aid: Long): List<FavFolder> =
        runCatching { api.favFolders(upMid = uid, rid = aid, type = 2).data?.list }
            .getOrNull() ?: emptyList()

    suspend fun repliesPage(aid: Long, next: Long = 0): ReplyPage {
        val page = runCatching { api.replies(oid = aid, next = next).data }.getOrNull() ?: ReplyPage()
        Diag.d("repliesPage oid=$aid next-in=$next " +
               "replies=${page.replies?.size} cursor.next=${page.cursor.next} is_end=${page.cursor.isEnd}")
        return page
    }

    suspend fun triple(aid: Long) = api.triple(aid, csrf()).toAction()

    suspend fun addReply(aid: Long, message: String) = api.addReply(oid = aid, message = message, csrf = csrf()).toAction()

    suspend fun postDanmaku(cid: Long, bvid: String, msg: String, progressMs: Int, mode: Int = 1, color: Int = 16777215) =
        api.postDanmaku(
            oid = cid, msg = msg, bvid = bvid, progress = progressMs, color = color,
            fontsize = 25, mode = mode, rnd = System.currentTimeMillis(), csrf = csrf()
        ).toAction()

    suspend fun follow(mid: Long, on: Boolean) = api.follow(mid, if (on) 1 else 2, csrf = csrf()).toAction()
}
