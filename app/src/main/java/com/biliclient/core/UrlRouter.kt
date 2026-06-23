package com.biliclient.core

/**
 * 搜索栏输入路由（doc 03）。权威来源：bili23 common/data/url_pattern.py。
 * 短链(b23.tv)需先展开再二次路由（展开放 Repository/网络层）。
 */
sealed interface Route {
    data class Video(val id: String) : Route      // BV.. / av.. / 数字
    data class Bangumi(val id: String) : Route    // ep.. / ss.. / md..
    data class Cheese(val id: String) : Route
    data class FavList(val mid: String) : Route
    data class Space(val mid: String) : Route
    object Unknown : Route
}

object UrlRouter {
    // 顺序敏感：先匹配完整域名 URL，再匹配裸号
    private val patterns: List<Pair<String, Regex>> = listOf(
        "video" to Regex("""bilibili\.com/video/([a-zA-Z0-9]+)"""),
        "bangumi" to Regex("""bilibili\.com/bangumi/(?:play|media)/(ss\d+|ep\d+|md\d+)"""),
        "cheese" to Regex("""bilibili\.com/cheese/play/(ss\d+|ep\d+)"""),
        "favlist" to Regex("""space\.bilibili\.com/(\d+)/favlist"""),
        "favlist" to Regex("""www\.bilibili\.com/list/ml(\d+)"""),
        "space" to Regex("""space\.bilibili\.com/(\d+)"""),
        "video" to Regex("""(BV[a-zA-Z0-9]+|av[0-9]+)"""),
        "bangumi" to Regex("""(ep[0-9]+|ss[0-9]+|md[0-9]+)""")
    )

    fun isShortLink(s: String): Boolean = s.contains("b23.tv") || s.contains("bili2233.cn")

    /** 已展开短链后的 url/文本 -> Route。 */
    fun route(input: String): Route {
        val s = input.trim()
        for ((type, re) in patterns) {
            val m = re.find(s) ?: continue
            val g = m.groupValues.last()
            return when (type) {
                "video" -> Route.Video(g)
                "bangumi" -> Route.Bangumi(g)
                "cheese" -> Route.Cheese(g)
                "favlist" -> Route.FavList(g)
                "space" -> Route.Space(g)
                else -> Route.Unknown
            }
        }
        return Route.Unknown
    }
}
