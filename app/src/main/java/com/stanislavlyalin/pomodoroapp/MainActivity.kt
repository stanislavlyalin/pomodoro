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
import java.util.Calendar

class MainActivity : AppCompatActivity() {

    private lateinit var timerText: TextView
    private lateinit var startButton: Button
    private lateinit var tomatoGrid: GridLayout
    private val tomatoImages = mutableListOf<ImageView>()
    private lateinit var sharedPreferences: SharedPreferences

    private var pomodoroCount = 0
    private val totalPomodoros = 12                 // Easily change the number of tomatoes here
    private val pomodoroDuration = 25 * 60 * 1000L  // 25 minutes in milliseconds
    private var timer: CountDownTimer? = null
    private var lastResetDay: Int = -1

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
        sharedPreferences = getSharedPreferences("PomodoroPrefs", MODE_PRIVATE)

        // Loading saved data
        pomodoroCount = sharedPreferences.getInt("pomodoroCount", 0)
        lastResetDay = sharedPreferences.getInt("lastResetDay", -1)

        timerText.text = formatPomodoroDuration(pomodoroDuration)

        checkForDayReset()
        generateTomatoImages()
        updateTomatoes()

        startButton.setOnClickListener {
            if (timer == null) {
                startTimer()
            } else {
                showEarlyFinishDialog()
            }
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
                timerText.text = String.format("%02d:%02d", minutes, seconds)
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
            imageView.setImageResource(R.drawable.tomato_gray)

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
        val editor = sharedPreferences.edit()
        editor.putInt("pomodoroCount", pomodoroCount)
        editor.putInt("lastResetDay", lastResetDay)
        editor.apply()
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
        return String.format("%02d:%02d", minutes, seconds)
    }
}
