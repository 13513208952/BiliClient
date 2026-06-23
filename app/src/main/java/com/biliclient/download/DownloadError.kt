package com.biliclient.download

/**
 * 下载错误分类（doc 08 §5）。errorType 写入 Room DownloadEntity.errorType。
 */
object DownloadError {
    const val NO_STREAM = "err_no_stream"   // playurl 无 dash / 无可用画质
    const val FORBIDDEN = "err_403"         // 403：Referer/cookie 缺失或链接过期 -> 重取 playurl
    const val NETWORK = "err_network"       // 网络中断（保留临时文件可续传）
    const val DISK_FULL = "err_disk"        // 磁盘不足
    const val MERGE = "err_merge"           // ffmpeg 合流失败
    const val DRM = "err_drm"               // DRM 拒绝
    const val SAVE = "err_save"             // 存相册失败
}

class DownloadException(val type: String, msg: String) : Exception(msg)
