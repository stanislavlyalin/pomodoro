package com.stanislavlyalin.pomodoroapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != PomodoroAlarmScheduler.ACTION_TIMER_FINISHED) {
            return
        }

        val repository = PomodoroRepository(context)
        val timerReachedEnd = repository.getRemainingTime() <= 0L

        if (repository.isTimerActive() && timerReachedEnd) {
            PomodoroTimerService.finish(context)
        } else if (repository.isTimerActive()) {
            PomodoroAlarmScheduler.schedule(context, repository.getEndTime())
            PomodoroTimerService.start(context)
        }
    }
}
