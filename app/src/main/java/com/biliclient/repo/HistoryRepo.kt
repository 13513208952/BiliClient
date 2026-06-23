package com.biliclient.repo

import com.biliclient.net.BiliApi
import com.biliclient.net.HistoryCursorData
import javax.inject.Inject
import javax.inject.Singleton

/** 观看历史（游标分页，doc 10 §4）。 */
@Singleton
class HistoryRepo @Inject constructor(private val api: BiliApi) {
    suspend fun history(max: Long = 0, viewAt: Long = 0): HistoryCursorData? =
        runCatching { api.historyCursor(max = max, viewAt = viewAt) }.getOrNull()?.data
}
