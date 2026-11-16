package com.workcamera.app

import android.net.Uri

/** 保存位置约定：手机「下载」目录下的「工作相机」文件夹 */
object WorkCameraStorage {
    const val FOLDER = "工作相机"
    const val PREFIX = "WCAM_"

    fun relativeFolder(): String = "Download/$FOLDER"
}

data class WorkPhoto(
    val id: Long,
    val uri: Uri,
    val path: String,
    val name: String,
    val takenAt: Long,
    val size: Long,
    val location: String
)
