package com.biliclient.download

import com.biliclient.net.Ua
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.cancellation.CancellationException

/**
 * 多线程分块下载（doc 08 §2.2/§3.3，照搬 test1 内核分块逻辑）。
 * 固定线程数等分字节区间 -> .part 临时文件（Range）-> 顺序合并；支持断点续传、暂停、取消、镜像 failover。
 */
class RangeDownloader(
    private val client: OkHttpClient,
    private val userAgent: String = Ua.PC
) {
    val mirror = MirrorFilter(client, userAgent)

    /** 仅探测镜像与总大小（用于磁盘空间检查，doc §2.2）。 */
    suspend fun probe(candidates: List<String>, referer: String): MirrorFilter.Result =
        mirror.filter(candidates, referer)

    /** @return 文件总字节（成功）。可传入已探测的 filtered 复用，避免重复探测。 */
    suspend fun download(
        candidates: List<String>,
        dst: File,
        threads: Int,
        referer: String,
        prefiltered: MirrorFilter.Result? = null,
        pause: PauseGate? = null,
        isCancelled: () -> Boolean = { false },
        onProgress: (done: Long, total: Long) -> Unit = { _, _ -> }
    ): Long {
        val filtered = prefiltered ?: mirror.filter(candidates, referer)
        val total = filtered.fileSize
        if (total <= 0 || filtered.urls.isEmpty()) throw IOException("无有效镜像")

        // 快速完成（doc §2.2）
        if (dst.exists() && dst.length() == total) { onProgress(total, total); return total }
        dst.parentFile?.mkdirs()

        val chunk = total / threads
        val progress = AtomicLong(0)
        coroutineScope {
            (0 until threads).map { i ->
                val start = i * chunk
                val end = if (i == threads - 1) total - 1 else start + chunk - 1
                val part = File(dst.parentFile, "${dst.name}.part$i")
                async(Dispatchers.IO) {
                    downloadRange(filtered.urls, part, start, end, referer, pause, isCancelled) { delta ->
                        onProgress(progress.addAndGet(delta), total)
                    }
                }
            }.awaitAll()
        }
        mergeParts(dst, threads)
        return total
    }

    private suspend fun downloadRange(
        urls: List<String>, part: File, start: Long, end: Long, referer: String,
        pause: PauseGate?, isCancelled: () -> Boolean, onDelta: (Long) -> Unit
    ) {
        var lastErr: Exception? = null
        for (url in urls) {
            try {
                val resume = if (part.exists()) part.length() else 0L
                val from = start + resume
                if (from > end) return  // 该段已完成
                val req = Request.Builder().url(url)
                    .header("Range", "bytes=$from-$end")
                    .header("Referer", referer)
                    .header("User-Agent", userAgent)
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (resp.code == 403) throw DownloadException(DownloadError.FORBIDDEN, "HTTP 403")
                    if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                    resp.body!!.byteStream().use { input ->
                        RandomAccessFile(part, "rw").use { raf ->
                            raf.seek(resume)
                            val buf = ByteArray(64 * 1024)
                            var n: Int
                            while (input.read(buf).also { n = it } > 0) {
                                if (isCancelled()) throw CancellationException("cancelled")
                                pause?.await()
                                raf.write(buf, 0, n)
                                onDelta(n.toLong())
                            }
                        }
                    }
                }
                return // 成功
            } catch (c: CancellationException) {
                throw c
            } catch (d: DownloadException) {
                throw d   // 403 等分类错误直接上抛，由上层处理（如重取 playurl）
            } catch (e: Exception) {
                lastErr = e // 换下一个镜像
            }
        }
        throw lastErr ?: IOException("全部镜像失败")
    }

    private fun mergeParts(dst: File, threads: Int) {
        FileOutputStream(dst).use { out ->
            for (i in 0 until threads) {
                val p = File(dst.parentFile, "${dst.name}.part$i")
                if (p.exists()) { FileInputStream(p).use { it.copyTo(out) }; p.delete() }
            }
        }
    }
}
