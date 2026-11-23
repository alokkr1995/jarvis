package com.alok.jarvis

import ai.picovoice.porcupine.Porcupine
import ai.picovoice.porcupine.PorcupineManager
import ai.picovoice.porcupine.PorcupineManagerCallback
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * हमेशा चलने वाली service:
 * "Jarvis" सुनो → beep → आपकी बात सुनो → Claude → जवाब बोलो → follow-up सुनो → वापस wake word।
 */
class JarvisService : Service() {

    companion object {
        var instance: JarvisService? = null
        var logListener: ((String) -> Unit)? = null
        fun log(msg: String) {
            android.util.Log.d("Jarvis", msg)
            Handler(Looper.getMainLooper()).post { logListener?.invoke(msg) }
        }
        private const val CHANNEL = "jarvis_service"
        private val STOP_WORDS = listOf("बस", "bas", "thank you", "थैंक यू", "धन्यवाद", "stop", "रहने दो", "chup")
    }

    private val main = Handler(Looper.getMainLooper())
    private lateinit var settings: Settings
    private lateinit var brain: Brain
    private lateinit var voice: Voice
    private var porcupine: PorcupineManager? = null
    private var recognizer: SpeechRecognizer? = null
    private var inSession = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        settings = Settings(this)
        brain = Brain(this)
        voice = Voice(this)
        Reminders.ensureChannel(this)
        startAsForeground()
        startWakeWord()
    }

    private fun startAsForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Jarvis listening", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Jarvis सुन रहा है")
            .setContentText("बोलिए \"Jarvis\"")
            .setContentIntent(open)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(1, n)
        }
    }

    // ---------- Wake word ----------
    private fun startWakeWord() {
        try {
            if (porcupine == null) {
                if (settings.picovoiceKey.isBlank()) { log("Picovoice key नहीं है — settings में डालिए"); return }
                porcupine = PorcupineManager.Builder()
                    .setAccessKey(settings.picovoiceKey)
                    .setKeyword(Porcupine.BuiltInKeyword.JARVIS)
                    .setSensitivity(settings.sensitivity)
                    .build(this, PorcupineManagerCallback { _ -> main.post { onWake() } })
            }
            porcupine?.start()
            log("👂 \"Jarvis\" का इंतज़ार...")
        } catch (e: Exception) {
            log("Wake word error: ${e.message}")
        }
    }

    private fun stopWakeWord() {
        try { porcupine?.stop() } catch (_: Exception) {}
    }

    private fun onWake() {
        if (inSession) return
        inSession = true
        stopWakeWord()
        brain.resetSession()
        log("⚡ जागा!")
        ToneGenerator(AudioManager.STREAM_MUSIC, 80).startTone(ToneGenerator.TONE_PROP_BEEP, 150)
        main.postDelayed({ listen() }, 350)
    }

    // ---------- Speech → text ----------
    private fun listen() {
        recognizer?.destroy()
        val r = SpeechRecognizer.createSpeechRecognizer(this)
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (text.isNullOrBlank()) endSession() else handleUserText(text)
            }
            override fun onError(error: Int) {
                log("सुनाई नहीं दिया (code $error)")
                endSession()
            }
            override fun onReadyForSpeech(params: Bundle?) { log("🎤 बोलिए...") }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, settings.language)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        r.startListening(i)
    }

    private fun handleUserText(text: String) {
        log("आप: $text")
        val lower = text.lowercase().trim()
        if (STOP_WORDS.any { lower == it || lower.endsWith(it) }) {
            voice.speak("ठीक है Sir।") { endSession() }
            return
        }
        Thread {
            val reply = try { brain.ask(text) } catch (e: Exception) {
                log("Error: ${e.message}"); "Sir, अभी connection में दिक्कत है।"
            }
            main.post {
                log("Jarvis: $reply")
                voice.speak(reply) { listen() }   // जवाब के बाद follow-up सुनो
            }
        }.start()
    }

    private fun endSession() {
        recognizer?.destroy(); recognizer = null
        inSession = false
        main.postDelayed({ startWakeWord() }, 300)
    }

    /** Typed message (app screen से test करने के लिए) */
    fun askTyped(text: String) {
        if (inSession) return
        inSession = true
        stopWakeWord()
        handleUserText(text)
    }

    /** Reminder का समय होने पर बोलकर बताना */
    fun speakReminder(text: String) {
        if (inSession) return
        inSession = true
        stopWakeWord()
        voice.speak("Sir, reminder: $text") { inSession = false; startWakeWord() }
    }

    override fun onDestroy() {
        instance = null
        recognizer?.destroy()
        try { porcupine?.stop(); porcupine?.delete() } catch (_: Exception) {}
        voice.shutdown()
        super.onDestroy()
    }
}
