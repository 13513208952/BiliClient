package com.biliclient.auth

import com.biliclient.core.RiskCookie
import com.biliclient.net.BiliApi
import com.biliclient.net.BiliResp
import com.biliclient.net.CaptchaData
import com.biliclient.net.LoginResult
import com.biliclient.net.PersistentCookieJar
import com.biliclient.net.QrGenData
import com.biliclient.net.QrPollData
import com.biliclient.net.QrStatus
import com.biliclient.net.SmsSendData
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 登录仓库（doc 04）：扫码 / 短信 / 极验 / 登出 / 登录态。
 * 登录 cookie 由 OkHttp 的 PersistentCookieJar 在响应 Set-Cookie 时自动捕获并落库。
 */
@Singleton
class AuthRepo @Inject constructor(
    private val api: BiliApi,
    private val jar: PersistentCookieJar,
    private val riskCookie: RiskCookie
) {
    private suspend fun ensureRisk() = riskCookie.ensureInitialized()

    suspend fun qrGenerate(): BiliResp<QrGenData> { ensureRisk(); return api.qrGenerate() }

    suspend fun qrPoll(key: String): BiliResp<QrPollData> = api.qrPoll(key)

    suspend fun captcha(): BiliResp<CaptchaData> { ensureRisk(); return api.captcha() }

    suspend fun smsSend(
        cid: String, tel: String, token: String, challenge: String, validate: String, seccode: String
    ): BiliResp<SmsSendData> = api.smsSend(cid, tel, "main-fe-header", token, challenge, validate, seccode)

    suspend fun smsLogin(cid: String, tel: String, code: String, captchaKey: String): LoginResult {
        val r = api.smsLogin(cid = cid, tel = tel, code = code, captchaKey = captchaKey)
        return if (r.code == 0) loginResult()
        else LoginResult(false, message = r.message.ifEmpty { "code=${r.code}" })
    }

    fun isLoggedIn(): Boolean = !jar.get("SESSDATA").isNullOrEmpty()

    fun loginResult(): LoginResult {
        val sess = jar.get("SESSDATA")
        return if (sess.isNullOrEmpty()) LoginResult(false, message = "未捕获 SESSDATA")
        else LoginResult(true, uid = jar.get("DedeUserID"), biliJct = jar.get("bili_jct"))
    }

    fun csrf(): String = jar.get("bili_jct") ?: ""

    suspend fun logout(): Boolean {
        val csrf = jar.get("bili_jct") ?: return false
        runCatching { api.logout(csrf) }
        listOf("SESSDATA", "bili_jct", "DedeUserID", "DedeUserID__ckMd5").forEach { jar.remove(it) }
        return true
    }

    companion object {
        val SUCCESS = QrStatus.SUCCESS
    }
}
