package com.biliclient

import com.biliclient.auth.AuthRepo
import com.biliclient.net.QrStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** P1 登录网络路径联网验证（无需真实扫码/收短信）。 */
class P1AuthNetworkTest {
    @Test
    fun qr_generate_and_poll() = runBlocking {
        val s = TestStack()
        val auth = AuthRepo(s.api, s.jar, s.risk)
        val gen = auth.qrGenerate()
        println("qr generate code=${gen.code} key=${gen.data?.qrcodeKey?.take(12)}")
        assertEquals(0, gen.code)
        assertTrue(gen.data?.url?.isNotEmpty() == true)
        val poll = auth.qrPoll(gen.data!!.qrcodeKey)
        println("qr poll inner=${poll.data?.code} msg=${poll.data?.message}")
        assertEquals(0, poll.code)
        assertEquals(QrStatus.NOT_SCANNED, poll.data?.code)
    }

    @Test
    fun captcha_init() = runBlocking {
        val s = TestStack()
        val auth = AuthRepo(s.api, s.jar, s.risk)
        val cap = auth.captcha()
        println("captcha code=${cap.code} type=${cap.data?.type} gt=${cap.data?.geetest?.gt?.take(12)}")
        assertEquals(0, cap.code)
        assertTrue(cap.data?.geetest?.gt?.isNotEmpty() == true)
    }
}
