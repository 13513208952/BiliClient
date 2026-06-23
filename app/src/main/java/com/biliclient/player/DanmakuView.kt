package com.biliclient.player

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.util.AttributeSet
import android.view.SurfaceHolder
import android.view.SurfaceView
import kotlin.math.abs

/** 渲染用弹幕项（与 protobuf 解耦）。 */
data class DanmakuItem(
    val timeMs: Int,
    val mode: Int,      // 1-3 滚动, 4 底, 5 顶, 6 逆向
    val text: String,
    val color: Int,     // 十进制 RGB
    val size: Int       // 25 标准 / 18 小
)

/**
 * 弹幕渲染（doc 06）：SurfaceView 独立线程 + Canvas + 对象池 + 弹道。
 * 性能地板骁龙835：对象池复用、文字宽度预测量、密度上限。
 * 用法：setData() 后，外部按播放进度持续 updateTime(ms)；setEnabled 控制显隐。
 */
class DanmakuView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : SurfaceView(context, attrs), SurfaceHolder.Callback, Runnable {

    @Volatile var danmakuEnabled: Boolean = true
    @Volatile var textScale: Float = 1.0f
    @Volatile var danmakuAlpha: Int = 255
    /** 弹幕密度因子 [0.1, 1.0]：最终上限 = maxActive × densityFactor，可实时调整。 */
    @Volatile var densityFactor: Float = 1.0f
    @Volatile private var running = false
    @Volatile private var nowMs: Long = 0
    private var lastNowMs: Long = 0

    private var thread: Thread? = null
    private val items = ArrayList<DanmakuItem>()
    private var cursor = 0

