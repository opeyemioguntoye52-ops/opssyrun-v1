package com.example.game

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

class SoundEngine {
  companion object {
    private const val SAMPLE_RATE = 22050
  }

  private val executor = Executors.newSingleThreadExecutor()
  @Volatile var isMuted: Boolean = false
  private var coinCombo = 0
  private var lastCoinMs = 0L

  private val footstepSoft = buildFootstep(0.55f)
  private val footstepHard = buildFootstep(0.95f)
  private val jumpBuffer = buildWhoosh(210f, 520f, 0.14f, 0.30f)
  private val slideBuffer = buildWhoosh(420f, 160f, 0.19f, 0.30f)
  private val laneShiftBuffer = buildWhoosh(260f, 360f, 0.08f, 0.20f)
  private val turnBuffer = buildTurnSwoosh()
  private val nearMissBuffer = buildNearMiss()
  private val shieldShatterBuffer = buildShieldShatter()
  private val stumbleBuffer = buildThud(145f, 48f, 0.22f, 0.55f)
  private val crashBuffer = buildThud(110f, 34f, 0.34f, 0.72f)
  private val coinBuffers = Array(8) { idx -> buildCoinChime(idx) }
  private val relicMagnet = buildRelicChord(523.25f, 659.25f, 783.99f)
  private val relicShield = buildRelicChord(440f, 554.37f, 880f)
  private val relicDash = buildRelicChord(587.33f, 880f, 1174.66f)

  fun playFootstep(heavy: Boolean = false) {
    playPcm(if (heavy) footstepHard else footstepSoft)
  }

  fun playJump() = playPcm(jumpBuffer)

  fun playSlide() = playPcm(slideBuffer)

  fun playLaneShift() = playPcm(laneShiftBuffer)

  fun playTurn() = playPcm(turnBuffer)

  fun playNearMiss() = playPcm(nearMissBuffer)

  fun playShieldShatter() = playPcm(shieldShatterBuffer)

  fun playStumble() = playPcm(stumbleBuffer)

  fun playCrash() = playPcm(crashBuffer)

  fun playCoin() {
    val now = System.currentTimeMillis()
    coinCombo = if (now - lastCoinMs < 650L) (coinCombo + 1).coerceAtMost(7) else 0
    lastCoinMs = now
    playPcm(coinBuffers[coinCombo])
  }

  fun playRelicPickup(kind: RelicKind) {
    val buf =
      when (kind) {
        RelicKind.MAGNET -> relicMagnet
        RelicKind.SHIELD -> relicShield
        RelicKind.DASH -> relicDash
      }
    playPcm(buf)
  }

  private fun playPcm(samples: ShortArray) {
    if (isMuted) return
    executor.execute {
      try {
        val attrs =
          AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val format =
          AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()
        val track =
          AudioTrack.Builder()
            .setAudioAttributes(attrs)
            .setAudioFormat(format)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(samples.size * 2)
            .build()
        track.write(samples, 0, samples.size)
        track.play()
        val durationMs = (samples.size * 1000L / SAMPLE_RATE) + 30L
        Thread.sleep(durationMs)
        track.stop()
        track.release()
      } catch (_: Exception) {
        // Ignore transient audio allocation issues
      }
    }
  }

  fun dispose() {
    executor.shutdownNow()
  }

  private fun buildFootstep(gain: Float): ShortArray {
    val len = (SAMPLE_RATE * 0.055f).toInt()
    val out = ShortArray(len)
    var lp = 0f
    for (i in 0 until len) {
      val t = i.toFloat() / SAMPLE_RATE
      val env = exp(-t * 68f)
      val noise = (Random.nextFloat() * 2f - 1f)
      lp = lp * 0.72f + noise * 0.28f
      val tap = sin(2.0 * PI * 125.0 * t).toFloat() * 0.45f
      out[i] = ((lp * 0.65f + tap) * env * gain * 14000f).toInt().coerceIn(-32767, 32767).toShort()
    }
    return out
  }

  private fun buildWhoosh(f0: Float, f1: Float, dur: Float, gain: Float): ShortArray {
    val len = (SAMPLE_RATE * dur).toInt()
    val out = ShortArray(len)
    var phase = 0.0
    var lp = 0f
    for (i in 0 until len) {
      val p = i.toFloat() / len
      val t = i.toFloat() / SAMPLE_RATE
      val freq = f0 + (f1 - f0) * p
      phase += 2.0 * PI * freq / SAMPLE_RATE
      val env = sin(PI * p).toFloat() * exp(-t * 4.5f)
      val noise = Random.nextFloat() * 2f - 1f
      lp = lp * 0.6f + noise * 0.4f
      val s = (sin(phase).toFloat() * 0.45f + lp * 0.55f) * env * gain * 22000f
      out[i] = s.toInt().coerceIn(-32767, 32767).toShort()
    }
    return out
  }

