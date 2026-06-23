package com.biliclient.repo

import android.net.Uri
import com.biliclient.core.Route
import com.biliclient.core.UrlRouter
import com.biliclient.data.db.SearchHistoryDao
import com.biliclient.data.db.SearchHistoryEntity
import com.biliclient.net.BiliApi
import com.biliclient.net.SearchBangumiItem
import com.biliclient.net.SearchVideoItem
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/** 搜索栏路由 + 关键词搜索 + 联想（doc 03/10）。 */
@Singleton
class SearchRepo @Inject constructor(
    private val api: BiliApi,
    private val userRepo: UserRepo,
    private val historyDao: SearchHistoryDao
) {
    val searchHistory: Flow<List<SearchHistoryEntity>> = historyDao.recent()

    suspend fun resolve(input: String): Route {
        val raw = input.trim()
        if (raw.isEmpty()) return Route.Unknown
        val expanded = if (UrlRouter.isShortLink(raw)) {
            runCatching { api.expand(raw).raw().request.url.toString() }.getOrDefault(raw)
        } else raw
        return UrlRouter.route(expanded)
    }

    suspend fun searchVideo(kw: String, page: Int = 1): List<SearchVideoItem> {
        if (kw.isBlank()) return emptyList()
        userRepo.ensureWbiKeys()
        return runCatching { api.searchType(keyword = kw, page = page) }
            .getOrNull()?.data?.result ?: emptyList()
    }

    /** 番剧搜索（media_bangumi），不受屏蔽开关影响。 */
    suspend fun searchBangumi(kw: String, page: Int = 1): List<SearchBangumiItem> {
        if (kw.isBlank()) return emptyList()
        userRepo.ensureWbiKeys()
        return runCatching { api.searchBangumi(keyword = kw, page = page) }
            .getOrNull()?.data?.result ?: emptyList()
    }

    suspend fun suggest(kw: String): List<String> {
        if (kw.isBlank()) return emptyList()
        val url = "https://s.search.bilibili.com/main/suggest?term=${Uri.encode(kw)}&main_ver=v1"
        return runCatching { api.suggest(url) }.getOrNull()
            ?.result?.tag?.map { it.value } ?: emptyList()
    }

    suspend fun saveHistory(kw: String) {
        if (kw.isNotBlank())
            historyDao.upsert(SearchHistoryEntity(kw, System.currentTimeMillis()))
    }

    suspend fun clearHistory() = historyDao.clear()
}