    private val active = ArrayList<Sprite>()
    private val pool = ArrayDeque<Sprite>()

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = Color.BLACK; strokeWidth = 3f
    }

    private val scrollDurationMs = 8000f
    private val maxActive = 200
    private var lineHeight = 0f
    private var laneCount = 0
    private var lastTextScale = 1.0f

    init {
        holder.addCallback(this)
        setZOrderOnTop(true)
        holder.setFormat(android.graphics.PixelFormat.TRANSLUCENT)
    }

    fun setData(list: List<DanmakuItem>) {
        synchronized(items) {
            items.clear(); items.addAll(list.sortedBy { it.timeMs }); cursor = 0
        }
        synchronized(active) { active.clear() }
    }

    /** 外部按播放进度调用。 */
    fun updateTime(ms: Long) { nowMs = ms }

    override fun surfaceCreated(holder: SurfaceHolder) {
        running = true
        thread = Thread(this).also { it.start() }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        lineHeight = sp(16.25f) * textScale + dp(6f)
        laneCount = (height / lineHeight).toInt().coerceAtLeast(1)
        lastTextScale = textScale
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        running = false
        thread?.join(500)
        thread = null
    }

    override fun run() {
        var lastNs = System.nanoTime()
        while (running) {
            val now = System.nanoTime()
            val dt = (now - lastNs) / 1_000_000f
            lastNs = now
            val canvas = holder.lockCanvas() ?: continue
            try {
                synchronized(holder) { frame(canvas, dt) }
            } finally {
                holder.unlockCanvasAndPost(canvas)
            }
            val sleep = 16 - ((System.nanoTime() - now) / 1_000_000)
            if (sleep > 0) try { Thread.sleep(sleep) } catch (_: InterruptedException) {}
        }
    }

    private fun frame(canvas: Canvas, dtMs: Float) {
        canvas.drawColor(0, PorterDuff.Mode.CLEAR)
        if (!danmakuEnabled) { lastNowMs = nowMs; return }

        // textScale 变化时重算行高和弹道数
        if (textScale != lastTextScale) {
            lastTextScale = textScale
            lineHeight = sp(16.25f) * textScale + dp(6f)
            laneCount  = (height / lineHeight).toInt().coerceAtLeast(1)
        }

        // seek 检测：回退或大跳 -> 重置游标 + 清屏
        if (nowMs < lastNowMs || abs(nowMs - lastNowMs) > 3000) reseek()
        lastNowMs = nowMs

        // 发射到点弹幕
        val dynamicMax = (maxActive * densityFactor).toInt().coerceAtLeast(1)
        synchronized(items) {
            synchronized(active) {
                while (cursor < items.size && items[cursor].timeMs <= nowMs) {
                    if (active.size < dynamicMax) emit(items[cursor])
                    cursor++
                }
            }
        }

        // 更新 + 绘制 + 回收
        synchronized(active) {
            val it = active.iterator()
            while (it.hasNext()) {
                val s = it.next()
                s.update(dtMs)
                if (s.isDead(width.toFloat(), nowMs)) {
                    it.remove(); recycle(s)
                } else {
                    s.draw(canvas, fill, stroke, danmakuAlpha)
                }
            }
        }
    }

    private fun reseek() {
        synchronized(items) {
            var lo = 0; var hi = items.size
            while (lo < hi) { val m = (lo + hi) / 2; if (items[m].timeMs < nowMs) lo = m + 1 else hi = m }
            cursor = lo
        }
        synchronized(active) { active.forEach { recycle(it) }; active.clear() }
    }

    private fun emit(item: DanmakuItem) {
        val s = pool.removeFirstOrNull() ?: Sprite()
        val ts = (if (item.size <= 18) sp(11.7f) else sp(16.25f)) * textScale
        fill.textSize = ts
        val w = fill.measureText(item.text)
        s.set(item, ts, w, Color.rgb((item.color shr 16) and 0xff, (item.color shr 8) and 0xff, item.color and 0xff))
        layout(s)
        active.add(s)
    }

    private fun layout(s: Sprite) {
        val h = height.toFloat().coerceAtLeast(lineHeight)
        when (s.mode) {
            4 -> { s.x = (width - s.textWidth) / 2f; s.y = h - lineHeight; s.bornMs = nowMs; s.speed = 0f } // 底
            5 -> { s.x = (width - s.textWidth) / 2f; s.y = lineHeight; s.bornMs = nowMs; s.speed = 0f }       // 顶
            else -> { // 滚动
                val lane = pickLane()
                s.y = lineHeight * (lane + 1)
                s.x = width.toFloat()
                s.speed = (width + s.textWidth) / scrollDurationMs   // px/ms
                if (lane < laneFreeAtSize()) laneFreeAtArr()[lane] = nowMs + (s.textWidth / s.speed).toLong()
            }
        }
    }

    // 轨道分配（简单轮转 + 空闲优先）
    private var laneRR = 0
    private var _laneFree: LongArray = LongArray(0)
    private fun laneFreeAtArr(): LongArray {
        if (_laneFree.size != laneCount) _laneFree = LongArray(laneCount)
        return _laneFree
    }
    private fun laneFreeAtSize() = laneFreeAtArr().size
    private fun pickLane(): Int {
        val arr = laneFreeAtArr()
        for (i in arr.indices) if (nowMs >= arr[i]) return i
        laneRR = (laneRR + 1) % laneCount
        return laneRR
    }

    private fun recycle(s: Sprite) { if (pool.size < maxActive) pool.addLast(s) }

    private fun sp(v: Float) = v * resources.displayMetrics.scaledDensity
    private fun dp(v: Float) = v * resources.displayMetrics.density

    /** 池化精灵。 */
    private class Sprite {
        var text = ""; var x = 0f; var y = 0f; var speed = 0f
        var textWidth = 0f; var textSize = 0f; var color = Color.WHITE; var mode = 1; var bornMs = 0L
        fun set(item: DanmakuItem, ts: Float, w: Float, c: Int) {
            text = item.text; textSize = ts; textWidth = w; color = c; mode = item.mode; bornMs = 0
        }
        fun update(dtMs: Float) { if (speed != 0f) x -= speed * dtMs }
        fun isDead(w: Float, now: Long): Boolean =
            if (speed != 0f) x + textWidth < 0 else now - bornMs > 4000L
        fun draw(canvas: Canvas, fill: Paint, stroke: Paint, alpha: Int) {
            fill.textSize = textSize; stroke.textSize = textSize
            stroke.color = Color.BLACK; stroke.alpha = alpha
            canvas.drawText(text, x, y, stroke)
            fill.color = color; fill.alpha = alpha   // alpha 必须在 color 赋值后设置
            canvas.drawText(text, x, y, fill)
        }
    }
}
