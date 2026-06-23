package com.biliclient.repo

import com.biliclient.core.WbiSigner
import com.biliclient.danmaku.proto.DanmakuElem
import com.biliclient.danmaku.proto.DmSegMobileReply
import com.biliclient.net.BiliApi
import com.biliclient.player.DanmakuItem
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ceil

/** 弹幕拉取 + protobuf 解析（doc 06，Wire）。 */
@Singleton
class DanmakuRepo @Inject constructor(private val api: BiliApi) {

    suspend fun fetch(cid: Long, durationSec: Int): List<DanmakuItem> {
        if (!WbiSigner.hasKeys) return emptyList()
        val parts = ceil(durationSec / 360.0).toInt().coerceAtLeast(1)
        val out = ArrayList<DanmakuElem>()
        for (i in 1..parts) {
            val bytes = runCatching { api.danmakuSeg(oid = cid, segmentIndex = i).bytes() }.getOrNull() ?: continue
            if (bytes.isEmpty()) continue
            runCatching {
                DmSegMobileReply.ADAPTER.decode(bytes).elems.forEach {
                    if (it.stime != 0 && it.text.isNotEmpty()) out.add(it)
                }
            }
        }
        return out.sortedBy { it.stime }.map { DanmakuItem(it.stime, it.mode, it.text, it.color, it.size) }
    }
}
