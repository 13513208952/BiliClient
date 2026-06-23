package com.biliclient.core

import com.biliclient.net.BiliApi
import com.biliclient.net.PersistentCookieJar
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * 风控 cookie 套件（doc 02 §4）：buvid3/4 + buvid_fp + bili_ticket + _uuid/b_lsid/b_nut + ExClimbWuzhi。
 * 本地随机 id 与生成算法在 [RiskIds] / [Murmur3] / [ExClimbPayload]；网络调用走 [BiliApi]；cookie 写入 [PersistentCookieJar]。
 */
class RiskCookie(
    private val api: BiliApi,
    private val jar: PersistentCookieJar,
    private val userAgent: String
) {
    /** 幂等初始化：已有 buvid3 则跳过。 */
    suspend fun ensureInitialized() {
        if (!jar.get("buvid3").isNullOrEmpty()) return
        val now = System.currentTimeMillis()

        // 本地随机 id（一次性写入）
        jar.putAll(
            mapOf(
                "_uuid" to RiskIds.genUuid(now),
                "b_lsid" to RiskIds.genBLsid(now),
                "b_nut" to RiskIds.genBNut(now),
                "buvid_fp" to genBuvidFp(userAgent)
            )
        )

        // buvid3/4
        runCatching { api.spi() }.getOrNull()?.let {
            if (it.code == 0 && it.data != null) {
                jar.put("buvid3", it.data.b3)
                jar.put("buvid4", it.data.b4)
            }
        }

        // bili_ticket（HMAC-SHA256）
        val ts = now / 1000
        runCatching {
            api.genWebTicket(
                keyId = "ec02",
                hexsign = Crypto.hmacSha256Hex("XgwSnGZ1p", "ts$ts"),
                ts = "$ts",
                csrf = jar.get("bili_jct") ?: ""
            )
        }.getOrNull()?.let {
            if (it.code == 0 && it.data != null) jar.put("bili_ticket", it.data.ticket)
        }

        // ExClimbWuzhi 激活 buvid3（best-effort）
        runCatching {
            val body = ExClimbPayload.build(userAgent, jar.get("_uuid") ?: "", now)
                .toRequestBody("application/json".toMediaType())
            api.exClimbWuzhi(body)
        }
    }
}
