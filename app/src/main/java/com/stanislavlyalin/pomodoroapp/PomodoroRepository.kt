package com.stanislavlyalin.pomodoroapp

import android.content.Context

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

    fun getStartTime(): Long = prefs.getLong(Constants.START_TIME_KEY, 0L)

    fun startSession(startTime: Long) {
        prefs.withPrefs { it.putLong(Constants.START_TIME_KEY, startTime) }
    }

    fun completeSession(newPomodoroCount: Int? = null) {
        prefs.withPrefs { editor ->
            editor.remove(Constants.START_TIME_KEY)
            if (newPomodoroCount != null) {
                editor.putInt(Constants.POMODORO_COUNT_KEY, newPomodoroCount)
            }
        }
    }
}
