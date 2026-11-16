package com.workcamera.app

import android.content.Context
import android.text.format.Formatter
import java.io.File

/**
 * 只清理 App 自己的缓存目录（缩略图/拍照临时文件/分享副本），
 * 绝不触碰「下载/工作相机」里的照片。
 */
object CacheCleaner {

    fun totalBytes(context: Context): Long =
        sizeOf(context.cacheDir) + sizeOf(context.externalCacheDir)

    /** @return 实际释放的字节数 */
    fun clear(context: Context): Long {
        val before = totalBytes(context)
        clearDir(context.cacheDir)
        clearDir(context.externalCacheDir)
        val after = totalBytes(context)
        val freed = before - after
        return if (freed > 0L) freed else 0L
    }

    fun format(context: Context, bytes: Long): String =
        Formatter.formatShortFileSize(context, bytes)

    private fun sizeOf(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0L
        if (dir.isFile) return dir.length()
        return dir.listFiles()?.sumOf { sizeOf(it) } ?: 0L
    }

    private fun clearDir(dir: File?) {
        if (dir == null || !dir.isDirectory) return
        dir.listFiles()?.forEach { child ->
            try {
                if (child.isDirectory) child.deleteRecursively() else child.delete()
            } catch (e: Exception) {
            }
        }
    }
}
