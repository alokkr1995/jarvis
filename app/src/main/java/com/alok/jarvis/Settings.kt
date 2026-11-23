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
        get() = p.getFloat("sensitivity", 0.6f)
        set(v) = p.edit().putFloat("sensitivity", v.coerceIn(0f, 1f)).apply()

    /** Jarvis का स्वभाव — आप जो चाहें लिखें, system prompt में जुड़ जाएगा */
    var personality: String
        get() = p.getString("personality", DEFAULT_PERSONALITY) ?: DEFAULT_PERSONALITY
        set(v) = p.edit().putString("personality", v).apply()

    /** बोलकर बनाए गए custom नियम ("जब मैं shoot mode बोलूँ तो...") */
    var customRules: String
        get() = p.getString("rules", "") ?: ""
        set(v) = p.edit().putString("rules", v).apply()

    companion object {
        const val DEFAULT_PERSONALITY =
            "You are Jarvis, Alok's personal AI assistant. Alok is an Associate Creative Producer at TVF. " +
            "Speak like a calm, witty, highly capable butler-assistant. Address him as 'Sir' now and then. " +
            "Alok often speaks Hindi or Hinglish; reply in natural Hinglish written in Roman (English) script, never Devanagari. " +
            "Keep replies short (1-3 sentences) because they are spoken aloud. No markdown, bullets or emoji."
    }
}
