package com.stanislavlyalin.pomodoroapp

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
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
import com.github.mikephil.charting.formatter.ValueFormatter
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max

class StatisticsActivity : AppCompatActivity() {
    private lateinit var repository: PomodoroRepository
    private lateinit var dateText: TextView
    private lateinit var previousDayButton: ImageButton
    private lateinit var nextDayButton: ImageButton
    private lateinit var totalText: TextView
    private lateinit var chartScroll: HorizontalScrollView
    private lateinit var chartView: StatisticsBarChart
    private lateinit var timelineContainer: LinearLayout
    private lateinit var homeButton: View
    private lateinit var settingsButton: View

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
        totalText = findViewById(R.id.statistics_total)
        chartScroll = findViewById(R.id.chart_scroll)
        chartView = findViewById(R.id.statistics_chart)
        timelineContainer = findViewById(R.id.timeline_container)
        homeButton = findViewById(R.id.home_button)
        settingsButton = findViewById(R.id.settings_button)
    }

    private fun setupChart() {
        val axisColor = ContextCompat.getColor(this, R.color.timer_green)
        val gridColor = ContextCompat.getColor(this, R.color.surface_line)
        val textColor = ContextCompat.getColor(this, R.color.text_secondary_green)
        chartView.description.isEnabled = false
        chartView.legend.isEnabled = false
        chartView.setScaleEnabled(false)
        chartView.setPinchZoom(false)
        chartView.setDrawGridBackground(false)
        chartView.setDrawBarShadow(false)
        chartView.setDrawValueAboveBar(false)
        chartView.setNoDataText(getString(R.string.statistics_empty))
        chartView.setNoDataTextColor(axisColor)
        chartView.extraBottomOffset = 70f
        chartView.extraLeftOffset = 8f

        chartView.axisLeft.apply {
            isEnabled = true
            axisMinimum = 0f
            setTextColor(textColor)
            textSize = 12f
            setDrawAxisLine(false)
            setDrawGridLines(true)
            setGridColor(gridColor)
            setGridLineWidth(1f)
            enableGridDashedLine(8f, 8f, 0f)
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return "${value.toInt()}м"
                }
            }
        }
        chartView.axisRight.isEnabled = false
        chartView.xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM
            setDrawGridLines(false)
            setDrawLabels(false)
            axisLineColor = axisColor
            axisLineWidth = 2.4f
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
        homeButton.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
        settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
            finish()
        }
    }

    private fun renderStatistics() {
        val entries = repository.getPomodoroHistoryEntries()
            .filter { it.completedAtMillis in selectedDayStartMillis until addDays(selectedDayStartMillis, 1) }

        dateText.text = dateFormat.format(Date(selectedDayStartMillis))
        updateNavigationButtons()
        totalText.text = getString(
            R.string.statistics_total,
            formatDuration(entries.sumOf { it.durationMillis })
        )
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
        val maxMinutes = barEntries.maxOf { it.y }.coerceAtLeast(15f)
        val axisMax = (ceil(maxMinutes / 15f) * 15f).coerceAtLeast(15f)
        chartView.axisLeft.axisMaximum = axisMax
        chartView.axisLeft.granularity = 15f
        chartView.axisLeft.setLabelCount((axisMax / 15f).toInt() + 1, true)
        chartView.invalidate()
        chartScroll.post { chartScroll.scrollTo(0, 0) }
    }

    private fun renderTimeline(entries: List<PomodoroHistoryEntry>) {
        timelineContainer.removeAllViews()

        if (entries.isEmpty()) {
            timelineContainer.addView(createEmptyTimelineText())
            return
        }

        val sortedEntries = entries.sortedBy { it.completedAtMillis }
        sortedEntries.forEachIndexed { index, entry ->
            timelineContainer.addView(createTimelineRow(index + 1, entry))
            if (index != sortedEntries.lastIndex) {
                timelineContainer.addView(createDivider())
            }
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

    private fun createEmptyTimelineText(): TextView {
        return TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            text = getString(R.string.statistics_empty)
            setTextColor(ContextCompat.getColor(this@StatisticsActivity, R.color.timer_green))
            textSize = 14f
            includeFontPadding = true
            gravity = Gravity.CENTER
            setPadding(0, dp(18), 0, dp(10))
        }
    }

    private fun createTimelineRow(position: Int, entry: PomodoroHistoryEntry): LinearLayout {
        val startMillis = entry.completedAtMillis - entry.durationMillis
        val timeRange = "${timeFormat.format(Date(startMillis))} - ${timeFormat.format(Date(entry.completedAtMillis))}"
        val label = getTimelineLabel(entry)

        return LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(8), 0, dp(8))

            addView(TextView(this@StatisticsActivity).apply {
                layoutParams = LinearLayout.LayoutParams(dp(34), dp(34))
                background = ContextCompat.getDrawable(this@StatisticsActivity, R.drawable.bg_timeline_index)
                gravity = Gravity.CENTER
                text = position.toString()
                setTextColor(ContextCompat.getColor(this@StatisticsActivity, R.color.text_primary_green))
                textSize = 13f
            })

            addView(TextView(this@StatisticsActivity).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.05f).apply {
                    marginStart = dp(16)
                }
                text = timeRange
                setTextColor(ContextCompat.getColor(this@StatisticsActivity, R.color.text_secondary_green))
                textSize = 13f
            })

            addView(TextView(this@StatisticsActivity).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dp(12)
                }
                text = label
                setTextColor(getEntryColor(entry))
                textSize = 13f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            })
        }
    }

    private fun createDivider(): View {
        return View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(1)
            )
            background = ContextCompat.getDrawable(this@StatisticsActivity, R.drawable.bg_soft_divider)
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

    private fun formatDuration(durationMillis: Long): String {
        val totalMinutes = durationMillis / 60000L
        val hours = totalMinutes / 60L
        val minutes = totalMinutes % 60L
        return if (hours > 0) {
            "${hours}ч ${minutes}м"
        } else {
            "${minutes}м"
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    companion object {
        private const val STATISTICS_RETENTION_DAYS = 31
    }
}
