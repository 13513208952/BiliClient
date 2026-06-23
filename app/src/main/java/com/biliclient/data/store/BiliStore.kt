package com.biliclient.data.store

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "biliclient")

/** UI 显示模式：手机竖屏 / 电视横屏。以 ordinal(Int) 存入 DataStore，默认 PHONE=0。 */
enum class UiMode { PHONE, TV }

/**
 * DataStore 持久化（doc 11）：**风控 cookie + 设置 + wbi key**。
 * 登录 cookie/账号在 Room（见 data/db）。
 */
class BiliStore(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }
    private val mapSerializer = MapSerializer(String.serializer(), String.serializer())

    private object Keys {
        val RISK_COOKIES = stringPreferencesKey("risk_cookies_json")
        val IMG_KEY = stringPreferencesKey("wbi_img_key")
        val SUB_KEY = stringPreferencesKey("wbi_sub_key")
        // 设置
        val DEFAULT_PLAY_QUALITY     = intPreferencesKey("default_play_quality")
        val DEFAULT_DOWNLOAD_QUALITY = intPreferencesKey("default_download_quality")
        val VIDEO_CODEC = intPreferencesKey("video_codec")
        val DANMAKU_ON = booleanPreferencesKey("danmaku_on")
        val DL_THREADS = intPreferencesKey("dl_threads")
        val DANMAKU_TEXT_SCALE = floatPreferencesKey("danmaku_text_scale")
        val DANMAKU_ALPHA = floatPreferencesKey("danmaku_alpha")
        // UI 模式（0=PHONE,1=TV）
        val UI_MODE = intPreferencesKey("ui_mode")
        // 桌面固定应用 package 名集合
        val DESKTOP_PKGS = stringSetPreferencesKey("desktop_pkgs")
        // TV 界面缩放（0.8–1.4，默认 1.0）
        val TV_UI_SCALE = floatPreferencesKey("tv_ui_scale")
        // 大会员番剧内容屏蔽（true=屏蔽，默认开）
        val BLOCK_PGC = booleanPreferencesKey("block_pgc")
    }

    suspend fun loadRiskCookies(): Map<String, String> {
        val prefs = context.dataStore.data.first()
        val raw = prefs[Keys.RISK_COOKIES] ?: return emptyMap()
        return runCatching { json.decodeFromString(mapSerializer, raw) }.getOrDefault(emptyMap())
    }

    suspend fun saveRiskCookies(cookies: Map<String, String>) {
        val raw = json.encodeToString(mapSerializer, cookies)
        context.dataStore.edit { it[Keys.RISK_COOKIES] = raw }
    }

    suspend fun loadWbiKeys(): Pair<String, String>? {
        val p = context.dataStore.data.first()
        val img = p[Keys.IMG_KEY] ?: return null
        return img to (p[Keys.SUB_KEY] ?: "")
    }

    suspend fun saveWbiKeys(img: String, sub: String) {
        context.dataStore.edit { it[Keys.IMG_KEY] = img; it[Keys.SUB_KEY] = sub }
    }

    suspend fun settings(): Settings {
        val p = context.dataStore.data.first()
        return Settings(
            defaultPlayQuality     = p[Keys.DEFAULT_PLAY_QUALITY]     ?: 80,
            defaultDownloadQuality = p[Keys.DEFAULT_DOWNLOAD_QUALITY] ?: 80,
            videoCodec        = p[Keys.VIDEO_CODEC]         ?: 7,
            danmakuOn         = p[Keys.DANMAKU_ON]          ?: true,
            downloadThreads   = p[Keys.DL_THREADS]          ?: 4,
            danmakuTextScale  = p[Keys.DANMAKU_TEXT_SCALE]  ?: 1.0f,
            danmakuAlpha      = p[Keys.DANMAKU_ALPHA]       ?: 1.0f,
            uiMode            = UiMode.values().getOrElse(p[Keys.UI_MODE] ?: 0) { UiMode.PHONE },
            tvUiScale         = p[Keys.TV_UI_SCALE] ?: 1.0f,
            blockPgc          = p[Keys.BLOCK_PGC] ?: true
        )
    }

    suspend fun setDefaultPlayQuality(qn: Int) =
        context.dataStore.edit { it[Keys.DEFAULT_PLAY_QUALITY] = qn }

    suspend fun setDefaultDownloadQuality(qn: Int) =
        context.dataStore.edit { it[Keys.DEFAULT_DOWNLOAD_QUALITY] = qn }
    suspend fun setVideoCodec(c: Int) = context.dataStore.edit { it[Keys.VIDEO_CODEC] = c }
    suspend fun setDanmakuOn(on: Boolean) = context.dataStore.edit { it[Keys.DANMAKU_ON] = on }
    suspend fun setDownloadThreads(n: Int) = context.dataStore.edit { it[Keys.DL_THREADS] = n }
    suspend fun setDanmakuTextScale(v: Float) = context.dataStore.edit { it[Keys.DANMAKU_TEXT_SCALE] = v }
    suspend fun setDanmakuAlpha(v: Float)     = context.dataStore.edit { it[Keys.DANMAKU_ALPHA] = v }

    suspend fun setUiMode(m: UiMode) = context.dataStore.edit { it[Keys.UI_MODE] = m.ordinal }
    suspend fun setTvUiScale(v: Float) = context.dataStore.edit { it[Keys.TV_UI_SCALE] = v }
    suspend fun setBlockPgc(on: Boolean) = context.dataStore.edit { it[Keys.BLOCK_PGC] = on }

    // ── 桌面固定应用 ──────────────────────────────────────────────
    val desktopPkgsFlow: Flow<Set<String>> =
        context.dataStore.data.map { it[Keys.DESKTOP_PKGS] ?: emptySet() }

    suspend fun setDesktopPkgs(s: Set<String>) =
        context.dataStore.edit { it[Keys.DESKTOP_PKGS] = s }

    // ── UI 模式响应式 Flow（仅供 RootViewModel 使用） ──────────────
    val uiModeFlow: Flow<UiMode> =
        context.dataStore.data.map { p ->
            UiMode.values().getOrElse(p[Keys.UI_MODE] ?: 0) { UiMode.PHONE }
        }

    // ── TV 界面缩放响应式 Flow ──────────────────────────────────────
    val tvUiScaleFlow: Flow<Float> =
        context.dataStore.data.map { p -> p[Keys.TV_UI_SCALE] ?: 1.0f }

    // ── 番剧内容屏蔽响应式 Flow（两端被动浏览面共用）──────────────
    val blockPgcFlow: Flow<Boolean> =
        context.dataStore.data.map { p -> p[Keys.BLOCK_PGC] ?: true }

    data class Settings(
        val defaultPlayQuality    : Int,
        val defaultDownloadQuality: Int,
        val videoCodec            : Int,
        val danmakuOn             : Boolean,
        val downloadThreads       : Int,
        val danmakuTextScale      : Float = 1.0f,
        val danmakuAlpha          : Float = 1.0f,
        val uiMode                : UiMode = UiMode.PHONE,
        val tvUiScale             : Float = 1.0f,
        val blockPgc              : Boolean = true
    )
}
