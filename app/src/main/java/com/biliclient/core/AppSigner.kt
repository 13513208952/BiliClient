package com.biliclient.core

/**
 * Android app 端 playurl 请求签名。
 * 权威来源（test1 优先）：GetDownUrl_Mobile。
 * 签名方式：所有参数按 key 升序拼成 "k=v&..."，追加 appsec 后取 MD5。
 * 用途：仅在 WEB 层「连接被拒绝」多次失败后作为备用源，主路线仍是 WEB。
 */
object AppSigner {
    const val APPKEY = "iVGUTjsxvpLeuDCf"
    private const val APPSEC = "aHRmhWMLkdeMuILqORnYZocwMBpMEOdt"

    /**
     * 对参数签名，返回完整 query 串（升序 "k=v&...&sign=<md5>"）。
     * 会自动注入 [APPKEY] 和当前时间戳 ts，调用方无需传入这两个字段。
     */
    fun sign(params: Map<String, String>): String {
        val all = params.toMutableMap()
        all["appkey"] = APPKEY
        all["ts"]     = (System.currentTimeMillis() / 1000).toString()
        val sorted = all.toSortedMap()
        val query  = sorted.entries.joinToString("&") { "${it.key}=${it.value}" }
        val sign   = Crypto.md5Hex(query + APPSEC)
        return "$query&sign=$sign"
    }
}
