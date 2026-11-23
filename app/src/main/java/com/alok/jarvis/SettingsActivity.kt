package com.alok.jarvis

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** Setup: keys, voice, language, models, personality, custom rules */
class SettingsActivity : Activity() {
    private fun dp(v: Float) = Ui.dp(this, v)

    private fun mono(t: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = t; textSize = size; setTextColor(color)
        typeface = if (bold) Ui.MONO_BOLD else Ui.MONO
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Ui.BG
        window.navigationBarColor = Ui.BG
        val s = Settings(this)

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18f), dp(16f), dp(18f), dp(40f))
        }

        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        top.addView(mono("◀ BACK", 11f, Ui.CYAN, bold = true).apply {
            gravity = Gravity.CENTER
            background = Ui.HudPanel(this@SettingsActivity, Ui.PANEL, Ui.LINE, Ui.CYAN, 6f)
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(dp(76f), dp(36f)))
        top.addView(mono("   SYSTEM SETUP", 18f, Ui.TEXT, bold = true).apply { letterSpacing = 0.12f })
        col.addView(top)

        fun section(title: String, subtitle: String): LinearLayout {
            col.addView(mono("// $title", 12f, Ui.CYAN, bold = true).apply {
                letterSpacing = 0.12f; setPadding(dp(2f), dp(24f), 0, dp(2f))
            })
            col.addView(mono(subtitle, 10f, Ui.MUTED).apply { setPadding(dp(2f), 0, 0, dp(8f)) })
            val c = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = Ui.HudPanel(this@SettingsActivity)
                setPadding(dp(14f), dp(6f), dp(14f), dp(14f))
            }
            col.addView(c, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            return c
        }

        fun field(parent: LinearLayout, label: String, value: String, secret: Boolean = false, multi: Boolean = false): EditText {
            parent.addView(mono(label, 11f, Ui.TEXT).apply { setPadding(0, dp(12f), 0, dp(6f)); letterSpacing = 0.05f })
            val e = EditText(this).apply {
                setText(value); setTextColor(Ui.TEXT); setHintTextColor(Ui.MUTED); textSize = 13f
                typeface = Ui.MONO
                background = Ui.HudPanel(this@SettingsActivity, Ui.PANEL2, Ui.LINE, Ui.CYAN_DIM, 5f)
                setPadding(dp(12f), dp(10f), dp(12f), dp(10f))
                inputType = when {
                    secret -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                    multi -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                    else -> InputType.TYPE_CLASS_TEXT
                }
                if (multi) { minLines = 3; gravity = Gravity.TOP or Gravity.START }
                if (secret) hint = if (value.isNotBlank()) "•••• saved" else "paste key"
            }
            parent.addView(e, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            return e
        }

        val keys = section("API KEYS", "Stored only on this phone")
        val pico = field(keys, "PICOVOICE ACCESS KEY  [wake word · required]", s.picovoiceKey, secret = true)
        val ant = field(keys, "ANTHROPIC KEY  [brain · required]", s.anthropicKey, secret = true)
        val oai = field(keys, "OPENAI KEY  [GPT · optional]", s.openaiKey, secret = true)
        val ele = field(keys, "ELEVENLABS KEY  [voice · optional]", s.elevenKey, secret = true)

        val voice = section("VOICE + LANGUAGE", "Change the ElevenLabs voice ID to change Jarvis's voice")
        val voiceId = field(voice, "ELEVENLABS VOICE ID", s.voiceId)
        val lang = field(voice, "SPEECH LANGUAGE  [hi-IN or en-IN]", s.language)
        val sens = field(voice, "WAKE WORD SENSITIVITY  [0.0 – 1.0]", s.sensitivity.toString())

        val brain = section("BRAIN", "Models and personality")
        val cm = field(brain, "CLAUDE MODEL", s.claudeModel)
        val gm = field(brain, "GPT MODEL", s.gptModel)
        val pers = field(brain, "PERSONALITY", s.personality, multi = true)
        val rules = field(brain, "CUSTOM RULES  [Jarvis can add these by voice too]", s.customRules, multi = true)

        col.addView(mono("SAVE CONFIGURATION", 13f, Ui.BG, bold = true).apply {
            gravity = Gravity.CENTER; letterSpacing = 0.12f
            background = Ui.HudPanel(this@SettingsActivity, Ui.CYAN, Ui.CYAN, Ui.TEXT, 10f)
            setOnClickListener {
                s.picovoiceKey = pico.text.toString(); s.anthropicKey = ant.text.toString()
                s.openaiKey = oai.text.toString(); s.elevenKey = ele.text.toString()
                s.voiceId = voiceId.text.toString(); s.language = lang.text.toString()
                s.sensitivity = sens.text.toString().toFloatOrNull() ?: 0.6f
                s.claudeModel = cm.text.toString(); s.gptModel = gm.text.toString()
                s.personality = pers.text.toString(); s.customRules = rules.text.toString()
                Toast.makeText(this@SettingsActivity, "Saved. Restart Jarvis to apply changes.", Toast.LENGTH_LONG).show()
                finish()
            }
        }, LinearLayout.LayoutParams(MATCH_PARENT, dp(52f)).apply { topMargin = dp(28f) })

        col.addView(mono("BATTERY: SET JARVIS TO UNRESTRICTED", 11f, Ui.CYAN, bold = true).apply {
            gravity = Gravity.CENTER; letterSpacing = 0.08f
            background = Ui.HudPanel(this@SettingsActivity, Ui.PANEL, Ui.LINE, Ui.CYAN, 8f)
            setOnClickListener { startActivity(Intent(AndroidSettings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
        }, LinearLayout.LayoutParams(MATCH_PARENT, dp(48f)).apply { topMargin = dp(12f) })

        setContentView(ScrollView(this).apply { background = Ui.GridBackground(this@SettingsActivity); addView(col) })
    }
}
