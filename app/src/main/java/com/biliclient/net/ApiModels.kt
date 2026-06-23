package com.biliclient.net

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class SpiData(
    @SerialName("b_3") val b3: String = "",
    @SerialName("b_4") val b4: String = ""
)

@Serializable
data class TicketData(
    val ticket: String = "",
    @SerialName("created_at") val createdAt: Long = 0,
    val ttl: Long = 0
)

@Serializable
data class NavData(
    val isLogin: Boolean = false,
    val mid: Long = 0,
    val uname: String = "",
    val face: String = "",
    val vipStatus: Int = 0,
    @SerialName("wbi_img") val wbiImg: WbiImg = WbiImg()
)

@Serializable
data class WbiImg(
    @SerialName("img_url") val imgUrl: String = "",
    @SerialName("sub_url") val subUrl: String = ""
)

/** view 接口模型（doc 05）。 */
@Serializable
data class ViewData(
    val bvid: String = "",
    val aid: Long = 0,
    val cid: Long = 0,
    val title: String = "",
    val pic: String = "",
    val desc: String = "",
    val duration: Int = 0,
    val owner: Owner = Owner(),
    val stat: Stat = Stat(),
    val pages: List<Page> = emptyList(),
    val rights: Rights = Rights(),
    @SerialName("redirect_url") val redirectUrl: String? = null,
    @SerialName("ugc_season") val ugcSeason: UgcSeason? = null
)

// ---- 合集（ugc_season，doc 05）----
@Serializable
data class UgcSeason(
    val id: Long = 0,
    val title: String = "",
    val cover: String = "",
    @SerialName("mid") val mid: Long = 0,
    @SerialName("ep_count") val epCount: Int = 0,
    val sections: List<UgcSection> = emptyList()
)

@Serializable
data class UgcSection(
    @SerialName("season_id") val seasonId: Long = 0,
    val id: Long = 0,
    val title: String = "",
    val type: Int = 0,
    val episodes: List<UgcEpisode> = emptyList()
)

@Serializable
data class UgcEpisode(
    @SerialName("season_id") val seasonId: Long = 0,
    @SerialName("section_id") val sectionId: Long = 0,
    val id: Long = 0,
    val aid: Long = 0,
    val cid: Long = 0,
    val title: String = "",
    val bvid: String = "",
    val arc: UgcArc = UgcArc(),
    val page: Page = Page(),
    val pages: List<Page> = emptyList()
)

@Serializable
data class UgcArc(
    val pic: String = "",
    @SerialName("pubdate") val pubdate: Long = 0
)

@Serializable
data class Owner(val mid: Long = 0, val name: String = "", val face: String = "")

@Serializable
data class Stat(
    val view: Long = 0, val danmaku: Long = 0, val like: Long = 0,
    val coin: Long = 0, val favorite: Long = 0, val share: Long = 0, val reply: Long = 0
)

@Serializable
data class Page(val cid: Long = 0, val page: Int = 0, val part: String = "", val duration: Int = 0)

@Serializable
data class Rights(@SerialName("is_stein_gate") val isSteinGate: Int = 0)

// ---- playurl / DASH（doc 05） ----

/** 普通视频 web playurl 的 data；番剧 PGC 的 result 也复用同结构。 */
@Serializable
data class PlayUrlData(
    val dash: Dash? = null,
    @SerialName("accept_quality") val acceptQuality: List<Int> = emptyList(),
    val timelength: Long = 0,
    @SerialName("is_drm") val isDrm: Boolean = false
)

@Serializable
data class Dash(
    val duration: Int = 0,
    val video: List<DashMedia> = emptyList(),
    val audio: List<DashMedia> = emptyList(),
    val dolby: Dolby? = null,
    val flac: Flac? = null
)

@Serializable
data class Dolby(val type: Int = 0, val audio: List<DashMedia> = emptyList())

@Serializable
data class Flac(val display: Boolean = false, val audio: DashMedia? = null)

@Serializable
data class DashMedia(
    val id: Int = 0,
    val codecid: Int = 0,
    val baseUrl: String = "",
    @SerialName("backupUrl") val backupUrl: List<String> = emptyList(),
    val bandwidth: Long = 0,
    @SerialName("frame_rate") val frameRate: String = "",
    val codecs: String = "",
    val width: Int = 0,
    val height: Int = 0,
    val size: Long? = null
)

// ---- 番剧整季（doc 09） ----
@Serializable
data class SeasonData(
    @SerialName("season_id") val seasonId: Long = 0,
    val title: String = "",
    @SerialName("season_title") val seasonTitle: String = "",
    val cover: String = "",
    val type: Int = 1,
    val episodes: List<BangumiEp> = emptyList(),
    val seasons: List<RelatedSeason> = emptyList(),
    val section: List<BangumiSection> = emptyList(),
    @SerialName("user_status") val userStatus: BangumiUserStatus = BangumiUserStatus()
)

