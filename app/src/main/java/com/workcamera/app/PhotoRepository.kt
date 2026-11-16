package com.workcamera.app

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import java.io.File

/**
 * 只读取「下载/工作相机」文件夹里、且由本相机命名的照片，
 * 因此相册不会混入系统相机或其它 App 的图片。
 */
class PhotoRepository(private val context: Context) {

    fun loadAll(): List<WorkPhoto> {
        val items = ArrayList<WorkPhoto>()
        val collection = MediaStore.Files.getContentUri("external")
        val columns = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.DATE_ADDED,
            MediaStore.Files.FileColumns.SIZE
        )

        val selection: String
        val args: Array<String>
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // 下载目录里的文件在媒体库中 media_type 可能为 0，因此不按类型过滤，
            // 只用「本相机目录 + 本相机文件前缀」保证只显示本 App 拍的照片。
            selection = MediaStore.Files.FileColumns.RELATIVE_PATH + " LIKE ? AND " +
                MediaStore.Files.FileColumns.DISPLAY_NAME + " LIKE ?"
            args = arrayOf(
                WorkCameraStorage.relativeFolder() + "/%",
                WorkCameraStorage.PREFIX + "%"
            )
        } else {
            selection = MediaStore.Files.FileColumns.DATA + " LIKE ? AND " +
                MediaStore.Files.FileColumns.DISPLAY_NAME + " LIKE ?"
            args = arrayOf(
                "%/" + WorkCameraStorage.relativeFolder() + "/%",
                WorkCameraStorage.PREFIX + "%"
            )
        }

        try {
            context.contentResolver.query(
                collection,
                columns,
                selection,
                args,
                MediaStore.Files.FileColumns.DATE_ADDED + " DESC"
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(0)
                    val path = cursor.getString(1) ?: ""
                    val name = cursor.getString(2) ?: continue
                    val addedAt = cursor.getLong(3) * 1000L
                    val size = cursor.getLong(4)
                    val uri = MediaStore.Files.getContentUri("external", id)
                    val location = resolveLocation(name, uri, null)
                    items.add(WorkPhoto(id, uri, path, name, addedAt, size, location))
                }
            }
        } catch (e: Exception) {
            // 没有读取权限时可能抛异常，下面用目录扫描兜底
        }

        if (items.isEmpty()) {
            items.addAll(scanFolder())
        }
        return items
    }

    /** 媒体库不可用时直接扫描文件夹（本 App 创建的文件可被自己读取） */
    @Suppress("DEPRECATION")
    private fun scanFolder(): List<WorkPhoto> {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            WorkCameraStorage.FOLDER
        )
        if (!dir.isDirectory) return emptyList()
        val files = dir.listFiles { file ->
            file != null && file.isFile && file.name.startsWith(WorkCameraStorage.PREFIX) &&
                file.name.endsWith(".jpg", ignoreCase = true)
        } ?: return emptyList()

        return files.sortedByDescending { it.lastModified() }.map { file ->
            val uri = Uri.fromFile(file)
            WorkPhoto(
                id = 0L,
                uri = uri,
                path = file.absolutePath,
                name = file.name,
                takenAt = file.lastModified(),
                size = file.length(),
                location = resolveLocation(file.name, uri, file)
            )
        }
    }

    private fun resolveLocation(name: String, uri: Uri, file: File?): String {
        val cached = PhotoIndex.locationOf(context, name)
        if (!cached.isNullOrBlank()) return cached
        val fromExif = if (file != null && file.exists()) {
            readExifComment(file)
        } else {
            readExifComment(uri)
        }
        if (fromExif.isNotBlank()) {
            PhotoIndex.put(context, name, fromExif)
        }
        return fromExif
    }

    private fun readExifComment(uri: Uri): String {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val exif = ExifInterface(input)
                unescapeUnicode(exif.getAttribute(ExifInterface.TAG_USER_COMMENT)?.trim().orEmpty())
            } ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    private fun readExifComment(file: File): String {
        return try {
            val exif = ExifInterface(file.absolutePath)
            unescapeUnicode(exif.getAttribute(ExifInterface.TAG_USER_COMMENT)?.trim().orEmpty())
        } catch (e: Exception) {
            ""
        }
    }

    fun delete(photo: WorkPhoto): Boolean {
        return try {
            val removed = if (photo.uri.scheme == "file") {
                val file = File(photo.path)
                file.exists() && file.delete()
            } else {
                context.contentResolver.delete(photo.uri, null, null) > 0
            }
            if (removed) {
                PhotoIndex.remove(context, photo.name)
                val file = File(photo.path)
                if (file.exists()) file.delete()
            }
            removed
        } catch (e: Exception) {
            false
        }
    }

    companion object {
        /** Exif 注释只写 ASCII，中文以 \\uXXXX 形式存储，读取时还原。 */
        fun escapeUnicode(text: String): String {
            val sb = StringBuilder()
            for (ch in text) {
                if (ch.code < 128) sb.append(ch)
                else sb.append(String.format("\\u%04x", ch.code))
            }
            return sb.toString()
        }

        fun unescapeUnicode(text: String): String {
            if (!text.contains("\\u")) return text
            val sb = StringBuilder()
            var i = 0
            while (i < text.length) {
                if (text[i] == '\\' && i + 5 < text.length && text[i + 1] == 'u') {
                    val hex = text.substring(i + 2, i + 6)
                    val code = hex.toIntOrNull(16)
                    if (code != null) {
                        sb.append(code.toChar())
                        i += 6
                        continue
                    }
                }
                sb.append(text[i])
                i++
            }
            return sb.toString()
        }
    }
}
