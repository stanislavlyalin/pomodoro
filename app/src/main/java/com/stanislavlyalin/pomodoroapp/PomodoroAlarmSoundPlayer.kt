package com.stanislavlyalin.pomodoroapp

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.PowerManager

object PomodoroAlarmSoundPlayer {
    private const val WAKE_LOCK_TIMEOUT_MILLIS = 30_000L
    private val activePlayers = mutableSetOf<MediaPlayer>()

    fun play(context: Context, onFinished: () -> Unit = {}) {
        val appContext = context.applicationContext
        val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val audioFocusRequest = requestAudioFocus(audioManager, audioAttributes)
        val wakeLock = acquireWakeLock(appContext)

        fun finish(player: MediaPlayer? = null) {
            player?.let {
                activePlayers.remove(it)
                it.release()
            }
            abandonAudioFocus(audioManager, audioFocusRequest)
            if (wakeLock.isHeld) {
                wakeLock.release()
            }
            onFinished()
        }

        try {
            val player = MediaPlayer().apply {
                setAudioAttributes(audioAttributes)
                setDataSource(appContext, notificationSoundUri(appContext))
                setOnPreparedListener { it.start() }
                setOnCompletionListener { finish(it) }
                setOnErrorListener { mp, _, _ ->
                    finish(mp)
                    true
                }
            }

            activePlayers.add(player)
            player.prepareAsync()
        } catch (_: Exception) {
            finish()
        }
    }

    private fun requestAudioFocus(
        audioManager: AudioManager,
        audioAttributes: AudioAttributes
    ): AudioFocusRequest? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(audioAttributes)
                .build()
                .also { audioManager.requestAudioFocus(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                null,
                AudioManager.STREAM_ALARM,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
            )
            null
        }
    }

    private fun abandonAudioFocus(
        audioManager: AudioManager,
        audioFocusRequest: AudioFocusRequest?
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioFocusRequest != null) {
            audioManager.abandonAudioFocusRequest(audioFocusRequest)
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(null)
        }
    }

    private fun acquireWakeLock(context: Context): PowerManager.WakeLock {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "${context.packageName}:PomodoroAlarmSound"
        ).apply {
            setReferenceCounted(false)
            acquire(WAKE_LOCK_TIMEOUT_MILLIS)
        }
    }

    private fun notificationSoundUri(context: Context): Uri {
        return Uri.parse("android.resource://${context.packageName}/${R.raw.notification_sound}")
    }
}
