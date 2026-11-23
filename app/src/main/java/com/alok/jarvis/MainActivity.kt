package com.alok.jarvis

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Main HUD dashboard */
class MainActivity : Activity() {
    private lateinit var orb: OrbView
    private lateinit var signal: SignalView
    private lateinit var status: TextView
    private lateinit var chatBox: LinearLayout
    private lateinit var chatScroll: ScrollView
    private lateinit var power: TextView
    private lateinit var empty: TextView
    private lateinit var clock: TextView
    private lateinit var date: TextView
    private lateinit var chipPwr: TextView
    private lateinit var chipTasks: TextView
    private lateinit var chipNotes: TextView
    private lateinit var chipSys: TextView
    private lateinit var sysInfo: TextView
    private val main = Handler(Looper.getMainLooper())
    private var updateChecked = false
    private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.ENGLISH)
    private val dateFmt = SimpleDateFormat("EEE dd MMM yyyy", Locale.ENGLISH)
    private fun dp(v: Float) = Ui.dp(this, v)

    private fun mono(t: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = t; textSize = size; setTextColor(color)
        typeface = if (bold) Ui.MONO_BOLD else Ui.MONO
    }

    private fun chip(): TextView = mono("", 9.5f, Ui.TEXT, bold = true).apply {
        gravity = Gravity.CENTER; letterSpacing = 0.08f
        background = Ui.HudPanel(this@MainActivity, Ui.PANEL, Ui.LINE, Ui.CYAN_DIM, 5f)
        setPadding(dp(4f), dp(6f), dp(4f), dp(6f))
    }

    private fun panel(title: String): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = Ui.HudPanel(this@MainActivity, Ui.alpha(Ui.PANEL, 235))
        setPadding(dp(10f), dp(8f), dp(10f), dp(8f))
        addView(mono(title, 9f, Ui.CYAN, bold = true).apply { letterSpacing = 0.15f })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Ui.BG
        window.navigationBarColor = Ui.BG

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Ui.GridBackground(this@MainActivity)
            setPadding(dp(14f), dp(12f), dp(14f), dp(12f))
        }

        // ---- Header: title + clock ----
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val titleCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titleCol.addView(mono("JARVIS", 22f, Ui.CYAN, bold = true).apply { letterSpacing = 0.35f })
        titleCol.addView(mono("PERSONAL ASSISTANT // ALOK", 9f, Ui.MUTED).apply { letterSpacing = 0.12f })
        top.addView(titleCol, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        val clockCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.END }
        clock = mono("--:--:--", 22f, Ui.TEXT, bold = true).apply { letterSpacing = 0.05f }
        date = mono("", 9f, Ui.MUTED).apply { letterSpacing = 0.1f }
        clockCol.addView(clock); clockCol.addView(date)
        top.addView(clockCol)
        root.addView(top)

        // ---- Stat chips ----
        val chips = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(10f), 0, 0) }
        chipSys = chip(); chipPwr = chip(); chipTasks = chip(); chipNotes = chip()
        listOf(chipSys, chipPwr, chipTasks, chipNotes).forEachIndexed { i, c ->
            chips.addView(c, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { if (i > 0) marginStart = dp(6f) })
        }
        root.addView(chips)

        // ---- Dial ----
        orb = OrbView(this).apply {
            onCore = { onCoreTap() }
            onModule = { onModuleTap(it) }
            contentDescription = "Jarvis control dial"
        }
        root.addView(orb, LinearLayout.LayoutParams(MATCH_PARENT, dp(300f)))

        status = mono("", 12.5f, Ui.TEXT, bold = true).apply { gravity = Gravity.CENTER; letterSpacing = 0.08f }
        root.addView(status, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        root.addView(mono("TAP THE CORE OR SAY \"JARVIS\"  ·  TAP A MODULE FOR QUICK ACTIONS", 8.5f, Ui.MUTED).apply {
            gravity = Gravity.CENTER; letterSpacing = 0.06f; setPadding(0, dp(3f), 0, dp(8f))
        })

        // ---- Side panels: signal + system ----
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val sigPanel = panel("VOICE SIGNAL")
        signal = SignalView(this)
        sigPanel.addView(signal, LinearLayout.LayoutParams(MATCH_PARENT, dp(46f)).apply { topMargin = dp(4f) })
        val sysPanel = panel("SYSTEM")
        sysInfo = mono("", 9.5f, Ui.TEXT).apply { setLineSpacing(0f, 1.25f); setPadding(0, dp(4f), 0, 0) }
        sysPanel.addView(sysInfo)
        row.addView(sigPanel, LinearLayout.LayoutParams(0, dp(82f), 1f))
        row.addView(sysPanel, LinearLayout.LayoutParams(0, dp(82f), 1f).apply { marginStart = dp(8f) })
        root.addView(row)

        // ---- Transcript ----
        val chatCard = FrameLayout(this).apply {
            background = Ui.HudPanel(this@MainActivity, Ui.alpha(Ui.PANEL, 235))
            setPadding(dp(10f), dp(10f), dp(10f), dp(10f))
        }
        chatScroll = ScrollView(this).apply { isVerticalScrollBarEnabled = false }
        chatBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        chatScroll.addView(chatBox)
        empty = mono("// TRANSCRIPT\n\nYour conversation with Jarvis appears here.", 11f, Ui.MUTED).apply { setLineSpacing(0f, 1.3f) }
        chatCard.addView(empty)
        chatCard.addView(chatScroll)
        root.addView(chatCard, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f).apply { topMargin = dp(8f) })

        // ---- Input ----
        val inputRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8f), 0, dp(8f))
        }
        val input = EditText(this).apply {
            hint = "Type a command…"; setHintTextColor(Ui.MUTED); setTextColor(Ui.TEXT); textSize = 13f
            typeface = Ui.MONO
            background = Ui.HudPanel(this@MainActivity, Ui.PANEL2, Ui.LINE, Ui.CYAN_DIM, 6f)
            setPadding(dp(12f), dp(10f), dp(12f), dp(10f))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            imeOptions = EditorInfo.IME_ACTION_SEND
        }
        val send = mono("SEND", 11f, Ui.BG, bold = true).apply {
            gravity = Gravity.CENTER
            background = Ui.HudPanel(this@MainActivity, Ui.CYAN, Ui.CYAN, Ui.TEXT, 6f)
        }
        fun doSend() {
            val t = input.text.toString().trim()
            if (t.isEmpty()) return
            if (ask(t)) input.setText("")
        }
        send.setOnClickListener { doSend() }
        input.setOnEditorActionListener { _, id, _ -> if (id == EditorInfo.IME_ACTION_SEND) { doSend(); true } else false }
        inputRow.addView(input, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        inputRow.addView(View(this), LinearLayout.LayoutParams(dp(8f), 1))
        inputRow.addView(send, LinearLayout.LayoutParams(dp(64f), dp(42f)))
        root.addView(inputRow)

        // ---- Power ----
        power = mono("", 12f, Ui.TEXT, bold = true).apply {
            gravity = Gravity.CENTER; letterSpacing = 0.15f
            setOnClickListener { togglePower() }
        }
        root.addView(power, LinearLayout.LayoutParams(MATCH_PARENT, dp(48f)))

        setContentView(root)
        askPermissions()
    }

    private val ticker = object : Runnable {
        var n = 0
        override fun run() {
            val now = Date()
            clock.text = timeFmt.format(now)
            date.text = dateFmt.format(now).uppercase(Locale.ENGLISH)
            if (n++ % 5 == 0) refreshStats()
            main.postDelayed(this, 1000)
        }
    }

    private fun refreshStats() {
        val bat = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val lvl = bat?.let { it.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) * 100 / maxOf(1, it.getIntExtra(BatteryManager.EXTRA_SCALE, 100)) } ?: 0
        val store = Store(this)
        val on = JarvisService.instance != null
        chipSys.text = if (on) "SYS ON" else "SYS OFF"
        chipSys.setTextColor(if (on) Ui.GREEN else Ui.MUTED)
        chipPwr.text = "PWR $lvl%"
        chipTasks.text = "TASKS ${store.pendingCount()}"
        chipNotes.text = "NOTES ${store.notesCount()}"
        val s = Settings(this)
        val build = packageManager.getPackageInfo(packageName, 0).longVersionCode
        sysInfo.text = "BUILD  $build\nCORE   ${if (s.anthropicKey.isNotBlank()) "CLAUDE" else if (s.geminiKey.isNotBlank()) "GEMINI" else "--"}\nVOICE  ${if (s.elevenKey.isBlank()) "PHONE" else "ELEVENLABS"}\nWAKE   \"JARVIS\""
    }

    override fun onResume() {
        super.onResume()
        JarvisService.stateListener = { render(it) }
        JarvisService.chatListener = { u, t -> addBubble(u, t) }
        render(if (JarvisService.instance == null) "off" else JarvisService.currentState)
        main.post(ticker)
        if (!updateChecked) {
            updateChecked = true
            Updater.check(this, silent = true)
            if (!Settings(this).hasBrain) startActivity(Intent(this, QuickSetupActivity::class.java))
        }
    }

    override fun onPause() {
        JarvisService.stateListener = null
        JarvisService.chatListener = null
        main.removeCallbacks(ticker)
        super.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) when (KeyGrabber.grab(this)) {
            "gemini" -> toast("Gemini key saved ✓")
            "picovoice" -> toast("Wake-word key saved ✓ — restart Jarvis")
        }
    }

    private fun render(s: String) {
        orb.state = s
        status.text = when (s) {
            "idle" -> if (Settings(this).picovoiceKey.isBlank()) "ONLINE — TAP THE CORE TO TALK" else "ONLINE — LISTENING FOR \"JARVIS\""
            "listening" -> "GO AHEAD, SIR"
            "thinking" -> "PROCESSING…"
            "speaking" -> "RESPONDING…"
            "error" -> "FAULT — CHECK PICOVOICE KEY IN SETUP"
            else -> "SYSTEM OFFLINE"
        }
        status.setTextColor(if (s == "error") Ui.RED else if (s == "thinking") Ui.AMBER else Ui.TEXT)
        signal.level = when (s) { "listening", "speaking" -> 0.9f; "thinking" -> 0.55f; "idle" -> 0.25f; else -> 0.05f }
        signal.color = when (s) { "listening" -> Ui.GREEN; "thinking" -> Ui.AMBER; "off" -> Ui.OFF; else -> Ui.CYAN }
        val on = JarvisService.instance != null
        power.text = if (on) "■  SYSTEM ONLINE  ·  TAP TO SHUT DOWN" else "▶  SYSTEM OFFLINE  ·  TAP TO START"
        power.setTextColor(if (on) Ui.BG else Ui.CYAN)
        power.background = if (on) Ui.HudPanel(this, Ui.CYAN, Ui.CYAN, Ui.TEXT, 10f)
                           else Ui.HudPanel(this, Ui.PANEL, Ui.LINE, Ui.CYAN, 10f)
        refreshStats()
    }

    private fun addBubble(fromUser: Boolean, text: String) {
        empty.visibility = View.GONE
        val wrap = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        wrap.addView(mono(if (fromUser) "YOU" else "JARVIS", 8.5f, if (fromUser) Ui.MUTED else Ui.CYAN, bold = true).apply {
            letterSpacing = 0.15f; gravity = if (fromUser) Gravity.END else Gravity.START
        }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        wrap.addView(TextView(this).apply {
            this.text = text; textSize = 13.5f; setLineSpacing(0f, 1.2f); setTextColor(Ui.TEXT)
            background = if (fromUser) Ui.HudPanel(this@MainActivity, Ui.PANEL2, Ui.LINE, Ui.MUTED, 6f)
                         else Ui.HudPanel(this@MainActivity, Ui.alpha(Ui.CYAN, 22), Ui.CYAN_DIM, Ui.CYAN, 6f)
            setPadding(dp(11f), dp(8f), dp(11f), dp(8f))
            maxWidth = (resources.displayMetrics.widthPixels * 0.74f).toInt()
        }, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
            gravity = if (fromUser) Gravity.END else Gravity.START; topMargin = dp(3f)
        })
        chatBox.addView(wrap, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(8f) })
        chatScroll.post { chatScroll.fullScroll(View.FOCUS_DOWN) }
    }

    /** Send a typed/quick command to Jarvis; false if offline */
    private fun ask(t: String): Boolean {
        val svc = JarvisService.instance
        if (svc == null) { toast("Bring Jarvis online first"); return false }
        svc.askTyped(t); return true
    }

    private fun onCoreTap() {
        val svc = JarvisService.instance
        if (svc == null) togglePower() else svc.manualWake()
    }

    private fun onModuleTap(m: String) {
        when (m) {
            "SETUP" -> startActivity(Intent(this, if (Settings(this).hasBrain) SettingsActivity::class.java else QuickSetupActivity::class.java))
            "UPDATE" -> { toast("Checking for updates…"); Updater.check(this, silent = false) }
            "NOTES" -> ask("Read me my most recent notes, briefly.")
            "TASKS" -> ask("What are my pending tasks?")
            "BRIEF" -> ask("Give me a short briefing: today's date and my pending tasks.")
            "RULES" -> ask("What custom rules do you remember for me?")
            "REMIND", "ASK GPT" -> {
                val svc = JarvisService.instance
                if (svc == null) toast("Bring Jarvis online first")
                else { toast(if (m == "REMIND") "Say the reminder…" else "Ask your GPT question…"); svc.manualWake() }
            }
        }
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
        if (!s.hasBrain) {
            startActivity(Intent(this, QuickSetupActivity::class.java)); return
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