  private fun buildTurnSwoosh(): ShortArray {
    val len = (SAMPLE_RATE * 0.16f).toInt()
    val out = ShortArray(len)
    var phase = 0.0
    for (i in 0 until len) {
      val p = i.toFloat() / len
      val freq = 310f + 290f * p
      phase += 2.0 * PI * freq / SAMPLE_RATE
      val env = sin(PI * p).toFloat() * (1f - p * 0.35f)
      val s = (sin(phase).toFloat() * 0.6f + sin(phase * 1.5).toFloat() * 0.25f) * env * 9500f
      out[i] = s.toInt().coerceIn(-32767, 32767).toShort()
    }
    return out
  }

  private fun buildCoinChime(combo: Int): ShortArray {
    val semitones = intArrayOf(0, 2, 4, 7, 9, 12, 14, 16)[combo.coerceIn(0, 7)]
    val baseFreq = 784.0 * Math.pow(2.0, semitones / 12.0)
    val len = (SAMPLE_RATE * 0.14f).toInt()
    val out = ShortArray(len)
    for (i in 0 until len) {
      val t = i.toFloat() / SAMPLE_RATE
      val env = exp(-t * 22f)
      val s1 = sin(2.0 * PI * baseFreq * t).toFloat()
      val s2 = sin(2.0 * PI * (baseFreq * 1.5) * t).toFloat() * 0.45f
      out[i] = ((s1 + s2) * env * 11000f).toInt().coerceIn(-32767, 32767).toShort()
    }
    return out
  }

  private fun buildRelicChord(f1: Float, f2: Float, f3: Float): ShortArray {
    val len = (SAMPLE_RATE * 0.32f).toInt()
    val out = ShortArray(len)
    for (i in 0 until len) {
      val t = i.toFloat() / SAMPLE_RATE
      val env = exp(-t * 7.5f) * (1f - exp(-t * 95f))
      val s =
        sin(2.0 * PI * f1 * t).toFloat() * 0.45f +
          sin(2.0 * PI * f2 * t).toFloat() * 0.35f +
          sin(2.0 * PI * f3 * t).toFloat() * 0.25f
      out[i] = (s * env * 15000f).toInt().coerceIn(-32767, 32767).toShort()
    }
    return out
  }

  private fun buildNearMiss(): ShortArray {
    val len = (SAMPLE_RATE * 0.18f).toInt()
    val out = ShortArray(len)
    var phase = 0.0
    for (i in 0 until len) {
      val p = i.toFloat() / len
      val freq = 880f + 440f * p
      phase += 2.0 * PI * freq / SAMPLE_RATE
      val env = exp(-p * 3.8f) * sin(PI * p).toFloat()
      out[i] = (sin(phase).toFloat() * env * 13000f).toInt().coerceIn(-32767, 32767).toShort()
    }
    return out
  }

  private fun buildShieldShatter(): ShortArray {
    val len = (SAMPLE_RATE * 0.26f).toInt()
    val out = ShortArray(len)
    for (i in 0 until len) {
      val t = i.toFloat() / SAMPLE_RATE
      val env = exp(-t * 14f)
      val chime = sin(2.0 * PI * 1180.0 * t).toFloat() + sin(2.0 * PI * 1760.0 * t).toFloat() * 0.5f
      val noise = (Random.nextFloat() * 2f - 1f) * 0.6f
      out[i] = ((chime * 0.5f + noise) * env * 16000f).toInt().coerceIn(-32767, 32767).toShort()
    }
    return out
  }

  private fun buildThud(f0: Float, f1: Float, dur: Float, gain: Float): ShortArray {
    val len = (SAMPLE_RATE * dur).toInt()
    val out = ShortArray(len)
    var phase = 0.0
    for (i in 0 until len) {
      val p = i.toFloat() / len
      val t = i.toFloat() / SAMPLE_RATE
      val freq = f0 + (f1 - f0) * p
      phase += 2.0 * PI * freq / SAMPLE_RATE
      val env = exp(-t * 10.5f)
      val noise = (Random.nextFloat() * 2f - 1f) * exp(-t * 24f) * 0.45f
      val s = (sin(phase).toFloat() + noise) * env * gain * 26000f
      out[i] = s.toInt().coerceIn(-32767, 32767).toShort()
    }
    return out
  }
}
