package com.biliclient.download

import kotlinx.coroutines.delay

/** 暂停闸门（doc 08 §3.3）。await() 在暂停时挂起，resume() 放行。 */
class PauseGate {
    @Volatile var paused: Boolean = false
        private set
    fun pause() { paused = true }
    fun resume() { paused = false }
    suspend fun await() { while (paused) delay(100) }
}
