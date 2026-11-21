package com.example.game

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

enum class HapticKind {
  TURN,
  RELIC,
  LIGHT,
  MEDIUM,
  HEAVY,
}

class HapticEngine(context: Context) {
  private val vibrator: Vibrator? by lazy {
    val appCtx = context.applicationContext
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      val manager = appCtx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
      manager?.defaultVibrator
    } else {
      @Suppress("DEPRECATION")
      appCtx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
  }

  fun trigger(kind: HapticKind) {
    val vib = vibrator ?: return
    if (!vib.hasVibrator()) return
    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val effect =
          when (kind) {
            HapticKind.TURN -> VibrationEffect.createOneShot(28L, 210)
            HapticKind.RELIC ->
              VibrationEffect.createWaveform(
                longArrayOf(0L, 22L, 35L, 34L),
                intArrayOf(0, 175, 0, 240),
                -1,
              )
            HapticKind.HEAVY -> VibrationEffect.createOneShot(75L, 255)
            HapticKind.MEDIUM -> VibrationEffect.createOneShot(32L, 190)
            HapticKind.LIGHT -> VibrationEffect.createOneShot(14L, 110)
          }
        vib.vibrate(effect)
      } else {
        val durationMs =
          when (kind) {
            HapticKind.TURN -> 28L
            HapticKind.RELIC -> 42L
            HapticKind.HEAVY -> 75L
            HapticKind.MEDIUM -> 32L
            HapticKind.LIGHT -> 14L
          }
        @Suppress("DEPRECATION")
        vib.vibrate(durationMs)
      }
    } catch (_: SecurityException) {
      // Ignore if vibration is blocked by device policy
    }
  }
}
