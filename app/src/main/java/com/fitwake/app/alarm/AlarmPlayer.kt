package com.fitwake.app.alarm

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

/**
 * 알람음과 진동. 알람 스트림(USAGE_ALARM)으로 재생하므로 무음·진동 모드에서도 울린다.
 * 알람 볼륨이 0이어도 들리도록 재생 중에는 알람 스트림을 최대로 올리고, 끝나면 원래대로 되돌린다 (PRD AL-06).
 */
class AlarmPlayer(private val context: Context) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var savedStreamVolume: Int? = null

    private val alarmAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    fun start(soundUri: String?, vibrate: Boolean, initialVolume: Float) {
        stop()
        raiseAlarmStream()
        player = candidates(soundUri).firstNotNullOfOrNull { uri -> tryCreatePlayer(uri) }?.apply {
            setVolume(initialVolume, initialVolume)
            start()
        }
        if (player == null) Log.w(TAG, "no playable alarm sound; vibrating only")
        if (vibrate || player == null) startVibration()
    }

    fun setVolume(volume: Float) {
        player?.setVolume(volume, volume)
    }

    fun stop() {
        player?.run {
            runCatching { stop() }
            release()
        }
        player = null
        vibrator?.cancel()
        vibrator = null
        restoreAlarmStream()
    }

    private fun candidates(soundUri: String?): List<Uri> = listOfNotNull(
        soundUri?.let(Uri::parse),
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
    )

    private fun tryCreatePlayer(uri: Uri): MediaPlayer? {
        val mp = MediaPlayer()
        return try {
            mp.setAudioAttributes(alarmAttributes)
            mp.setDataSource(context, uri)
            mp.isLooping = true
            mp.prepare()
            mp
        } catch (e: Exception) {
            Log.w(TAG, "cannot play $uri", e)
            mp.release()
            null
        }
    }

    private fun raiseAlarmStream() {
        runCatching {
            val stream = AudioManager.STREAM_ALARM
            savedStreamVolume = audioManager.getStreamVolume(stream)
            audioManager.setStreamVolume(stream, audioManager.getStreamMaxVolume(stream), 0)
        }.onFailure { Log.w(TAG, "cannot raise alarm volume", it) }
    }

    private fun restoreAlarmStream() {
        val saved = savedStreamVolume ?: return
        runCatching { audioManager.setStreamVolume(AudioManager.STREAM_ALARM, saved, 0) }
        savedStreamVolume = null
    }

    @Suppress("DEPRECATION")
    private fun startVibration() {
        val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            context.getSystemService(Vibrator::class.java)
        }
        v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 800, 600), 0), alarmAttributes)
        vibrator = v
    }

    private companion object {
        const val TAG = "AlarmPlayer"
    }
}
