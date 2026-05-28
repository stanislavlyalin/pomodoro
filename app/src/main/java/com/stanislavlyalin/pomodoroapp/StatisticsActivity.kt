package com.stanislavlyalin.pomodoroapp

import android.os.Bundle
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
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
    private lateinit var chartView: StatisticsChartView
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
        chartView.setBars(bars)
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
            val label = getTimelineLabel(entry)
            val text = "${timeFormat.format(Date(startMillis))} - ${timeFormat.format(Date(entry.completedAtMillis))} $label"
            timelineContainer.addView(createTimelineText(text, getEntryColor(entry)))
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

    private fun createTimelineText(text: String, color: Int): TextView {
        return TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setText(text)
            setTextColor(color)
            textSize = 23f
            includeFontPadding = true
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
