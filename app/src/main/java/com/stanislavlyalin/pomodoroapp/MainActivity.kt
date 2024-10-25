package com.stanislavlyalin.pomodoroapp

import android.content.SharedPreferences
import android.media.MediaPlayer
import android.os.Bundle
import android.os.CountDownTimer
import android.widget.Button
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.setMargins
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import java.util.Calendar
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.math.min

class MainActivity : AppCompatActivity() {

    private lateinit var timerText: TextView
    private lateinit var startButton: Button
    private lateinit var tomatoGrid: GridLayout
    private val tomatoImages = mutableListOf<ImageView>()
    private lateinit var sharedPreferences: SharedPreferences

    private var pomodoroCount = 0
    private val totalPomodoros = 12                 // Easily change the number of tomatoes here
    private val pomodoroDuration = 25 * 60 * 1000L  // 25 minutes in milliseconds
    private var startTime: Long = 0L
    private var timer: CountDownTimer? = null
    private var lastResetDay: Int = -1
    private var workRequestId: UUID? = null         // Background timer ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Setting up the layout
        setContentView(R.layout.activity_main)

        // Initializing UI elements
        timerText = findViewById(R.id.timer_text)
        startButton = findViewById(R.id.start_button)
        tomatoGrid = findViewById(R.id.tomato_grid)

        // Setting the number of columns in GridLayout based on totalPomodoros
        tomatoGrid.columnCount = 6 // Can be adjusted according to your preferences

        // Initializing SharedPreferences
        sharedPreferences = getSharedPreferences(Constants.PREFERENCES, MODE_PRIVATE)

        // Loading saved data
        pomodoroCount = sharedPreferences.getInt(Constants.POMODORO_COUNT_KEY, 0)
        lastResetDay = sharedPreferences.getInt(Constants.LAST_RESET_DAY_KEY, -1)
        sharedPreferences.getString(Constants.WORK_REQUEST_ID_KEY, null)?.let {
            workRequestId = UUID.fromString(it)
        }
        val currentTime = System.currentTimeMillis()
        startTime = sharedPreferences.getLong(Constants.START_TIME_KEY, currentTime)
        val remainingTime = min(pomodoroDuration, pomodoroDuration - (currentTime - startTime))

        timerText.text = formatPomodoroDuration(pomodoroDuration)

        checkForDayReset()
        generateTomatoImages()
        updateTomatoes()

        if (remainingTime in 1..<pomodoroDuration) {
            // Restore and continue timer
            resumeTimer(remainingTime)

            // If we launched a program with a background timer running, stop it
            workRequestId?.let {
                WorkManager.getInstance(this).cancelWorkById(it)
            }
        } else {
            timerText.text = formatPomodoroDuration(pomodoroDuration)
        }

        startButton.setOnClickListener {
            if (timer == null) {
                startTimer()
            } else {
                showEarlyFinishDialog()
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        if (timer != null) {
            val currentTime = System.currentTimeMillis()
            val remainingDuration = startTime + pomodoroDuration - currentTime
            startTimerWithWorkManager(remainingDuration)
        }
    }

    private fun checkForDayReset() {
        val calendar = Calendar.getInstance()
        val currentDay = calendar.get(Calendar.DAY_OF_YEAR)

        if (currentDay != lastResetDay) {
            pomodoroCount = 0
            lastResetDay = currentDay
            saveData()
            updateTomatoes()
        }
    }

    private fun startTimer() {
        // Disabling the "Start" button
        startButton.text = getString(R.string.FinishTimer)
        startButton.isEnabled = true

        timer?.cancel()

        timer = object : CountDownTimer(pomodoroDuration, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val minutes = (millisUntilFinished / 1000) / 60
                val seconds = (millisUntilFinished / 1000) % 60
                timerText.text = String.format(Constants.TIME_FORMAT, minutes, seconds)
            }

            override fun onFinish() {
                timer = null
                playSound()

                timerText.text = formatPomodoroDuration(pomodoroDuration)

                // Enabling the "Start" button
                startButton.text = getString(R.string.Start)
                startButton.isEnabled = true

                if (pomodoroCount < totalPomodoros) {
                    pomodoroCount++
                    updateTomatoes()
                    saveData()
                }

                sharedPreferences.withPrefs { it.remove(Constants.START_TIME_KEY) }
            }
        }.start()

        startTime = System.currentTimeMillis()
        sharedPreferences.withPrefs { it.putLong(Constants.START_TIME_KEY, startTime) }
    }

