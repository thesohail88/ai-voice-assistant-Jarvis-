package com.example.aiassistant

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream
import kotlin.math.sqrt

class ContinuousAudioRecorder(
    private val onSpeechDetected: (ByteArray) -> Unit
) {
    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat).coerceAtLeast(4096)

    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var recordingJob: Job? = null

    private val silenceThreshold = 1200.0
    private val outputStream = ByteArrayOutputStream()
    private var isSpeaking = false
    private var silenceFramesCount = 0

    @SuppressLint("MissingPermission")
    fun startListening() {
        if (isRecording) return

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e("AudioRecorder", "AudioRecord initialization failed")
                return
            }

            audioRecord?.startRecording()
            isRecording = true

            recordingJob = CoroutineScope(Dispatchers.IO).launch {
                val buffer = ShortArray(bufferSize / 2)

                while (isRecording && isActive) {
                    val readCount = audioRecord?.read(buffer, 0, buffer.size) ?: 0

                    if (readCount > 0) {
                        val rms = calculateRMS(buffer, readCount)
                        handleAudioChunk(buffer, readCount, rms)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("AudioRecorder", "Error starting AudioRecord", e)
        }
    }

    private fun calculateRMS(buffer: ShortArray, readCount: Int): Double {
        var sum = 0.0
        for (i in 0 until readCount) {
            sum += (buffer[i] * buffer[i]).toDouble()
        }
        return sqrt(sum / readCount)
    }

    private fun handleAudioChunk(buffer: ShortArray, readCount: Int, rms: Double) {
        val byteChunk = ByteArray(readCount * 2)
        for (i in 0 until readCount) {
            byteChunk[i * 2] = (buffer[i].toInt() and 0xFF).toByte()
            byteChunk[i * 2 + 1] = ((buffer[i].toInt() shr 8) and 0xFF).toByte()
        }

        if (rms > silenceThreshold) {
            isSpeaking = true
            silenceFramesCount = 0
            synchronized(outputStream) {
                outputStream.write(byteChunk, 0, byteChunk.size)
            }
        } else if (isSpeaking) {
            silenceFramesCount++
            synchronized(outputStream) {
                outputStream.write(byteChunk, 0, byteChunk.size)
            }

            if (silenceFramesCount > 15) {
                isSpeaking = false
                silenceFramesCount = 0
                val completeUtterance: ByteArray
                synchronized(outputStream) {
                    completeUtterance = outputStream.toByteArray()
                    outputStream.reset()
                }
                if (completeUtterance.isNotEmpty()) {
                    onSpeechDetected(completeUtterance)
                }
            }
        }
    }

    fun stopListening() {
        isRecording = false
        recordingJob?.cancel()
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        audioRecord = null
    }
}
