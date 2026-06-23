package com.biliclient

import com.biliclient.download.MirrorFilter
import com.biliclient.download.RangeDownloader
import com.biliclient.repo.VideoRepo
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * P3 下载内核联网验证（doc 08）：镜像筛选 + Range 分块下载 + 合并，校验文件大小 == Content-Length。
 * 下载真实音频流（比视频小）以控制测试时长。
 */
class P3DownloadTest {
    @Test
    fun mirror_filter_and_range_download() = runBlocking {
        val s = TestStack()
        s.prepare()
        val video = VideoRepo(s.api)
        val view = video.view("BV1ZW411i7UY").data!!
        val dash = video.resolveDash(view.bvid, view.cid, 80)!!
        val sel = video.select(dash)!!
        val audio = sel.audio!!
        val referer = "https://www.bilibili.com/video/${view.bvid}"
        val candidates = listOf(audio.baseUrl) + audio.backupUrl

        val filtered = MirrorFilter(s.client).filter(candidates, referer)
        println("mirror fileSize=${filtered.fileSize} kept=${filtered.urls.size}")
        assertTrue("应探测到文件大小", filtered.fileSize > 0)
        assertTrue("应有有效镜像", filtered.urls.isNotEmpty())

        val dst = File.createTempFile("bili_audio_", ".m4s")
        try {
            val total = RangeDownloader(s.client).download(candidates, dst, threads = 4, referer = referer)
            println("downloaded=${dst.length()} expected=$total")
            assertEquals("合并后大小应等于总长度", total, dst.length())
            assertTrue(dst.length() > 0)
        } finally {
            dst.delete()
        }
    }
}
