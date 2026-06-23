package com.biliclient.net

import kotlinx.serialization.json.JsonElement
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Url

/**
 * B站 PC web 端 API（doc 01 §4 / doc 14）。baseUrl = https://api.bilibili.com/。
 * passport.* 用绝对 URL。带 `X-Wbi: 1` 头的请求由 WbiInterceptor 自动 wbi 签名。
 */
interface BiliApi {

    // ---- 风控/鉴权基础 ----
    @GET("x/frontend/finger/spi")
    suspend fun spi(): BiliResp<SpiData>

    @FormUrlEncoded
    @POST("https://api.bilibili.com/bapis/bilibili.api.ticket.v1.Ticket/GenWebTicket")
    suspend fun genWebTicket(
        @Field("key_id") keyId: String,
        @Field("hexsign") hexsign: String,
        @Field("context[ts]") ts: String,
        @Field("csrf") csrf: String
    ): BiliResp<TicketData>

    @POST("x/internal/gaia-gateway/ExClimbWuzhi")
    suspend fun exClimbWuzhi(@Body body: RequestBody): ResponseBody

    @GET("x/web-interface/nav")
    suspend fun nav(): BiliResp<NavData>

    // ---- 视频 ----
    @Headers("X-Wbi: 1")
    @GET("x/web-interface/wbi/view")
    suspend fun view(@Query("bvid") bvid: String): BiliResp<ViewData>

    @Headers("X-Wbi: 1")
    @GET("x/player/wbi/playurl")
    suspend fun playUrlWeb(
        @Query("bvid") bvid: String,
        @Query("cid") cid: Long,
        @Query("qn") qn: Int,
        @Query("fnver") fnver: Int = 0,
        @Query("fnval") fnval: Int = 4048,
        @Query("fourk") fourk: Int = 1
    ): BiliResp<PlayUrlData>

    @GET("pgc/player/web/playurl")
    suspend fun playUrlPgc(
        @Query("bvid") bvid: String,
        @Query("cid") cid: Long,
        @Query("qn") qn: Int,
        @Query("fnver") fnver: Int = 0,
        @Query("fnval") fnval: Int = 12240,
        @Query("fourk") fourk: Int = 1
    ): PgcResp<PlayUrlData>

    /** md → season_id 换算（doc 09 §2.3）。 */
    @GET("pgc/review/user")
    suspend fun pgcReviewUser(@Query("media_id") mediaId: Long): BiliResp<MediaReview>

    /** 整季解析（doc 09 §3）。ep_id/season_id 择一传入，返回 PgcResp.result。 */
    @GET("pgc/view/web/season")
    suspend fun pgcSeason(
        @Query("ep_id") epId: Long? = null,
        @Query("season_id") seasonId: Long? = null
    ): PgcResp<SeasonData>

    @GET("pugv/player/web/playurl")
    suspend fun playUrlCheese(
        @Query("avid") avid: Long,
        @Query("cid") cid: Long,
        @Query("ep_id") epId: Long,
        @Query("qn") qn: Int = 0,
        @Query("fnver") fnver: Int = 0,
        @Query("fnval") fnval: Int = 16,
        @Query("fourk") fourk: Int = 1
    ): BiliResp<PlayUrlData>

    @Headers("X-Wbi: 1")
    @GET("x/v2/dm/wbi/web/seg.so")
    suspend fun danmakuSeg(
        @Query("oid") oid: Long,
        @Query("segment_index") segmentIndex: Int,
        @Query("type") type: Int = 1
    ): ResponseBody

    @GET("x/web-interface/archive/related")
    suspend fun related(@Query("bvid") bvid: String): BiliResp<List<RelatedItem>>

    @Headers("X-Wbi: 1")
    @GET("x/v2/reply/wbi/main")
    suspend fun replies(
        @Query("oid") oid: Long,
        @Query("type") type: Int = 1,
        @Query("mode") mode: Int = 3,
        @Query("next") next: Long = 0
    ): BiliResp<ReplyPage>

