package com.biliclient.feature.tv.common

/**
 * qn（画质 id）到显示标签的映射。
 * 与 SettingsScreen.kt 的 quality 选项保持一致，提取为共享常量。
 */
object QualityLabels {
    val MAP: Map<Int, String> = mapOf(
        127 to "8K",
        126 to "杜比视界",
        125 to "HDR 真彩",
        120 to "4K 超清",
        116 to "1080P 60帧",
        112 to "1080P 高码率",
        100 to "智能修复",
         80 to "1080P 高清",
         64 to "720P 高清",
         32 to "480P 清晰",
         16 to "360P 流畅"
    )

    fun label(qn: Int): String = MAP[qn] ?: "$qn"

    val SPEEDS = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

    fun speedLabel(speed: Float): String = when (speed) {
        0.5f  -> "×0.5"
        0.75f -> "×0.75"
        1.0f  -> "×1.0"
        1.25f -> "×1.25"
        1.5f  -> "×1.5"
        2.0f  -> "×2.0"
        else  -> "×${"%.2f".format(speed)}"
    }
}
