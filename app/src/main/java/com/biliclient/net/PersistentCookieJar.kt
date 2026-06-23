package com.biliclient.net

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.util.concurrent.ConcurrentHashMap

/**
 * 合并风控 cookie(DataStore) + 登录 cookie(Room) 的 CookieJar（doc 02/11）。
 * 运行时持有内存映射，变更经 CookieStore 落库。
 */
class PersistentCookieJar(private val store: CookieStore) : CookieJar {

    private val cookies = ConcurrentHashMap<String, String>().apply { putAll(store.load()) }

    fun put(name: String, value: String) { cookies[name] = value; store.persist(cookies.toMap()) }
    fun putAll(map: Map<String, String>) { cookies.putAll(map); store.persist(cookies.toMap()) }
    fun get(name: String): String? = cookies[name]
    fun remove(name: String) { cookies.remove(name); store.persist(cookies.toMap()) }
    fun snapshot(): Map<String, String> = cookies.toMap()

    override fun saveFromResponse(url: HttpUrl, list: List<Cookie>) {
        if (list.isEmpty()) return
        list.forEach { cookies[it.name] = it.value }
        store.persist(cookies.toMap())
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        if (!url.host.endsWith("bilibili.com")) return emptyList()
        return cookies.map {
            Cookie.Builder().name(it.key).value(it.value).domain("bilibili.com").build()
        }
    }
}
