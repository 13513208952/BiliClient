package com.biliclient.download

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 下载调度（doc 08 §6：限并发 2-3，避免风控/带宽打满）+ 暂停/取消 + 进度上报。
 * 由前台 DownloadService 驱动。
 */
@Singleton
class DownloadController @Inject constructor(
    private val repo: DownloadRepo
) {
    data class Progress(val taskId: Long, val done: Long, val total: Long, val status: Int, val speedBps: Long = 0L)

    /** 当前各活跃任务进度（taskId -> Progress），UI/通知观察。 */
    val progress = MutableStateFlow<Map<Long, Progress>>(emptyMap())

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val semaphore = Semaphore(MAX_CONCURRENT)   // doc §6：限并发
    private val gates = ConcurrentHashMap<Long, PauseGate>()
    private val cancelled = ConcurrentHashMap<Long, Boolean>()
    private val running = ConcurrentHashMap<Long, Boolean>()
    private val speedSamples = ConcurrentHashMap<Long, Pair<Long, Long>>() // taskId -> (timestampMs, done)

    /** 入队任务 id（任务已写 Room）。 */
    fun submit(taskId: Long) {
        if (running.putIfAbsent(taskId, true) != null) return  // 已在跑，去重
        scope.launch {
            semaphore.withPermit {
                val gate = PauseGate().also { gates[taskId] = it }
                cancelled[taskId] = false
                var lastPersist = 0L
                runCatching {
                    repo.run(
                        taskId = taskId,
                        pause = gate,
                        isCancelled = { cancelled[taskId] == true },
                        onProgress = { done, total ->
                            update(taskId, Progress(taskId, done, total, 1))
                            val now = System.currentTimeMillis()
                            if (now - lastPersist > 1000) {   // 节流落库（续传用）
                                lastPersist = now
                                scope.launch { repo.saveProgress(taskId, done) }
                            }
                        }
                    )
                }
                gates.remove(taskId); cancelled.remove(taskId); running.remove(taskId)
                speedSamples.remove(taskId)
                progress.value = progress.value - taskId
            }
        }
    }

    fun pause(taskId: Long) { gates[taskId]?.pause() }
    fun resume(taskId: Long) { gates[taskId]?.resume() }
    fun cancel(taskId: Long) { cancelled[taskId] = true; gates[taskId]?.resume() }

    fun hasActive(): Boolean = running.isNotEmpty()

    private fun update(taskId: Long, p: Progress) {
        val now = System.currentTimeMillis()
        val prev = speedSamples[taskId]
        val existing = progress.value[taskId]?.speedBps ?: 0L
        val speedBps = if (prev != null) {
            val deltaMs = now - prev.first
            if (deltaMs >= 500L) {
                val deltaBytes = (p.done - prev.second).coerceAtLeast(0L)
                speedSamples[taskId] = now to p.done
                (deltaBytes * 1000L / deltaMs.coerceAtLeast(1L))
            } else existing
        } else {
            speedSamples[taskId] = now to p.done
            0L
        }
        progress.value = progress.value + (taskId to p.copy(speedBps = speedBps))
    }

    companion object {
        const val MAX_CONCURRENT = 2   // doc §6
    }
}
