package com.stanislavlyalin.pomodoroapp

import android.os.CountDownTimer

enum class TimerState {
    IDLE, RUNNING
}

class PomodoroTimer(private val listener: TimerListener) {

    var state: TimerState = TimerState.IDLE
        private set

    private var systemTimer: CountDownTimer? = null

    fun start(durationMillis: Long) {
        if (state == TimerState.RUNNING) return

        systemTimer?.cancel()

        state = TimerState.RUNNING

        systemTimer = object : CountDownTimer(durationMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                listener.onTick(millisUntilFinished)
            }

            override fun onFinish() {
                state = TimerState.IDLE
                listener.onFinish()
            }
        }.start()
    }

    fun stop() {
        systemTimer?.cancel()
        systemTimer = null
        state = TimerState.IDLE
    }
}
