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
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat).coerceAtLeast(4096) * 2

    private var audioRecord: AudioRecord? = null
    @Volatile private var isRecording = false
    private var recordingThread: Thread? = null

    // Adaptive noise tracking
    private var noiseFloor = 180f

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
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    channelConfig,
                    audioFormat,
                    bufferSize
                )
            }

            audioRecord?.startRecording()
            isRecording = true

            recordingThread = Thread {
                val pcmBuffer = ShortArray(1024)
                val chunkAccumulator = mutableListOf<Byte>()
                var speechDetected = false
                var silenceFrames = 0

                while (isRecording) {
                    val readCount = audioRecord?.read(pcmBuffer, 0, pcmBuffer.size) ?: 0
                    if (readCount > 0) {
                        var maxPeak = 0
                        val byteData = ByteArray(readCount * 2)

                        // 3.5x Dynamic Audio Pre-Amp
                        for (i in 0 until readCount) {
                            var sample = (pcmBuffer[i] * 3.5f).toInt()
                            if (sample > Short.MAX_VALUE) sample = Short.MAX_VALUE.toInt()
                            if (sample < Short.MIN_VALUE) sample = Short.MIN_VALUE.toInt()

                            val absVal = abs(sample)
                            if (absVal > maxPeak) maxPeak = absVal

                            byteData[i * 2] = (sample and 0xFF).toByte()
                            byteData[i * 2 + 1] = ((sample shr 8) and 0xFF).toByte()
                        }

                        // Auto-adjust noise floor for ambient acoustics
                        if (!speechDetected) {
                            noiseFloor = (noiseFloor * 0.95f) + (maxPeak * 0.05f)
                        }

                        val dynamicThreshold = (noiseFloor * 1.6f).coerceIn(250f, 1200f)

                        if (maxPeak > dynamicThreshold) {
                            speechDetected = true
                            silenceFrames = 0
                        } else if (speechDetected) {
                            silenceFrames++
                        }

                        if (speechDetected) {
                            for (b in byteData) chunkAccumulator.add(b)

                            // Sentence completion trigger (2.2s cap or 10 frames of silence)
                            if (chunkAccumulator.size >= 16000 * 2 * 2.2 || silenceFrames > 10) {
                                if (chunkAccumulator.size >= 6400) { // Minimum 0.2s speech
                                    onAudioChunkReady(chunkAccumulator.toByteArray())
                                }
                                chunkAccumulator.clear()
                                speechDetected = false
                                silenceFrames = 0
                            }
                        }
                    }
                }
            }
            recordingThread?.priority = Thread.MAX_PRIORITY
            recordingThread?.start()
        } catch (e: Exception) {
            Log.e("ContinuousRecorder", "Recording start failure", e)
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
