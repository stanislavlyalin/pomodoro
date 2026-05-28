package com.stanislavlyalin.pomodoroapp

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.Locale

class PomodoroRepository(context: Context) {
    private val prefs = context.getSharedPreferences(Constants.PREFERENCES, Context.MODE_PRIVATE)

    val pomodoroDuration: Long
        get() = prefs.getLong(Constants.POMODORO_DURATION_KEY, 25 * 60 * 1000L)

    val totalPomodoros: Int
        get() = prefs.getInt(Constants.TOTAL_POMODOROS_KEY, 12)

    var pomodoroCount: Int
        get() = prefs.getInt(Constants.POMODORO_COUNT_KEY, 0)
        set(value) = prefs.withPrefs { it.putInt(Constants.POMODORO_COUNT_KEY, value) }

    var lastResetDay: Int
        get() = prefs.getInt(Constants.LAST_RESET_DAY_KEY, -1)
        set(value) = prefs.withPrefs { it.putInt(Constants.LAST_RESET_DAY_KEY, value) }

    val pomodoroLabelsEnabled: Boolean
        get() = prefs.getBoolean(Constants.POMODORO_LABELS_ENABLED_KEY, false)

    val lastPomodoroLabel: String
        get() = prefs.getString(Constants.LAST_POMODORO_LABEL_KEY, "") ?: ""

    fun isTimerActive(): Boolean {
        return getEndTime() > 0L
    }

    fun getStartTime(): Long = prefs.getLong(Constants.START_TIME_KEY, 0L)

    fun getEndTime(): Long {
        val endTime = prefs.getLong(Constants.END_TIME_KEY, 0L)
        if (endTime > 0L) {
            return endTime
        }

        val legacyStartTime = getStartTime()
        return if (legacyStartTime > 0L) legacyStartTime + pomodoroDuration else 0L
    }

    fun getRemainingTime(nowMillis: Long = System.currentTimeMillis()): Long {
        val endTime = getEndTime()
        return if (endTime > 0L) endTime - nowMillis else 0L
    }

    fun getPomodoroLabels(): List<String> {
        val labelsJson = prefs.getString(Constants.POMODORO_LABELS_KEY, null) ?: return emptyList()
        val labels = JSONArray(labelsJson)
        return List(labels.length()) { index -> labels.optString(index, "") }
    }

    fun getPomodoroHistoryEntries(
        nowMillis: Long = System.currentTimeMillis()
    ): List<PomodoroHistoryEntry> {
        val entries = getRawPomodoroHistoryEntries()
        val retainedEntries = entries.filter { it.completedAtMillis >= getHistoryCutoffMillis(nowMillis) }

        if (retainedEntries.size != entries.size) {
            prefs.withPrefs {
                it.putString(Constants.POMODORO_HISTORY_KEY, retainedEntries.toJsonArray().toString())
            }
        }

        return retainedEntries.sortedBy { it.completedAtMillis }
    }

    fun getSavedPomodoroLabels(): List<String> {
        val labelLastUsed = getPomodoroLabelLastUsed()
        return getSavedPomodoroLabelEntries()
            .mapIndexed { index, label ->
                SavedPomodoroLabel(
                    label = label,
                    lastUsedAt = labelLastUsed.optLong(label.pomodoroLabelKey(), 0L),
                    index = index
                )
            }
            .sortedWith(
                compareByDescending<SavedPomodoroLabel> { it.lastUsedAt }
                    .thenBy { it.index }
            )
            .map { it.label }
    }

    private fun getSavedPomodoroLabelEntries(): List<String> {
        val labelsJson = prefs.getString(Constants.SAVED_POMODORO_LABELS_KEY, null) ?: return emptyList()
        val labels = JSONArray(labelsJson)
        return List(labels.length()) { index -> labels.optString(index, "") }
            .map { it.normalizePomodoroLabel() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.pomodoroLabelKey() }
    }

    fun savePomodoroLabel(label: String) {
        val normalizedLabel = label.normalizePomodoroLabel()
        if (normalizedLabel.isEmpty()) {
            return
        }

        val savedLabels = getSavedPomodoroLabels()
        val labelExists = savedLabels.any { it.equals(normalizedLabel, ignoreCase = true) }
        ensurePomodoroLabelColor(normalizedLabel)
        if (labelExists) {
            return
        }

        prefs.withPrefs {
            it.putString(
                Constants.SAVED_POMODORO_LABELS_KEY,
                JSONArray(savedLabels + normalizedLabel).toString()
            )
        }
    }

