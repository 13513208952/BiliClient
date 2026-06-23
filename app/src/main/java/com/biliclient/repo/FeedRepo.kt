package com.biliclient.repo

import com.biliclient.data.store.BiliStore
import com.biliclient.net.BiliApi
import com.biliclient.net.FeedItem
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/** 首页推荐 feed（doc 10 §2）。 */
@Singleton
class FeedRepo @Inject constructor(
    private val api: BiliApi,
    private val userRepo: UserRepo,
    private val store: BiliStore
) {
    /**
     * 取推荐 feed。
     * blockPgc=true（默认）时只放行 goto=="av" 的普通视频，屏蔽番剧条目；
     * blockPgc=false 时同时放行 goto=="bangumi" 的番剧条目。
     */
    suspend fun rcmd(freshIdx: Int): List<FeedItem> {
        userRepo.ensureWbiKeys()
        val blockPgc = store.blockPgcFlow.first()
        val items = runCatching { api.homeRcmd(freshIdx = freshIdx, freshIdx1h = freshIdx) }
            .getOrNull()?.data?.item ?: emptyList()
        return items.filter { item ->
            when (item.goto) {
                "av" -> true
                "bangumi" -> !blockPgc
                else -> false
            }
        }
    }
}
