package com.biliclient

import com.biliclient.core.AbConvert
import com.biliclient.core.Route
import com.biliclient.core.UrlRouter
import com.biliclient.core.genBuvidFp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreUnitTest {

    @Test
    fun abconvert_known_case() {
        assertEquals(18190165L, AbConvert.bvToAv("BV1ZW411i7UY"))
        assertEquals("BV1ZW411i7UY", AbConvert.avToBv(18190165L))
    }

    @Test
    fun abconvert_roundtrip() {
        for (av in listOf(2L, 170001L, 18190165L, 999999999L, 113000000000000L)) {
            val bv = AbConvert.avToBv(av)
            assertEquals(av, AbConvert.bvToAv(bv))
        }
    }

    @Test
    fun buvid_fp_matches_bili23() {
        // 与 bili23 auth/cookie.py get_buvid_fp 同输入对拍（Python 参考输出）
        val ua = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
        assertEquals("98e1d7273f121f256259e726fb3a5bb8", genBuvidFp(ua))
    }

    @Test
    fun url_router() {
        assertTrue(UrlRouter.route("https://www.bilibili.com/video/BV1ZW411i7UY") is Route.Video)
        assertTrue(UrlRouter.route("BV1ZW411i7UY") is Route.Video)
        assertTrue(UrlRouter.route("av18190165") is Route.Video)
        assertTrue(UrlRouter.route("https://www.bilibili.com/bangumi/play/ep123") is Route.Bangumi)
        assertTrue(UrlRouter.route("ss456") is Route.Bangumi)
        assertTrue(UrlRouter.route("https://www.bilibili.com/cheese/play/ep789") is Route.Cheese)
        assertEquals("BV1ZW411i7UY", (UrlRouter.route("BV1ZW411i7UY") as Route.Video).id)
        assertTrue(UrlRouter.isShortLink("https://b23.tv/abc123"))
    }
}
