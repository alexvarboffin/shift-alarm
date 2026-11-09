package com.walhalla.shiftalarm.core.alarm

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Process-wide "a ring is going on" flag. Written only by [RingingService]. */
internal object RingingState {
    private val ringing = MutableStateFlow(false)
    val isRinging: StateFlow<Boolean> = ringing.asStateFlow()

    fun set(value: Boolean) {
        ringing.value = value
    }
}

/**
 * Sound and vibration of the ring, owned by the service, not by the notification channel.
 * So the alarm rings even when the user switched the channel or app notifications off.
 */
internal class RingingPlayback(private val context: Context) {
    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    fun start(vibration: Boolean) {
        stop()
        player = createPlayer()
        if (vibration) startVibration()
    }

    fun stop() {
        player?.let { current ->
            runCatching { if (current.isPlaying) current.stop() }
            current.release()
        }
        player = null
        vibrator?.cancel()
        vibrator = null
    }

    private fun createPlayer(): MediaPlayer? {
        val attributes = alarmAudioAttributes()
        for (uri in alarmSoundCandidates()) {
            val created = runCatching {
                MediaPlayer().apply {
                    setAudioAttributes(attributes)
                    setDataSource(context, uri)
                    isLooping = true
                    prepare()
                    start()
                }
            }.getOrNull()
            if (created != null) return created
        }
        return null
    }

    private fun alarmSoundCandidates(): List<Uri> = listOfNotNull(
        RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM),
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
    ).distinct()

    @Suppress("DEPRECATION")
    private fun startVibration() {
        val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            context.getSystemService(Vibrator::class.java)
        } ?: return
        if (!device.hasVibrator()) return
        vibrator = device
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            device.vibrate(VibrationEffect.createWaveform(VIBRATION_PATTERN, 0), alarmAudioAttributes())
        } else {
            device.vibrate(VIBRATION_PATTERN, 0, alarmAudioAttributes())
        }
    }

    private fun alarmAudioAttributes(): AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private companion object {
        val VIBRATION_PATTERN: LongArray = longArrayOf(0, 600, 200, 600, 200, 800)
    }
}
