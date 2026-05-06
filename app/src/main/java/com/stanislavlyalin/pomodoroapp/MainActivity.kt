package com.stanislavlyalin.pomodoroapp

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.Manifest
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.GridLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.setMargins
import java.util.Calendar
import kotlin.math.min

class MainActivity : AppCompatActivity(), TimerListener {

    // UI Elements
    private lateinit var timerText: TextView
    private lateinit var startButton: Button
    private lateinit var tomatoGrid: GridLayout
    private lateinit var settingsButton: ImageButton

    // Dependencies
    private lateinit var repository: PomodoroRepository
    private lateinit var pomodoroTimer: PomodoroTimer
    private lateinit var alarmManager: AlarmManager

    // Local State (Logic)
    private val tomatoImages = mutableListOf<ImageView>()
    private val tomatoLabels = mutableListOf<TextView>()
    private var startTime: Long = 0L
    private val ALARM_REQUEST_CODE = 0
    private val NOTIFICATION_PERMISSION_REQUEST_CODE = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        repository = PomodoroRepository(this)
        pomodoroTimer = PomodoroTimer(this)
        alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager

        initViews()
        checkDayReset()
        initTomatoGrid()

        restoreTimerState()

        setupClickListeners()

        requestNotificationPermission()
    }

    private fun initViews() {
        timerText = findViewById(R.id.timer_text)
        startButton = findViewById(R.id.start_button)
        tomatoGrid = findViewById(R.id.tomato_grid)
        settingsButton = findViewById(R.id.settings_button)

        timerText.text = formatPomodoroDuration(repository.pomodoroDuration)
    }

    private fun checkDayReset() {
        val currentDay = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
        if (currentDay != repository.lastResetDay) {
            repository.clearDailyProgress()
            repository.lastResetDay = currentDay
        }
    }

    private fun initTomatoGrid() {
        tomatoGrid.columnCount = 6
        tomatoGrid.removeAllViews()
        tomatoImages.clear()
        tomatoLabels.clear()

        val size = resources.getDimensionPixelSize(R.dimen.tomato_size)
        val margin = resources.getDimensionPixelSize(R.dimen.tomato_margin)
        val total = repository.totalPomodoros
        val labels = repository.getPomodoroLabels()

        for (i in 0 until total) {
            val cellView = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
            }
            val imageView = ImageView(this)
            val params = GridLayout.LayoutParams().apply {
                width = size
                height = LinearLayout.LayoutParams.WRAP_CONTENT
                setMargins(margin)
            }
            cellView.layoutParams = params
            imageView.layoutParams = LinearLayout.LayoutParams(size, size)
            val imageRes = if (i < repository.pomodoroCount) R.drawable.tomato_red else R.drawable.tomato_green
            imageView.setImageResource(imageRes)

            val labelView = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(size, LinearLayout.LayoutParams.WRAP_CONTENT)
                gravity = Gravity.CENTER
                includeFontPadding = false
                maxLines = 2
                textSize = 10f
                text = if (repository.pomodoroLabelsEnabled && i < repository.pomodoroCount) {
                    formatPomodoroLabel(labels.getOrNull(i).orEmpty())
                } else {
                    ""
                }
            }

            cellView.addView(imageView)
            cellView.addView(labelView)
            tomatoGrid.addView(cellView)
            tomatoImages.add(imageView)
            tomatoLabels.add(labelView)
        }
    }

    private fun refreshTomatoes() {
        val count = repository.pomodoroCount
        val labels = repository.getPomodoroLabels()
        tomatoImages.forEachIndexed { index, imageView ->
            val res = if (index < count) R.drawable.tomato_red else R.drawable.tomato_green
            imageView.setImageResource(res)
            tomatoLabels[index].text = if (repository.pomodoroLabelsEnabled && index < count) {
                formatPomodoroLabel(labels.getOrNull(index).orEmpty())
            } else {
                ""
            }
        }
    }

    private fun setupClickListeners() {
        startButton.setOnClickListener {
            when (pomodoroTimer.state) {
                TimerState.IDLE -> {
                    if (repository.pomodoroLabelsEnabled) {
                        showPomodoroLabelDialog { label -> startTimerSession(label) }
                    } else {
                        startTimerSession()
                    }
                }
                TimerState.RUNNING -> showEarlyFinishDialog()
            }
        }
        settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun restoreTimerState() {
        val savedStartTime = repository.getStartTime()
        val duration = repository.pomodoroDuration

        if (savedStartTime == 0L) return

        val currentTime = System.currentTimeMillis()
        val remainingTime = min(duration, duration - (currentTime - savedStartTime))

        if (remainingTime > 0) {
            startTime = savedStartTime
            pomodoroTimer.start(remainingTime)
            updateUIState()
            cancelAlarm()
        } else {
            repository.completeActiveSession()
            refreshTomatoes()
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    NOTIFICATION_PERMISSION_REQUEST_CODE
                )
            }
        }
    }

    private fun checkAndRequestExactAlarmPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager

            if (!alarmManager.canScheduleExactAlarms()) {
                AlertDialog.Builder(this)
                    .setTitle(R.string.permission_required)
                    .setMessage(R.string.exact_alarm_permission_message)
                    .setPositiveButton(R.string.open_settings) { dialog, which ->
                        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                        intent.data = Uri.fromParts("package", packageName, null)
                        startActivity(intent)
                    }
                    .setNegativeButton(R.string.cancel) { dialog, which ->
                        Toast.makeText(
                            this,
                            getString(R.string.alarm_permission_denied),
                            Toast.LENGTH_LONG
                        ).show()
                        dialog.dismiss()
                    }
                    .show()
                return false
            }
        }
        return true
    }

    private fun startTimerSession(label: String = "") {
        if (!checkAndRequestExactAlarmPermission()) {
            return
        }

        startTime = System.currentTimeMillis()
        repository.startSession(startTime, label)

        pomodoroTimer.start(repository.pomodoroDuration)
        updateUIState()
    }

    private fun stopTimerSession(completedSuccessfully: Boolean) {
        pomodoroTimer.stop()
        timerText.text = formatPomodoroDuration(repository.pomodoroDuration)
        updateUIState()
        cancelAlarm()

        if (completedSuccessfully) {
            repository.completeActiveSession()
            refreshTomatoes()
        } else {
            repository.completeSession()
        }
    }

    override fun onTick(millisUntilFinished: Long) {
        timerText.text = formatPomodoroDuration(millisUntilFinished)
    }

    override fun onFinish() {
        if (repository.isTimerActive()) {
            playSound()
            stopTimerSession(completedSuccessfully = true)
        }
    }

    override fun onStop() {
        super.onStop()
        if (pomodoroTimer.state == TimerState.RUNNING) {
            val endTime = startTime + repository.pomodoroDuration
            scheduleAlarm(endTime)
        }
    }

    override fun onStart() {
        super.onStart()
        cancelAlarm()
        if (::repository.isInitialized && tomatoLabels.isNotEmpty()) {
            refreshTomatoes()
        }
    }

    @SuppressLint("ScheduleExactAlarm")
    private fun scheduleAlarm(triggerTimeMillis: Long) {
        val intent = Intent(this, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            this, ALARM_REQUEST_CODE, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP, triggerTimeMillis, pendingIntent
        )
    }

    private fun cancelAlarm() {
        val intent = Intent(this, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            this, ALARM_REQUEST_CODE, intent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        pendingIntent?.let { alarmManager.cancel(it); it.cancel() }
    }

    private fun updateUIState() {
        when (pomodoroTimer.state) {
            TimerState.IDLE -> {
                startButton.text = getString(R.string.Start)
                startButton.isEnabled = true
            }
            TimerState.RUNNING -> {
                startButton.text = getString(R.string.FinishTimer)
                startButton.isEnabled = true
            }
        }
    }

    private fun playSound() {
        val mediaPlayer = MediaPlayer.create(this, R.raw.notification_sound)
        mediaPlayer?.setOnCompletionListener { mp ->
            mp.release()
        }
        mediaPlayer?.start()
    }

    private fun showEarlyFinishDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_custom, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialogView.findViewById<Button>(R.id.with_tomato_button).setOnClickListener {
            stopTimerSession(completedSuccessfully = true)
            dialog.dismiss()
        }

        dialogView.findViewById<Button>(R.id.without_tomato_button).setOnClickListener {
            stopTimerSession(completedSuccessfully = false)
            dialog.dismiss()
        }

        dialogView.findViewById<Button>(R.id.cancel_button).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showPomodoroLabelDialog(onLabelConfirmed: (String) -> Unit) {
        val labelInput = EditText(this).apply {
            setText(repository.lastPomodoroLabel.take(15))
            setSelection(text.length)
            hint = getString(R.string.pomodoro_label_hint)
            filters = arrayOf(InputFilter.LengthFilter(15))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setSingleLine(true)
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.pomodoro_label_dialog_title)
            .setView(labelInput)
            .setPositiveButton(R.string.Start) { _, _ ->
                onLabelConfirmed(labelInput.text.toString())
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun formatPomodoroLabel(label: String): String {
        val trimmedLabel = label.trim().take(15)
        if (trimmedLabel.length <= 8) {
            return trimmedLabel
        }

        val preferredBreakIndex = findPomodoroLabelBreakIndex(trimmedLabel)
        val breakIndex = preferredBreakIndex ?: 8
        val firstLine = trimmedLabel.substring(0, breakIndex).trimEnd(' ', '-')
        val secondLineStart = if (preferredBreakIndex != null) breakIndex + 1 else breakIndex
        val secondLine = trimmedLabel.substring(secondLineStart).trimStart(' ', '-')

        return "$firstLine\n$secondLine"
    }

    private fun findPomodoroLabelBreakIndex(label: String): Int? {
        val minBreakIndex = label.length - 8
        val maxBreakIndex = 8

        return label
            .indices
            .filter { index ->
                index in minBreakIndex..maxBreakIndex &&
                    index > 0 &&
                    index < label.lastIndex &&
                    (label[index] == ' ' || label[index] == '-')
            }
            .lastOrNull()
    }

    private fun formatPomodoroDuration(durationMillis: Long): String {
        val minutes = (durationMillis / 1000) / 60
        val seconds = (durationMillis / 1000) % 60
        return String.format(Constants.TIME_FORMAT, minutes, seconds)
    }

    override fun onDestroy() {
        super.onDestroy()
        pomodoroTimer.stop()
    }
}
