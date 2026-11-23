package com.alok.jarvis

import android.content.Context

/** सारी settings और API keys फ़ोन के अंदर ही save रहती हैं। */
class Settings(context: Context) {
    private val p = context.getSharedPreferences("jarvis_settings", Context.MODE_PRIVATE)

    var picovoiceKey: String
        get() = p.getString("picovoice", "") ?: ""
        set(v) = p.edit().putString("picovoice", v.trim()).apply()

    var anthropicKey: String
        get() = p.getString("anthropic", "") ?: ""
        set(v) = p.edit().putString("anthropic", v.trim()).apply()

    /** Google Gemini key — free tier, no card needed */
    var geminiKey: String
        get() = p.getString("gemini", "") ?: ""
        set(v) = p.edit().putString("gemini", v.trim()).apply()

    var geminiModel: String
        get() = p.getString("gemini_model", "gemini-flash-lite-latest") ?: "gemini-flash-lite-latest"
        set(v) = p.edit().putString("gemini_model", v.trim()).apply()

    /** true = brain has at least one key */
    val hasBrain: Boolean get() = anthropicKey.isNotBlank() || geminiKey.isNotBlank()

    var openaiKey: String
        get() = p.getString("openai", "") ?: ""
        set(v) = p.edit().putString("openai", v.trim()).apply()

    var elevenKey: String
        get() = p.getString("eleven", "") ?: ""
        set(v) = p.edit().putString("eleven", v.trim()).apply()

    /** ElevenLabs voice ID. Default: "George" (British, calm) — settings से बदल सकते हैं। */
    var voiceId: String
        get() = p.getString("voice_id", "JBFqnCBsd6RMkjVDRZzb") ?: ""
        set(v) = p.edit().putString("voice_id", v.trim()).apply()

    /** Phone (Google TTS) voice name used when there is no ElevenLabs key. Empty = auto (male Hindi). */
    var ttsVoice: String
        get() = p.getString("tts_voice", "") ?: ""
        set(v) = p.edit().putString("tts_voice", v.trim()).apply()

    var claudeModel: String
        get() = p.getString("claude_model", "claude-sonnet-5-5") ?: ""
        set(v) = p.edit().putString("claude_model", v.trim()).apply()

    var gptModel: String
        get() = p.getString("gpt_model", "gpt-5") ?: ""
        set(v) = p.edit().putString("gpt_model", v.trim()).apply()

    /** Speech recognition की भाषा: hi-IN (Hinglish के लिए) या en-IN */
    var language: String
        get() = p.getString("lang", "hi-IN") ?: "hi-IN"
        set(v) = p.edit().putString("lang", v.trim()).apply()

    /** 0.0 – 1.0 : ज़्यादा = आसानी से जागेगा, पर गलती से भी जाग सकता है */
    var sensitivity: Float
        get() = p.getFloat("sensitivity", 0.5f)
        set(v) = p.edit().putFloat("sensitivity", v.coerceIn(0f, 1f)).apply()

    /** Jarvis का स्वभाव — आप जो चाहें लिखें, system prompt में जुड़ जाएगा */
    var personality: String
        get() {
            val v = p.getString("personality", null)
            // Old default asked for Roman-script Hinglish (sounds bad when spoken) → use the new default
            return if (v == null || v.isBlank() || v.contains("never Devanagari")) DEFAULT_PERSONALITY else v
        }
        set(v) = p.edit().putString("personality", v).apply()

    /** बोलकर बनाए गए custom नियम ("जब मैं shoot mode बोलूँ तो...") */
    var customRules: String
        get() = p.getString("rules", "") ?: ""
        set(v) = p.edit().putString("rules", v).apply()

    companion object {
        const val DEFAULT_PERSONALITY =
            "You are Jarvis, Alok's personal AI assistant. Alok is an Associate Creative Producer at TVF. " +
            "Speak like a calm, witty, highly capable butler-assistant. Address him as 'Sir' now and then. " +
            "Alok speaks Hindi or Hinglish. Reply in natural, clean, conversational Hindi written in Devanagari script " +
            "(the way a polite educated Delhi/Mumbai person talks; common English words like meeting, phone, message, " +
            "reminder are fine, but write them in Devanagari too). Never use Roman-script Hindi. " +
            "Keep replies short (1-3 sentences) because they are spoken aloud. No markdown, bullets or emoji."
    }
}
