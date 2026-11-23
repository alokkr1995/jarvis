package com.alok.jarvis

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import android.text.InputType
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** Settings screen + Start/Stop + typed test + live log */
class MainActivity : Activity() {
    private lateinit var s: Settings
    private lateinit var logView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        s = Settings(this)

        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 60, 40, 60) }
        fun title(t: String) = col.addView(TextView(this).apply { text = t; textSize = 22f; setPadding(0, 30, 0, 10) })
        fun field(label: String, value: String, secret: Boolean = false, multi: Boolean = false): EditText {
            col.addView(TextView(this).apply { text = label; setPadding(0, 18, 0, 0) })
            return EditText(this).apply {
                setText(value)
                inputType = when {
                    secret -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                    multi -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                    else -> InputType.TYPE_CLASS_TEXT
                }
                if (multi) minLines = 3
                col.addView(this, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            }
        }
        fun button(t: String, onClick: () -> Unit) =
            col.addView(Button(this).apply { text = t; setOnClickListener { onClick() } })

        title("🤖 Jarvis")
        button("▶ Jarvis चालू करो") { startJarvis() }
        button("■ Jarvis बंद करो") { stopService(Intent(this, JarvisService::class.java)); log("Jarvis बंद") }

        title("Test (लिखकर पूछो)")
        val test = field("Jarvis से पूछें", "")
        button("भेजो") {
            val svc = JarvisService.instance
            if (svc == null) toast("पहले Jarvis चालू करो") else { svc.askTyped(test.text.toString()); test.setText("") }
        }

        logView = TextView(this).apply { textSize = 13f; setPadding(0, 20, 0, 20) }
        col.addView(logView)

        title("🔑 API Keys")
        val pico = field("Picovoice AccessKey", s.picovoiceKey, secret = true)
        val ant = field("Anthropic (Claude) API key", s.anthropicKey, secret = true)
        val oai = field("OpenAI (GPT) API key", s.openaiKey, secret = true)
        val ele = field("ElevenLabs API key (खाली = फ़ोन की आवाज़)", s.elevenKey, secret = true)

        title("⚙️ Settings")
        val voiceId = field("ElevenLabs Voice ID", s.voiceId)
        val cm = field("Claude model", s.claudeModel)
        val gm = field("GPT model", s.gptModel)
        val lang = field("सुनने की भाषा (hi-IN / en-IN)", s.language)
        val sens = field("Wake word sensitivity (0.0 – 1.0)", s.sensitivity.toString())
        val pers = field("Jarvis का स्वभाव (personality)", s.personality, multi = true)
        val rules = field("Custom नियम (Jarvis बोलकर भी जोड़ता है)", s.customRules, multi = true)

        button("💾 Save") {
            s.picovoiceKey = pico.text.toString(); s.anthropicKey = ant.text.toString()
            s.openaiKey = oai.text.toString(); s.elevenKey = ele.text.toString()
            s.voiceId = voiceId.text.toString(); s.claudeModel = cm.text.toString()
            s.gptModel = gm.text.toString(); s.language = lang.text.toString()
            s.sensitivity = sens.text.toString().toFloatOrNull() ?: 0.6f
            s.personality = pers.text.toString(); s.customRules = rules.text.toString()
            toast("Saved. बदलाव के लिए Jarvis बंद करके फिर चालू करें।")
        }
        button("🔋 Battery: Jarvis को Unrestricted करो") {
            startActivity(Intent(AndroidSettings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }

        setContentView(ScrollView(this).apply { addView(col) })
        JarvisService.logListener = { log(it) }
        askPermissions()
    }

    private fun askPermissions() {
        val need = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33) need += Manifest.permission.POST_NOTIFICATIONS
        val missing = need.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) requestPermissions(missing.toTypedArray(), 1)
    }

    private fun startJarvis() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            askPermissions(); toast("Mic की permission दीजिए"); return
        }
        startForegroundService(Intent(this, JarvisService::class.java))
        log("Jarvis चालू हो रहा है...")
    }

    private fun log(msg: String) {
        val lines = (msg + "\n" + logView.text).lines().take(40)
        logView.text = lines.joinToString("\n")
    }

    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_LONG).show()

    override fun onDestroy() {
        JarvisService.logListener = null
        super.onDestroy()
    }
}
