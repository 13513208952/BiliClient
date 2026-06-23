package com.biliclient.download

import com.biliclient.net.Ua
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 镜像筛选（doc 08 §2.1，照搬 test1 镜像筛选逻辑）。
 * 非 hk 主机优先、hk 兜底；探测 Content-Length，第一个为权威 fileSize，仅保留长度一致的镜像。
 */
class MirrorFilter(private val client: OkHttpClient, private val userAgent: String = Ua.PC) {

    data class Result(val fileSize: Long, val urls: List<String>)

    suspend fun filter(candidates: List<String>, referer: String): Result = withContext(Dispatchers.IO) {
        // 非 hk 优先（host 含 "hk" 排后）
        val sorted = candidates.filter { it.isNotBlank() }.sortedBy { if (it.contains("hk")) 1 else 0 }
        var fileSize = -1L
        val kept = ArrayList<String>()
        for (u in sorted) {
            val len = probeLength(u, referer) ?: continue
            if (fileSize < 0) fileSize = len
            if (len == fileSize) kept.add(u)
        }
        Result(fileSize, kept)
    }

    /** 用 Range bytes=0-0 探测总长度（CDN 通用，比整体 GET 省流）。 */
    private fun probeLength(url: String, referer: String): Long? {
        val req = Request.Builder().url(url)
            .header("Range", "bytes=0-0")
            .header("Referer", referer)
            .header("User-Agent", userAgent)
            .build()
        return runCatching {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val cr = resp.header("Content-Range") // "bytes 0-0/12345"
                cr?.substringAfter('/')?.toLongOrNull()
                    ?: resp.body?.contentLength()?.takeIf { it > 0 }
            }
        }.getOrNull()
    }
}
