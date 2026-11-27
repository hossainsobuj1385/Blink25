package com.example.platform

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import com.example.domain.SoundType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

class SoundPlayer {
    suspend fun play(soundType: SoundType, volume: Float) = withContext(Dispatchers.IO) {
        if (soundType == SoundType.NO_SOUND || volume <= 0.01f) return@withContext
        val clampedVol = volume.coerceIn(0.05f, 1.0f)
        try {
            val sampleRate = 22050
            val samples = synthesizeTone(soundType, sampleRate, clampedVol)
            if (samples.isEmpty()) return@withContext

            val bufferSize = samples.size * 2
            val audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
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

            audioTrack.write(samples, 0, samples.size)
            audioTrack.play()
            val durationMs = (samples.size * 1000L) / sampleRate + 60L
            kotlinx.coroutines.delay(durationMs)
            audioTrack.stop()
            audioTrack.release()
        } catch (e: Exception) {
            Log.w("SoundPlayer", "Audio playback skipped: ${e.message}")
        }
    }

    private fun synthesizeTone(soundType: SoundType, sampleRate: Int, volume: Float): ShortArray {
        val durationSec = when (soundType) {
            SoundType.SOFT_TICK -> 0.07f
            SoundType.SOFT_POP -> 0.12f
            SoundType.QUIET_PULSE -> 0.22f
            SoundType.AIR_TONE -> 0.28f
            SoundType.GENTLE_BELL -> 0.38f
            SoundType.CALM_CHIME -> 0.45f
            SoundType.NO_SOUND -> return ShortArray(0)
        }
        val count = (sampleRate * durationSec).toInt()
        val out = ShortArray(count)
        val peak = 0.28f * volume * Short.MAX_VALUE

        for (i in 0 until count) {
            val t = i.toFloat() / sampleRate
            val env = when (soundType) {
                SoundType.SOFT_TICK -> exp(-45f * t) * fadeEdge(i, count, 60)
                SoundType.SOFT_POP -> sin(PI * (i.toDouble() / count)).toFloat()
                SoundType.QUIET_PULSE -> sin(PI * (i.toDouble() / count)).toFloat() * exp(-4f * t)
                SoundType.AIR_TONE -> sin(PI * (i.toDouble() / count)).toFloat() * exp(-3f * t)
                SoundType.GENTLE_BELL -> fadeEdge(i, count, 120) * exp(-7f * t)
                SoundType.CALM_CHIME -> fadeEdge(i, count, 140) * exp(-5.5f * t)
                SoundType.NO_SOUND -> 0f
            }
            val wave = when (soundType) {
                SoundType.SOFT_TICK -> sin(2.0 * PI * 420.0 * t)
                SoundType.SOFT_POP -> {
                    val freq = 320.0 + (220.0 * i / count)
                    sin(2.0 * PI * freq * t)
                }
                SoundType.QUIET_PULSE -> sin(2.0 * PI * 261.63 * t)
                SoundType.AIR_TONE -> 0.7 * sin(2.0 * PI * 396.0 * t) + 0.3 * sin(2.0 * PI * 594.0 * t)
                SoundType.GENTLE_BELL -> 0.75 * sin(2.0 * PI * 528.0 * t) + 0.25 * sin(2.0 * PI * 1056.0 * t)
                SoundType.CALM_CHIME -> {
                    val f = if (i < count / 2) 523.25 else 659.25
                    0.8 * sin(2.0 * PI * f * t) + 0.2 * sin(2.0 * PI * (f * 1.5) * t)
                }
                SoundType.NO_SOUND -> 0.0
            }
            out[i] = (wave * env * peak).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return out
    }

    private fun fadeEdge(index: Int, total: Int, edgeSamples: Int): Float {
        if (index < edgeSamples) return index.toFloat() / edgeSamples
        if (total - index < edgeSamples) return (total - index).toFloat() / edgeSamples
        return 1f
    }
}
