package com.biliclient

import com.biliclient.repo.DanmakuRepo
import com.biliclient.repo.RelatedRepo
import com.biliclient.repo.VideoRepo
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** P2 视频核心联网验证：view 全字段 -> playurl 选流 -> 弹幕解析 -> related。 */
class P2VideoNetworkTest {
    @Test
    fun view_playurl_danmaku_related() = runBlocking {
        val s = TestStack()
        s.prepare()
        val video = VideoRepo(s.api)
        val danmakuRepo = DanmakuRepo(s.api)
        val relatedRepo = RelatedRepo(s.api)

        val view = video.view("BV1ZW411i7UY")
        val d = view.data!!
        println("view up=${d.owner.name} view=${d.stat.view} pages=${d.pages.size}")
        assertTrue(view.code == 0)
        assertTrue(d.owner.name.isNotEmpty())
        assertTrue(d.stat.view > 0)
        assertTrue(d.pages.isNotEmpty())

        val dash = video.resolveDash(d.bvid, d.cid, 80)
        assertNotNull(dash)
        val sel = video.select(dash!!)
        assertNotNull(sel)
        println("video qn=${sel!!.video.id} codec=${sel.video.codecid} audio=${sel.audio?.id}")
        assertTrue(sel.video.baseUrl.isNotEmpty())
        assertNotNull(sel.audio)

        val danmaku = danmakuRepo.fetch(d.cid, d.duration)
        println("danmaku count=${danmaku.size}")
        assertTrue(danmaku.isNotEmpty())

        val related = relatedRepo.related(d.bvid)
        println("related count=${related.size}")
        assertTrue(related.isNotEmpty())
    }
}
