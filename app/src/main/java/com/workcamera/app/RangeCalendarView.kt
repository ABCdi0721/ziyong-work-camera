package com.workcamera.app

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

/**
 * 日期区间日历：顶部「年月 + < 今日 >」，下方 6 周日期网格。
 * 选择逻辑与常见区间筛选一致：第一次点选开始日期，第二次点选结束日期。
 */
class RangeCalendarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    var onRangeChanged: ((LocalDate?, LocalDate?) -> Unit)? = null

    private var month: YearMonth = YearMonth.now()
    private var startDate: LocalDate? = null
    private var endDate: LocalDate? = null

    private val monthLabel = TextView(context)
    private val todayView = TextView(context)
    private val cells = ArrayList<TextView>(42)

    private val ink = Color.parseColor("#1B1F24")
    private val faint = Color.parseColor("#C5CAD3")
    private val sub = Color.parseColor("#9AA1AC")
    private val accent = Color.parseColor("#2F7CF6")
    private val accentSoft = Color.parseColor("#EAF2FF")
    private val accentInk = Color.parseColor("#1F63D2")

    init {
        orientation = VERTICAL

        val header = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        monthLabel.apply {
            setTextColor(ink)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        header.addView(monthLabel, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))

        header.addView(iconButton(GlyphView.Style.CHEVRON_LEFT) {
            month = month.minusMonths(1)
            render()
        })

        todayView.apply {
            text = "今日"
            setTextColor(accent)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            gravity = Gravity.CENTER
            setPadding(context.dp(8f), context.dp(5f), context.dp(8f), context.dp(5f))
            setOnClickListener { goToday() }
        }
        header.addView(todayView, LayoutParams(LayoutParams.WRAP_CONTENT, context.dp(30f)))

        header.addView(iconButton(GlyphView.Style.CHEVRON_RIGHT) {
            month = month.plusMonths(1)
            render()
        })

        addView(header, LayoutParams(LayoutParams.MATCH_PARENT, context.dp(36f)))

        val weekdayRow = LinearLayout(context).apply { orientation = HORIZONTAL }
        val weekNames = arrayOf("一", "二", "三", "四", "五", "六", "日")
        for (name in weekNames) {
            val tv = TextView(context).apply {
                text = name
                gravity = Gravity.CENTER
                setTextColor(sub)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            }
            weekdayRow.addView(tv, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        }
        addView(weekdayRow, LayoutParams(LayoutParams.MATCH_PARENT, context.dp(28f)))

        val grid = LinearLayout(context).apply { orientation = VERTICAL }
        for (r in 0 until 6) {
            val row = LinearLayout(context).apply { orientation = HORIZONTAL }
            for (c in 0 until 7) {
                val cell = TextView(context).apply {
                    gravity = Gravity.CENTER
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
                    isClickable = true
                    isFocusable = true
                }
                val lp = LayoutParams(0, context.dp(40f), 1f)
                lp.setMargins(context.dp(2f), context.dp(2f), context.dp(2f), context.dp(2f))
                row.addView(cell, lp)
                cells.add(cell)
            }
            grid.addView(row, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        }
        addView(grid, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        render()
    }

    private fun iconButton(style: GlyphView.Style, onClick: () -> Unit): FrameLayout {
        val box = FrameLayout(context).apply {
            layoutParams = LayoutParams(context.dp(30f), context.dp(30f))
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }
        val glyph = GlyphView(context).apply {
            this.style = style
            tint = Color.parseColor("#5B6270")
        }
        box.addView(glyph, FrameLayout.LayoutParams(context.dp(18f), context.dp(18f), Gravity.CENTER))
        return box
    }

    private fun goToday() {
        month = YearMonth.now()
        startDate = LocalDate.now()
        endDate = startDate
        render()
        onRangeChanged?.invoke(startDate, endDate)
    }

    private fun onDayClicked(date: LocalDate) {
        val s = startDate
        val e = endDate
        when {
            s == null || e != null -> {
                startDate = date
                endDate = null
            }
            date.isBefore(s) -> {
                startDate = date
                endDate = null
            }
            else -> endDate = date
        }
        render()
        onRangeChanged?.invoke(startDate, endDate)
    }

    private fun render() {
        monthLabel.text = String.format(Locale.CHINA, "%d年 %d月", month.year, month.monthValue)
        val offset = month.atDay(1).dayOfWeek.value - 1
        val daysInMonth = month.lengthOfMonth()
        val prevMonth = month.minusMonths(1)
        val prevDays = prevMonth.lengthOfMonth()
        val nextMonth = month.plusMonths(1)
        val today = LocalDate.now()

        for (i in 0 until 42) {
            val date: LocalDate = when {
                i < offset -> prevMonth.atDay(prevDays - offset + i + 1)
                i >= offset + daysInMonth -> nextMonth.atDay(i - offset - daysInMonth + 1)
                else -> month.atDay(i - offset + 1)
            }
            val cell = cells[i]
            cell.text = date.dayOfMonth.toString()
            applyCellStyle(cell, date, today)
            cell.setOnClickListener { onDayClicked(date) }
        }
    }

    private fun applyCellStyle(cell: TextView, date: LocalDate, today: LocalDate) {
        val inMonth = YearMonth.from(date) == month
        val isStart = startDate == date
        val isEnd = endDate == date
        val s = startDate
        val e = endDate
        val inRange = s != null && e != null && !date.isBefore(s) && !date.isAfter(e)

        when {
            isStart || isEnd -> {
                cell.setTextColor(Color.WHITE)
                cell.background = roundedDrawable(9f, accent)
            }
            inRange -> {
                cell.setTextColor(accentInk)
                cell.background = roundedDrawable(9f, accentSoft)
            }
            date == today -> {
                cell.setTextColor(accent)
                cell.background = roundedDrawable(9f, Color.WHITE, accent, 1.2f)
            }
            else -> {
                cell.setTextColor(if (inMonth) ink else faint)
                cell.background = null
            }
        }
    }

    fun setRange(start: LocalDate?, end: LocalDate?, moveToStart: Boolean = true) {
        startDate = start
        endDate = end
        month = when {
            moveToStart && start != null -> YearMonth.from(start)
            else -> YearMonth.now()
        }
        render()
    }

    fun clearRange() {
        startDate = null
        endDate = null
        month = YearMonth.now()
        render()
        onRangeChanged?.invoke(null, null)
    }

    fun currentStart(): LocalDate? = startDate

    fun currentEnd(): LocalDate? = endDate
}
