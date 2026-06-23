package com.biliclient.core

import com.biliclient.net.Dash
import com.biliclient.net.DashMedia

/**
 * DASH 选流（doc 05）。画质/编码/音质表照搬 bili23 common/data/media_info.py。
 * 选流逻辑照搬 video_info.py / audio_info.py。下载默认编码 AVC(7)。
 */
object MediaSelect {

    // 画质 id（降序优先级）
    val VIDEO_QUALITY_DESC = listOf(127, 126, 125, 120, 116, 112, 100, 80, 64, 32, 16)
    // 编码优先：AVC > HEVC > AV1（兼容性优先，doc 05）
    val CODEC_PRIORITY = listOf(7, 12, 13)
    // 音质优先（reorder：Hi-Res > 杜比 > 192 > 132 > 64）
    val AUDIO_PRIORITY = listOf(30251, 30250, 30280, 30232, 30216)
    private val AUDIO_REORDER = mapOf(30251 to 0, 30250 to 1, 30280 to 2, 30232 to 3, 30216 to 4)

    const val QUALITY_AUTO = 200
    const val CODEC_AUTO = 20
    const val AUDIO_AUTO = 30300

    data class Selection(val video: DashMedia, val audio: DashMedia?)

    /**
     * @param qualityPref 200=auto；否则具体 qn
     * @param codecPref 20=auto；否则 7/12/13
     * @param audioPref 30300=auto；否则具体音质 id
     */
    fun select(
        dash: Dash,
        qualityPref: Int = QUALITY_AUTO,
        codecPref: Int = 7,
        audioPref: Int = AUDIO_AUTO
    ): Selection? {
        val video = selectVideo(dash, qualityPref, codecPref) ?: return null
        val audio = selectAudio(dash, audioPref)
        return Selection(video, audio)
    }

    private fun selectVideo(dash: Dash, qualityPref: Int, codecPref: Int): DashMedia? {
        if (dash.video.isEmpty()) return null
        // map[qn][codecid] = media
        val map = HashMap<Int, HashMap<Int, DashMedia>>()
        for (m in dash.video) map.getOrPut(m.id) { HashMap() }[m.codecid] = m
        val availQn = map.keys.sortedDescending()

        val qn = when {
            qualityPref == QUALITY_AUTO -> VIDEO_QUALITY_DESC.firstOrNull { it in map.keys } ?: availQn.first()
            map.containsKey(qualityPref) -> qualityPref
            else -> availQn.first()
        }
        val codecs = map[qn]!!
        val codec = when {
            codecPref == CODEC_AUTO -> CODEC_PRIORITY.firstOrNull { it in codecs.keys } ?: codecs.keys.first()
            codecs.containsKey(codecPref) -> codecPref
            else -> CODEC_PRIORITY.firstOrNull { it in codecs.keys } ?: codecs.keys.first()
        }
        return codecs[codec]
    }

    private fun selectAudio(dash: Dash, audioPref: Int): DashMedia? {
        val all = LinkedHashMap<Int, DashMedia>()
        dash.flac?.audio?.let { all[it.id] = it }
        dash.dolby?.audio?.forEach { all[it.id] = it }
        dash.audio.forEach { if (!all.containsKey(it.id)) all[it.id] = it }
        if (all.isEmpty()) return null
        val sorted = all.keys.sortedBy { AUDIO_REORDER[it] ?: Int.MAX_VALUE }

        val id = when {
            audioPref == AUDIO_AUTO -> AUDIO_PRIORITY.firstOrNull { it in all.keys } ?: sorted.first()
            all.containsKey(audioPref) -> audioPref
            else -> sorted.first()
        }
        return all[id]
    }
}
