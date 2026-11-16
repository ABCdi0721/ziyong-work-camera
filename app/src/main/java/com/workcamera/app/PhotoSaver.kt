package com.workcamera.app

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 把成品 JPEG 写入手机「下载/工作相机」。
 * Android 10+ 走 MediaStore（无需存储权限），Android 8/9 直接写公共下载目录。
 */
object PhotoSaver {

    private val fileStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
    private val exifStamp = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US)

    fun save(context: Context, bitmap: Bitmap, takenAt: Long, location: String): Uri? {
        val name = buildName(takenAt)
        val temp = File(context.cacheDir, "wc_export_${System.currentTimeMillis()}.jpg")
        try {
            FileOutputStream(temp).use { out ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)) return null
            }
            writeExif(temp, takenAt, location)
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveToDownloads(context, temp, name, takenAt)
            } else {
                saveLegacy(context, temp, name)
            }
            if (uri != null) {
                PhotoIndex.put(context, name, location.ifBlank { "位置未知" })
            }
            return uri
        } catch (e: Exception) {
            return null
        } finally {
            temp.delete()
        }
    }

    private fun buildName(takenAt: Long): String {
        val millis = String.format(Locale.US, "%03d", takenAt % 1000L)
        return WorkCameraStorage.PREFIX + fileStamp.format(Date(takenAt)) + "_" + millis + ".jpg"
    }

    private fun writeExif(file: File, takenAt: Long, location: String) {
        try {
            val exif = ExifInterface(file.absolutePath)
            exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, exifStamp.format(Date(takenAt)))
            exif.setAttribute(
                ExifInterface.TAG_USER_COMMENT,
                PhotoRepository.escapeUnicode(location.ifBlank { "位置未知" })
            )
            exif.saveAttributes()
        } catch (e: Exception) {
            // EXIF 写入失败不影响照片
        }
    }

    private fun saveToDownloads(context: Context, source: File, name: String, takenAt: Long): Uri? {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, WorkCameraStorage.relativeFolder())
            put(MediaStore.MediaColumns.DATE_TAKEN, takenAt)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        if (uri == null) {
            // 部分 ROM 不支持 Downloads 集合，退回 Files 集合
            return saveViaFiles(context, source, name, takenAt)
        }
        try {
            resolver.openOutputStream(uri)?.use { output ->
                FileInputStream(source).use { input -> input.copyTo(output) }
            } ?: return null
            val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)
            return uri
        } catch (e: Exception) {
            try {
                resolver.delete(uri, null, null)
            } catch (ignored: Exception) {
            }
            return null
        }
    }

    private fun saveViaFiles(context: Context, source: File, name: String, takenAt: Long): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, WorkCameraStorage.relativeFolder())
            put(MediaStore.MediaColumns.DATE_TAKEN, takenAt)
        }
        val uri = context.contentResolver.insert(MediaStore.Files.getContentUri("external"), values) ?: return null
        context.contentResolver.openOutputStream(uri)?.use { output ->
            FileInputStream(source).use { input -> input.copyTo(output) }
        }
        return uri
    }

    @Suppress("DEPRECATION")
    private fun saveLegacy(context: Context, source: File, name: String): Uri? {
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), WorkCameraStorage.FOLDER)
        if (!dir.exists() && !dir.mkdirs()) return null
        val target = File(dir, name)
        try {
            FileInputStream(source).use { input ->
                FileOutputStream(target).use { output -> input.copyTo(output) }
            }
            MediaScannerConnection.scanFile(context, arrayOf(target.absolutePath), arrayOf("image/jpeg"), null)
            return Uri.fromFile(target)
        } catch (e: Exception) {
            return null
        }
    }
}
