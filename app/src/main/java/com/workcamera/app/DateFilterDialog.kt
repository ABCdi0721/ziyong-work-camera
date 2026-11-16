package com.workcamera.app

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 日期区间筛选弹层，样式对齐参考图：开始日期 / 至 / 结束日期 + 区间日历 */
class DateFilterDialog(
    private val context: Context,
    private val initialStart: LocalDate?,
    private val initialEnd: LocalDate?,
    private val onApply: (LocalDate?, LocalDate?) -> Unit
) {

    private val dialog = Dialog(context)
    private val dateFormat = DateTimeFormatter.ofPattern("yyyy.MM.dd", Locale.CHINA)
    private var startValue: LocalDate? = initialStart
    private var endValue: LocalDate? = initialEnd

    fun show() {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_date_filter, null)
        dialog.setContentView(view)
        dialog.window?.let { window ->
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            window.setGravity(Gravity.BOTTOM)
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window.setDimAmount(0.42f)
        }
        dialog.setCanceledOnTouchOutside(true)

        view.findViewById<GlyphView>(R.id.dlgCloseGlyph).apply {
            style = GlyphView.Style.CLOSE
            tint = Color.parseColor("#6B7280")
        }

        val calendar = RangeCalendarView(context)
        view.findViewById<FrameLayout>(R.id.calendarHost).addView(
            calendar,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )
        calendar.setRange(initialStart, initialEnd, moveToStart = initialStart != null)
        calendar.onRangeChanged = { start, end ->
            startValue = start
            endValue = end
            updateFields(view)
        }
        updateFields(view)

        view.findViewById<View>(R.id.dlgClose).setOnClickListener { dialog.dismiss() }
        view.findViewById<View>(R.id.dlgClear).setOnClickListener {
            onApply(null, null)
            dialog.dismiss()
        }
        view.findViewById<View>(R.id.dlgConfirm).setOnClickListener {
            onApply(startValue, endValue)
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun updateFields(root: View) {
        val startField = root.findViewById<TextView>(R.id.dlgStartField)
        val endField = root.findViewById<TextView>(R.id.dlgEndField)
        val ink = Color.parseColor("#1B1F24")
        val faint = Color.parseColor("#9AA1AC")

        val start = startValue
        val end = endValue

        if (start == null) {
            startField.text = context.getString(R.string.date_start)
            startField.setTextColor(faint)
            startField.setBackgroundResource(R.drawable.bg_date_field)
        } else {
            startField.text = start.format(dateFormat)
            startField.setTextColor(ink)
            startField.setBackgroundResource(R.drawable.bg_date_field_active)
        }

        if (end == null) {
            endField.text = context.getString(R.string.date_end)
            endField.setTextColor(faint)
            endField.setBackgroundResource(R.drawable.bg_date_field)
        } else {
            endField.text = end.format(dateFormat)
            endField.setTextColor(ink)
            endField.setBackgroundResource(R.drawable.bg_date_field_active)
        }
    }
}
