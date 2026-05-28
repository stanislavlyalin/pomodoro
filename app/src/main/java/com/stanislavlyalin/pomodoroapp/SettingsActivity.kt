package com.stanislavlyalin.pomodoroapp

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity


class SettingsActivity : AppCompatActivity() {

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var pomodoroCountInput: EditText
    private lateinit var pomodoroDurationInput: EditText
    private lateinit var pomodoroLabelsCheckbox: CompoundButton
    private lateinit var pomodoroCountIncrement: ImageButton
    private lateinit var pomodoroCountDecrement: ImageButton
    private lateinit var pomodoroDurationIncrement: ImageButton
    private lateinit var pomodoroDurationDecrement: ImageButton
    private lateinit var saveButton: Button
    private lateinit var homeButton: View
    private lateinit var statisticsButton: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // Initialization SharedPreferences
        sharedPreferences = getSharedPreferences(Constants.PREFERENCES, MODE_PRIVATE)

        // Initializing UI elements
        pomodoroCountInput = findViewById(R.id.pomodoro_count_input)
        pomodoroDurationInput = findViewById(R.id.pomodoro_duration_input)
        pomodoroLabelsCheckbox = findViewById(R.id.pomodoro_labels_checkbox)
        pomodoroCountIncrement = findViewById(R.id.pomodoro_count_increment)
        pomodoroCountDecrement = findViewById(R.id.pomodoro_count_decrement)
        pomodoroDurationIncrement = findViewById(R.id.pomodoro_duration_increment)
        pomodoroDurationDecrement = findViewById(R.id.pomodoro_duration_decrement)
        saveButton = findViewById(R.id.save_button)
        homeButton = findViewById(R.id.home_button)
        statisticsButton = findViewById(R.id.statistics_button)

        // Loading saved data
        pomodoroCountInput.setText(
            sharedPreferences.getInt(Constants.TOTAL_POMODOROS_KEY, 12).toString()
        )
        pomodoroDurationInput.setText(
            (sharedPreferences.getLong(
                Constants.POMODORO_DURATION_KEY,
                25 * 60 * 1000L
            ) / 60000).toString()
        )
        pomodoroLabelsCheckbox.isChecked = sharedPreferences.getBoolean(
            Constants.POMODORO_LABELS_ENABLED_KEY,
            false
        )

        // Displaying the application version
        val commitHash = BuildConfig.COMMIT_HASH
        val versionInfo = getString(R.string.version_info, BuildConfig.VERSION_NAME, commitHash)
        findViewById<TextView>(R.id.appVersion).text = versionInfo

        setupSteppers()
        setupNavigation()

        saveButton.setOnClickListener {
            val newPomodoroCount = pomodoroCountInput.text.toString().toIntOrNull()
            val newPomodoroDuration = pomodoroDurationInput.text.toString().toLongOrNull()

            if (newPomodoroCount == null || newPomodoroDuration == null || newPomodoroCount < 1 || newPomodoroDuration < 1) {
                Toast.makeText(this, getString(R.string.enterCorrectSettings), Toast.LENGTH_SHORT)
                    .show()
            } else {
                sharedPreferences.edit().apply {
                    putInt(Constants.TOTAL_POMODOROS_KEY, newPomodoroCount)
                    putLong(Constants.POMODORO_DURATION_KEY, newPomodoroDuration * 60 * 1000L)
                    putBoolean(
                        Constants.POMODORO_LABELS_ENABLED_KEY,
                        pomodoroLabelsCheckbox.isChecked
                    )
                    apply()
                }
                Toast.makeText(this, getString(R.string.settings_saved), Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    private fun setupSteppers() {
        pomodoroCountIncrement.setOnClickListener {
            adjustNumber(pomodoroCountInput, delta = 1, min = 1, max = 99)
        }
        pomodoroCountDecrement.setOnClickListener {
            adjustNumber(pomodoroCountInput, delta = -1, min = 1, max = 99)
        }
        pomodoroDurationIncrement.setOnClickListener {
            adjustNumber(pomodoroDurationInput, delta = 5, min = 1, max = 240)
        }
        pomodoroDurationDecrement.setOnClickListener {
            adjustNumber(pomodoroDurationInput, delta = -5, min = 1, max = 240)
        }
    }

    private fun setupNavigation() {
        homeButton.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
        statisticsButton.setOnClickListener {
            startActivity(Intent(this, StatisticsActivity::class.java))
            finish()
        }
    }

    private fun adjustNumber(input: EditText, delta: Int, min: Int, max: Int) {
        val currentValue = input.text.toString().toIntOrNull() ?: min
        val nextValue = (currentValue + delta).coerceIn(min, max)
        input.setText(nextValue.toString())
        input.setSelection(input.text.length)
    }
}
