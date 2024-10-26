package com.stanislavlyalin.pomodoroapp

import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity


class SettingsActivity : AppCompatActivity() {

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var pomodoroCountInput: EditText
    private lateinit var pomodoroDurationInput: EditText
    private lateinit var saveButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // Initialization SharedPreferences
        sharedPreferences = getSharedPreferences(Constants.PREFERENCES, MODE_PRIVATE)

        // Initializing UI elements
        pomodoroCountInput = findViewById(R.id.pomodoro_count_input)
        pomodoroDurationInput = findViewById(R.id.pomodoro_duration_input)
        saveButton = findViewById(R.id.save_button)

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

        // Displaying the application version
        val commitHash = BuildConfig.COMMIT_HASH
        val versionInfo = getString(R.string.version_info, BuildConfig.VERSION_NAME, commitHash)
        findViewById<TextView>(R.id.appVersion).text = versionInfo

        saveButton.setOnClickListener {
            val newPomodoroCount = pomodoroCountInput.text.toString().toIntOrNull()
            val newPomodoroDuration = pomodoroDurationInput.text.toString().toLongOrNull()

            if (newPomodoroCount == null || newPomodoroDuration == null) {
                Toast.makeText(this, getString(R.string.enterCorrectSettings), Toast.LENGTH_SHORT)
                    .show()
            } else {
                sharedPreferences.edit().apply {
                    putInt(Constants.TOTAL_POMODOROS_KEY, newPomodoroCount)
                    putLong(Constants.POMODORO_DURATION_KEY, newPomodoroDuration * 60 * 1000L)
                    apply()
                }
                Toast.makeText(this, getString(R.string.restartToApplySettings), Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }
}
