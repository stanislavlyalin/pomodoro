package com.stanislavlyalin.pomodoroapp

import android.content.Context
import org.json.JSONArray

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

    fun clearDailyProgress() {
        prefs.withPrefs {
            it.putInt(Constants.POMODORO_COUNT_KEY, 0)
            it.remove(Constants.POMODORO_LABELS_KEY)
            it.remove(Constants.LAST_POMODORO_LABEL_KEY)
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
        prefs.withCommittedPrefs {
            it.putLong(Constants.START_TIME_KEY, startTime)
            it.putLong(Constants.END_TIME_KEY, endTime)
            it.putString(Constants.PENDING_POMODORO_LABEL_KEY, label)
            it.putString(Constants.LAST_POMODORO_LABEL_KEY, label)
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

        val currentCount = pomodoroCount
        val canAddPomodoro = currentCount < totalPomodoros
        val nextCount = if (canAddPomodoro) currentCount + 1 else currentCount
        val pendingLabel = prefs.getString(Constants.PENDING_POMODORO_LABEL_KEY, "") ?: ""
        val labels = getPomodoroLabels().toMutableList()

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

        prefs.withCommittedPrefs { editor ->
            editor.remove(Constants.START_TIME_KEY)
            editor.remove(Constants.END_TIME_KEY)
            editor.remove(Constants.PENDING_REQUEST_CODE_KEY)
            editor.remove(Constants.PENDING_POMODORO_LABEL_KEY)
            if (canAddPomodoro) {
                editor.putInt(Constants.POMODORO_COUNT_KEY, nextCount)
            }
            if (canAddPomodoro && pomodoroLabelsEnabled) {
                editor.putString(Constants.POMODORO_LABELS_KEY, JSONArray(labels).toString())
            }
        }

        return true
    }
}
