package com.stanislavlyalin.pomodoroapp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

class PomodoroTimerService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var repository: PomodoroRepository
    private val finishRunnable = Runnable { finishTimer() }

    override fun onCreate() {
        super.onCreate()
        repository = PomodoroRepository(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopTimer()
            ACTION_FINISH -> {
                startForeground(NOTIFICATION_ID, createRunningNotification())
                finishTimer()
            }
            else -> runTimer()
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacks(finishRunnable)
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (repository.isTimerActive()) {
            PomodoroAlarmScheduler.schedule(this, repository.getEndTime())
            runTimer()
        }
        super.onTaskRemoved(rootIntent)
    }

    private fun runTimer() {
        if (!repository.isTimerActive()) {
            stopSelf()
            return
        }

        startForeground(NOTIFICATION_ID, createRunningNotification())

        val remainingTime = repository.getRemainingTime()
        if (remainingTime <= 0L) {
            finishTimer()
            return
        }

        PomodoroAlarmScheduler.schedule(this, repository.getEndTime())
        handler.removeCallbacks(finishRunnable)
        handler.postDelayed(finishRunnable, remainingTime)
    }

    private fun stopTimer() {
        handler.removeCallbacks(finishRunnable)
        PomodoroAlarmScheduler.cancel(this)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun finishTimer() {
        handler.removeCallbacks(finishRunnable)
        PomodoroAlarmScheduler.cancel(this)

        val completed = repository.completeActiveSession()
        if (completed) {
            PomodoroNotifier.notifyTimerFinished(this)
            PomodoroAlarmSoundPlayer.play(this) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        } else {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun createRunningNotification(): Notification {
        createNotificationChannel()

        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.tomato_green)
            .setContentTitle(getString(R.string.timer_running_title))
            .setContentText(getString(R.string.timer_running_text))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(contentIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.timer_running_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.timer_running_channel_description)
            setSound(null, null)
            enableVibration(false)
        }

        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "pomodoro_timer_running_v1"
        private const val NOTIFICATION_ID = 2
        private const val ACTION_START = "com.stanislavlyalin.pomodoroapp.action.START_TIMER_SERVICE"
        private const val ACTION_STOP = "com.stanislavlyalin.pomodoroapp.action.STOP_TIMER_SERVICE"
        const val ACTION_FINISH = "com.stanislavlyalin.pomodoroapp.action.FINISH_TIMER_SERVICE"

        fun start(context: Context) {
            val intent = Intent(context, PomodoroTimerService::class.java).apply {
                action = ACTION_START
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, PomodoroTimerService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun finish(context: Context) {
            val intent = Intent(context, PomodoroTimerService::class.java).apply {
                action = ACTION_FINISH
            }
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
