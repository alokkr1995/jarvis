package com.alok.jarvis

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** Minimal main screen */
class MainActivity : Activity() {
    private lateinit var orb: OrbView
    private lateinit var status: TextView
    private lateinit var chatBox: LinearLayout
    private lateinit var chatScroll: ScrollView
    private lateinit var power: TextView
    private lateinit var updateBtn: TextView
    private var updateChecked = false
    private fun dp(v: Float) = Ui.dp(this, v)

    private fun pill(fill: Int, stroke: Int?, radius: Float = 100f) = GradientDrawable().apply {
        setColor(fill); cornerRadius = dp(radius).toFloat()
        if (stroke != null) setStroke(dp(1f), stroke)
    }

    private fun label(t: String, size: Float, color: Int) = TextView(this).apply {
        text = t; textSize = size; setTextColor(color); typeface = Ui.REGULAR
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Ui.BG
        window.navigationBarColor = Ui.BG

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Ui.BG) }
        Ui.applyInsets(root, dp(20f), dp(14f), dp(20f), dp(14f))

        // ---- Top bar: name · update · power · settings ----
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        top.addView(label("JARVIS", 15f, Ui.TEXT).apply { typeface = Ui.LIGHT; letterSpacing = 0.45f },
            LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        updateBtn = label("Update", 12f, Ui.TEXT).apply {
            typeface = Ui.MEDIUM; gravity = Gravity.CENTER
            background = pill(Ui.BG, Ui.LINE); setPadding(dp(12f), dp(6f), dp(12f), dp(6f))
            setOnClickListener { Updater.check(this@MainActivity, silent = false) }
        }
        top.addView(updateBtn)
        power = label("", 12f, Ui.TEXT).apply {
            typeface = Ui.MEDIUM; gravity = Gravity.CENTER
            setPadding(dp(12f), dp(6f), dp(12f), dp(6f))
            setOnClickListener { togglePower() }
        }
        top.addView(power, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginStart = dp(8f) })
        top.addView(label("⚙", 18f, Ui.MUTED).apply {
            gravity = Gravity.CENTER
            setOnClickListener { startActivity(Intent(this@MainActivity, SettingsActivity::class.java)) }
        }, LinearLayout.LayoutParams(dp(40f), dp(40f)).apply { marginStart = dp(4f) })
        root.addView(top)

        // ---- Orb + status ----
        orb = OrbView(this).apply { onCore = { onCoreTap() }; contentDescription = "Talk to Jarvis" }
        root.addView(orb, LinearLayout.LayoutParams(MATCH_PARENT, dp(240f)).apply { topMargin = dp(12f) })
        status = label("", 13f, Ui.MUTED).apply { gravity = Gravity.CENTER }
        root.addView(status, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(16f) })

        // ---- Conversation ----
        chatScroll = ScrollView(this).apply { isVerticalScrollBarEnabled = false }
        chatBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        chatScroll.addView(chatBox)
        root.addView(chatScroll, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))

        // ---- Input ----
        val inputRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = pill(Ui.PANEL2, Ui.LINE)
            setPadding(dp(18f), dp(4f), dp(6f), dp(4f))
        }
        val input = EditText(this).apply {
            hint = "Ask Jarvis…"; setHintTextColor(Ui.MUTED); setTextColor(Ui.TEXT); textSize = 15f
            typeface = Ui.REGULAR; background = null
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            imeOptions = EditorInfo.IME_ACTION_SEND
        }
        val send = label("↑", 18f, Ui.BG).apply {
            typeface = Ui.MEDIUM; gravity = Gravity.CENTER; background = pill(Ui.CYAN, null)
        }
        fun doSend() {
            val t = input.text.toString().trim()
            if (t.isEmpty()) return
            val svc = JarvisService.instance
            if (svc == null) { toast("Turn Jarvis on first (top right)"); return }
            svc.askTyped(t); input.setText("")
        }
        send.setOnClickListener { doSend() }
        input.setOnEditorActionListener { _, id, _ -> if (id == EditorInfo.IME_ACTION_SEND) { doSend(); true } else false }
        inputRow.addView(input, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        inputRow.addView(send, LinearLayout.LayoutParams(dp(40f), dp(40f)))
        root.addView(inputRow, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(10f) })

        setContentView(root)
        askPermissions()
    }

    override fun onResume() {
        super.onResume()
        JarvisService.stateListener = { render(it) }
        JarvisService.chatListener = { u, t -> addBubble(u, t) }
        render(if (JarvisService.instance == null) "off" else JarvisService.currentState)
        if (!updateChecked) {
            updateChecked = true
            Updater.check(this, silent = true) {
                updateBtn.text = "Update ●"; updateBtn.setTextColor(Ui.BG); updateBtn.background = pill(Ui.AMBER, null)
            }
            if (!Settings(this).hasBrain) startActivity(Intent(this, QuickSetupActivity::class.java))
        }
    }

    override fun onPause() {
        JarvisService.stateListener = null
        JarvisService.chatListener = null
        super.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) when (KeyGrabber.grab(this)) {
            "gemini" -> toast("Gemini key saved ✓")
        }
    }

    private fun render(s: String) {
        orb.state = s
        status.text = when (s) {
            "idle" -> "Say \"Hey Jarvis\" or tap the orb"
            "listening" -> "Listening…"
            "thinking" -> "Thinking…"
            "speaking" -> "Speaking…"
            "error" -> "Something's wrong — check Settings"
            else -> "Jarvis is off"
        }
        status.setTextColor(if (s == "error") Ui.RED else Ui.MUTED)
        val on = JarvisService.instance != null
        power.text = if (on) "● On" else "○ Off"
        power.setTextColor(if (on) Ui.BG else Ui.TEXT)
        power.background = if (on) pill(Ui.CYAN, null) else pill(Ui.BG, Ui.LINE)
    }

    private fun addBubble(fromUser: Boolean, text: String) {
        val b = TextView(this).apply {
            this.text = text; textSize = 15f; setLineSpacing(0f, 1.25f); typeface = Ui.REGULAR
            setTextColor(if (fromUser) Ui.MUTED else Ui.TEXT)
            if (!fromUser) { background = pill(Ui.PANEL, null, 18f); setPadding(dp(14f), dp(10f), dp(14f), dp(10f)) }
            maxWidth = (resources.displayMetrics.widthPixels * 0.78f).toInt()
            gravity = if (fromUser) Gravity.END else Gravity.START
        }
        chatBox.addView(b, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
            gravity = if (fromUser) Gravity.END else Gravity.START
            topMargin = dp(10f)
        })
        chatScroll.post { chatScroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun onCoreTap() {
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
        if (!Settings(this).hasBrain) {
            startActivity(Intent(this, QuickSetupActivity::class.java)); return
        }
        startForegroundService(Intent(this, JarvisService::class.java))
        orb.postDelayed({ render(JarvisService.currentState) }, 600)
    }

    private fun askPermissions() {
        val need = mutableListOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.READ_CONTACTS,
            Manifest.permission.CALL_PHONE, Manifest.permission.SEND_SMS)
        if (Build.VERSION.SDK_INT >= 33) need += Manifest.permission.POST_NOTIFICATIONS
        val missing = need.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) requestPermissions(missing.toTypedArray(), 1)
    }

    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_LONG).show()
}
