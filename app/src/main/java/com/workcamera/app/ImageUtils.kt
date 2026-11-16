package com.workcamera.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import java.io.File

object ImageUtils {

    /**
     * 读取相机原图，按 EXIF 方向摆正，并限制最长边，避免大图合成时内存爆掉。
     */
    fun decodeForEdit(file: File, maxSide: Int = 3200): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val rawWidth = bounds.outWidth
        val rawHeight = bounds.outHeight
        if (rawWidth <= 0 || rawHeight <= 0) return null

        var sample = 1
        while (maxOf(rawWidth, rawHeight) / sample > maxSide) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val source = BitmapFactory.decodeFile(file.absolutePath, options) ?: return null
        Log.d("WorkCamera", "decodeForEdit raw=${rawWidth}x$rawHeight sample=$sample decoded=${source.width}x${source.height} mean=${String.format("%.1f", meanLuminance(source))}")

        val orientation = try {
            ExifInterface(file.absolutePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        } catch (e: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }

        val matrix = Matrix()
        Log.d("WorkCamera", "decodeForEdit orientation=$orientation")
        var swap = false
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> {
                // 旋转后原点会跑到画布外，必须再平移回来，否则成品是一片黑
                matrix.postRotate(90f)
                matrix.postTranslate(source.height.toFloat(), 0f)
                swap = true
            }
            ExifInterface.ORIENTATION_ROTATE_180 -> {
                matrix.postRotate(180f)
                matrix.postTranslate(source.width.toFloat(), source.height.toFloat())
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> {
                matrix.postRotate(270f)
                matrix.postTranslate(0f, source.width.toFloat())
                swap = true
            }
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> {
                matrix.postScale(-1f, 1f)
                matrix.postTranslate(source.width.toFloat(), 0f)
            }
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
                matrix.postScale(1f, -1f)
                matrix.postTranslate(0f, source.height.toFloat())
            }
        }

        if (orientation == ExifInterface.ORIENTATION_NORMAL || orientation == ExifInterface.ORIENTATION_UNDEFINED) {
            return source
        }

        val outWidth = if (swap) source.height else source.width
        val outHeight = if (swap) source.width else source.height
        val result = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888, true)
        Canvas(result).drawBitmap(source, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
        source.recycle()
        return result
    }

    fun scaleDown(source: Bitmap, maxSide: Int): Bitmap {
        val width = source.width
        val height = source.height
        val longer = maxOf(width, height)
        if (longer <= maxSide) return source
        val ratio = maxSide.toFloat() / longer
        val targetWidth = (width * ratio).toInt().coerceAtLeast(1)
        val targetHeight = (height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true)
    }

    /** 粗略采样位图平均亮度，用于诊断黑图出现在哪一步 */
    fun meanLuminance(bitmap: Bitmap): Double {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) return 0.0
        val stepX = maxOf(1, width / 48)
        val stepY = maxOf(1, height / 48)
        var sum = 0L
        var count = 0
        var y = 0
        while (y < height) {
            var x = 0
            while (x < width) {
                val pixel = bitmap.getPixel(x, y)
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                sum += (r * 30 + g * 59 + b * 11) / 100
                count++
                x += stepX
            }
            y += stepY
        }
        return if (count == 0) 0.0 else sum.toDouble() / count
    }

    fun recycleQuietly(bitmap: Bitmap?) {
        if (bitmap == null || bitmap.isRecycled) return
        try {
            bitmap.recycle()
        } catch (e: Exception) {
        }
    }

    /**
     * 采样整张照片亮度，判断是否“全黑”。
     * 相机尚未出帧（部分小米机型 HAL 常见）、镜头被遮挡、被隐私保护拦截时会得到近乎全黑的图。
     * 判定阈值刻意收紧，正常夜景照片有高光，不会被误判。
     */
    fun isNearlyBlack(file: File): Boolean {
        if (!file.exists() || file.length() == 0L) return true
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return true

            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 96) {
                sample *= 2
            }
            val options = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            val bitmap = BitmapFactory.decodeFile(file.absolutePath, options) ?: return true
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            bitmap.recycle()
            if (pixels.isEmpty()) return true

            var sum = 0L
            var maxLuminance = 0
            for (pixel in pixels) {
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                val luminance = (r * 30 + g * 59 + b * 11) / 100
                sum += luminance
                if (luminance > maxLuminance) maxLuminance = luminance
            }
            val mean = sum.toDouble() / pixels.size
            val black = mean < 6.0 && maxLuminance < 20
            Log.d("WorkCamera", "photo luminance mean=${String.format("%.1f", mean)} max=$maxLuminance black=$black")
            black
        } catch (e: Exception) {
            false
        }
    }
}
