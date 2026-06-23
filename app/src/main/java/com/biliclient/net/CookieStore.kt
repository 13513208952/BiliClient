package com.biliclient.net

import java.util.concurrent.ConcurrentHashMap

/** cookie 持久化抽象。Android 用 Room(账号)+DataStore(风控) 实现；测试用内存实现。 */
interface CookieStore {
    fun load(): Map<String, String>
    fun persist(all: Map<String, String>)
}

/** 内存实现（JVM 测试用）。 */
class InMemoryCookieStore : CookieStore {
    private val m = ConcurrentHashMap<String, String>()
    override fun load(): Map<String, String> = m.toMap()
    override fun persist(all: Map<String, String>) { m.clear(); m.putAll(all) }
}
