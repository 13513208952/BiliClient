package com.biliclient

import com.biliclient.core.RiskCookie
import com.biliclient.core.WbiSigner
import com.biliclient.net.BiliApi
import com.biliclient.net.CookieInterceptor
import com.biliclient.net.InMemoryCookieStore
import com.biliclient.net.PersistentCookieJar
import com.biliclient.net.Ua
import com.biliclient.net.WbiInterceptor
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit

/**
 * JVM 联网测试装配（不依赖 Hilt/Room）：手动组装与生产一致的 Retrofit + 拦截器 + 风控。
 */
class TestStack {
    val jar = PersistentCookieJar(InMemoryCookieStore())
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    val client = OkHttpClient.Builder()
        .cookieJar(jar)
        .addInterceptor(CookieInterceptor(Ua.PC))
        .addInterceptor(WbiInterceptor())
        .build()
    val api: BiliApi = Retrofit.Builder()
        .baseUrl("https://api.bilibili.com/")
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(BiliApi::class.java)
    val risk = RiskCookie(api, jar, Ua.PC)

    /** 初始化风控 + 取 wbi key。 */
    suspend fun prepare() {
        risk.ensureInitialized()
        val nav = api.nav().data
        if (nav != null && nav.wbiImg.imgUrl.isNotEmpty()) {
            WbiSigner.updateKeysFromUrls(nav.wbiImg.imgUrl, nav.wbiImg.subUrl)
        }
    }
}
