package com.example.aiassistant

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlin.math.abs

class ContinuousAudioRecorder(private val onAudioChunkReady: (ByteArray) -> Unit) {

    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat) * 2

    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var recordingThread: Thread? = null

    @SuppressLint("MissingPermission")
    fun startListening() {
        if (isRecording) return

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION, // Optimized for AI STT & Noise Suppression
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e("ContinuousRecorder", "AudioRecord initialization failed")
                return
            }

            audioRecord?.startRecording()
            isRecording = true

            recordingThread = Thread {
                val pcmBuffer = ShortArray(bufferSize / 2)
                val chunkAccumulator = mutableListOf<Byte>()
                var soundDetected = false
                var silenceCounter = 0

                while (isRecording) {
                    val readCount = audioRecord?.read(pcmBuffer, 0, pcmBuffer.size) ?: 0
                    if (readCount > 0) {
                        var maxAmplitude = 0

                        // Apply 2.5x Software Audio Gain Boost for distance sensitivity
                        val byteData = ByteArray(readCount * 2)
                        for (i in 0 until readCount) {
                            var sample = (pcmBuffer[i] * 2.5f).toInt()
                            if (sample > Short.MAX_VALUE) sample = Short.MAX_VALUE.toInt()
                            if (sample < Short.MIN_VALUE) sample = Short.MIN_VALUE.toInt()

                            val absVal = abs(sample)
                            if (absVal > maxAmplitude) maxAmplitude = absVal

                            byteData[i * 2] = (sample and 0xFF).toByte()
                            byteData[i * 2 + 1] = ((sample shr 8) and 0xFF).toByte()
                        }

                        // Voice Activity Threshold (Lowered so you don't need to yell)
                        if (maxAmplitude > 700) {
                            soundDetected = true
                            silenceCounter = 0
                        } else if (soundDetected) {
                            silenceCounter++
                        }

                        if (soundDetected) {
                            for (b in byteData) chunkAccumulator.add(b)

                            // 1.8s chunk buffer window or end of phrase
                            if (chunkAccumulator.size >= 16000 * 2 * 2 || silenceCounter > 8) {
                                onAudioChunkReady(chunkAccumulator.toByteArray())
                                chunkAccumulator.clear()
                                soundDetected = false
                                silenceCounter = 0
                            }
                        }
                    }
                }
            }
            recordingThread?.start()
        } catch (e: Exception) {
            Log.e("ContinuousRecorder", "Recording thread failed", e)
        }
    }

    fun stopListening() {
        isRecording = false
        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
            recordingThread?.interrupt()
            recordingThread = null
        } catch (e: Exception) {
            Log.e("ContinuousRecorder", "Stop recording error", e)
        }
    }
}
