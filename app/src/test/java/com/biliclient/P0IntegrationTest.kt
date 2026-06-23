package com.biliclient

import com.biliclient.core.WbiSigner
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** P0 端到端联网验证：风控套件 -> nav 取 wbi key -> wbi/view。 */
class P0IntegrationTest {
    @Test
    fun risk_nav_wbi_view_flow() = runBlocking {
        val s = TestStack()
        s.risk.ensureInitialized()
        println("buvid3=${s.jar.get("buvid3")?.take(12)} buvid_fp=${s.jar.get("buvid_fp")}")
        assertTrue("应取到 buvid3", s.jar.get("buvid3")?.isNotEmpty() == true)

        val nav = s.api.nav().data
        assertNotNull(nav)
        WbiSigner.updateKeysFromUrls(nav!!.wbiImg.imgUrl, nav.wbiImg.subUrl)
        assertTrue("应取到 wbi key", WbiSigner.hasKeys)

        val view = s.api.view("BV1ZW411i7UY")
        println("view code=${view.code} title=${view.data?.title}")
        assertEquals(0, view.code)
        assertEquals(18190165L, view.data!!.aid)
        assertTrue(view.data!!.cid > 0)
    }
}
