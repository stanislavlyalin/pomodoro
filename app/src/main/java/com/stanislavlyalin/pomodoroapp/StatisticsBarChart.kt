package com.stanislavlyalin.pomodoroapp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.utils.MPPointD

data class StatisticsBar(
    val label: String,
    val count: Int,
    val totalDurationMillis: Long,
    val color: Int
)

class StatisticsBarChart @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : BarChart(context, attrs, defStyleAttr) {
    private val bars = mutableListOf<StatisticsBar>()
    private val tomatoBitmap: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.tomato_red)
    private val tomatoDst = RectF()
    private val pillRect = RectF()
    private val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        alpha = 245
    }
    private val pillStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(95, 125, 183, 105)
        style = Paint.Style.STROKE
        strokeWidth = dp(1).toFloat()
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
    }
    private val labelTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = dp(10).toFloat()
        typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
    }

    fun setStatisticsBars(newBars: List<StatisticsBar>) {
        bars.clear()
        bars.addAll(newBars)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (bars.isEmpty() || data == null) {
            return
        }

        bars.forEachIndexed { index, bar ->
            drawBarContent(canvas, index, bar)
        }
    }

    private fun drawBarContent(canvas: Canvas, index: Int, bar: StatisticsBar) {
        val topPoint = getChartPoint(index.toFloat(), bar.totalDurationMillis / 60000f)
        val bottomPoint = getChartPoint(index.toFloat(), 0f)
        val barHeight = bottomPoint.y - topPoint.y
        val centerX = topPoint.x.toFloat()
        val topY = topPoint.y.toFloat()

        val tomatoSize = dp(24).toFloat()
        val countTextSize = dp(16).toFloat()
        val durationTextSize = dp(15).toFloat()
        val rowTop = topY + dp(12)

        textPaint.textSize = countTextSize
        textPaint.color = android.graphics.Color.rgb(13, 85, 34)
        val countText = bar.count.toString()
        val countWidth = textPaint.measureText(countText)
        val combinedWidth = countWidth + dp(6) + tomatoSize
        val tomatoLeft = centerX - combinedWidth / 2f
        val countX = tomatoLeft + tomatoSize + dp(6) + countWidth / 2f
        val countY = rowTop + tomatoSize / 2f - (textPaint.descent() + textPaint.ascent()) / 2f

        pillRect.set(
            centerX - combinedWidth / 2f - dp(10),
            rowTop - dp(4),
            centerX + combinedWidth / 2f + dp(10),
            rowTop + tomatoSize + dp(4)
        )
        canvas.drawRoundRect(pillRect, dp(8).toFloat(), dp(8).toFloat(), pillPaint)
        canvas.drawRoundRect(pillRect, dp(8).toFloat(), dp(8).toFloat(), pillStrokePaint)
        canvas.drawText(countText, countX, countY, textPaint)
        tomatoDst.set(tomatoLeft, rowTop, tomatoLeft + tomatoSize, rowTop + tomatoSize)
        canvas.drawBitmap(tomatoBitmap, null, tomatoDst, null)

        textPaint.textSize = durationTextSize
        val durationY = rowTop + tomatoSize + dp(22)
        val durationFitsInside = durationY + textPaint.descent() < bottomPoint.y - dp(4)
        val fallbackY = (topY - dp(6)).coerceAtLeast(viewPortHandler.contentTop() + dp(16))
        val textY = if (barHeight > dp(62) && durationFitsInside) durationY else fallbackY
        textPaint.color = if (textY == fallbackY) bar.color else android.graphics.Color.WHITE
        canvas.drawText(formatDuration(bar.totalDurationMillis), centerX, textY, textPaint)
        textPaint.color = android.graphics.Color.WHITE

        drawLabel(canvas, bar, centerX, viewPortHandler.contentBottom() + dp(34))
    }

    private fun drawLabel(canvas: Canvas, bar: StatisticsBar, centerX: Float, y: Float) {
        labelTextPaint.color = bar.color
        splitLabel(bar.label).forEachIndexed { index, line ->
            canvas.drawText(line, centerX, y + index * dp(14), labelTextPaint)
        }
    }

    private fun getChartPoint(x: Float, y: Float): MPPointD {
        return getTransformer(YAxis.AxisDependency.LEFT).getPixelForValues(x, y)
    }

    private fun formatDuration(durationMillis: Long): String {
        val totalMinutes = durationMillis / 60000L
        val hours = totalMinutes / 60L
        val minutes = totalMinutes % 60L
        return "${hours}ч ${minutes}м"
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

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}
