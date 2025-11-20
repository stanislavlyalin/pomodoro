package com.stanislavlyalin.pomodoroapp

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

class AlarmReceiver : BroadcastReceiver() {
    companion object {
        private const val CHANNEL_ID = "pomodoro_notification_channel"
        private const val NOTIFICATION_ID = 1
    }

    override fun onReceive(context: Context, intent: Intent) {
        val repository = PomodoroRepository(context)

        if (repository.isTimerActive()) {
            incrementPomodoroCount(context)
            showNotification(context)
            clearTimerState(context)
        }
    }

    private fun showNotification(context: Context) {
        val manager = ContextCompat.getSystemService(context, NotificationManager::class.java)

        createNotificationChannel(context, manager)

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.tomato_red)
            .setContentTitle(context.getString(R.string.pomodoro_finished_title))
            .setContentText(context.getString(R.string.pomodoro_finished_text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        manager?.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel(context: Context, manager: NotificationManager?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val soundUri =
                Uri.parse("android.resource://" + context.packageName + "/" + R.raw.notification_sound)

            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.channel_description)
                setSound(soundUri, attributes)
                enableVibration(true)
            }
            manager?.createNotificationChannel(channel)
        }
    }

    private fun incrementPomodoroCount(context: Context) {
        val sharedPreferences =
            context.getSharedPreferences(Constants.PREFERENCES, Context.MODE_PRIVATE)
        val pomodoroCount = sharedPreferences.getInt(Constants.POMODORO_COUNT_KEY, 0) + 1
        sharedPreferences.withPrefs { it.putInt(Constants.POMODORO_COUNT_KEY, pomodoroCount) }
    }

    private fun clearTimerState(context: Context) {
        val repository = PomodoroRepository(context)
        repository.clearTimerState()
    }
}