    private fun recordPomodoroLabelUse(label: String, usedAtMillis: Long = System.currentTimeMillis()) {
        val normalizedLabel = label.normalizePomodoroLabel()
        if (normalizedLabel.isEmpty()) {
            return
        }

        val labelKey = normalizedLabel.pomodoroLabelKey()
        val savedLabels = getSavedPomodoroLabelEntries()
            .filterNot { it.pomodoroLabelKey() == labelKey }
        val labelLastUsed = getPomodoroLabelLastUsed().apply {
            put(labelKey, usedAtMillis)
        }

        ensurePomodoroLabelColor(normalizedLabel)
        prefs.withPrefs {
            it.putString(
                Constants.SAVED_POMODORO_LABELS_KEY,
                JSONArray(listOf(normalizedLabel) + savedLabels).toString()
            )
            it.putString(Constants.POMODORO_LABEL_LAST_USED_KEY, labelLastUsed.toString())
            it.putString(Constants.LAST_POMODORO_LABEL_KEY, normalizedLabel)
        }
    }

    fun getPomodoroLabelColor(label: String): Int? {
        val normalizedLabel = label.normalizePomodoroLabel()
        if (normalizedLabel.isEmpty()) {
            return null
        }

        return ensurePomodoroLabelColor(normalizedLabel)
    }

    private fun ensurePomodoroLabelColor(label: String): Int {
        val labelKey = label.pomodoroLabelKey()
        val labelColors = getPomodoroLabelColors()
        val existingColor = labelColors.optInt(labelKey, 0)
        if (existingColor != 0) {
            return existingColor
        }

        val color = generatePomodoroLabelColor(labelColors)
        labelColors.put(labelKey, color)
        prefs.withPrefs {
            it.putString(Constants.POMODORO_LABEL_COLORS_KEY, labelColors.toString())
        }

        return color
    }

    private fun getPomodoroLabelColors(): JSONObject {
        val colorsJson = prefs.getString(Constants.POMODORO_LABEL_COLORS_KEY, null) ?: return JSONObject()
        return JSONObject(colorsJson)
    }

    private fun getPomodoroLabelLastUsed(): JSONObject {
        val lastUsedJson = prefs.getString(Constants.POMODORO_LABEL_LAST_USED_KEY, null) ?: return JSONObject()
        return JSONObject(lastUsedJson)
    }

    fun clearDailyProgress() {
        prefs.withPrefs {
            it.putInt(Constants.POMODORO_COUNT_KEY, 0)
            it.remove(Constants.POMODORO_LABELS_KEY)
            it.remove(Constants.PENDING_POMODORO_LABEL_KEY)
        }
    }

    fun clearTimerState() {
        prefs.withCommittedPrefs {
            it.remove(Constants.START_TIME_KEY)
            it.remove(Constants.END_TIME_KEY)
            it.remove(Constants.PENDING_REQUEST_CODE_KEY)
            it.remove(Constants.PENDING_POMODORO_LABEL_KEY)
        }
    }

    fun startSession(startTime: Long, endTime: Long, label: String = "") {
        val normalizedLabel = label.normalizePomodoroLabel()
        prefs.withCommittedPrefs {
            it.putLong(Constants.START_TIME_KEY, startTime)
            it.putLong(Constants.END_TIME_KEY, endTime)
            it.putString(Constants.PENDING_POMODORO_LABEL_KEY, normalizedLabel)
        }
    }

    fun completeSession(newPomodoroCount: Int? = null) {
        prefs.withCommittedPrefs { editor ->
            editor.remove(Constants.START_TIME_KEY)
            editor.remove(Constants.END_TIME_KEY)
            editor.remove(Constants.PENDING_POMODORO_LABEL_KEY)
            if (newPomodoroCount != null) {
                editor.putInt(Constants.POMODORO_COUNT_KEY, newPomodoroCount)
            }
        }
    }

