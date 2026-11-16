package com.workcamera.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * 极简线条图标：全部用 Canvas 几何绘制，避免多份矢量资源。
 */
class GlyphView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class Style {
        BOLT, BOLT_OFF, BOLT_AUTO, SWITCH, GRID, GALLERY,
        SEARCH, CALENDAR, CLOSE, CHEVRON_LEFT, CHEVRON_RIGHT, LOCATION,
        SHARE, SETTINGS, CHECK
    }

    var style: Style = Style.BOLT
        set(value) {
            field = value
            invalidate()
        }

    var tint: Int = Color.WHITE
        set(value) {
            field = value
            invalidate()
        }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val path = Path()
    private val rect = RectF()

    override fun onDraw(canvas: Canvas) {
        val size = min(width, height).toFloat()
        if (size <= 0f) return
        val cx = width / 2f
        val cy = height / 2f
        val u = size / 24f
        paint.color = tint
        paint.strokeWidth = 1.8f * u
        fillPaint.color = tint

        when (style) {
            Style.BOLT -> drawBolt(canvas, cx, cy, u, true, false)
            Style.BOLT_AUTO -> drawBolt(canvas, cx, cy, u, false, false)
            Style.BOLT_OFF -> drawBolt(canvas, cx, cy, u, false, true)
            Style.SWITCH -> drawSwitch(canvas, cx, cy, u)
            Style.GRID -> drawGrid(canvas, cx, cy, u)
            Style.GALLERY -> drawGallery(canvas, cx, cy, u)
            Style.SEARCH -> drawSearch(canvas, cx, cy, u)
            Style.CALENDAR -> drawCalendar(canvas, cx, cy, u)
            Style.CLOSE -> drawClose(canvas, cx, cy, u)
            Style.CHEVRON_LEFT -> drawChevron(canvas, cx, cy, u, true)
            Style.CHEVRON_RIGHT -> drawChevron(canvas, cx, cy, u, false)
            Style.LOCATION -> drawLocation(canvas, cx, cy, u)
            Style.SHARE -> drawShare(canvas, cx, cy, u)
            Style.SETTINGS -> drawSettings(canvas, cx, cy, u)
            Style.CHECK -> drawCheck(canvas, cx, cy, u)
        }
    }

    private fun px(v: Float, cx: Float, u: Float) = cx + (v - 12f) * u
    private fun py(v: Float, cy: Float, u: Float) = cy + (v - 12f) * u

    private fun drawBolt(canvas: Canvas, cx: Float, cy: Float, u: Float, filled: Boolean, slashed: Boolean) {
        path.reset()
        path.moveTo(px(13f, cx, u), py(2f, cy, u))
        path.lineTo(px(4.2f, cx, u), py(13f, cy, u))
        path.lineTo(px(10.8f, cx, u), py(13f, cy, u))
        path.lineTo(px(9.2f, cx, u), py(22f, cy, u))
        path.lineTo(px(19.8f, cx, u), py(10.4f, cy, u))
        path.lineTo(px(13f, cx, u), py(10.4f, cy, u))
        path.close()
        paint.style = if (filled) Paint.Style.FILL else Paint.Style.STROKE
        canvas.drawPath(path, paint)
        paint.style = Paint.Style.STROKE
        if (slashed) {
            canvas.drawLine(px(3f, cx, u), py(3f, cy, u), px(21f, cx, u), py(21f, cy, u), paint)
        }
    }

    private fun drawSwitch(canvas: Canvas, cx: Float, cy: Float, u: Float) {
        val r = 7.4f * u
        rect.set(cx - r, cy - r, cx + r, cy + r)
        canvas.drawArc(rect, 180f, 140f, false, paint)
        canvas.drawArc(rect, 0f, 140f, false, paint)
        drawArrowHead(canvas, cx, cy, r, 320f, u)
        drawArrowHead(canvas, cx, cy, r, 140f, u)
    }

    private fun drawArrowHead(canvas: Canvas, cx: Float, cy: Float, r: Float, angleDeg: Float, u: Float) {
        val rad = Math.toRadians(angleDeg.toDouble())
        val sx = cx + (r * cos(rad)).toFloat()
        val sy = cy + (r * sin(rad)).toFloat()
        val tx = (-sin(rad)).toFloat()
        val ty = cos(rad).toFloat()
        val nx = cos(rad).toFloat()
        val ny = sin(rad).toFloat()
        path.reset()
        path.moveTo(sx + tx * 2.6f * u, sy + ty * 2.6f * u)
        path.lineTo(sx + nx * 1.5f * u, sy + ny * 1.5f * u)
        path.lineTo(sx - nx * 1.5f * u, sy - ny * 1.5f * u)
        path.close()
        canvas.drawPath(path, fillPaint)
    }

    private fun drawGrid(canvas: Canvas, cx: Float, cy: Float, u: Float) {
        val half = 8.2f * u
        rect.set(cx - half, cy - half, cx + half, cy + half)
        canvas.drawRoundRect(rect, 2f * u, 2f * u, paint)
        for (i in 1..2) {
            val d = -half + (2f * half) * i / 3f
            canvas.drawLine(cx + d, cy - half, cx + d, cy + half, paint)
            canvas.drawLine(cx - half, cy + d, cx + half, cy + d, paint)
        }
    }

    private fun drawGallery(canvas: Canvas, cx: Float, cy: Float, u: Float) {
        rect.set(cx - 8.6f * u, cy - 6.6f * u, cx + 8.6f * u, cy + 6.8f * u)
        canvas.drawRoundRect(rect, 1.8f * u, 1.8f * u, paint)
        canvas.drawCircle(cx - 4.6f * u, cy - 2.8f * u, 1.3f * u, paint)
        path.reset()
        path.moveTo(cx - 7.8f * u, cy + 5.4f * u)
        path.lineTo(cx - 2.4f * u, cy - 1.2f * u)
        path.lineTo(cx + 2f * u, cy + 5.4f * u)
        canvas.drawPath(path, paint)
    }

    private fun drawSearch(canvas: Canvas, cx: Float, cy: Float, u: Float) {
        canvas.drawCircle(cx - 2.2f * u, cy - 2.4f * u, 5.2f * u, paint)
        canvas.drawLine(cx + 1.6f * u, cy + 1.5f * u, cx + 8.4f * u, cy + 8.4f * u, paint)
    }

    private fun drawCalendar(canvas: Canvas, cx: Float, cy: Float, u: Float) {
        rect.set(cx - 8.6f * u, cy - 7f * u, cx + 8.6f * u, cy + 7.4f * u)
        canvas.drawRoundRect(rect, 2f * u, 2f * u, paint)
        canvas.drawLine(cx - 8.6f * u, cy - 2.8f * u, cx + 8.6f * u, cy - 2.8f * u, paint)
        canvas.drawLine(cx - 4.4f * u, cy - 9.6f * u, cx - 4.4f * u, cy - 4.6f * u, paint)
        canvas.drawLine(cx + 4.4f * u, cy - 9.6f * u, cx + 4.4f * u, cy - 4.6f * u, paint)
        canvas.drawCircle(cx - 3.6f * u, cy + 1.8f * u, 1.1f * u, fillPaint)
        canvas.drawCircle(cx + 3.6f * u, cy + 1.8f * u, 1.1f * u, fillPaint)
    }

    private fun drawClose(canvas: Canvas, cx: Float, cy: Float, u: Float) {
        canvas.drawLine(cx - 6f * u, cy - 6f * u, cx + 6f * u, cy + 6f * u, paint)
        canvas.drawLine(cx + 6f * u, cy - 6f * u, cx - 6f * u, cy + 6f * u, paint)
    }

    private fun drawChevron(canvas: Canvas, cx: Float, cy: Float, u: Float, left: Boolean) {
        val w = 3.2f * u
        val h = 7f * u
        if (left) {
            canvas.drawLine(cx + w, cy - h, cx - w, cy, paint)
            canvas.drawLine(cx - w, cy, cx + w, cy + h, paint)
        } else {
            canvas.drawLine(cx - w, cy - h, cx + w, cy, paint)
            canvas.drawLine(cx + w, cy, cx - w, cy + h, paint)
        }
    }

    private fun drawLocation(canvas: Canvas, cx: Float, cy: Float, u: Float) {
        canvas.drawCircle(cx, cy - 3.2f * u, 4.4f * u, paint)
        path.reset()
        path.moveTo(cx - 2.9f * u, cy + 0.4f * u)
        path.lineTo(cx, cy + 8.4f * u)
        path.lineTo(cx + 2.9f * u, cy + 0.4f * u)
        canvas.drawPath(path, paint)
    }

    /** 分享：三个节点用连线连起来（Android 经典分享图标） */
    private fun drawShare(canvas: Canvas, cx: Float, cy: Float, u: Float) {
        val r = 2.1f * u
        val left = floatArrayOf(cx - 6.4f * u, cy)
        val topRight = floatArrayOf(cx + 6.4f * u, cy - 5.2f * u)
        val bottomRight = floatArrayOf(cx + 6.4f * u, cy + 5.2f * u)
        canvas.drawLine(left[0], left[1], topRight[0], topRight[1], paint)
        canvas.drawLine(left[0], left[1], bottomRight[0], bottomRight[1], paint)
        canvas.drawCircle(left[0], left[1], r, paint)
        canvas.drawCircle(topRight[0], topRight[1], r, paint)
        canvas.drawCircle(bottomRight[0], bottomRight[1], r, paint)
    }

    /** 设置：中心圆点加一圈齿轮刻度 */
    private fun drawSettings(canvas: Canvas, cx: Float, cy: Float, u: Float) {
        val rows = floatArrayOf(cy - 5.6f * u, cy, cy + 5.6f * u)
        for (y in rows) {
            canvas.drawLine(cx - 7.6f * u, y, cx + 7.6f * u, y, paint)
        }
        val knobs = floatArrayOf(3.8f, -3.8f, 4.8f)
        paint.style = Paint.Style.FILL
        for (i in rows.indices) {
            canvas.drawCircle(cx + knobs[i] * u, rows[i], 2.4f * u, paint)
        }
        paint.style = Paint.Style.STROKE
    }

    private fun drawCheck(canvas: Canvas, cx: Float, cy: Float, u: Float) {
        canvas.drawLine(cx - 6f * u, cy + 0.4f * u, cx - 1.8f * u, cy + 4.6f * u, paint)
        canvas.drawLine(cx - 1.8f * u, cy + 4.6f * u, cx + 6.4f * u, cy - 4.6f * u, paint)
    }
}
