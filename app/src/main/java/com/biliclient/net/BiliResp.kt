package com.biliclient.net

import kotlinx.serialization.Serializable

/** B站标准响应（data 容器）。 */
@Serializable
data class BiliResp<T>(
    val code: Int = -1,
    val message: String = "",
    val ttl: Int = 1,
    val data: T? = null
)

/** 番剧/课堂 PGC 响应（数据在 result）。 */
@Serializable
data class PgcResp<T>(val code: Int = -1, val message: String = "", val result: T? = null)
