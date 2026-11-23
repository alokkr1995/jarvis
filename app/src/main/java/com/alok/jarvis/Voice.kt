package com.alok.jarvis

import android.content.Context
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import org.json.JSONObject
import java.io.File
import java.util.Locale

/** Jarvis की आवाज़: ElevenLabs (अगर key है), वरना फ़ोन की अपनी आवाज़ (fallback)। */
class Voice(private val context: Context) {
    private val settings = Settings(context)
    private val main = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    init {
        tts = TextToSpeech(context) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            tts?.setLanguage(Locale("hi", "IN"))
        }
    }

    /** text बोलो, ख़त्म होने पर onDone (main thread पर) */
    fun speak(text: String, onDone: () -> Unit) {
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
                JarvisService.log("ElevenLabs error, फ़ोन की आवाज़ इस्तेमाल: ${e.message}")
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
