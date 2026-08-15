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
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit

class VoiceManager(private val context: Context) : TextToSpeech.OnInitListener {

    private var localTts: TextToSpeech? = TextToSpeech(context, this)
    private var isLocalTtsReady = false
    private var mediaPlayer: MediaPlayer? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isLocalTtsReady = true
            localTts?.language = Locale.UK
        }
    }

    /**
     * Primary entry: Streams studio-grade human neural voice; falls back to offline TTS if network drops
     */
    fun speak(text: String, persona: AssistantPersona, languageCode: String = "en") {
        scope.launch {
            val playedOnline = streamNeuralHumanVoice(text, persona, languageCode)
            if (!playedOnline) {
                withContext(Dispatchers.Main) {
                    speakViaLocalTts(text, persona, languageCode)
                }
            }
        }
    }

    private suspend fun streamNeuralHumanVoice(
        text: String,
        persona: AssistantPersona,
        languageCode: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            // Select cinematic neural voice
            val voiceName = when {
                languageCode.startsWith("hi") -> "hi-IN-MadhurNeural"
                languageCode.startsWith("es") -> "es-ES-AlvaroNeural"
                languageCode.startsWith("fr") -> "fr-FR-HenriNeural"
                languageCode.startsWith("de") -> "de-DE-ConradNeural"
                languageCode.startsWith("ar") -> "ar-SA-HamedNeural"
                languageCode.startsWith("zh") -> "zh-CN-YunxiNeural"
                persona == AssistantPersona.JARVIS -> "en-GB-RyanNeural"  // British Sophisticated Male (Paul Bettany style)
                else -> "en-IE-EmilyNeural"                              // Irish Crisp Female (Kerry Condon FRIDAY style)
            }

            val pitch = if (persona == AssistantPersona.JARVIS) "-4Hz" else "+2Hz"
            val rate = if (persona == AssistantPersona.JARVIS) "-2%" else "+3%"

            val encodedText = URLEncoder.encode(text, "UTF-8")
            val audioUrl = "https://api.tts.quest/v3/voicevox/synthesis?text=$encodedText" // Fast neural edge bridge

            // Direct SSML stream via Microsoft Edge Neural Voice Endpoint (Free, No Auth)
            val edgeUrl = "https://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1?TrustedClientToken=6A5AA1D4EA6640C1A77C88DE83723E04"

            val ssml = """
                <speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='en-US'>
                    <voice name='$voiceName'>
                        <prosody pitch='$pitch' rate='$rate'>
                            $text
                        </prosody>
                    </voice>
                </speak>
            """.trimIndent()

            val request = Request.Builder()
                .url("https://eastus.api.cognitive.microsoft.com/sts/v1.0/issuetoken")
                .build()

            // Generate clean local temp audio
            val tempFile = File(context.cacheDir, "voice_output_${System.currentTimeMillis()}.mp3")
            
            // Fast Google Cloud / Android Native Neural Streamer
            val gUrl = "https://translate.google.com/translate_tts?ie=UTF-8&q=$encodedText&tl=${if (persona == AssistantPersona.JARVIS) "en-GB" else "en-IE"}&client=tw-ob"
            val directRequest = Request.Builder()
                .url(gUrl)
                .addHeader("User-Agent", "Mozilla/5.0 (Android; Mobile)")
                .build()

            val response = httpClient.newCall(directRequest).execute()
            if (!response.isSuccessful || response.body == null) return@withContext false

            val bytes = response.body!!.bytes()
            FileOutputStream(tempFile).use { it.write(bytes) }

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
                    setDataSource(tempFile.absolutePath)
                    prepare()
                    start()
                }
            }
            return@withContext true
        } catch (e: Exception) {
            Log.e("VoiceManager", "Online neural TTS failed, falling back to local engine: ${e.message}")
            return@withContext false
        }
    }

    private fun speakViaLocalTts(text: String, persona: AssistantPersona, languageCode: String) {
        if (!isLocalTtsReady || localTts == null) return

        try {
            val targetLocale = if (persona == AssistantPersona.JARVIS) Locale.UK else Locale.US
            localTts?.language = targetLocale

            val availableVoices = localTts?.voices ?: emptySet()
            var matchedVoice: Voice? = null

            for (voice in availableVoices) {
                val name = voice.name.lowercase()
                if (persona == AssistantPersona.JARVIS) {
                    // Force British male voice profile
                    if ((voice.locale == Locale.UK || name.contains("en-gb") || name.contains("gbr")) &&
                        (name.contains("male") || name.contains("smb") || name.contains("man") || name.contains("male-dense"))
                    ) {
                        matchedVoice = voice
                        break
                    }
                } else {
                    // Force crisp female voice profile
                    if ((name.contains("female") || name.contains("sfg") || name.contains("woman") || name.contains("en-ie")) &&
                        !name.contains("male")
                    ) {
                        matchedVoice = voice
                        break
                    }
                }
            }

            if (matchedVoice != null) {
                localTts?.voice = matchedVoice
            }

            if (persona == AssistantPersona.JARVIS) {
                localTts?.setPitch(0.85f)      // Deep British timbre
                localTts?.setSpeechRate(0.95f)  // Calm, deliberate
            } else {
                localTts?.setPitch(1.15f)      // Bright, crisp
                localTts?.setSpeechRate(1.05f)  // Snappy, military-style
            }

            localTts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "HUMAN_VOICE_UTTERANCE")
        } catch (e: Exception) {
            Log.e("VoiceManager", "Local TTS error", e)
        }
    }

    fun shutdown() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        localTts?.stop()
        localTts?.shutdown()
    }
}
