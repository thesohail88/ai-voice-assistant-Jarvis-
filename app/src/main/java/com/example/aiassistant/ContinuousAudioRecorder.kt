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

    // Sensitive speech threshold
    private val silenceThreshold = 300.0
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

            // Cut down silence wait to ~500ms (6 frames) for near-instant dispatch
            if (silenceFramesCount > 6) {
                isSpeaking = false
                silenceFramesCount = 0
                val pcmData: ByteArray
                synchronized(outputStream) {
                    pcmData = outputStream.toByteArray()
                    outputStream.reset()
                }
                if (pcmData.size > 6000) {
                    val wavData = addWavHeader(pcmData, sampleRate, 1, 16)
                    onSpeechDetected(wavData)
                }
            }
        }
    }

    private fun addWavHeader(pcmData: ByteArray, sampleRate: Int, channels: Int, bitDepth: Int): ByteArray {
        val totalDataLen = pcmData.size + 36
        val byteRate = sampleRate * channels * (bitDepth / 8)
        val header = ByteArray(44)

        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = ((totalDataLen shr 8) and 0xff).toByte()
        header[6] = ((totalDataLen shr 16) and 0xff).toByte()
        header[7] = ((totalDataLen shr 24) and 0xff).toByte()
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        header[16] = 16
        header[17] = 0
        header[18] = 0
        header[19] = 0
        header[20] = 1
        header[21] = 0
        header[22] = channels.toByte()
        header[23] = 0
        header[24] = (sampleRate and 0xff).toByte()
        header[25] = ((sampleRate shr 8) and 0xff).toByte()
        header[26] = ((sampleRate shr 16) and 0xff).toByte()
        header[27] = ((sampleRate shr 24) and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = ((byteRate shr 8) and 0xff).toByte()
        header[30] = ((byteRate shr 16) and 0xff).toByte()
        header[31] = ((byteRate shr 24) and 0xff).toByte()
        header[32] = (channels * (bitDepth / 8)).toByte()
        header[33] = 0
        header[34] = bitDepth.toByte()
        header[35] = 0
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        header[40] = (pcmData.size and 0xff).toByte()
        header[41] = ((pcmData.size shr 8) and 0xff).toByte()
        header[42] = ((pcmData.size shr 16) and 0xff).toByte()
        header[43] = ((pcmData.size shr 24) and 0xff).toByte()

        return header + pcmData
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
