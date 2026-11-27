package com.example.platform

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import com.example.domain.SoundType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.tanh

class SoundPlayer {
    private val sampleCache = ConcurrentHashMap<String, ShortArray>()

    suspend fun play(soundType: SoundType, volume: Float) = withContext(Dispatchers.IO) {
        if (soundType == SoundType.NO_SOUND || volume <= 0.01f) return@withContext
        val clampedVol = volume.coerceIn(0.05f, 1.0f)
        val quantizedStep = (clampedVol * 20f).toInt().coerceIn(1, 20)
        val cacheKey = "${soundType.name}_$quantizedStep"

        try {
            val sampleRate = 22050
            val samples = sampleCache.getOrPut(cacheKey) {
                synthesizeTone(soundType, sampleRate, quantizedStep / 20f)
            }
            if (samples.isEmpty()) return@withContext

            val bufferSize = samples.size * 2
            val audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            audioTrack.setVolume(1.0f)
            audioTrack.write(samples, 0, samples.size)
            audioTrack.play()
            val durationMs = (samples.size * 1000L) / sampleRate + 60L
            delay(durationMs)
            audioTrack.stop()
            audioTrack.release()
        } catch (e: Exception) {
            Log.w("SoundPlayer", "Audio playback skipped: ${e.message}")
        }
    }

    private fun synthesizeTone(soundType: SoundType, sampleRate: Int, volume: Float): ShortArray {
        val durationSec = when (soundType) {
            SoundType.SOFT_TICK -> 0.09f
            SoundType.SOFT_POP -> 0.14f
            SoundType.QUIET_PULSE -> 0.24f
            SoundType.AIR_TONE -> 0.30f
            SoundType.GENTLE_BELL -> 0.42f
            SoundType.CALM_CHIME -> 0.48f
            SoundType.WATER_DROP -> 0.22f
            SoundType.CRYSTAL_PING -> 0.40f
            SoundType.BAMBOO_TAP -> 0.14f
            SoundType.MORNING_BIRD -> 0.36f
            SoundType.BRIGHT_CHORD -> 0.52f
            SoundType.NO_SOUND -> return ShortArray(0)
        }
        val count = (sampleRate * durationSec).toInt()
        val out = ShortArray(count)
        // High-headroom gain (0.96f) with soft-limiter shaping so phone speakers play clearly & loudly
        val gain = 0.96f * volume

        for (i in 0 until count) {
            val t = i.toFloat() / sampleRate
            val env = when (soundType) {
                SoundType.SOFT_TICK -> exp(-34f * t) * fadeEdge(i, count, 60)
                SoundType.SOFT_POP -> sin(PI * (i.toDouble() / count)).toFloat()
                SoundType.QUIET_PULSE -> sin(PI * (i.toDouble() / count)).toFloat() * exp(-3.5f * t)
                SoundType.AIR_TONE -> sin(PI * (i.toDouble() / count)).toFloat() * exp(-2.8f * t)
                SoundType.GENTLE_BELL -> fadeEdge(i, count, 110) * exp(-5.8f * t)
                SoundType.CALM_CHIME -> fadeEdge(i, count, 120) * exp(-4.8f * t)
                SoundType.WATER_DROP -> fadeEdge(i, count, 80) * exp(-10f * t)
                SoundType.CRYSTAL_PING -> fadeEdge(i, count, 90) * exp(-6.2f * t)
                SoundType.BAMBOO_TAP -> fadeEdge(i, count, 40) * exp(-22f * t)
                SoundType.MORNING_BIRD -> {
                    val phase = (i.toFloat() / count)
                    val pulse = if (phase < 0.45f) sin(PI * phase / 0.45) else if (phase > 0.55f) sin(PI * (phase - 0.55) / 0.45) else 0.0
                    pulse.toFloat()
                }
                SoundType.BRIGHT_CHORD -> fadeEdge(i, count, 120) * exp(-4.2f * t)
                SoundType.NO_SOUND -> 0f
            }
            val wave = when (soundType) {
                SoundType.SOFT_TICK -> 0.7 * sin(2.0 * PI * 680.0 * t) + 0.3 * sin(2.0 * PI * 1360.0 * t)
                SoundType.SOFT_POP -> {
                    val freq = 440.0 + (340.0 * i / count)
                    sin(2.0 * PI * freq * t)
                }
                SoundType.QUIET_PULSE -> 0.7 * sin(2.0 * PI * 440.0 * t) + 0.3 * sin(2.0 * PI * 880.0 * t)
                SoundType.AIR_TONE -> 0.65 * sin(2.0 * PI * 528.0 * t) + 0.35 * sin(2.0 * PI * 1056.0 * t)
                SoundType.GENTLE_BELL -> 0.70 * sin(2.0 * PI * 660.0 * t) + 0.30 * sin(2.0 * PI * 1320.0 * t)
                SoundType.CALM_CHIME -> {
                    val f = if (i < count / 2) 587.33 else 880.0
                    0.75 * sin(2.0 * PI * f * t) + 0.25 * sin(2.0 * PI * (f * 1.5) * t)
                }
                SoundType.WATER_DROP -> {
                    val freq = 620.0 + (980.0 * i / count)
                    sin(2.0 * PI * freq * t)
                }
                SoundType.CRYSTAL_PING -> 0.72 * sin(2.0 * PI * 1174.66 * t) + 0.28 * sin(2.0 * PI * 2349.32 * t)
                SoundType.BAMBOO_TAP -> 0.68 * sin(2.0 * PI * 920.0 * t) + 0.32 * sin(2.0 * PI * 1480.0 * t)
                SoundType.MORNING_BIRD -> {
                    val sweep = if (i < count / 2) 1100.0 + (550.0 * i / (count / 2)) else 1250.0 + (600.0 * (i - count / 2) / (count / 2))
                    0.8 * sin(2.0 * PI * sweep * t) + 0.2 * sin(2.0 * PI * sweep * 2.0 * t)
                }
                SoundType.BRIGHT_CHORD -> {
                    val third = count / 3
                    val baseFreq = when {
                        i < third -> 523.25
                        i < third * 2 -> 659.25
                        else -> 783.99
                    }
                    0.65 * sin(2.0 * PI * baseFreq * t) + 0.35 * sin(2.0 * PI * (baseFreq * 2.0) * t)
                }
                SoundType.NO_SOUND -> 0.0
            }
            val shaped = tanh(wave * env * gain * 1.75).toFloat()
            out[i] = (shaped * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return out
    }

    private fun fadeEdge(index: Int, total: Int, edgeSamples: Int): Float {
        if (index < edgeSamples) return index.toFloat() / edgeSamples
        if (total - index < edgeSamples) return (total - index).toFloat() / edgeSamples
        return 1f
    }
}
