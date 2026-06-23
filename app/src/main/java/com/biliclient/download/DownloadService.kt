package com.biliclient.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 前台下载服务（doc 08 §3.2）：foregroundServiceType=dataSync + 常驻通知（进度/暂停/取消）。
 * 零 root、标准 API；ColorOS 保活靠前台服务 + 电池白名单引导 + WorkManager 兜底。
 */
@AndroidEntryPoint
class DownloadService : LifecycleService() {

    @Inject lateinit var controller: DownloadController

    override fun onCreate() {
        super.onCreate()
        createChannel()
        val notif = buildNotification("准备下载…", 0, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIF_ID, notif)
        }
        // 观察活跃进度刷新通知（取并发任务的整体进度）
        lifecycleScope.launch {
            controller.progress.collect { map ->
                if (map.isEmpty()) {
                    updateNotification("空闲", 0, 0)
                    if (!controller.hasActive()) stopSelf()   // 无任务则退出前台服务
                } else {
                    val done = map.values.sumOf { it.done }
                    val total = map.values.sumOf { it.total }.coerceAtLeast(1)
                    val pct = (done * 100 / total).toInt()
                    val speed = fmtSpeed(map.values.sumOf { it.speedBps })
                    val text = "下载中 ${map.size} 个 · $pct%${if (speed.isNotEmpty()) " · $speed" else ""}"
                    updateNotification(text, (done / 1024).toInt(), (total / 1024).toInt())
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_ENQUEUE -> intent.getLongExtra(EXTRA_TASK_ID, -1).takeIf { it > 0 }?.let { controller.submit(it) }
            ACTION_PAUSE -> intent.getLongExtra(EXTRA_TASK_ID, -1).takeIf { it > 0 }?.let { controller.pause(it) }
            ACTION_RESUME -> intent.getLongExtra(EXTRA_TASK_ID, -1).takeIf { it > 0 }?.let { controller.resume(it) }
            ACTION_CANCEL -> intent.getLongExtra(EXTRA_TASK_ID, -1).takeIf { it > 0 }?.let { controller.cancel(it) }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "下载", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
        }
    }

    private fun buildNotification(text: String, progress: Int, max: Int): Notification {
        val b = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("BiliClient 下载")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
        if (max > 0) b.setProgress(max, progress, false)
        return b.build()
    }

    private fun fmtSpeed(bps: Long): String = when {
        bps >= 1024 * 1024 -> "${"%.1f".format(bps.toDouble() / 1024 / 1024)} MB/s"
        bps >= 1024 -> "${bps / 1024} KB/s"
        bps > 0 -> "$bps B/s"
        else -> ""
    }

    private fun updateNotification(text: String, progress: Int, max: Int) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification(text, progress, max))
    }

    companion object {
        private const val CHANNEL_ID = "download"
        private const val NOTIF_ID = 1001
        const val ACTION_ENQUEUE = "com.biliclient.download.ENQUEUE"
        const val ACTION_PAUSE = "com.biliclient.download.PAUSE"
        const val ACTION_RESUME = "com.biliclient.download.RESUME"
        const val ACTION_CANCEL = "com.biliclient.download.CANCEL"
        const val EXTRA_TASK_ID = "task_id"

        fun enqueue(ctx: Context, taskId: Long) {
            val i = Intent(ctx, DownloadService::class.java).apply {
                action = ACTION_ENQUEUE; putExtra(EXTRA_TASK_ID, taskId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(i) else ctx.startService(i)
        }

        fun control(ctx: Context, action: String, taskId: Long) {
            ctx.startService(Intent(ctx, DownloadService::class.java).apply {
                this.action = action; putExtra(EXTRA_TASK_ID, taskId)
            })
        }
    }
}
