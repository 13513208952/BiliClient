package com.biliclient.core

/**
 * wbi 签名。
 * 权威来源：bili23 parser/base.py:16-63（mixinKeyEncTab + enc_wbi）。
 * img_key/sub_key 来自 /x/web-interface/nav 的 wbi_img.{img_url,sub_url} 文件名 stem。
 */
object WbiSigner {
    private val MIXIN_TAB = intArrayOf(
        46, 47, 18, 2, 53, 8, 23, 32, 15, 50, 10, 31, 58, 3, 45, 35, 27, 43, 5, 49,
        33, 9, 42, 19, 29, 28, 14, 39, 12, 38, 41, 13, 37, 48, 7, 16, 24, 55, 40,
        61, 26, 17, 0, 1, 60, 51, 30, 4, 22, 25, 54, 21, 56, 59, 6, 63, 57, 62, 11,
        36, 20, 34, 44, 52
    )

    @Volatile private var imgKey: String = ""
    @Volatile private var subKey: String = ""

    val hasKeys: Boolean get() = imgKey.isNotEmpty() && subKey.isNotEmpty()

    fun updateKeysFromUrls(imgUrl: String, subUrl: String) {
        imgKey = stem(imgUrl)
        subKey = stem(subUrl)
    }

    fun updateKeysRaw(img: String, sub: String) {
        imgKey = img; subKey = sub
    }

    private fun stem(url: String): String =
        url.substringAfterLast('/').substringBeforeLast('.')

    private fun mixinKey(): String {
        val raw = imgKey + subKey
        val sb = StringBuilder(32)
        for (i in MIXIN_TAB) {
            if (i < raw.length) sb.append(raw[i])
            if (sb.length == 32) break
        }
        return sb.toString()
    }

    /**
     * 对参数签名，返回完整 query 串（已 url-encode，含 wts 与 w_rid）。
     * @param nowSec 可注入时间（测试用），默认当前秒。
     */
    fun sign(params: Map<String, String>, nowSec: Long = System.currentTimeMillis() / 1000): String {
        check(hasKeys) { "wbi keys not loaded; call nav first" }
        val m = mixinKey()
        val all = params.toMutableMap()
        all["wts"] = nowSec.toString()
        val sorted = all.toSortedMap()
        // 过滤 value 中的 !'()* 字符（B站要求）
        val filtered = sorted.mapValues { (_, v) -> v.filterNot { it in "!'()*" } }
        val query = filtered.entries.joinToString("&") {
            "${Crypto.urlEncode(it.key)}=${Crypto.urlEncode(it.value)}"
        }
        val wRid = Crypto.md5Hex(query + m)
        return "$query&w_rid=$wRid"
    }
}
