package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

object AudioEngine {
    private val scope = CoroutineScope(Dispatchers.Default)

    /**
     * Synthesizes rich procedural sound effects (Whoosh, Swoosh, Pop, Ding, Click, Boom)
     * using AudioTrack so they can be played with zero latency and zero external dependencies!
     */
    fun playSoundEffect(name: String, volume: Float = 0.9f) {
        scope.launch {
            try {
                val sampleRate = 44100
                val durationMs = when (name.lowercase()) {
                    "whoosh" -> 450
                    "swoosh" -> 350
                    "pop" -> 120
                    "ding" -> 600
                    "click" -> 80
                    "boom" -> 800
                    else -> 250
                }

                val numSamples = (sampleRate * durationMs) / 1000
                val buffer = ShortArray(numSamples)

                for (i in 0 until numSamples) {
                    val t = i.toDouble() / sampleRate
                    val progress = i.toDouble() / numSamples

                    val sampleVal = when (name.lowercase()) {
                        "whoosh" -> {
                            // Frequency sweep from 200Hz to 900Hz and down
                            val freq = 200.0 + 700.0 * sin(progress * PI)
                            val env = sin(progress * PI)
                            sin(2 * PI * freq * t) * env
                        }
                        "swoosh" -> {
                            // Faster airy high-pass sweep
                            val freq = 400.0 + 1200.0 * progress
                            val env = (1.0 - progress) * sin(progress * PI)
                            sin(2 * PI * freq * t) * env
                        }
                        "pop" -> {
                            // Quick pitch drop with exponential decay
                            val freq = 800.0 * exp(-progress * 15.0) + 150.0
                            val env = exp(-progress * 12.0)
                            sin(2 * PI * freq * t) * env
                        }
                        "ding" -> {
                            // Bell / chime fundamental + high harmonics with long decay
                            val f1 = 1200.0
                            val f2 = 2400.0
                            val env = exp(-progress * 5.0)
                            (0.7 * sin(2 * PI * f1 * t) + 0.3 * sin(2 * PI * f2 * t)) * env
                        }
                        "click" -> {
                            // Short crisp click impulse
                            val freq = 1800.0
                            val env = exp(-progress * 40.0)
                            sin(2 * PI * freq * t) * env
                        }
                        "boom" -> {
                            // Low frequency sub-bass thud with rumble
                            val freq = 120.0 * (1.0 - progress * 0.7)
                            val env = exp(-progress * 3.5)
                            sin(2 * PI * freq * t) * env
                        }
                        else -> {
                            sin(2 * PI * 440.0 * t) * exp(-progress * 5.0)
                        }
                    }

                    val clamped = (sampleVal * volume * Short.MAX_VALUE).coerceIn(
                        Short.MIN_VALUE.toDouble(),
                        Short.MAX_VALUE.toDouble()
                    ).toInt().toShort()
                    buffer[i] = clamped
                }

                val audioTrack = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
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
                    .setBufferSizeInBytes(buffer.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                audioTrack.write(buffer, 0, buffer.size)
                audioTrack.play()
                // Auto-release after playing
                scope.launch {
                    kotlinx.coroutines.delay(durationMs.toLong() + 100)
                    audioTrack.release()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Generates simulated realistic speech waveform amplitude samples for timeline display
     */
    fun generateWaveformAmplitudes(numPoints: Int = 120): List<Float> {
        val result = mutableListOf<Float>()
        var speechPhase = 0.0
        for (i in 0 until numPoints) {
            // Simulate voiceover cadences: speech bursts separated by brief pauses
            val cycle = (i % 24) / 24.0
            val isPause = cycle > 0.82
            if (isPause) {
                result.add(0.05f + (sin(i.toDouble()) * 0.03f).toFloat())
            } else {
                speechPhase += 0.35
                val amp = 0.25f + 0.55f * sin(speechPhase).toFloat().coerceAtLeast(0f) + (sin(i * 1.7) * 0.15f).toFloat()
                result.add(amp.coerceIn(0.08f, 0.95f))
            }
        }
        return result
    }
}
