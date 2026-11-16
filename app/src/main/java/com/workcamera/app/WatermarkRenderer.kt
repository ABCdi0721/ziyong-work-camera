package com.workcamera.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 把拍摄时间与拍摄地点直接烧录到照片左下角。
 * 尺寸随图片短边等比缩放，任何分辨率下比例一致。
 */
object WatermarkRenderer {

    private val dateFormat = SimpleDateFormat("yyyy.MM.dd", Locale.CHINA)
    private val weekFormat = SimpleDateFormat("EEE", Locale.CHINA)
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.CHINA)

    fun render(source: Bitmap, takenAt: Long, location: String): Bitmap {
        if (source.width <= 0 || source.height <= 0) return source
        val bitmap: Bitmap = if (source.isMutable) {
            source
        } else {
            source.copy(Bitmap.Config.ARGB_8888, true) ?: return source
        }

        val canvas = Canvas(bitmap)
        val width = bitmap.width.toFloat()
        val height = bitmap.height.toFloat()
        val shortSide = minOf(width, height)

        val margin = shortSide * 0.042f
        val paddingH = shortSide * 0.022f
        val paddingV = shortSide * 0.018f
        val barWidth = shortSide * 0.0055f
        val barGap = shortSide * 0.014f
        val gap = shortSide * 0.008f

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = shortSide * 0.031f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(236, 255, 255, 255)
            textSize = shortSide * 0.0205f
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        }

        val date = Date(takenAt)
        val title = dateFormat.format(date) + "  " + weekFormat.format(date) + "  " + timeFormat.format(date)
        var subtitle = location.ifBlank { "位置未知" }

        val maxTextWidth = width * 0.68f
        var attempts = 0
        while (subPaint.measureText(subtitle) > maxTextWidth && subPaint.textSize > shortSide * 0.0135f && attempts < 12) {
            subPaint.textSize = subPaint.textSize * 0.93f
            attempts++
        }
        if (subPaint.measureText(subtitle) > maxTextWidth) {
            var text = subtitle
            while (text.length > 3 && subPaint.measureText(text + "…") > maxTextWidth) {
                text = text.substring(0, text.length - 1)
            }
            subtitle = text + "…"
        }

        val titleMetrics = titlePaint.fontMetrics
        val subMetrics = subPaint.fontMetrics
        val titleHeight = titleMetrics.descent - titleMetrics.ascent
        val subHeight = subMetrics.descent - subMetrics.ascent

        val textHeight = titleHeight + gap + subHeight
        val textWidth = maxOf(titlePaint.measureText(title), subPaint.measureText(subtitle))
        val cardWidth = paddingH * 2f + barWidth + barGap + textWidth
        val cardHeight = paddingV * 2f + textHeight

        val left = margin
        val bottom = height - margin
        val right = minOf(left + cardWidth, width - margin)
        val top = bottom - cardHeight

        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(112, 0, 0, 0) }
        canvas.drawRoundRect(
            RectF(left, top, right, bottom),
            shortSide * 0.010f,
            shortSide * 0.010f,
            cardPaint
        )

        val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#4FA3FF") }
        canvas.drawRoundRect(
            RectF(left + paddingH, top + paddingV, left + paddingH + barWidth, top + paddingV + textHeight),
            barWidth / 2f,
            barWidth / 2f,
            barPaint
        )

        val textX = left + paddingH + barWidth + barGap
        val titleBaseline = top + paddingV - titleMetrics.ascent
        canvas.drawText(title, textX, titleBaseline, titlePaint)

        val subBaseline = titleBaseline + titleMetrics.descent + gap - subMetrics.ascent
        canvas.drawText(subtitle, textX, subBaseline, subPaint)

        return bitmap
    }
}
