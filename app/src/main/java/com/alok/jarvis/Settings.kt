package com.alok.jarvis

import android.content.Context

/** All settings and API keys stay on this phone, encrypted with a hardware-backed key. */
class Settings(context: Context) {
    private val p = SecurePrefs.get(context)

    var picovoiceKey: String
        get() = p.getString("picovoice", "")
        set(v) = p.putString("picovoice", v.trim())

    var anthropicKey: String
        get() = p.getString("anthropic", "")
        set(v) = p.putString("anthropic", v.trim())

    /** Google Gemini key — free tier, no card needed */
    var geminiKey: String
        get() = p.getString("gemini", "")
        set(v) = p.putString("gemini", v.trim())

    var geminiModel: String
        get() = p.getString("gemini_model", "gemini-flash-lite-latest")
        set(v) = p.putString("gemini_model", v.trim())

    /** true = brain has at least one key */
    val hasBrain: Boolean get() = anthropicKey.isNotBlank() || geminiKey.isNotBlank()

    var openaiKey: String
        get() = p.getString("openai", "")
        set(v) = p.putString("openai", v.trim())

    var elevenKey: String
        get() = p.getString("eleven", "")
        set(v) = p.putString("eleven", v.trim())

    /** ElevenLabs voice ID. Default: "George" (British, calm) — settings से बदल सकते हैं। */
    var voiceId: String
        get() = p.getString("voice_id", "JBFqnCBsd6RMkjVDRZzb")
        set(v) = p.putString("voice_id", v.trim())

    /** Phone (Google TTS) voice name used when there is no ElevenLabs key. Empty = auto (male Hindi). */
    var ttsVoice: String
        get() = p.getString("tts_voice", "")
        set(v) = p.putString("tts_voice", v.trim())

    var claudeModel: String
        get() = p.getString("claude_model", "claude-sonnet-5-5")
        set(v) = p.putString("claude_model", v.trim())

    var gptModel: String
        get() = p.getString("gpt_model", "gpt-5")
        set(v) = p.putString("gpt_model", v.trim())

    /** Speech recognition की भाषा: hi-IN (Hinglish के लिए) या en-IN */
    var language: String
        get() = p.getString("lang", "hi-IN")
        set(v) = p.putString("lang", v.trim())

    /** 0.0 – 1.0 : ज़्यादा = आसानी से जागेगा, पर गलती से भी जाग सकता है */
    var sensitivity: Float
        get() = p.getFloat("sensitivity", 0.5f)
        set(v) = p.putFloat("sensitivity", v.coerceIn(0f, 1f))

    /** Jarvis का स्वभाव — आप जो चाहें लिखें, system prompt में जुड़ जाएगा */
    var personality: String
        get() {
            val v = if (p.has("personality")) p.getString("personality", "") else null
            // Old default asked for Roman-script Hinglish (sounds bad when spoken) → use the new default
            return if (v == null || v.isBlank() || v.contains("never Devanagari")) DEFAULT_PERSONALITY else v
        }
        set(v) = p.putString("personality", v)

    /** बोलकर बनाए गए custom नियम ("जब मैं shoot mode बोलूँ तो...") */
    var customRules: String
        get() = p.getString("rules", "")
        set(v) = p.putString("rules", v)

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
