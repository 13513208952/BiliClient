package com.biliclient.net

import com.biliclient.core.WbiSigner
import okhttp3.Interceptor
import okhttp3.Response

/**
 * 注入 UA / Referer / Origin（doc 02 §2/§5）。cookie 由 PersistentCookieJar 自动附加。
 */
class CookieInterceptor(private val userAgent: String) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val orig = chain.request()
        val b = orig.newBuilder()
            .header("User-Agent", userAgent)
            .header("Origin", "https://www.bilibili.com")
        if (orig.header("Referer") == null) b.header("Referer", "https://www.bilibili.com")
        return chain.proceed(b.build())
    }
}

/**
 * wbi 签名（doc 02 §3/§5）。对带 `X-Wbi: 1` 头的请求，把全部 query 参数按 wbi 规则重签。
 */
class WbiInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val req = chain.request()
        if (req.header("X-Wbi") == null) return chain.proceed(req)
        if (!WbiSigner.hasKeys) {
            return chain.proceed(req.newBuilder().removeHeader("X-Wbi").build())
        }
        val url = req.url
        val params = LinkedHashMap<String, String>()
        for (name in url.queryParameterNames) {
            params[name] = url.queryParameter(name) ?: ""
        }
        val signedQuery = WbiSigner.sign(params)   // 已 url-encode，含 wts + w_rid
        val newUrl = url.newBuilder().encodedQuery(signedQuery).build()
        return chain.proceed(req.newBuilder().url(newUrl).removeHeader("X-Wbi").build())
    }
}
