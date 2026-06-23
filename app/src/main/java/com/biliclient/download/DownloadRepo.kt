package com.biliclient.download

import android.content.Context
import com.biliclient.data.db.DownloadDao
import com.biliclient.data.db.DownloadEntity
import com.biliclient.net.Ua
import com.biliclient.repo.VideoRepo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import okhttp3.OkHttpClient
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/**
 * 下载编排（doc 08 §3.1）：取流(05) → 分块下 video/audio → ffmpeg 合流 → 存相册 → 写 Room。
 * 状态：0排队 1下载中 2暂停 3合流中 4完成 5失败。
 */
@Singleton
class DownloadRepo @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: OkHttpClient,
    private val videoRepo: VideoRepo,
    private val dao: DownloadDao
) {
    private val downloader = RangeDownloader(client, Ua.PC)

    fun all(): Flow<List<DownloadEntity>> = dao.all()
    suspend fun unfinished(): List<DownloadEntity> = dao.unfinished()

    private fun cacheDir(): File = File(context.cacheDir, "downloads").apply { mkdirs() }

    /** 入队一个下载任务，返回任务 id。epId/seasonTitle 用于番剧（doc 09 §5）。 */
    suspend fun enqueue(
        bvid: String, aid: Long, cid: Long, title: String, cover: String,
        qualityId: Int, codecId: Int,
        epId: Long? = null, seasonTitle: String? = null
    ): Long {
        val now = System.currentTimeMillis()
        return dao.insert(
            DownloadEntity(
                bvid = bvid, aid = aid, cid = cid, title = title, cover = cover,
                qualityId = qualityId, codecId = codecId, status = 0,
                createdAt = now, updatedAt = now,
                epId = epId, seasonTitle = seasonTitle
            )
        )
    }

    /**
     * 执行下载（在前台 Service / Worker 的协程中调用）。
     * @return savedUri（成功）或 null（失败，状态已写 Room）。
     */
    suspend fun run(
        taskId: Long,
        pause: PauseGate? = null,
        isCancelled: () -> Boolean = { false },
        onProgress: (done: Long, total: Long) -> Unit = { _, _ -> }
    ): String? {
        val entity = dao.byId(taskId) ?: return fail(taskId, "任务不存在")
        val referer = "https://www.bilibili.com/video/${entity.bvid}"
        val dir = cacheDir()
        val videoFile = File(dir, "${taskId}.video.m4s")
        val audioFile = File(dir, "${taskId}.audio.m4s")
        val out = File(dir, "${taskId}.out.mp4")
        val qn = entity.qualityId.takeIf { it > 0 } ?: 80
        try {
            mark(entity, status = 1)
            // 1. 取流（新鲜获取，baseUrl 有时效；doc §6）
            val play = videoRepo.resolvePlay(entity.bvid, entity.cid, qn = qn, epId = entity.epId)
                ?: throw DownloadException(DownloadError.NO_STREAM, "无可用流")
            // DRM 拒绝（doc §5 / 05 篇 is_drm）
            if (play.isDrm) throw DownloadException(DownloadError.DRM, "受 DRM 保护，不支持下载")
            val dash = play.dash ?: throw DownloadException(DownloadError.NO_STREAM, "无 DASH")
            val sel = videoRepo.select(
                dash,
                qualityPref = entity.qualityId.takeIf { it > 0 } ?: 200,
                codecPref = if (entity.codecId > 0) entity.codecId else 7
            ) ?: throw DownloadException(DownloadError.NO_STREAM, "无可用画质")

            val videoCands = listOf(sel.video.baseUrl) + sel.video.backupUrl
            val audioCands = sel.audio?.let { listOf(it.baseUrl) + it.backupUrl } ?: emptyList()
            val hasAudio = audioCands.isNotEmpty()

            // 镜像探测 + 磁盘空间检查（doc §2.2）
            val vFiltered = downloader.probe(videoCands, referer)
            val aFiltered = if (hasAudio) downloader.probe(audioCands, referer) else null
            if (vFiltered.fileSize <= 0 || vFiltered.urls.isEmpty())
                throw DownloadException(DownloadError.NO_STREAM, "视频流无有效镜像")
            val needBytes = vFiltered.fileSize + (aFiltered?.fileSize ?: 0L)
            if (dir.usableSpace < needBytes + 50L * 1024 * 1024)  // 预留 50MB
                throw DownloadException(DownloadError.DISK_FULL, "磁盘空间不足")

            val totalBytes = needBytes
            dao.update(entity.copy(totalBytes = totalBytes, updatedAt = System.currentTimeMillis()))

            // 2/3. 下载 video + audio
            val threads = 4
            val vTotal = downloader.download(videoCands, videoFile, threads, referer, vFiltered, pause, isCancelled) { d, _ ->
                onProgress(d, totalBytes)
            }
            if (hasAudio) {
                downloader.download(audioCands, audioFile, threads, referer, aFiltered, pause, isCancelled) { d, _ ->
                    onProgress(vTotal + d, totalBytes)
                }
            }

            // 4. ffmpeg 合流（doc §3.4）
            mark(entity, status = 3)
            val mux = FfmpegMuxer.mux(videoFile, if (hasAudio) audioFile else null, out)
            if (!mux.ok) throw DownloadException(DownloadError.MERGE, "ffmpeg 合流失败")

            // 5. 存相册（doc §3.5）；番剧按 seasonTitle 归子目录（doc 09 §5）
            val subDir = entity.seasonTitle?.let { sanitize(it) }
            val uri = com.biliclient.util.Gallery.saveVideo(context, out, sanitize(entity.title), subDir)
                ?: throw DownloadException(DownloadError.SAVE, "存相册失败")

            // 6. 清理临时文件 + 标记完成
            cleanTemp(videoFile, audioFile, out)
            dao.update(
                entity.copy(
                    status = 4, savedUri = uri.toString(),
                    downloadedBytes = totalBytes, updatedAt = System.currentTimeMillis()
                )
            )
            return uri.toString()
        } catch (c: CancellationException) {
            // 取消：删临时文件（doc §3.3）
            cleanTemp(videoFile, audioFile, out)
            throw c
        } catch (d: DownloadException) {
            // doc §5：网络中断保留临时文件可续传；其余清理 out（part 保留以便续传）
            if (d.type != DownloadError.NETWORK) out.delete()
            return fail(taskId, d.type)
        } catch (e: Exception) {
            // 网络等未分类异常：归 err_network，保留临时文件可续传（doc §5）
            out.delete()
            return fail(taskId, DownloadError.NETWORK)
        }
    }

    /** 进度落库（续传用），由调度器节流调用。 */
    suspend fun saveProgress(taskId: Long, done: Long) {
        runCatching { dao.progress(taskId, done, 1, System.currentTimeMillis()) }
    }

    private fun cleanTemp(vararg files: File) {
        files.forEach { f ->
            f.delete()
            // 同时清理分块 .partN
            f.parentFile?.listFiles { it -> it.name.startsWith(f.name + ".part") }?.forEach { it.delete() }
        }
    }

    private suspend fun mark(e: DownloadEntity, status: Int) {
        dao.update(e.copy(status = status, updatedAt = System.currentTimeMillis()))
    }

    private suspend fun fail(taskId: Long, err: String): String? {
        dao.byId(taskId)?.let { dao.update(it.copy(status = 5, errorType = err, updatedAt = System.currentTimeMillis())) }
        return null
    }

    private fun sanitize(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(80)
}
