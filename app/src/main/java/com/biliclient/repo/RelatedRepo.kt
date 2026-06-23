package com.biliclient.repo

import com.biliclient.net.BiliApi
import com.biliclient.net.RelatedItem
import javax.inject.Inject
import javax.inject.Singleton

/** 猜你喜欢（doc 10，非 wbi 的 archive/related）。 */
@Singleton
class RelatedRepo @Inject constructor(private val api: BiliApi) {
    suspend fun related(bvid: String): List<RelatedItem> =
        runCatching { api.related(bvid).data }.getOrNull() ?: emptyList()
}
