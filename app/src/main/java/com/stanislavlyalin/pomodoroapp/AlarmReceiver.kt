package com.stanislavlyalin.pomodoroapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        playSound(context)
        incrementPomodoroCount(context)
        clearTimerState(context)
    }

    private fun playSound(context: Context) {
        val mediaPlayer = MediaPlayer.create(context, R.raw.notification_sound)
        mediaPlayer.start()
    }

    private fun incrementPomodoroCount(context: Context) {
        val sharedPreferences =
            context.getSharedPreferences(Constants.PREFERENCES, Context.MODE_PRIVATE)
        val pomodoroCount = sharedPreferences.getInt(Constants.POMODORO_COUNT_KEY, 0) + 1
        sharedPreferences.withPrefs { it.putInt(Constants.POMODORO_COUNT_KEY, pomodoroCount) }
    }

    private fun clearTimerState(context: Context) {
        val sharedPreferences =
            context.getSharedPreferences(Constants.PREFERENCES, Context.MODE_PRIVATE)
        sharedPreferences.withPrefs {
            it.remove(Constants.START_TIME_KEY)
            it.remove(Constants.PENDING_REQUEST_CODE_KEY)
        }
    }
}
