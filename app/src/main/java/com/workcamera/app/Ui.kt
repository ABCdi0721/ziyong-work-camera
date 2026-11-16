package com.workcamera.app

import android.content.Context
import android.content.res.Resources
import android.graphics.drawable.GradientDrawable

fun Context.dp(value: Float): Int = (value * resources.displayMetrics.density).toInt()

fun Context.dpF(value: Float): Float = value * resources.displayMetrics.density

fun roundedDrawable(
    radiusDp: Float,
    fillColor: Int,
    strokeColor: Int? = null,
    strokeWidthDp: Float = 1f
): GradientDrawable {
    val density = Resources.getSystem().displayMetrics.density
    val drawable = GradientDrawable()
    drawable.shape = GradientDrawable.RECTANGLE
    drawable.cornerRadius = radiusDp * density
    drawable.setColor(fillColor)
    if (strokeColor != null) {
        drawable.setStroke((strokeWidthDp * density).toInt().coerceAtLeast(1), strokeColor)
    }
    return drawable
}
