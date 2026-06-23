package com.biliclient.core

import android.util.Log
import com.biliclient.BuildConfig

/**
 * DEBUG 门控诊断工具。
 * BuildConfig.DEBUG=true 时写 logcat（tag=BiliDiag）；release 构建 BuildConfig.DEBUG=false，
 * R8 会把整个 if 分支作为死代码消除，零开销、零日志残留。
 */
object Diag {
    fun d(msg: String) {
        if (BuildConfig.DEBUG) Log.d("BiliDiag", msg)
    }
}
