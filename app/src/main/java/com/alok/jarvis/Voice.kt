package com.alok.jarvis

import android.content.Context
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice as TtsVoice
import org.json.JSONObject
import java.io.File
import java.util.Locale

/**
 * Jarvis's voice: ElevenLabs if a key is set, otherwise Google's free Hindi voice on the phone
 * (a male voice by default; can be changed in Settings → Jarvis voice).
 */
class Voice(private val context: Context) {
    companion object {
        const val GOOGLE_TTS = "com.google.android.tts"
        // Google Hindi voices that sound male, best first
        private val MALE_HINTS = listOf("hi-in-x-hic", "hi-in-x-hie", "hi-IN-Standard-B", "hi-IN-Standard-C")

        /** Hindi voices available in the TTS engine, male-sounding ones first */
        fun hindiVoices(tts: TextToSpeech): List<TtsVoice> {
            val all = try { tts.voices?.toList() ?: emptyList() } catch (_: Exception) { emptyList() }
            val hi = all.filter { it.locale.language == "hi" && !it.features.contains("notInstalled") }
            return hi.sortedWith(compareBy<TtsVoice>(
                { v -> MALE_HINTS.indexOfFirst { v.name.startsWith(it, true) }.let { if (it < 0) 99 else it } },
                { v -> if (v.isNetworkConnectionRequired) 1 else 0 },
                { it.name }))
        }

        /** Strip things that sound bad when read aloud */
        fun clean(text: String) = text.replace(Regex("[*#_`>~]"), "").replace(Regex("\\s+"), " ").trim()
    }

    private val settings = Settings(context)
    private val main = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var appliedVoice: String? = null

    init {
        val listener = TextToSpeech.OnInitListener { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) applyVoice()
        }
        tts = try { TextToSpeech(context, listener, GOOGLE_TTS) } catch (_: Exception) { TextToSpeech(context, listener) }
    }

    private fun applyVoice() {
        val t = tts ?: return
        t.setLanguage(Locale("hi", "IN"))
        val voices = hindiVoices(t)
        val chosen = voices.firstOrNull { it.name == settings.ttsVoice } ?: voices.firstOrNull()
        if (chosen != null) t.setVoice(chosen)
        appliedVoice = settings.ttsVoice
        // a touch deeper and calmer
        val male = chosen != null && MALE_HINTS.any { chosen.name.startsWith(it, true) }
        t.setPitch(if (male) 0.92f else 0.8f)
        t.setSpeechRate(1.0f)
    }

    /** text बोलो, ख़त्म होने पर onDone (main thread पर) */
    fun speak(raw: String, onDone: () -> Unit) {
        val text = clean(raw)
        if (settings.elevenKey.isBlank()) { speakFallback(text, onDone); return }
        Thread {
            try {
                val body = JSONObject().put("text", text).put("model_id", "eleven_multilingual_v2")
                val audio = Http.postRaw(
                    "https://api.elevenlabs.io/v1/text-to-speech/${settings.voiceId}?output_format=mp3_44100_128",
                    mapOf("xi-api-key" to settings.elevenKey, "accept" to "audio/mpeg"),
                    body
                )
                val f = File(context.cacheDir, "jarvis_reply.mp3")
                f.writeBytes(audio)
                main.post { playFile(f, onDone) }
            } catch (e: Exception) {
                JarvisService.log("ElevenLabs error, using phone voice: ${e.message}")
                main.post { speakFallback(text, onDone) }
            }
        }.start()
    }

    private fun playFile(f: File, onDone: () -> Unit) {
        player?.release()
        player = MediaPlayer().apply {
            setDataSource(f.absolutePath)
            setOnCompletionListener { it.release(); player = null; onDone() }
            setOnErrorListener { mp, _, _ -> mp.release(); player = null; onDone(); true }
            prepare()
            start()
        }
    }

    private fun speakFallback(text: String, onDone: () -> Unit) {
        val t = tts
        if (t == null || !ttsReady) { main.post(onDone); return }
        if (settings.ttsVoice != appliedVoice) applyVoice()
        t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) { main.post(onDone) }
            @Deprecated("old API") override fun onError(id: String?) { main.post(onDone) }
        })
        t.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis")
    }

    fun shutdown() {
        player?.release(); player = null
        tts?.shutdown(); tts = null
    }
}
