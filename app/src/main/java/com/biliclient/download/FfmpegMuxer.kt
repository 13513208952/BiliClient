package com.biliclient.download

import com.antonkarpenko.ffmpegkit.FFmpegKit
import com.antonkarpenko.ffmpegkit.ReturnCode
import java.io.File

/**
 * ffmpeg 合流（doc 08 §3.4）。-c copy 不重编码（秒级）。
 * 依赖维护中 fork com.antonkarpenko:ffmpeg-kit-full（运行时零联网、含 arm64-v8a+x86_64、无 GMS）。
 */
object FfmpegMuxer {

    data class Result(val ok: Boolean, val log: String)

    /** 视频+音频合流为 mp4。 */
    fun mux(video: File, audio: File?, out: File): Result {
        if (out.exists()) out.delete()
        val cmd = if (audio != null) {
            "-y -i \"${video.absolutePath}\" -i \"${audio.absolutePath}\" -c copy \"${out.absolutePath}\""
        } else {
            // 纯视频 remux
            "-y -i \"${video.absolutePath}\" -c copy \"${out.absolutePath}\""
        }
        val session = FFmpegKit.execute(cmd)
        val ok = ReturnCode.isSuccess(session.returnCode)
        return Result(ok, session.allLogsAsString ?: "")
    }
}
