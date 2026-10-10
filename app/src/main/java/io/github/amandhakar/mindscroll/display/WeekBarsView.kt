package io.github.amandhakar.mindscroll.display

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import io.github.amandhakar.mindscroll.data.DayTotal
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** Seven small bars with day letters; today, the last bar, in full color. For dark backgrounds. */
class WeekBarsView(context: Context) : View(context) {
    private var days: List<DayTotal> = emptyList()
    private var accent = Color.WHITE
    private val density = resources.displayMetrics.density
    private val bar = Paint(Paint.ANTI_ALIAS_FLAG)
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(150, 255, 255, 255)
        textSize = 10 * resources.displayMetrics.scaledDensity
        textAlign = Paint.Align.CENTER
    }
    private val rect = RectF()

    fun set(days: List<DayTotal>, accent: Int) {
        this.days = days
        this.accent = accent
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (days.isEmpty()) return
        val labelHeight = 14 * density
        val chartHeight = height - labelHeight - 2 * density
        val slot = width / days.size.toFloat()
        val barWidth = slot * 0.5f
        val max = days.maxOf { it.total }.coerceAtLeast(1)
        days.forEachIndexed { i, day ->
            val cx = slot * i + slot / 2
            val h = maxOf(3 * density, chartHeight * day.total / max)
            rect.set(cx - barWidth / 2, chartHeight - h, cx + barWidth / 2, chartHeight)
            bar.color = if (i == days.lastIndex) accent else Color.argb(80, 255, 255, 255)
            canvas.drawRoundRect(rect, 3 * density, 3 * density, bar)
            val letter = LocalDate.ofEpochDay(day.day).dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault())
            canvas.drawText(letter, cx, height - 2 * density, label)
        }
    }
}
