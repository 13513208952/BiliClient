package com.biliclient

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.antonkarpenko.ffmpegkit.FFprobeKit
import com.antonkarpenko.ffmpegkit.ReturnCode
import com.biliclient.core.RiskCookie
import com.biliclient.core.WbiSigner
import com.biliclient.download.FfmpegMuxer
import com.biliclient.download.RangeDownloader
import com.biliclient.net.BiliApi
import com.biliclient.net.CookieInterceptor
import com.biliclient.net.InMemoryCookieStore
import com.biliclient.net.PersistentCookieJar
import com.biliclient.net.Ua
import com.biliclient.net.WbiInterceptor
import com.biliclient.repo.VideoRepo
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import retrofit2.Retrofit
import java.io.File

/**
 * P3c 设备验证（doc 08 §7.1 / doc 15）：真实下载 video+audio → ffmpeg 合流 → ffprobe 校验可播放。
 * 必须在模拟器/真机运行（加载 ffmpeg native 库）。
 */
@RunWith(AndroidJUnit4::class)
class FfmpegMuxInstrumentedTest {

    private fun stack(): Triple<BiliApi, RiskCookie, PersistentCookieJar> {
        val jar = PersistentCookieJar(InMemoryCookieStore())
        val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
        val client = OkHttpClient.Builder()
            .cookieJar(jar)
            .addInterceptor(CookieInterceptor(Ua.PC))
            .addInterceptor(WbiInterceptor())
            .build()
        val api = Retrofit.Builder()
            .baseUrl("https://api.bilibili.com/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build().create(BiliApi::class.java)
        return Triple(api, RiskCookie(api, jar, Ua.PC), jar)
    }

    @Test
    fun download_mux_and_ffprobe() = runBlocking {
        val (api, risk, _) = stack()
        risk.ensureInitialized()
        api.nav().data?.let { WbiSigner.updateKeysFromUrls(it.wbiImg.imgUrl, it.wbiImg.subUrl) }

        val video = VideoRepo(api)
        val v = video.view("BV1ZW411i7UY").data!!
        val play = video.resolvePlay(v.bvid, v.cid, 80)!!
        val sel = video.select(play.dash!!)!!
        val referer = "https://www.bilibili.com/video/${v.bvid}"

        val client = OkHttpClient.Builder().build()
        val dl = RangeDownloader(client, Ua.PC)
        val dir = File(System.getProperty("java.io.tmpdir") ?: "/data/local/tmp")
        val vf = File.createTempFile("vid_", ".m4s", dir)
        val af = File.createTempFile("aud_", ".m4s", dir)
        val out = File.createTempFile("out_", ".mp4", dir)
        try {
            dl.download(listOf(sel.video.baseUrl) + sel.video.backupUrl, vf, 4, referer)
            dl.download(listOf(sel.audio!!.baseUrl) + sel.audio!!.backupUrl, af, 4, referer)
            assertTrue("video 下载非空", vf.length() > 0)
            assertTrue("audio 下载非空", af.length() > 0)

            // ffmpeg 合流（native）
            val r = FfmpegMuxer.mux(vf, af, out)
            assertTrue("ffmpeg 合流应成功: ${r.log.takeLast(300)}", r.ok)
            assertTrue("产物非空", out.length() > 0)

            // ffprobe 校验：应同时含 video 与 audio 流
            val probe = FFprobeKit.execute(
                "-v error -show_entries stream=codec_type -of csv=p=0 \"${out.absolutePath}\""
            )
            assertTrue("ffprobe 应成功", ReturnCode.isSuccess(probe.returnCode))
            val streams = probe.allLogsAsString.orEmpty()
            println("ffprobe streams=$streams")
            assertTrue("应含视频流", streams.contains("video"))
            assertTrue("应含音频流", streams.contains("audio"))

            // 时长校验：应 > 0
            val dur = FFprobeKit.execute(
                "-v error -show_entries format=duration -of csv=p=0 \"${out.absolutePath}\""
            )
            val durStr = dur.allLogsAsString.orEmpty().trim()
            println("ffprobe duration=$durStr")
            assertTrue("时长应 > 0", (durStr.toDoubleOrNull() ?: 0.0) > 0.0)
        } finally {
            vf.delete(); af.delete(); out.delete()
        }
    }
}
