package com.stanislavlyalin.pomodoroapp

import android.content.SharedPreferences
import android.media.MediaPlayer
import android.os.Bundle
import android.os.CountDownTimer
import android.widget.Button
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.TextView
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
    private val totalPomodoros = 11 // Легко изменить количество помидорок здесь
    private val pomodoroDuration = 35 * 60 * 1000L // 25 минут в миллисекундах
    private var timer: CountDownTimer? = null
    private var lastResetDay: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Устанавливаем макет
        setContentView(R.layout.activity_main)

        // Инициализируем элементы интерфейса
        timerText = findViewById(R.id.timer_text)
        startButton = findViewById(R.id.start_button)
        tomatoGrid = findViewById(R.id.tomato_grid)

        // Устанавливаем количество столбцов в GridLayout в зависимости от totalPomodoros
        tomatoGrid.columnCount = 6 // Можно изменить в соответствии с вашими предпочтениями

        // Инициализируем SharedPreferences
        sharedPreferences = getSharedPreferences("PomodoroPrefs", MODE_PRIVATE)

        // Загружаем сохраненные данные
        pomodoroCount = sharedPreferences.getInt("pomodoroCount", 0)
        lastResetDay = sharedPreferences.getInt("lastResetDay", -1)

        checkForDayReset()
        generateTomatoImages()
        updateTomatoes()

        startButton.setOnClickListener {
            startTimer()
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
        // Отключаем кнопку "Старт"
        startButton.text = "Работаем..."
        startButton.isEnabled = false

        timer?.cancel()

        timer = object : CountDownTimer(pomodoroDuration, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val minutes = (millisUntilFinished / 1000) / 60
                val seconds = (millisUntilFinished / 1000) % 60
                timerText.text = String.format("%02d:%02d", minutes, seconds)
            }

            override fun onFinish() {
                playSound()

                timerText.text = "25:00"

                // Включаем кнопку "Старт"
                startButton.text = "Старт"
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
        // Очищаем предыдущие помидорки
        tomatoGrid.removeAllViews()
        tomatoImages.clear()

        // Размер и отступы для помидорок
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
                    imageView.setImageResource(R.drawable.tomato_gray)
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
}
