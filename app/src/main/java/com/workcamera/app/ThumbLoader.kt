package com.workcamera.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.util.Size
import android.widget.ImageView
import java.io.File
import java.util.concurrent.Executors

/** 相册缩略图与大图加载，带内存缓存，避免滚动卡顿 */
class ThumbLoader(private val context: Context) {

    private val executor = Executors.newFixedThreadPool(3)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val cache: LruCache<String, Bitmap>

    init {
        val maxMemoryKb = (Runtime.getRuntime().maxMemory() / 1024L / 7L).toInt()
        cache = object : LruCache<String, Bitmap>(maxMemoryKb) {
            override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
        }
    }

    fun loadThumb(photo: WorkPhoto, targetPx: Int, imageView: ImageView) {
        val key = photo.uri.toString() + "#" + targetPx
        imageView.tag = key
        val cached = cache.get(key)
        if (cached != null && !cached.isRecycled) {
            imageView.setImageBitmap(cached)
            return
        }
        imageView.setImageBitmap(null)
        executor.execute {
            val bitmap = decodeThumb(photo, targetPx)
            if (bitmap != null) cache.put(key, bitmap)
            mainHandler.post {
                if (imageView.tag == key && bitmap != null && !bitmap.isRecycled) {
                    imageView.setImageBitmap(bitmap)
                }
            }
        }
    }

    fun loadFull(photo: WorkPhoto, targetPx: Int, onLoaded: (Bitmap?) -> Unit) {
        executor.execute {
            val bitmap = decodeSampled(photo, targetPx)
            mainHandler.post { onLoaded(bitmap) }
        }
    }

    fun shutdown() {
        executor.shutdownNow()
    }

    private fun decodeThumb(photo: WorkPhoto, targetPx: Int): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                return context.contentResolver.loadThumbnail(photo.uri, Size(targetPx, targetPx), null)
            } catch (e: Exception) {
                // 走完整解码兜底
            }
        }
        return decodeSampled(photo, targetPx)
    }

    private fun decodeSampled(photo: WorkPhoto, targetPx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        var opened = false
        try {
            context.contentResolver.openInputStream(photo.uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
                opened = true
            }
        } catch (e: Exception) {
        }
        if (!opened || bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            val file = File(photo.path)
            if (photo.path.isNotBlank() && file.exists()) {
                BitmapFactory.decodeFile(photo.path, bounds)
            }
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > targetPx) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sample }

        try {
            context.contentResolver.openInputStream(photo.uri)?.use {
                return BitmapFactory.decodeStream(it, null, options)
            }
        } catch (e: Exception) {
        }
        return try {
            BitmapFactory.decodeFile(photo.path, options)
        } catch (e: Exception) {
            null
        }
    }
}