    fun completeActiveSession(): Boolean {
        if (!isTimerActive()) {
            return false
        }

        val completedAtMillis = System.currentTimeMillis()
        val currentCount = pomodoroCount
        val canAddPomodoro = currentCount < totalPomodoros
        val nextCount = if (canAddPomodoro) currentCount + 1 else currentCount
        val pendingLabel = prefs.getString(Constants.PENDING_POMODORO_LABEL_KEY, "") ?: ""
        val labels = getPomodoroLabels().toMutableList()
        val historyEntries = getPomodoroHistoryEntries(completedAtMillis).toMutableList()

        if (canAddPomodoro && pomodoroLabelsEnabled) {
            while (labels.size < currentCount) {
                labels.add("")
            }
            if (labels.size == currentCount) {
                labels.add(pendingLabel)
            } else {
                labels[currentCount] = pendingLabel
            }
        }

        if (canAddPomodoro) {
            historyEntries.add(
                PomodoroHistoryEntry(
                    completedAtMillis = completedAtMillis,
                    durationMillis = pomodoroDuration,
                    label = if (pomodoroLabelsEnabled) pendingLabel else ""
                )
            )
        }

        val completed = prefs.withCommittedPrefs { editor ->
            editor.remove(Constants.START_TIME_KEY)
            editor.remove(Constants.END_TIME_KEY)
            editor.remove(Constants.PENDING_REQUEST_CODE_KEY)
            editor.remove(Constants.PENDING_POMODORO_LABEL_KEY)
            if (canAddPomodoro) {
                editor.putInt(Constants.POMODORO_COUNT_KEY, nextCount)
                editor.putString(Constants.POMODORO_HISTORY_KEY, historyEntries.toJsonArray().toString())
            }
            if (canAddPomodoro && pomodoroLabelsEnabled) {
                editor.putString(Constants.POMODORO_LABELS_KEY, JSONArray(labels).toString())
            }
        }

        if (completed && canAddPomodoro && pomodoroLabelsEnabled) {
            recordPomodoroLabelUse(pendingLabel)
        }

        return completed
    }

    private fun getRawPomodoroHistoryEntries(): List<PomodoroHistoryEntry> {
        val historyJson = prefs.getString(Constants.POMODORO_HISTORY_KEY, null) ?: return emptyList()
        val entries = JSONArray(historyJson)
        return List(entries.length()) { index ->
            val entry = entries.optJSONObject(index) ?: JSONObject()
            PomodoroHistoryEntry(
                completedAtMillis = entry.optLong(HISTORY_COMPLETED_AT_KEY, 0L),
                durationMillis = entry.optLong(HISTORY_DURATION_KEY, pomodoroDuration),
                label = entry.optString(HISTORY_LABEL_KEY, "")
            )
        }
            .filter { it.completedAtMillis > 0L && it.durationMillis > 0L }
    }

    private fun List<PomodoroHistoryEntry>.toJsonArray(): JSONArray {
        val entries = JSONArray()
        forEach { entry ->
            entries.put(
                JSONObject()
                    .put(HISTORY_COMPLETED_AT_KEY, entry.completedAtMillis)
                    .put(HISTORY_DURATION_KEY, entry.durationMillis)
                    .put(HISTORY_LABEL_KEY, entry.label)
            )
        }
        return entries
    }

    private fun getHistoryCutoffMillis(nowMillis: Long): Long {
        return Calendar.getInstance().apply {
            timeInMillis = nowMillis
            add(Calendar.DAY_OF_YEAR, -HISTORY_RETENTION_DAYS + 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    companion object {
        private const val HISTORY_RETENTION_DAYS = 31
        private const val HISTORY_COMPLETED_AT_KEY = "completedAtMillis"
        private const val HISTORY_DURATION_KEY = "durationMillis"
        private const val HISTORY_LABEL_KEY = "label"
    }
}

data class PomodoroHistoryEntry(
    val completedAtMillis: Long,
    val durationMillis: Long,
    val label: String
)

private data class SavedPomodoroLabel(
    val label: String,
    val lastUsedAt: Long,
    val index: Int
)

private fun String.normalizePomodoroLabel(): String = trim().take(15)

private fun String.pomodoroLabelKey(): String = normalizePomodoroLabel().lowercase(Locale.ROOT)

private fun generatePomodoroLabelColor(existingColors: JSONObject): Int {
    val usedColors = existingColors.keys().asSequence()
        .map { key -> existingColors.optInt(key, 0) }
        .filter { it != 0 }
        .toSet()
    val availablePaletteColors = POMODORO_LABEL_COLOR_PALETTE.filterNot { it in usedColors }

    return availablePaletteColors.randomOrNull() ?: POMODORO_LABEL_COLOR_PALETTE.random()
}

private val POMODORO_LABEL_COLOR_PALETTE = intArrayOf(
    0xFFE67E22.toInt(),
    0xFF1B5E20.toInt(),
    0xFF1565C0.toInt(),
    0xFFC62828.toInt(),
    0xFF6A1B9A.toInt(),
    0xFF00838F.toInt(),
    0xFFAD1457.toInt(),
    0xFF2E7D32.toInt(),
    0xFFEF6C00.toInt(),
    0xFF283593.toInt(),
    0xFF5D4037.toInt(),
    0xFF455A64.toInt()
)
