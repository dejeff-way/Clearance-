package com.forma.app.util

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.forma.app.data.workout.TimerAlert

/** Short buzz + beep for timer events. Never throws; silently does nothing on devices without a vibrator. */
class TimerAlerts(private val context: Context) {
    private val tone by lazy { runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80) }.getOrNull() }

    private val vibrator: Vibrator? by lazy {
        runCatching {
            if (Build.VERSION.SDK_INT >= 31) (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            else @Suppress("DEPRECATION") (context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator)
        }.getOrNull()
    }

    fun play(a: TimerAlert) {
        runCatching {
            when (a) {
                TimerAlert.COUNTDOWN -> { tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 120); buzz(longArrayOf(0, 60)) }
                TimerAlert.PHASE -> { tone?.startTone(ToneGenerator.TONE_PROP_BEEP2, 250); buzz(longArrayOf(0, 200)) }
                TimerAlert.DONE -> { tone?.startTone(ToneGenerator.TONE_PROP_ACK, 600); buzz(longArrayOf(0, 300, 150, 300)) }
            }
        }
    }

    private fun buzz(pattern: LongArray) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        v.vibrate(VibrationEffect.createWaveform(pattern, -1))
    }
}
