package com.biliclient.core

import kotlin.random.Random

/**
 * 风控 cookie 的本地随机标识生成。
 * 权威来源：bili23 auth/cookie.py（_uuid:182-189, b_lsid:191-199, b_nut:201-202）。
 */
object RiskIds {
    /** _uuid：分段随机 + 毫秒尾数 + "infoc"。 */
    fun genUuid(nowMs: Long = System.currentTimeMillis()): String {
        val mp = (('1'..'9') + ('A'..'F')).map { it.toString() } + "10"
        val pck = intArrayOf(8, 4, 4, 4, 12)
        val t = (nowMs % 100000).toString().padEnd(5, '0')
        val parts = pck.map { len -> (1..len).joinToString("") { mp[Random.nextInt(mp.size)] } }
        return parts.joinToString("-") + t + "infoc"
    }

    /** b_lsid：8 位大写 hex + "_" + 大写hex(秒级时间戳)。 */
    fun genBLsid(nowMs: Long = System.currentTimeMillis()): String {
        val r = (1..8).joinToString("") { Integer.toHexString(Random.nextInt(16)).uppercase() }
        val ts = java.lang.Long.toHexString(nowMs / 1000).uppercase()
        return "${r}_${ts}"
    }

    /** b_nut：秒级时间戳。 */
    fun genBNut(nowMs: Long = System.currentTimeMillis()): String = (nowMs / 1000).toString()
}
