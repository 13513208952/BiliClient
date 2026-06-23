package com.biliclient.di

import com.biliclient.BuildConfig
import com.biliclient.core.RiskCookie
import com.biliclient.net.BiliApi
import com.biliclient.net.CookieInterceptor
import com.biliclient.net.CookieStore
import com.biliclient.net.PersistentCookieJar
import com.biliclient.net.Ua
import com.biliclient.net.WbiInterceptor
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides @Singleton
    fun json(): Json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    @Provides @Singleton
    fun cookieJar(store: CookieStore): PersistentCookieJar = PersistentCookieJar(store)

    @Provides @Singleton
    fun okHttp(jar: PersistentCookieJar): OkHttpClient =
        OkHttpClient.Builder()
            .cookieJar(jar)
            .addInterceptor(CookieInterceptor(Ua.PC))
            .addInterceptor(WbiInterceptor())
            .apply {
                // DEBUG 门控：原始 HTTP 报文日志（含完整响应体），用于诊断 API 返回结构。
                // release 构建 BuildConfig.DEBUG=false，R8 消除整个分支，零开销。
                if (BuildConfig.DEBUG) addInterceptor(
                    HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY }
                )
            }
            .build()

    @Provides @Singleton
    fun retrofit(client: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl("https://api.bilibili.com/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides @Singleton
    fun biliApi(retrofit: Retrofit): BiliApi = retrofit.create(BiliApi::class.java)

    @Provides @Singleton
    fun riskCookie(api: BiliApi, jar: PersistentCookieJar): RiskCookie = RiskCookie(api, jar, Ua.PC)
}
