package com.stanislavlyalin.pomodoroapp

import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.max

class StatisticsActivity : AppCompatActivity() {
    private lateinit var repository: PomodoroRepository
    private lateinit var dateText: TextView
    private lateinit var previousDayButton: ImageButton
    private lateinit var nextDayButton: ImageButton
    private lateinit var chartScroll: HorizontalScrollView
    private lateinit var chartView: StatisticsBarChart
    private lateinit var timelineContainer: LinearLayout

    private val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale("ru"))
    private val timeFormat = SimpleDateFormat("HH:mm", Locale("ru"))
    private var selectedDayStartMillis = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_statistics)

        repository = PomodoroRepository(this)
        selectedDayStartMillis = getDayStartMillis(System.currentTimeMillis())

        initViews()
        setupChart()
        setupClickListeners()
        renderStatistics()
    }

    private fun initViews() {
        dateText = findViewById(R.id.statistics_date)
        previousDayButton = findViewById(R.id.previous_day_button)
        nextDayButton = findViewById(R.id.next_day_button)
        chartScroll = findViewById(R.id.chart_scroll)
        chartView = findViewById(R.id.statistics_chart)
        timelineContainer = findViewById(R.id.timeline_container)
    }

    private fun setupChart() {
        val axisColor = ContextCompat.getColor(this, R.color.timer_green)
        chartView.description.isEnabled = false
        chartView.legend.isEnabled = false
        chartView.setScaleEnabled(false)
        chartView.setPinchZoom(false)
        chartView.setDrawGridBackground(false)
        chartView.setDrawBarShadow(false)
        chartView.setDrawValueAboveBar(false)
        chartView.extraBottomOffset = 64f

        chartView.axisLeft.isEnabled = false
        chartView.axisRight.isEnabled = false
        chartView.xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM
            setDrawGridLines(false)
            setDrawLabels(false)
            axisLineColor = axisColor
            axisLineWidth = 3f
        }
    }

    private fun setupClickListeners() {
        previousDayButton.setOnClickListener {
            selectedDayStartMillis = addDays(selectedDayStartMillis, -1)
            renderStatistics()
        }
        nextDayButton.setOnClickListener {
            selectedDayStartMillis = addDays(selectedDayStartMillis, 1)
            renderStatistics()
        }
    }

    private fun renderStatistics() {
        val entries = repository.getPomodoroHistoryEntries()
            .filter { it.completedAtMillis in selectedDayStartMillis until addDays(selectedDayStartMillis, 1) }

        dateText.text = dateFormat.format(Date(selectedDayStartMillis))
        updateNavigationButtons()
        renderChart(entries)
        renderTimeline(entries)
    }

    private fun renderChart(entries: List<PomodoroHistoryEntry>) {
        val bars = buildBars(entries)
        val minChartWidth = max(resources.displayMetrics.widthPixels - chartScroll.paddingLeft - chartScroll.paddingRight, bars.size * dp(120))
        chartView.layoutParams = chartView.layoutParams.apply {
            width = minChartWidth
        }
        chartView.setStatisticsBars(bars)

        if (bars.isEmpty()) {
            chartView.clear()
            chartView.invalidate()
            return
        }

        val barEntries = bars.mapIndexed { index, bar ->
            BarEntry(index.toFloat(), (bar.totalDurationMillis / 60000L).toFloat())
        }
        val dataSet = BarDataSet(barEntries, "").apply {
            colors = bars.map { it.color }
            setDrawValues(false)
            isHighlightEnabled = false
        }

        chartView.data = BarData(dataSet).apply {
            barWidth = 0.58f
        }
        chartView.xAxis.axisMinimum = -0.5f
        chartView.xAxis.axisMaximum = bars.size - 0.5f
        chartView.axisLeft.axisMinimum = 0f
        chartView.axisLeft.axisMaximum = barEntries.maxOf { it.y }.coerceAtLeast(1f) * 1.08f
        chartView.invalidate()
        chartScroll.post { chartScroll.scrollTo(0, 0) }
    }

    private fun renderTimeline(entries: List<PomodoroHistoryEntry>) {
        timelineContainer.removeAllViews()

        if (entries.isEmpty()) {
            timelineContainer.addView(createTimelineText(getString(R.string.statistics_empty), ContextCompat.getColor(this, R.color.timer_green)))
            return
        }

        entries.sortedBy { it.completedAtMillis }.forEach { entry ->
            val startMillis = entry.completedAtMillis - entry.durationMillis
            val timeRange = "${timeFormat.format(Date(startMillis))} - ${timeFormat.format(Date(entry.completedAtMillis))}"
            val label = getTimelineLabel(entry)
            timelineContainer.addView(createTimelineText(createTimelineEntryText(timeRange, label, getEntryColor(entry))))
        }
    }

    private fun buildBars(entries: List<PomodoroHistoryEntry>): List<StatisticsBar> {
        if (entries.isEmpty()) {
            return emptyList()
        }

        if (!repository.pomodoroLabelsEnabled) {
            return listOf(
                StatisticsBar(
                    label = getString(R.string.statistics_unlabeled),
                    count = entries.size,
                    totalDurationMillis = entries.sumOf { it.durationMillis },
                    color = ContextCompat.getColor(this, R.color.timer_green)
                )
            )
        }

        return entries
            .groupBy { it.label.trim() }
            .map { (label, labelEntries) ->
                StatisticsBar(
                    label = label.ifEmpty { getString(R.string.statistics_without_label) },
                    count = labelEntries.size,
                    totalDurationMillis = labelEntries.sumOf { it.durationMillis },
                    color = getLabelColor(label)
                )
            }
            .sortedWith(
                compareByDescending<StatisticsBar> { it.totalDurationMillis }
                    .thenByDescending { it.count }
                    .thenBy { it.label.lowercase(Locale.ROOT) }
            )
    }

    private fun createTimelineText(text: CharSequence): TextView {
        return TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setText(text)
            textSize = 20f
            includeFontPadding = true
        }
    }

    private fun createTimelineText(text: String, color: Int): TextView {
        return createTimelineText(SpannableString(text).apply {
            setSpan(ForegroundColorSpan(color), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        })
    }

    private fun createTimelineEntryText(timeRange: String, label: String, labelColor: Int): SpannableString {
        val text = "$timeRange $label"
        return SpannableString(text).apply {
            setSpan(
                ForegroundColorSpan(ContextCompat.getColor(this@StatisticsActivity, R.color.timer_green)),
                0,
                timeRange.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            setSpan(
                ForegroundColorSpan(labelColor),
                timeRange.length + 1,
                text.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
    }

    private fun getTimelineLabel(entry: PomodoroHistoryEntry): String {
        if (!repository.pomodoroLabelsEnabled) {
            return getString(R.string.statistics_unlabeled_entry)
        }

        return entry.label.trim().ifEmpty { getString(R.string.statistics_without_label) }
    }

    private fun getEntryColor(entry: PomodoroHistoryEntry): Int {
        if (!repository.pomodoroLabelsEnabled) {
            return ContextCompat.getColor(this, R.color.timer_green)
        }

        return getLabelColor(entry.label)
    }

    private fun getLabelColor(label: String): Int {
        return repository.getPomodoroLabelColor(label)
            ?: ContextCompat.getColor(this, R.color.timer_green)
    }

    private fun updateNavigationButtons() {
        val todayStartMillis = getDayStartMillis(System.currentTimeMillis())
        val firstAvailableDayMillis = addDays(todayStartMillis, -STATISTICS_RETENTION_DAYS + 1)

        previousDayButton.isEnabled = selectedDayStartMillis > firstAvailableDayMillis
        previousDayButton.alpha = if (previousDayButton.isEnabled) 1f else 0.35f
        nextDayButton.isEnabled = selectedDayStartMillis < todayStartMillis
        nextDayButton.alpha = if (nextDayButton.isEnabled) 1f else 0.35f
    }

    private fun getDayStartMillis(timeMillis: Long): Long {
        return Calendar.getInstance().apply {
            timeInMillis = timeMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun addDays(timeMillis: Long, days: Int): Long {
        return Calendar.getInstance().apply {
            timeInMillis = timeMillis
            add(Calendar.DAY_OF_YEAR, days)
        }.timeInMillis
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    companion object {
        private const val STATISTICS_RETENTION_DAYS = 31
    }
}
