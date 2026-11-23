package com.alok.jarvis

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** Main HUD screen: core + status + transcript + text input + power */
class MainActivity : Activity() {
    private lateinit var orb: OrbView
    private lateinit var status: TextView
    private lateinit var chatBox: LinearLayout
    private lateinit var chatScroll: ScrollView
    private lateinit var power: TextView
    private lateinit var empty: TextView
    private fun dp(v: Float) = Ui.dp(this, v)

    private fun mono(t: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = t; textSize = size; setTextColor(color)
        typeface = if (bold) Ui.MONO_BOLD else Ui.MONO
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Ui.BG
        window.navigationBarColor = Ui.BG

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Ui.GridBackground(this@MainActivity)
            setPadding(dp(18f), dp(16f), dp(18f), dp(16f))
        }

        // ---- Header ----
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val titleCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titleCol.addView(mono("JARVIS", 24f, Ui.CYAN, bold = true).apply { letterSpacing = 0.35f })
        titleCol.addView(mono("PERSONAL ASSISTANT // ALOK", 10f, Ui.MUTED).apply { letterSpacing = 0.12f })
        top.addView(titleCol, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        top.addView(mono("SETUP", 11f, Ui.CYAN, bold = true).apply {
            gravity = Gravity.CENTER; letterSpacing = 0.1f
            background = Ui.HudPanel(this@MainActivity, Ui.PANEL, Ui.LINE, Ui.CYAN, 6f)
            setOnClickListener { startActivity(Intent(this@MainActivity, SettingsActivity::class.java)) }
        }, LinearLayout.LayoutParams(dp(72f), dp(36f)))
        root.addView(top)

        // ---- Core ----
        orb = OrbView(this).apply {
            setOnClickListener { onOrbTap() }
            contentDescription = "Talk to Jarvis"
        }
        root.addView(orb, LinearLayout.LayoutParams(MATCH_PARENT, dp(270f)))

        status = mono("", 14f, Ui.TEXT, bold = true).apply { gravity = Gravity.CENTER; letterSpacing = 0.08f }
        root.addView(status, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        root.addView(mono("TAP THE CORE OR SAY \"JARVIS\"", 10f, Ui.MUTED).apply {
            gravity = Gravity.CENTER; letterSpacing = 0.1f; setPadding(0, dp(4f), 0, dp(12f))
        })

        // ---- Transcript ----
        val chatCard = FrameLayout(this).apply {
            background = Ui.HudPanel(this@MainActivity, Ui.alpha(Ui.PANEL, 230))
            setPadding(dp(12f), dp(12f), dp(12f), dp(12f))
        }
        chatScroll = ScrollView(this).apply { isVerticalScrollBarEnabled = false }
        chatBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        chatScroll.addView(chatBox)
        empty = mono(
            "// TRANSCRIPT\n\nYour conversation with Jarvis appears here.\n\nTry:\n> Set a reminder for 9 AM tomorrow\n> Take a note for BIT\n> Ask GPT for a reel hook\n> What's on my list today?",
            12f, Ui.MUTED
        ).apply { setLineSpacing(0f, 1.3f) }
        chatCard.addView(empty)
        chatCard.addView(chatScroll)
        root.addView(chatCard, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))

        // ---- Input ----
        val inputRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(12f), 0, dp(12f))
        }
        val input = EditText(this).apply {
            hint = "Type a command…"; setHintTextColor(Ui.MUTED); setTextColor(Ui.TEXT); textSize = 14f
            typeface = Ui.MONO
            background = Ui.HudPanel(this@MainActivity, Ui.PANEL2, Ui.LINE, Ui.CYAN_DIM, 6f)
            setPadding(dp(14f), dp(12f), dp(14f), dp(12f))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            imeOptions = EditorInfo.IME_ACTION_SEND
        }
        val send = mono("SEND", 12f, Ui.BG, bold = true).apply {
            gravity = Gravity.CENTER
            background = Ui.HudPanel(this@MainActivity, Ui.CYAN, Ui.CYAN, Ui.TEXT, 6f)
        }
        fun doSend() {
            val t = input.text.toString().trim()
            if (t.isEmpty()) return
            val svc = JarvisService.instance
            if (svc == null) { toast("Bring Jarvis online first"); return }
            svc.askTyped(t); input.setText("")
        }
        send.setOnClickListener { doSend() }
        input.setOnEditorActionListener { _, id, _ -> if (id == EditorInfo.IME_ACTION_SEND) { doSend(); true } else false }
        inputRow.addView(input, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        inputRow.addView(View(this), LinearLayout.LayoutParams(dp(8f), 1))
        inputRow.addView(send, LinearLayout.LayoutParams(dp(68f), dp(46f)))
        root.addView(inputRow)

        // ---- Power ----
        power = mono("", 13f, Ui.TEXT, bold = true).apply {
            gravity = Gravity.CENTER; letterSpacing = 0.15f
            setOnClickListener { togglePower() }
        }
        root.addView(power, LinearLayout.LayoutParams(MATCH_PARENT, dp(52f)))

        setContentView(root)
        askPermissions()
    }

    override fun onResume() {
        super.onResume()
        JarvisService.stateListener = { render(it) }
        JarvisService.chatListener = { u, t -> addBubble(u, t) }
        render(if (JarvisService.instance == null) "off" else JarvisService.currentState)
    }

    override fun onPause() {
        JarvisService.stateListener = null
        JarvisService.chatListener = null
        super.onPause()
    }

    private fun render(s: String) {
        orb.state = s
        status.text = when (s) {
            "idle" -> "ONLINE — LISTENING FOR \"JARVIS\""
            "listening" -> "GO AHEAD, SIR"
            "thinking" -> "PROCESSING…"
            "speaking" -> "RESPONDING…"
            "error" -> "FAULT — CHECK PICOVOICE KEY IN SETUP"
            else -> "SYSTEM OFFLINE"
        }
        status.setTextColor(if (s == "error") Ui.RED else if (s == "thinking") Ui.AMBER else Ui.TEXT)
        val on = JarvisService.instance != null
        power.text = if (on) "■  SYSTEM ONLINE  ·  TAP TO SHUT DOWN" else "▶  SYSTEM OFFLINE  ·  TAP TO START"
        power.setTextColor(if (on) Ui.BG else Ui.CYAN)
        power.background = if (on) Ui.HudPanel(this, Ui.CYAN, Ui.CYAN, Ui.TEXT, 10f)
                           else Ui.HudPanel(this, Ui.PANEL, Ui.LINE, Ui.CYAN, 10f)
    }

    private fun addBubble(fromUser: Boolean, text: String) {
        empty.visibility = View.GONE
        val wrap = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        wrap.addView(mono(if (fromUser) "YOU" else "JARVIS", 9f, if (fromUser) Ui.MUTED else Ui.CYAN, bold = true).apply {
            letterSpacing = 0.15f; gravity = if (fromUser) Gravity.END else Gravity.START
        }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        wrap.addView(TextView(this).apply {
            this.text = text; textSize = 14f; setLineSpacing(0f, 1.2f); setTextColor(Ui.TEXT)
            background = if (fromUser) Ui.HudPanel(this@MainActivity, Ui.PANEL2, Ui.LINE, Ui.MUTED, 6f)
                         else Ui.HudPanel(this@MainActivity, Ui.alpha(Ui.CYAN, 22), Ui.CYAN_DIM, Ui.CYAN, 6f)
            setPadding(dp(12f), dp(9f), dp(12f), dp(9f))
            maxWidth = (resources.displayMetrics.widthPixels * 0.74f).toInt()
        }, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
            gravity = if (fromUser) Gravity.END else Gravity.START; topMargin = dp(3f)
        })
        chatBox.addView(wrap, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(10f) })
        chatScroll.post { chatScroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun onOrbTap() {
        val svc = JarvisService.instance
        if (svc == null) togglePower() else svc.manualWake()
    }

    private fun togglePower() {
        if (JarvisService.instance != null) {
            stopService(Intent(this, JarvisService::class.java))
            orb.postDelayed({ render("off") }, 300)
            return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            askPermissions(); toast("Microphone permission is required"); return
        }
        val s = Settings(this)
        if (s.anthropicKey.isBlank() || s.picovoiceKey.isBlank()) {
            toast("Add your Picovoice and Anthropic keys in SETUP first")
            startActivity(Intent(this, SettingsActivity::class.java)); return
        }
        startForegroundService(Intent(this, JarvisService::class.java))
        orb.postDelayed({ render(JarvisService.currentState) }, 600)
    }

    private fun askPermissions() {
        val need = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33) need += Manifest.permission.POST_NOTIFICATIONS
        val missing = need.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) requestPermissions(missing.toTypedArray(), 1)
    }

    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_LONG).show()
}