@Serializable
data class BangumiEp(
    val id: Long = 0,
    val aid: Long = 0,
    val bvid: String = "",
    val cid: Long = 0,
    val title: String = "",
    @SerialName("long_title") val longTitle: String = "",
    @SerialName("show_title") val showTitle: String = "",
    val cover: String = "",
    val duration: Long = 0,
    val badge: String = ""
)

@Serializable
data class BangumiUserStatus(
    val follow: Int = 0,
    @SerialName("follow_status") val followStatus: Int = 0,
    val pay: Int = 0,
    val login: Int = 0,
    val progress: BangumiProgress? = null
)

@Serializable
data class BangumiProgress(
    @SerialName("last_ep_id") val lastEpId: Long = 0,
    @SerialName("last_ep_index") val lastEpIndex: String = "",
    @SerialName("last_time") val lastTime: Long = 0
)

@Serializable
data class RelatedSeason(
    @SerialName("season_id") val seasonId: Long = 0,
    @SerialName("season_title") val seasonTitle: String = ""
)

@Serializable
data class BangumiSection(
    val title: String = "",
    val episodes: List<BangumiEp> = emptyList()
)

/** md（media_id）换 season_id（doc 09 §2.3）。 */
@Serializable
data class MediaReview(val media: MediaInfo? = null)

@Serializable
data class MediaInfo(
    @SerialName("season_id") val seasonId: Long = 0
)

// ---- 猜你喜欢 related（doc 10） ----
@Serializable
data class RelatedItem(
    val aid: Long = 0,
    val bvid: String = "",
    val cid: Long = 0,
    val title: String = "",
    val pic: String = "",
    val duration: Int = 0,
    val owner: Owner = Owner(),
    val stat: Stat = Stat()
)

// ---- 评论（doc 07） ----
@Serializable
data class ReplyPage(
    val replies: List<Reply>? = null,
    val cursor: ReplyCursor = ReplyCursor()
)

@Serializable
data class ReplyCursor(@SerialName("is_end") val isEnd: Boolean = false, val next: Long = 0)

@Serializable
data class Reply(
    val rpid: Long = 0,
    val member: ReplyMember = ReplyMember(),
    val content: ReplyContent = ReplyContent(),
    val like: Long = 0,
    val ctime: Long = 0
)

@Serializable
data class ReplyMember(val uname: String = "", val avatar: String = "")

@Serializable
data class ReplyContent(val message: String = "")

// ---- 收藏夹（doc 07/10） ----
@Serializable
data class FavFolderListData(val count: Int = 0, val list: List<FavFolder> = emptyList())

@Serializable
data class FavFolder(
    val id: Long = 0,
    val title: String = "",
    @SerialName("media_count") val mediaCount: Int = 0,
    @SerialName("fav_state") val favState: Int = 0
)

/** 互动状态查询 archive/relation。 */
@Serializable
data class ArchiveRelation(
    val like: Boolean = false,
    val coin: Long = 0,
    val favorite: Boolean = false,
    val attention: Boolean = false
)

// ---- 登录（P1） ----

@Serializable
data class QrGenData(
    val url: String = "",
    @SerialName("qrcode_key") val qrcodeKey: String = ""
)

@Serializable
data class QrPollData(
    val url: String = "",
    @SerialName("refresh_token") val refreshToken: String = "",
    val timestamp: Long = 0,
    val code: Int = -1,
    val message: String = ""
)

@Serializable
data class GeetestInfo(
    val challenge: String = "",
    val gt: String = ""
)

@Serializable
data class CaptchaData(
    val type: String = "",
    val token: String = "",
    val geetest: GeetestInfo = GeetestInfo()
)

@Serializable
data class SmsSendData(
    @SerialName("captcha_key") val captchaKey: String = ""
)

@Serializable
data class SmsLoginData(
    @SerialName("is_new") val isNew: Boolean = false,
    val status: Int = 0,
    val message: String = "",
    val url: String = ""
)

/** 扫码轮询状态码。 */
object QrStatus {
    const val SUCCESS = 0
    const val NOT_SCANNED = 86101
    const val SCANNED_WAIT = 86090
    const val EXPIRED = 86038
}

/** 登录结果（捕获到的关键 cookie）。 */
data class LoginResult(
    val success: Boolean,
    val uid: String? = null,
    val biliJct: String? = null,
    val message: String = ""
)

/** 写操作结果（仅 code/message）。 */
data class ActionResult(val code: Int, val message: String) {
    val ok: Boolean get() = code == 0
}

// ---- 首页推荐 feed（doc 10 §2）----
@Serializable
data class RcmdData(val item: List<FeedItem> = emptyList())

