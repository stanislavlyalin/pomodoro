package com.stanislavlyalin.pomodoroapp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import kotlin.math.max

data class StatisticsBar(
    val label: String,
    val count: Int,
    val totalDurationMillis: Long,
    val color: Int
)

class StatisticsChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    private val bars = mutableListOf<StatisticsBar>()
    private val tomatoBitmap: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.tomato_red)
    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.timer_green)
        strokeWidth = dp(4).toFloat()
        strokeCap = Paint.Cap.ROUND
    }
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val whiteTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, android.R.color.white)
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.SERIF, android.graphics.Typeface.BOLD)
    }
    private val labelTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = dp(24).toFloat()
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.SERIF, android.graphics.Typeface.NORMAL)
    }
    private val tomatoDst = RectF()

    fun setBars(newBars: List<StatisticsBar>) {
        bars.clear()
        bars.addAll(newBars)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val baselineY = height - dp(82).toFloat()
        canvas.drawLine(dp(4).toFloat(), baselineY, width - dp(4).toFloat(), baselineY, axisPaint)

        if (bars.isEmpty()) {
            return
        }

        val maxDurationMillis = bars.maxOf { it.totalDurationMillis }.coerceAtLeast(1L)
        val slotWidth = width.toFloat() / bars.size
        val barWidth = minOf(dp(96).toFloat(), slotWidth * 0.58f)
        val topPadding = dp(28).toFloat()
        val maxBarHeight = max(dp(90).toFloat(), baselineY - topPadding)

        bars.forEachIndexed { index, bar ->
            val centerX = slotWidth * index + slotWidth / 2f
            val barHeight = max(dp(72).toFloat(), maxBarHeight * bar.totalDurationMillis / maxDurationMillis)
            val left = centerX - barWidth / 2f
            val top = baselineY - barHeight
            val right = centerX + barWidth / 2f

            barPaint.color = bar.color
            canvas.drawRect(left, top, right, baselineY, barPaint)

            drawBarText(canvas, bar, centerX, top, barWidth)
            drawLabel(canvas, bar, centerX, baselineY + dp(36))
        }
    }

    private fun drawBarText(canvas: Canvas, bar: StatisticsBar, centerX: Float, top: Float, barWidth: Float) {
        val tomatoSize = minOf(dp(52).toFloat(), barWidth * 0.58f)
        whiteTextPaint.textSize = dp(24).toFloat()

        val countText = bar.count.toString()
        val countWidth = whiteTextPaint.measureText(countText)
        val combinedWidth = countWidth + dp(7) + tomatoSize
        val countX = centerX - combinedWidth / 2f + countWidth / 2f
        val tomatoLeft = centerX - combinedWidth / 2f + countWidth + dp(7)
        val rowTop = top + dp(22)
        val countY = rowTop + tomatoSize / 2f - (whiteTextPaint.descent() + whiteTextPaint.ascent()) / 2f

        canvas.drawText(countText, countX, countY, whiteTextPaint)
        tomatoDst.set(tomatoLeft, rowTop, tomatoLeft + tomatoSize, rowTop + tomatoSize)
        canvas.drawBitmap(tomatoBitmap, null, tomatoDst, null)

        whiteTextPaint.textSize = dp(23).toFloat()
        canvas.drawText(formatDuration(bar.totalDurationMillis), centerX, rowTop + tomatoSize + dp(36), whiteTextPaint)
    }

    private fun drawLabel(canvas: Canvas, bar: StatisticsBar, centerX: Float, y: Float) {
        labelTextPaint.color = bar.color
        val labelLines = splitLabel(bar.label)
        labelLines.forEachIndexed { index, line ->
            canvas.drawText(line, centerX, y + index * dp(30), labelTextPaint)
        }
    }

    private fun splitLabel(label: String): List<String> {
        val trimmed = label.trim()
        if (trimmed.length <= 8) {
            return listOf(trimmed)
        }

        val preferredBreak = trimmed.indices
            .filter { index ->
                index in (trimmed.length - 8)..8 &&
                    index > 0 &&
                    index < trimmed.lastIndex &&
                    (trimmed[index] == ' ' || trimmed[index] == '-')
            }
            .lastOrNull()

        val breakIndex = preferredBreak ?: 8
        val secondLineStart = if (preferredBreak != null) breakIndex + 1 else breakIndex
        return listOf(
            trimmed.substring(0, breakIndex).trimEnd(' ', '-'),
            trimmed.substring(secondLineStart).trimStart(' ', '-')
        )
    }

    private fun formatDuration(durationMillis: Long): String {
        val totalMinutes = durationMillis / 60000L
        val hours = totalMinutes / 60L
        val minutes = totalMinutes % 60L
        return "${hours}ч ${minutes}м"
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}