    @GET("x/web-interface/archive/relation")
    suspend fun archiveRelation(@Query("bvid") bvid: String): BiliResp<ArchiveRelation>

    @GET("x/v3/fav/folder/created/list-all")
    suspend fun favFolders(
        @Query("up_mid") upMid: Long,
        @Query("rid")    rid: Long? = null,
        @Query("type")   type: Int? = null
    ): BiliResp<FavFolderListData>

    // ---- 互动（写，需 csrf） ----
    @FormUrlEncoded @POST("x/web-interface/archive/like")
    suspend fun like(@Field("aid") aid: Long, @Field("like") like: Int, @Field("csrf") csrf: String): BiliResp<JsonElement>

    @FormUrlEncoded @POST("x/web-interface/coin/add")
    suspend fun coin(@Field("aid") aid: Long, @Field("multiply") multiply: Int, @Field("select_like") selectLike: Int, @Field("csrf") csrf: String): BiliResp<JsonElement>

    @FormUrlEncoded @POST("x/v3/fav/resource/deal")
    suspend fun favDeal(@Field("rid") rid: Long, @Field("type") type: Int, @Field("add_media_ids") add: String, @Field("del_media_ids") del: String, @Field("csrf") csrf: String): BiliResp<JsonElement>

    @FormUrlEncoded @POST("x/web-interface/archive/like/triple")
    suspend fun triple(@Field("aid") aid: Long, @Field("csrf") csrf: String): BiliResp<JsonElement>

    @FormUrlEncoded @POST("x/v2/reply/add")
    suspend fun addReply(@Field("oid") oid: Long, @Field("message") message: String, @Field("type") type: Int = 1, @Field("plat") plat: Int = 1, @Field("csrf") csrf: String): BiliResp<JsonElement>

    @FormUrlEncoded @POST("x/v2/dm/post")
    suspend fun postDanmaku(
        @Field("oid") oid: Long, @Field("msg") msg: String, @Field("bvid") bvid: String,
        @Field("progress") progress: Int, @Field("color") color: Int, @Field("fontsize") fontsize: Int,
        @Field("mode") mode: Int, @Field("rnd") rnd: Long, @Field("type") type: Int = 1,
        @Field("pool") pool: Int = 0, @Field("plat") plat: Int = 1, @Field("csrf") csrf: String
    ): BiliResp<JsonElement>

    @FormUrlEncoded @POST("x/relation/modify")
    suspend fun follow(@Field("fid") fid: Long, @Field("act") act: Int, @Field("re_src") reSrc: Int = 14, @Field("csrf") csrf: String): BiliResp<JsonElement>

    // ---- 登录（passport，绝对 URL） ----
    @GET("https://passport.bilibili.com/x/passport-login/web/qrcode/generate")
    suspend fun qrGenerate(
        @Query("source") source: String = "main-fe-header",
        @Query("go_url") goUrl: String = "https://www.bilibili.com/",
        @Query("web_location") webLocation: String = "333.1007"
    ): BiliResp<QrGenData>

    @GET("https://passport.bilibili.com/x/passport-login/web/qrcode/poll")
    suspend fun qrPoll(@Query("qrcode_key") key: String): BiliResp<QrPollData>

    @GET("https://passport.bilibili.com/x/passport-login/captcha")
    suspend fun captcha(@Query("source") source: String = "main-fe-header"): BiliResp<CaptchaData>

    @FormUrlEncoded
    @POST("https://passport.bilibili.com/x/passport-login/web/sms/send")
    suspend fun smsSend(
        @Field("cid") cid: String, @Field("tel") tel: String,
        @Field("source") source: String = "main-fe-header",
        @Field("token") token: String, @Field("challenge") challenge: String,
        @Field("validate") validate: String, @Field("seccode") seccode: String
    ): BiliResp<SmsSendData>

    @FormUrlEncoded
    @POST("https://passport.bilibili.com/x/passport-login/web/login/sms")
    suspend fun smsLogin(
        @Field("cid") cid: String, @Field("tel") tel: String, @Field("code") code: String,
        @Field("source") source: String = "main-fe-header",
        @Field("captcha_key") captchaKey: String,
        @Field("go_url") goUrl: String = "https://www.bilibili.com/"
    ): BiliResp<SmsLoginData>