    private fun startTimerWithWorkManager(durationMillis: Long) {
        workRequestId?.let { WorkManager.getInstance(this).cancelWorkById(it) }

        // Create OneTimeWorkRequest to be executed after a specified time
        val timerRequest: WorkRequest = OneTimeWorkRequestBuilder<PomodoroWorker>()
            .setInitialDelay(durationMillis, TimeUnit.MILLISECONDS) // Set the delay time
            .build()

        workRequestId = timerRequest.id

        // Launch WorkManager to complete the task
        WorkManager.getInstance(this).enqueue(timerRequest)

        sharedPreferences.withPrefs {
            it.putString(
                Constants.WORK_REQUEST_ID_KEY,
                workRequestId.toString()
            )
        }
    }

    private fun resumeTimer(remainingTime: Long) {
        // Updating the "Start" button
        startButton.text = getString(R.string.FinishTimer)
        startButton.isEnabled = true

        timer?.cancel()

        // Create a new CountDownTimer that continues from the remaining time
        timer = object : CountDownTimer(remainingTime, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val minutes = (millisUntilFinished / 1000) / 60
                val seconds = (millisUntilFinished / 1000) % 60
                timerText.text = String.format(Constants.TIME_FORMAT, minutes, seconds)
            }

            override fun onFinish() {
                timer = null
                playSound()

                timerText.text = formatPomodoroDuration(pomodoroDuration)

                // Turn on the "Start" button
                startButton.text = getString(R.string.Start)
                startButton.isEnabled = true

                if (pomodoroCount < totalPomodoros) {
                    pomodoroCount++
                    updateTomatoes()
                    saveData()
                }
            }
        }.start()
    }

    private fun generateTomatoImages() {
        // Clearing previous tomatoes
        tomatoGrid.removeAllViews()
        tomatoImages.clear()

        // Size and spacing for tomatoes
        val size = resources.getDimensionPixelSize(R.dimen.tomato_size)
        val margin = resources.getDimensionPixelSize(R.dimen.tomato_margin)

        for (i in 0 until totalPomodoros) {
            val imageView = ImageView(this)

            val params = GridLayout.LayoutParams()
            params.width = size
            params.height = size
            params.setMargins(margin)

            imageView.layoutParams = params
            imageView.setImageResource(R.drawable.tomato_green)

            tomatoGrid.addView(imageView)
            tomatoImages.add(imageView)
        }
    }

    private fun updateTomatoes() {
        if (tomatoImages.size >= totalPomodoros) {
            for (i in 0 until totalPomodoros) {
                val imageView = tomatoImages[i]
                if (i < pomodoroCount) {
                    imageView.setImageResource(R.drawable.tomato_red)
                } else {
                    imageView.setImageResource(R.drawable.tomato_green)
                }
            }
        }
    }

    private fun saveData() {
        sharedPreferences.withPrefs {
            it.putInt(Constants.POMODORO_COUNT_KEY, pomodoroCount)
            it.putInt(Constants.LAST_RESET_DAY_KEY, lastResetDay)
        }
    }

    private fun playSound() {
        val mediaPlayer = MediaPlayer.create(this, R.raw.notification_sound)
        mediaPlayer.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        timer?.cancel()
    }

    private fun showEarlyFinishDialog() {
        val alertDialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.ConfirmFinishTimer))
            .setMessage(getString(R.string.AreYouSureToFinishTimer))
            .setPositiveButton(getString(R.string.Yes)) { _, _ ->
                timer?.cancel()
                timerText.text = formatPomodoroDuration(pomodoroDuration)
                if (pomodoroCount < totalPomodoros) {
                    pomodoroCount++
                    updateTomatoes()
                    saveData()
                }
                startButton.text = getString(R.string.Start)
                timer = null

                // Canceling a WorkManager task
                workRequestId?.let {
                    WorkManager.getInstance(this).cancelWorkById(it)
                }

                sharedPreferences.withPrefs { it.remove(Constants.START_TIME_KEY) }
            }
            .setNegativeButton(getString(R.string.No)) { dialog, _ ->
                dialog.dismiss()
            }
            .create()

        alertDialog.show()
    }

    private fun formatPomodoroDuration(durationMillis: Long): String {
        val minutes = (durationMillis / 1000) / 60
        val seconds = (durationMillis / 1000) % 60
        return String.format(Constants.TIME_FORMAT, minutes, seconds)
    }
}
