package com.example.aiassistant

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.PresetReverb
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class VoiceManager(private val context: Context) : TextToSpeech.OnInitListener {

    private var localTts: TextToSpeech? = TextToSpeech(context, this)
    private var isLocalTtsReady = false
    private var mediaPlayer: MediaPlayer? = null
    private var activeEqualizer: Equalizer? = null
    private var activeBassBoost: BassBoost? = null
    private var activeReverb: PresetReverb? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isLocalTtsReady = true
            localTts?.language = Locale.UK
        }
    }

    fun speak(text: String, persona: AssistantPersona, languageCode: String = "en") {
        scope.launch {
            val success = streamEdgeNeuralAudio(text, persona, languageCode)
            if (!success) {
                withContext(Dispatchers.Main) {
                    speakFallback(text, persona)
                }
            }
        }
    }

    private suspend fun streamEdgeNeuralAudio(
        text: String,
        persona: AssistantPersona,
        languageCode: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val voiceName = when {
                languageCode.startsWith("hi") -> "hi-IN-MadhurNeural"
                languageCode.startsWith("es") -> "es-ES-AlvaroNeural"
                languageCode.startsWith("fr") -> "fr-FR-HenriNeural"
                languageCode.startsWith("de") -> "de-DE-ConradNeural"
                languageCode.startsWith("ar") -> "ar-SA-HamedNeural"
                languageCode.startsWith("zh") -> "zh-CN-YunxiNeural"
                persona == AssistantPersona.JARVIS -> "en-GB-RyanNeural"  // Paul Bettany British RP
                else -> "en-IE-EmilyNeural"                              // Kerry Condon Irish Dialect
            }

            // Dialect & Prosody Configuration
            val pitch = if (persona == AssistantPersona.JARVIS) "-5Hz" else "+4Hz"  // FRIDAY ~210Hz target
            val rate = if (persona == AssistantPersona.JARVIS) "-1%" else "+2%"

            // FRIDAY gets an expressive Irish melodic rise at trailing phrases
            val pitchContour = if (persona == AssistantPersona.JARVIS) {
                "contour='(0%, +0Hz) (100%, +0Hz)'"
            } else {
                "contour='(0%, +0Hz) (70%, +1Hz) (100%, +3Hz)'"
            }

            val audioBuffer = ByteArrayOutputStream()
            val latch = CountDownLatch(1)
            var socketError = false

            val requestId = UUID.randomUUID().toString().replace("-", "")
            val wsUrl = "wss://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1?TrustedClientToken=6A5AA1D4EA6640C1A77C88DE83723E04"

            val request = Request.Builder()
                .url(wsUrl)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .addHeader("Origin", "chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold")
                .build()

            val webSocket = httpClient.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    val timestamp = SimpleDateFormat("EEE MMM dd yyyy HH:mm:ss 'GMT'Z", Locale.US).format(Date())

                    val configMsg = "X-Timestamp:$timestamp\r\nContent-Type:application/json; charset=utf-8\r\nPath:speech.config\r\n\r\n" +
                            "{\"context\":{\"synthesis\":{\"audio\":{\"metadataoptions\":{\"sentenceBoundaryEnabled\":\"false\",\"wordBoundaryEnabled\":\"false\"},\"outputFormat\":\"audio-24khz-48kbitrate-mono-mp3\"}}}}"
                    webSocket.send(configMsg)

                    val xmlLang = if (persona == AssistantPersona.JARVIS) "en-GB" else "en-IE"
                    val ssml = """
                        <speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='$xmlLang'>
                            <voice name='$voiceName'>
                                <prosody pitch='$pitch' rate='$rate' $pitchContour>
                                    $text
                                </prosody>
                            </voice>
                        </speak>
                    """.trimIndent()

                    val ssmlMsg = "X-RequestId:$requestId\r\nContent-Type:application/ssml+xml\r\nX-Timestamp:$timestamp\r\nPath:ssml\r\n\r\n$ssml"
                    webSocket.send(ssmlMsg)
                }

                override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                    val rawBytes = bytes.toByteArray()
                    if (rawBytes.size > 2) {
                        val headerLen = (rawBytes[0].toInt() and 0xFF shl 8) or (rawBytes[1].toInt() and 0xFF)
                        if (rawBytes.size > headerLen + 2) {
                            audioBuffer.write(rawBytes, headerLen + 2, rawBytes.size - (headerLen + 2))
                        }
                    }
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    if (text.contains("Path:turn.end")) {
                        webSocket.close(1000, "Completed")
                        latch.countDown()
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    socketError = true
                    latch.countDown()
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    latch.countDown()
                }
            })

            val finished = latch.await(4500, TimeUnit.MILLISECONDS)
            if (socketError || !finished || audioBuffer.size() < 1000) {
                webSocket.cancel()
                return@withContext false
            }

            val audioData = audioBuffer.toByteArray()
            val tempAudioFile = File(context.cacheDir, "${persona.name.lowercase()}_voice_${System.currentTimeMillis()}.mp3")
            FileOutputStream(tempAudioFile).use { it.write(audioData) }

            withContext(Dispatchers.Main) {
                playProcessedAudio(tempAudioFile.absolutePath, persona)
            }
            return@withContext true
        } catch (e: Exception) {
            Log.e("VoiceManager", "Neural streaming error: ${e.message}")
            return@withContext false
        }
    }

    /**
     * Hardware DSP Equalizer:
     * - JARVIS: Paul Bettany profile (+2dB chest warmth @ 100-140Hz, -1.5dB mud @ 300Hz, +2dB consonants @ 3.5kHz)
     * - FRIDAY: Kerry Condon profile (+1.5dB body @ 200-240Hz, +2.0dB presence @ 4kHz, tight comms reverb)
     */
    private fun playProcessedAudio(filePath: String, persona: AssistantPersona) {
        try {
            cleanupAudioEffects()

            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_ASSISTANT)
                        .build()
                )
                setDataSource(filePath)
                prepare()

                val sessionId = audioSessionId
                if (sessionId != 0) {
                    activeEqualizer = Equalizer(0, sessionId).apply {
                        enabled = true
                        val numBands = numberOfBands
                        for (band in 0 until numBands) {
                            val centerFreqHz = getCenterFreq(band.toShort()) / 1000
                            if (persona == AssistantPersona.JARVIS) {
                                when (centerFreqHz) {
                                    in 60..150 -> setBandLevel(band.toShort(), 200)      // +2.0 dB Chest Warmth
                                    in 200..400 -> setBandLevel(band.toShort(), -150)    // -1.5 dB Boxiness Cut
                                    in 3000..5000 -> setBandLevel(band.toShort(), 200)  // +2.0 dB British Consonants
                                    else -> setBandLevel(band.toShort(), 0)
                                }
                            } else {
                                // FRIDAY (Kerry Condon)
                                when (centerFreqHz) {
                                    in 0..110 -> setBandLevel(band.toShort(), -250)      // High-Pass Filter Cut
                                    in 200..260 -> setBandLevel(band.toShort(), 150)     // +1.5 dB Body & Warmth
                                    in 3500..5000 -> setBandLevel(band.toShort(), 200)   // +2.0 dB Accent Presence
                                    else -> setBandLevel(band.toShort(), 0)
                                }
                            }
                        }
                    }

                    if (persona == AssistantPersona.JARVIS) {
                        activeBassBoost = BassBoost(0, sessionId).apply {
                            enabled = true
                            setStrength(150.toShort())
                        }
                    } else {
                        // Tactical In-Helmet Comms Reverb for FRIDAY
                        activeReverb = PresetReverb(0, sessionId).apply {
                            preset = PresetReverb.PRESET_SMALLROOM
                            enabled = true
                        }
                    }
                }

                setOnCompletionListener {
                    cleanupAudioEffects()
                }
                start()
            }
        } catch (e: Exception) {
            Log.e("VoiceManager", "DSP Audio Playback error", e)
        }
    }

    private fun speakFallback(text: String, persona: AssistantPersona) {
        if (!isLocalTtsReady || localTts == null) return

        try {
            localTts?.language = if (persona == AssistantPersona.JARVIS) Locale.UK else Locale.US
            val voices = localTts?.voices ?: emptySet()

            for (voice in voices) {
                val name = voice.name.lowercase()
                if (persona == AssistantPersona.JARVIS) {
                    if (name.contains("en-gb") && (name.contains("male") || name.contains("man") || name.contains("male-dense"))) {
                        localTts?.voice = voice
                        break
                    }
                } else {
                    if ((name.contains("female") || name.contains("woman") || name.contains("en-ie")) && !name.contains("male")) {
                        localTts?.voice = voice
                        break
                    }
                }
            }

            if (persona == AssistantPersona.JARVIS) {
                localTts?.setPitch(0.80f)
                localTts?.setSpeechRate(0.96f)
            } else {
                localTts?.setPitch(1.20f)       // Targets ~210Hz
                localTts?.setSpeechRate(1.03f)
            }

            localTts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "FALLBACK_VOICE")
        } catch (e: Exception) {
            Log.e("VoiceManager", "Fallback TTS error", e)
        }
    }

    private fun cleanupAudioEffects() {
        try {
            activeEqualizer?.release()
            activeEqualizer = null
            activeBassBoost?.release()
            activeBassBoost = null
            activeReverb?.release()
            activeReverb = null
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (e: Exception) {
            // Ignore release exceptions
        }
    }

    fun shutdown() {
        cleanupAudioEffects()
        localTts?.stop()
        localTts?.shutdown()
    }
}
