package com.workcamera.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * 分享照片：先把照片复制到私有 cache/share，再通过 FileProvider 暴露给其它 App，
 * 这样不需要对方有「下载/工作相机」目录的读取权限。副本可被「清理缓存」清掉。
 */
object PhotoSharer {

    const val SHARE_DIR = "share"

    fun share(context: Context, photos: List<WorkPhoto>): Boolean {
        val uris = copyToShare(context, photos)
        if (uris.isEmpty()) return false
        return launchChooser(context, uris)
    }

    /** 复制到私有 cache/share（耗时操作，放后台线程调用） */
    fun copyToShare(context: Context, photos: List<WorkPhoto>): List<Uri> {
        if (photos.isEmpty()) return emptyList()
        return try {
            val dir = File(context.cacheDir, SHARE_DIR)
            clearShareDir(dir)
            dir.mkdirs()

            val uris = ArrayList<Uri>()
            for (photo in photos) {
                val target = File(dir, photo.name)
                var copied = false
                try {
                    context.contentResolver.openInputStream(photo.uri)?.use { input ->
                        target.outputStream().use { output -> input.copyTo(output) }
                        copied = true
                    }
                } catch (e: Exception) {
                    copied = false
                }
                if (!copied || !target.exists() || target.length() == 0L) {
                    val source = File(photo.path)
                    if (source.exists()) {
                        source.copyTo(target, overwrite = true)
                    }
                }
                if (target.exists() && target.length() > 0L) {
                    uris.add(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", target))
                }
            }
            uris
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** 拉起系统分享面板（主线程调用） */
    fun launchChooser(context: Context, uris: List<Uri>): Boolean {
        if (uris.isEmpty()) return false
        return try {
            val intent = if (uris.size == 1) {
                Intent(Intent.ACTION_SEND).apply {
                    type = "image/jpeg"
                    putExtra(Intent.EXTRA_STREAM, uris.first())
                }
            } else {
                Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = "image/jpeg"
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                }
            }
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(
                Intent.createChooser(intent, context.getString(R.string.share_title))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun clearShareDir(dir: File) {
        if (dir.isDirectory) {
            dir.listFiles()?.forEach { child ->
                if (child.isDirectory) child.deleteRecursively() else child.delete()
            }
        }
    }
}
