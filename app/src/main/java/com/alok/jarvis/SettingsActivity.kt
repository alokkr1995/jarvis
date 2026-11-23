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

/** Settings: keys, आवाज़, भाषा, personality, custom नियम */
class SettingsActivity : Activity() {
    private fun dp(v: Float) = Ui.dp(this, v)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Ui.BG
        window.navigationBarColor = Ui.BG
        val s = Settings(this)

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20f), dp(18f), dp(20f), dp(40f))
        }

        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        top.addView(TextView(this).apply {
            text = "←"; textSize = 22f; setTextColor(Ui.TEXT); gravity = Gravity.CENTER
            background = Ui.rounded(this@SettingsActivity, Ui.SURFACE, 14f, Ui.BORDER)
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(dp(46f), dp(46f)))
        top.addView(TextView(this).apply {
            text = "  Settings"; textSize = 22f; setTextColor(Ui.TEXT); typeface = Ui.MEDIUM
        })
        col.addView(top)

        fun card(title: String, subtitle: String): LinearLayout {
            col.addView(TextView(this).apply {
                text = title; setTextColor(Ui.CYAN); textSize = 13f; typeface = Ui.MONO; letterSpacing = 0.1f
                setPadding(dp(4f), dp(24f), 0, dp(2f))
            })
            col.addView(TextView(this).apply {
                text = subtitle; setTextColor(Ui.MUTED); textSize = 12f; setPadding(dp(4f), 0, 0, dp(8f))
            })
            val c = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = Ui.rounded(this@SettingsActivity, Ui.SURFACE, 18f, Ui.BORDER)
                setPadding(dp(16f), dp(8f), dp(16f), dp(16f))
            }
            col.addView(c, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            return c
        }

        fun field(parent: LinearLayout, label: String, value: String, secret: Boolean = false, multi: Boolean = false): EditText {
            parent.addView(TextView(this).apply {
                text = label; setTextColor(Ui.TEXT); textSize = 13f; setPadding(0, dp(12f), 0, dp(6f))
            })
            val e = EditText(this).apply {
                setText(value); setTextColor(Ui.TEXT); setHintTextColor(Ui.MUTED); textSize = 14f
                background = Ui.rounded(this@SettingsActivity, Ui.SURFACE2, 12f, Ui.BORDER)
                setPadding(dp(14f), dp(10f), dp(14f), dp(10f))
                inputType = when {
                    secret -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                    multi -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                    else -> InputType.TYPE_CLASS_TEXT
                }
                if (multi) { minLines = 3; gravity = Gravity.TOP or Gravity.START }
                if (secret && value.isNotBlank()) hint = "••••• saved"
            }
            parent.addView(e, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            return e
        }

        val keys = card("API KEYS", "ये keys सिर्फ़ आपके फ़ोन में save रहती हैं")
        val pico = field(keys, "Picovoice AccessKey  (wake word, ज़रूरी)", s.picovoiceKey, secret = true)
        val ant = field(keys, "Anthropic / Claude key  (दिमाग़, ज़रूरी)", s.anthropicKey, secret = true)
        val oai = field(keys, "OpenAI / GPT key  (optional)", s.openaiKey, secret = true)
        val ele = field(keys, "ElevenLabs key  (अच्छी आवाज़, optional)", s.elevenKey, secret = true)

        val voice = card("आवाज़ और भाषा", "ElevenLabs Voice ID बदलकर आवाज़ बदलें")
        val voiceId = field(voice, "ElevenLabs Voice ID", s.voiceId)
        val lang = field(voice, "सुनने की भाषा  (hi-IN या en-IN)", s.language)
        val sens = field(voice, "Wake word sensitivity  (0.0 – 1.0)", s.sensitivity.toString())

        val brain = card("दिमाग़", "Models और Jarvis का स्वभाव")
        val cm = field(brain, "Claude model", s.claudeModel)
        val gm = field(brain, "GPT model", s.gptModel)
        val pers = field(brain, "Jarvis का स्वभाव (personality)", s.personality, multi = true)
        val rules = field(brain, "Custom नियम  (Jarvis बोलकर भी जोड़ता है)", s.customRules, multi = true)

        col.addView(TextView(this).apply {
            text = "💾  Save करें"; textSize = 16f; typeface = Ui.MEDIUM; setTextColor(Ui.BG); gravity = Gravity.CENTER
            background = Ui.gradient(this@SettingsActivity, intArrayOf(Ui.CYAN, Ui.BLUE), 26f)
            setOnClickListener {
                s.picovoiceKey = pico.text.toString(); s.anthropicKey = ant.text.toString()
                s.openaiKey = oai.text.toString(); s.elevenKey = ele.text.toString()
                s.voiceId = voiceId.text.toString(); s.language = lang.text.toString()
                s.sensitivity = sens.text.toString().toFloatOrNull() ?: 0.6f
                s.claudeModel = cm.text.toString(); s.gptModel = gm.text.toString()
                s.personality = pers.text.toString(); s.customRules = rules.text.toString()
                Toast.makeText(this@SettingsActivity, "Saved ✓  Jarvis को OFF/ON करें ताकि बदलाव लागू हों", Toast.LENGTH_LONG).show()
                finish()
            }
        }, LinearLayout.LayoutParams(MATCH_PARENT, dp(54f)).apply { topMargin = dp(28f) })

        col.addView(TextView(this).apply {
            text = "🔋  Battery: Jarvis को Unrestricted करें"; textSize = 14f; setTextColor(Ui.TEXT); gravity = Gravity.CENTER
            background = Ui.rounded(this@SettingsActivity, Ui.SURFACE2, 26f, Ui.BORDER)
            setOnClickListener { startActivity(Intent(AndroidSettings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
        }, LinearLayout.LayoutParams(MATCH_PARENT, dp(50f)).apply { topMargin = dp(12f) })

        setContentView(ScrollView(this).apply { setBackgroundColor(Ui.BG); addView(col) })
    }
}
