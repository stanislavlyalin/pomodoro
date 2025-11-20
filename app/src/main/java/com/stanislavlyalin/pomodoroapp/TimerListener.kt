package com.stanislavlyalin.pomodoroapp

interface TimerListener {
    fun onTick(millisUntilFinished: Long)
    fun onFinish()
}
