package com.example.aiassistant

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
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
            // Select Neural Voice Name
            val voiceName = when {
                languageCode.startsWith("hi") -> "hi-IN-MadhurNeural"
                languageCode.startsWith("es") -> "es-ES-AlvaroNeural"
                languageCode.startsWith("fr") -> "fr-FR-HenriNeural"
                languageCode.startsWith("de") -> "de-DE-ConradNeural"
                languageCode.startsWith("ar") -> "ar-SA-HamedNeural"
                languageCode.startsWith("zh") -> "zh-CN-YunxiNeural"
                persona == AssistantPersona.JARVIS -> "en-GB-RyanNeural"  // Paul Bettany British Tone
                else -> "en-IE-EmilyNeural"                              // Kerry Condon Crisp Irish Tone
            }

            val pitch = if (persona == AssistantPersona.JARVIS) "-3Hz" else "+2Hz"
            val rate = if (persona == AssistantPersona.JARVIS) "-2%" else "+2%"

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
                    
                    // 1. Send speech config
                    val configMsg = "X-Timestamp:$timestamp\r\nContent-Type:application/json; charset=utf-8\r\nPath:speech.config\r\n\r\n" +
                            "{\"context\":{\"synthesis\":{\"audio\":{\"metadataoptions\":{\"sentenceBoundaryEnabled\":\"false\",\"wordBoundaryEnabled\":\"false\"},\"outputFormat\":\"audio-24khz-48kbitrate-mono-mp3\"}}}}"
                    webSocket.send(configMsg)

                    // 2. Send SSML payload
                    val ssml = "<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='en-US'>" +
                            "<voice name='$voiceName'><prosody pitch='$pitch' rate='$rate'>$text</prosody></voice></speak>"
                    
                    val ssmlMsg = "X-RequestId:$requestId\r\nContent-Type:application/ssml+xml\r\nX-Timestamp:$timestamp\r\nPath:ssml\r\n\r\n$ssml"
                    webSocket.send(ssmlMsg)
                }

                override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                    val rawBytes = bytes.toByteArray()
                    // Binary header check
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
            val tempAudioFile = File(context.cacheDir, "neural_voice_${System.currentTimeMillis()}.mp3")
            FileOutputStream(tempAudioFile).use { it.write(audioData) }

            withContext(Dispatchers.Main) {
                mediaPlayer?.stop()
                mediaPlayer?.release()
                mediaPlayer = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .setUsage(AudioAttributes.USAGE_ASSISTANT)
                            .build()
                    )
                    setDataSource(tempAudioFile.absolutePath)
                    prepare()
                    start()
                }
            }
            return@withContext true
        } catch (e: Exception) {
            Log.e("VoiceManager", "Edge neural streaming error: ${e.message}")
            return@withContext false
        }
    }

    private fun speakFallback(text: String, persona: AssistantPersona) {
        if (!isLocalTtsReady || localTts == null) return

        try {
            localTts?.language = Locale.UK
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
                localTts?.setPitch(0.82f)
                localTts?.setSpeechRate(0.95f)
            } else {
                localTts?.setPitch(1.15f)
                localTts?.setSpeechRate(1.05f)
            }

            localTts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "FALLBACK_VOICE")
        } catch (e: Exception) {
            Log.e("VoiceManager", "Fallback TTS error", e)
        }
    }

    fun shutdown() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        localTts?.stop()
        localTts?.shutdown()
    }
}
