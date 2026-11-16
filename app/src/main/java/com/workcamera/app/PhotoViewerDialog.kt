package com.workcamera.app

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 大图查看：显示时间、地点与文件位置，可删除、可分享 */
class PhotoViewerDialog(
    private val context: Context,
    private val photo: WorkPhoto,
    private val loader: ThumbLoader,
    private val onDelete: (WorkPhoto) -> Unit,
    private val onShare: (WorkPhoto) -> Unit
) {

    private val dialog = Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
    private val timeFormat = SimpleDateFormat("yyyy年MM月dd日 HH:mm", Locale.CHINA)

    fun show() {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_photo_viewer, null)
        dialog.setContentView(view)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.BLACK))

        view.findViewById<GlyphView>(R.id.viewerCloseGlyph).apply {
            style = GlyphView.Style.CLOSE
            tint = Color.WHITE
        }
        view.findViewById<TextView>(R.id.viewerTime).text = timeFormat.format(Date(photo.takenAt))
        view.findViewById<TextView>(R.id.viewerLocation).text =
            photo.location.ifBlank { "未记录拍摄地点" }
        view.findViewById<TextView>(R.id.viewerPath).text =
            photo.path.ifBlank { WorkCameraStorage.relativeFolder() }

        view.findViewById<View>(R.id.viewerClose).setOnClickListener { dialog.dismiss() }
        view.findViewById<View>(R.id.viewerDone).setOnClickListener { dialog.dismiss() }
        view.findViewById<View>(R.id.viewerDelete).setOnClickListener { confirmDelete() }
        view.findViewById<View>(R.id.viewerShare).setOnClickListener { onShare(photo) }

        val metrics = context.resources.displayMetrics
        val target = maxOf(metrics.widthPixels, metrics.heightPixels)
        loader.loadFull(photo, target) { bitmap ->
            if (bitmap != null) {
                view.findViewById<ImageView>(R.id.viewerImage).setImageBitmap(bitmap)
            }
        }

        dialog.show()
    }

    private fun confirmDelete() {
        AlertDialog.Builder(context)
            .setMessage(R.string.delete_confirm)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                dialog.dismiss()
                onDelete(photo)
            }
            .show()
    }
}