    @FormUrlEncoded
    @POST("https://passport.bilibili.com/login/exit/v2")
    suspend fun logout(@Field("biliCSRF") csrf: String): ResponseBody

    // ---- 首页推荐 feed（doc 10 §2, wbi）----
    @Headers("X-Wbi: 1")
    @GET("x/web-interface/wbi/index/top/rcmd")
    suspend fun homeRcmd(
        @Query("fresh_type") freshType: Int = 3,
        @Query("ps") ps: Int = 12,
        @Query("fresh_idx") freshIdx: Int = 0,
        @Query("fresh_idx_1h") freshIdx1h: Int = 0,
        @Query("feed_version") feedVersion: String = "V8",
        @Query("web_location") webLocation: String = "1430650"
    ): BiliResp<RcmdData>

    // ---- 收藏夹内容（doc 10 §3）----
    @GET("x/v3/fav/resource/list")
    suspend fun favResourceList(
        @Query("media_id") mediaId: Long,
        @Query("pn") pn: Int = 1,
        @Query("ps") ps: Int = 20,
        @Query("keyword") keyword: String = "",
        @Query("order") order: String = "mtime",
        @Query("type") type: Int = 0,
        @Query("tid") tid: Int = 0,
        @Query("platform") platform: String = "web"
    ): BiliResp<FavResourceData>

    // ---- 历史游标版（doc 10 §4）----
    @GET("x/web-interface/history/cursor")
    suspend fun historyCursor(
        @Query("max") max: Long = 0,
        @Query("view_at") viewAt: Long = 0,
        @Query("business") business: String = "archive",
        @Query("ps") ps: Int = 20
    ): BiliResp<HistoryCursorData>

    // ---- 搜索（doc 10 §5, wbi）----
    @Headers("X-Wbi: 1")
    @GET("x/web-interface/wbi/search/type")
    suspend fun searchType(
        @Query("search_type") searchType: String = "video",
        @Query("keyword") keyword: String,
        @Query("page") page: Int = 1,
        @Query("page_size") pageSize: Int = 20
    ): BiliResp<SearchTypeData>

    /** 番剧搜索（search_type=media_bangumi）。 */
    @Headers("X-Wbi: 1")
    @GET("x/web-interface/wbi/search/type")
    suspend fun searchBangumi(
        @Query("search_type") searchType: String = "media_bangumi",
        @Query("keyword") keyword: String,
        @Query("page") page: Int = 1,
        @Query("page_size") pageSize: Int = 20
    ): BiliResp<SearchBangumiData>

    // ---- 追番（pgc/web/follow，需 csrf）----
    @FormUrlEncoded
    @POST("pgc/web/follow/add")
    suspend fun pgcFollowAdd(
        @Field("season_id") seasonId: Long,
        @Field("csrf") csrf: String
    ): BiliResp<JsonElement>

    @FormUrlEncoded
    @POST("pgc/web/follow/del")
    suspend fun pgcFollowDel(
        @Field("season_id") seasonId: Long,
        @Field("csrf") csrf: String
    ): BiliResp<JsonElement>

    // ---- 短链展开（@Url 绝对地址，跟随重定向取最终 URL） ----
    @GET
    suspend fun expand(@Url url: String): Response<ResponseBody>

    // ---- 搜索联想（s.search.bilibili.com，绝对 URL）----
    @GET
    suspend fun suggest(@Url url: String): SuggestResp

    // ---- Android app 端 playurl（备用，WEB「连接被拒绝」多次后才调用）----
    // 端点 app.bilibili.com/x/playurl；URL 由 VideoRepo 用 AppSigner 拼好后整体传入。
    // 返回结构与 playUrlWeb 相同（data.dash），直接复用 PlayUrlData。
    @GET
    suspend fun playUrlApp(@Url url: String): BiliResp<PlayUrlData>
}
