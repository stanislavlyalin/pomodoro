package com.stanislavlyalin.pomodoroapp

import android.content.Context
import android.media.MediaPlayer
import androidx.work.Worker
import androidx.work.WorkerParameters

class PomodoroWorker(context: Context, workerParams: WorkerParameters) :
    Worker(context, workerParams) {

    override fun doWork(): Result {
        // This function will be executed even if the application is unloaded
        playSound()
        incrementPomodoroCount()
        clearTimerState()
        return Result.success()
    }

    private fun playSound() {
        val mediaPlayer = MediaPlayer.create(applicationContext, R.raw.notification_sound)
        mediaPlayer.start()
    }

    private fun incrementPomodoroCount() {
        val sharedPreferences =
            applicationContext.getSharedPreferences(Constants.PREFERENCES, Context.MODE_PRIVATE)
        val pomodoroCount = sharedPreferences.getInt(Constants.POMODORO_COUNT_KEY, 0) + 1
        sharedPreferences.edit().putInt(Constants.POMODORO_COUNT_KEY, pomodoroCount).apply()
    }

    private fun clearTimerState() {
        val sharedPreferences =
            applicationContext.getSharedPreferences(Constants.PREFERENCES, Context.MODE_PRIVATE)
        sharedPreferences.withPrefs {
            it.remove(Constants.START_TIME_KEY)
            it.remove(Constants.WORK_REQUEST_ID_KEY)
        }
    }
}
