package com.stanislavlyalin.pomodoroapp

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.MediaPlayer
import android.os.Bundle
import android.os.CountDownTimer
import android.widget.Button
import android.widget.GridLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.setMargins
import java.util.Calendar
import kotlin.math.min

class MainActivity : AppCompatActivity() {

    private lateinit var timerText: TextView
    private lateinit var startButton: Button
    private lateinit var tomatoGrid: GridLayout
    private val tomatoImages = mutableListOf<ImageView>()
    private lateinit var sharedPreferences: SharedPreferences

    private var pomodoroCount = 0
    private var totalPomodoros = 12                 // Easily change the number of tomatoes here
    private var pomodoroDuration = 25 * 60 * 1000L  // 25 minutes in milliseconds
    private var startTime: Long = 0L
    private var timer: CountDownTimer? = null
    private var lastResetDay: Int = -1
    private var pendingRequestCode: Int? = null
    private lateinit var alarmManager: AlarmManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Setting up the layout
        setContentView(R.layout.activity_main)

        // Initializing UI elements
        timerText = findViewById(R.id.timer_text)
        startButton = findViewById(R.id.start_button)
        tomatoGrid = findViewById(R.id.tomato_grid)
        val settingsButton: ImageButton = findViewById(R.id.settings_button)

        // Setting the number of columns in GridLayout based on totalPomodoros
        tomatoGrid.columnCount = 6 // Can be adjusted according to your preferences

        // Initializing SharedPreferences
        sharedPreferences = getSharedPreferences(Constants.PREFERENCES, MODE_PRIVATE)

        // Loading saved data
        pomodoroCount = sharedPreferences.getInt(Constants.POMODORO_COUNT_KEY, 0)
        lastResetDay = sharedPreferences.getInt(Constants.LAST_RESET_DAY_KEY, -1)
        totalPomodoros = sharedPreferences.getInt(Constants.TOTAL_POMODOROS_KEY, 12)
        pomodoroDuration =
            sharedPreferences.getLong(Constants.POMODORO_DURATION_KEY, 25 * 60 * 1000L)
        pendingRequestCode = if (sharedPreferences.contains(Constants.PENDING_REQUEST_CODE_KEY)) {
            sharedPreferences.getInt(Constants.PENDING_REQUEST_CODE_KEY, 0)
        } else {
            null
        }
        val currentTime = System.currentTimeMillis()
        startTime = sharedPreferences.getLong(Constants.START_TIME_KEY, currentTime)
        val remainingTime = min(pomodoroDuration, pomodoroDuration - (currentTime - startTime))

        timerText.text = formatPomodoroDuration(pomodoroDuration)

        checkForDayReset()
        generateTomatoImages()
        updateTomatoes()

        alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager

        if (remainingTime in 1..<pomodoroDuration) {
            // Restore and continue timer
            resumeTimer(remainingTime)

            // If we launched a program with a background timer running, stop it
            cancelAlarmTimer()
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

        settingsButton.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        if (timer != null) {
            val currentTime = System.currentTimeMillis()
            val remainingDuration = startTime + pomodoroDuration - currentTime
            startAlarmManagerTimer(remainingDuration)
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

    @SuppressLint("ScheduleExactAlarm")
    private fun startAlarmManagerTimer(durationMillis: Long) {
        val intent = Intent(this, AlarmReceiver::class.java)
        val pendingRequestCode = 0
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            pendingRequestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT
        )

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + durationMillis,
            pendingIntent
        )

        sharedPreferences.withPrefs {
            it.putInt(Constants.PENDING_REQUEST_CODE_KEY, pendingRequestCode)
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
        val dialogView = layoutInflater.inflate(R.layout.dialog_custom, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialogView.findViewById<Button>(R.id.with_tomato_button).setOnClickListener {
            finishTimer(addTomato = true)
            dialog.dismiss()
        }

        dialogView.findViewById<Button>(R.id.without_tomato_button).setOnClickListener {
            finishTimer(addTomato = false)
            dialog.dismiss()
        }

        dialogView.findViewById<Button>(R.id.cancel_button).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun finishTimer(addTomato: Boolean) {
        timer?.cancel()
        timer = null

        timerText.text = formatPomodoroDuration(pomodoroDuration)
        startButton.text = getString(R.string.Start)
        startButton.isEnabled = true

        if (addTomato && pomodoroCount < totalPomodoros) {
            pomodoroCount++
            updateTomatoes()
            saveData()
        }

        cancelAlarmTimer()
        sharedPreferences.withPrefs { it.remove(Constants.START_TIME_KEY) }
    }

    private fun cancelAlarmTimer() {
        pendingRequestCode?.let {
            val intent = Intent(this, AlarmReceiver::class.java)
            val pendingIntent =
                PendingIntent.getBroadcast(this, it, intent, PendingIntent.FLAG_UPDATE_CURRENT)
            alarmManager.cancel(pendingIntent)
        }
    }

    private fun formatPomodoroDuration(durationMillis: Long): String {
        val minutes = (durationMillis / 1000) / 60
        val seconds = (durationMillis / 1000) % 60
        return String.format(Constants.TIME_FORMAT, minutes, seconds)
    }
}