@Serializable
data class FeedItem(
    val id: Long = 0,
    val bvid: String = "",
    val cid: Long = 0,
    val goto: String = "",
    val pic: String = "",
    val title: String = "",
    val duration: Int = 0,
    val owner: FeedOwner = FeedOwner(),
    val stat: FeedStat = FeedStat(),
    @SerialName("pubdate") val pubdate: Long = 0,
    /** 番剧条目（goto=="bangumi"）的 season_id，用于路由到 BangumiScreen。 */
    @SerialName("season_id") val seasonId: Long = 0,
    @Transient val uiKey: String = java.util.UUID.randomUUID().toString()
) {
    /** goto=="bangumi" 时为番剧条目。 */
    val isBangumi: Boolean get() = goto == "bangumi"
    /** 番剧条目的路由 token：优先 ss{seasonId}，否则 ep{id}。 */
    val bangumiToken: String get() = if (seasonId > 0) "ss$seasonId" else "ep$id"
}

@Serializable
data class FeedOwner(val name: String = "", val mid: Long = 0)

@Serializable
data class FeedStat(val view: Long = 0, val danmaku: Long = 0, val like: Long = 0)

// ---- 收藏夹内容（doc 10 §3）----
@Serializable
data class FavResourceData(
    val info: FavInfo = FavInfo(),
    val medias: List<FavMedia>? = null
)

@Serializable
data class FavInfo(
    @SerialName("media_count") val mediaCount: Int = 0,
    val title: String = ""
)

@Serializable
data class FavMedia(
    val id: Long = 0,
    val bvid: String = "",
    val title: String = "",
    val cover: String = "",
    @SerialName("cnt_info") val cntInfo: FavCntInfo = FavCntInfo(),
    val upper: FavUpper = FavUpper(),
    val duration: Int = 0,
    @Transient val uiKey: String = java.util.UUID.randomUUID().toString()
)

@Serializable
data class FavCntInfo(val play: Long = 0, val danmaku: Long = 0)

@Serializable
data class FavUpper(val name: String = "")

// ---- 观看历史（doc 10 §4）----
@Serializable
data class HistoryCursorData(
    val cursor: HistoryCursor = HistoryCursor(),
    val list: List<HistoryItem> = emptyList()
)

@Serializable
data class HistoryCursor(
    val max: Long = 0,
    @SerialName("view_at") val viewAt: Long = 0,
    val business: String = ""
)

@Serializable
data class HistoryItem(
    val title: String = "",
    val cover: String = "",
    val history: HistoryDetail = HistoryDetail(),
    @SerialName("author_name") val authorName: String = "",
    val progress: Int = 0,
    @SerialName("view_at") val viewAt: Long = 0,
    val duration: Int = 0,
    @Transient val uiKey: String = java.util.UUID.randomUUID().toString()
)

@Serializable
data class HistoryDetail(
    val bvid: String = "",
    val oid: Long = 0,
    val cid: Long = 0,
    val business: String = ""
)

// ---- 搜索（doc 10 §5）----
@Serializable
data class SearchTypeData(
    val result: List<SearchVideoItem> = emptyList()
)

/** 番剧搜索（search_type=media_bangumi）结果。 */
@Serializable
data class SearchBangumiData(
    val result: List<SearchBangumiItem> = emptyList()
)

@Serializable
data class SearchBangumiItem(
    @SerialName("season_id") val seasonId: Long = 0,
    @SerialName("media_id") val mediaId: Long = 0,
    @SerialName("season_type") val seasonType: Int = 0,
    val title: String = "",
    val cover: String = "",
    @SerialName("ep_size") val epSize: Int = 0,
    @SerialName("media_score") val mediaScore: MediaScore? = null,
    @SerialName("is_follow") val isFollow: Int = 0,
    val badge: String = "",
    @Transient val uiKey: String = java.util.UUID.randomUUID().toString()
) {
    fun cleanTitle(): String = title.replace(Regex("<[^>]*>"), "")
    fun coverHttps(): String = if (cover.startsWith("//")) "https:$cover" else cover
}

@Serializable
data class MediaScore(
    val score: Float = 0f,
    @SerialName("user_count") val userCount: Int = 0
)

@Serializable
data class SearchVideoItem(
    val aid: Long = 0,
    val bvid: String = "",
    val title: String = "",
    val pic: String = "",
    val author: String = "",
    val play: Long = 0,
    val duration: String = "",
    @Transient val uiKey: String = java.util.UUID.randomUUID().toString()
) {
    fun cleanTitle(): String = title.replace(Regex("<[^>]*>"), "")
    fun picHttps(): String = if (pic.startsWith("//")) "https:$pic" else pic
}

// ---- 搜索联想（doc 10 §5.1，s.search.bilibili.com）----
@Serializable
data class SuggestResp(
    val result: SuggestResult = SuggestResult()
)

@Serializable
data class SuggestResult(val tag: List<SuggestTag> = emptyList())

@Serializable
data class SuggestTag(val value: String = "")
