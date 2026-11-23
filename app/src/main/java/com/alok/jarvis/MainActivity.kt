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

/** मुख्य screen: orb + status + बातचीत + लिखकर पूछना + ON/OFF */
class MainActivity : Activity() {
    private lateinit var orb: OrbView
    private lateinit var status: TextView
    private lateinit var chatBox: LinearLayout
    private lateinit var chatScroll: ScrollView
    private lateinit var power: TextView
    private lateinit var empty: TextView
    private fun dp(v: Float) = Ui.dp(this, v)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Ui.BG
        window.navigationBarColor = Ui.BG

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.BG)
            setPadding(dp(20f), dp(18f), dp(20f), dp(18f))
        }

        // ---- Top bar ----
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val titleCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titleCol.addView(TextView(this).apply {
            text = "J.A.R.V.I.S"; setTextColor(Ui.CYAN); textSize = 22f; typeface = Ui.MONO; letterSpacing = 0.15f
        })
        titleCol.addView(TextView(this).apply {
            text = "Alok का personal assistant"; setTextColor(Ui.MUTED); textSize = 12f
        })
        top.addView(titleCol, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        top.addView(TextView(this).apply {
            text = "⚙"; textSize = 22f; setTextColor(Ui.TEXT); gravity = Gravity.CENTER
            background = Ui.rounded(this@MainActivity, Ui.SURFACE, 14f, Ui.BORDER)
            setOnClickListener { startActivity(Intent(this@MainActivity, SettingsActivity::class.java)) }
        }, LinearLayout.LayoutParams(dp(46f), dp(46f)))
        root.addView(top)

        // ---- Orb ----
        orb = OrbView(this).apply {
            setOnClickListener { onOrbTap() }
            contentDescription = "Jarvis से बात करें"
        }
        root.addView(orb, LinearLayout.LayoutParams(MATCH_PARENT, dp(260f)))

        status = TextView(this).apply {
            setTextColor(Ui.TEXT); textSize = 16f; typeface = Ui.MEDIUM; gravity = Gravity.CENTER
        }
        root.addView(status, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        root.addView(TextView(this).apply {
            text = "Orb पर tap करें या बोलें \"Jarvis\""; setTextColor(Ui.MUTED); textSize = 12f; gravity = Gravity.CENTER
            setPadding(0, dp(4f), 0, dp(14f))
        })

        // ---- Chat ----
        val chatCard = FrameLayout(this).apply {
            background = Ui.rounded(this@MainActivity, Ui.SURFACE, 20f, Ui.BORDER)
            setPadding(dp(12f), dp(12f), dp(12f), dp(12f))
        }
        chatScroll = ScrollView(this).apply { isVerticalScrollBarEnabled = false }
        chatBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        chatScroll.addView(chatBox)
        empty = TextView(this).apply {
            text = "यहाँ आपकी और Jarvis की बातचीत दिखेगी।\n\nकुछ आज़माइए:\n• \"कल 9 बजे dubbing studio call का reminder लगाओ\"\n• \"BIT के लिए note करो…\"\n• \"GPT से पूछो…\"\n• \"आज के काम क्या हैं?\""
            setTextColor(Ui.MUTED); textSize = 13f; setLineSpacing(0f, 1.25f)
        }
        chatCard.addView(empty)
        chatCard.addView(chatScroll)
        root.addView(chatCard, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))

        // ---- Input ----
        val inputRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(12f), 0, dp(12f))
        }
        val input = EditText(this).apply {
            hint = "Jarvis को लिखें…"; setHintTextColor(Ui.MUTED); setTextColor(Ui.TEXT); textSize = 15f
            background = Ui.rounded(this@MainActivity, Ui.SURFACE2, 24f, Ui.BORDER)
            setPadding(dp(18f), dp(12f), dp(18f), dp(12f))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            imeOptions = EditorInfo.IME_ACTION_SEND
        }
        val send = TextView(this).apply {
            text = "➤"; textSize = 20f; setTextColor(Ui.BG); gravity = Gravity.CENTER
            background = Ui.rounded(this@MainActivity, Ui.CYAN, 24f)
        }
        fun doSend() {
            val t = input.text.toString().trim()
            if (t.isEmpty()) return
            val svc = JarvisService.instance
            if (svc == null) { toast("पहले नीचे से Jarvis ON करें"); return }
            svc.askTyped(t); input.setText("")
        }
        send.setOnClickListener { doSend() }
        input.setOnEditorActionListener { _, id, _ -> if (id == EditorInfo.IME_ACTION_SEND) { doSend(); true } else false }
        inputRow.addView(input, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        inputRow.addView(View(this), LinearLayout.LayoutParams(dp(10f), 1))
        inputRow.addView(send, LinearLayout.LayoutParams(dp(48f), dp(48f)))
        root.addView(inputRow)

        // ---- Power ----
        power = TextView(this).apply {
            textSize = 15f; typeface = Ui.MEDIUM; gravity = Gravity.CENTER; letterSpacing = 0.05f
            setOnClickListener { togglePower() }
        }
        root.addView(power, LinearLayout.LayoutParams(MATCH_PARENT, dp(54f)))

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
            "idle" -> "सुन रहा हूँ — बोलिए \"Jarvis\""
            "listening" -> "🎤 बोलिए, Sir…"
            "thinking" -> "सोच रहा हूँ…"
            "speaking" -> "🔊 बोल रहा हूँ…"
            "error" -> "⚠ Settings में Picovoice key जाँचिए"
            else -> "Jarvis बंद है"
        }
        val on = JarvisService.instance != null
        power.text = if (on) "●  JARVIS ON  —  बंद करने के लिए tap" else "○  JARVIS OFF  —  चालू करने के लिए tap"
        power.setTextColor(if (on) Ui.BG else Ui.TEXT)
        power.background = if (on) Ui.gradient(this, intArrayOf(Ui.CYAN, Ui.BLUE), 27f)
                           else Ui.rounded(this, Ui.SURFACE2, 27f, Ui.BORDER)
    }

    private fun addBubble(fromUser: Boolean, text: String) {
        empty.visibility = View.GONE
        val b = TextView(this).apply {
            this.text = text; textSize = 14.5f; setLineSpacing(0f, 1.2f)
            setTextColor(if (fromUser) Ui.BG else Ui.TEXT)
            background = if (fromUser) Ui.gradient(this@MainActivity, intArrayOf(Ui.CYAN, Ui.BLUE), 18f)
                         else Ui.rounded(this@MainActivity, Ui.SURFACE2, 18f, Ui.BORDER)
            setPadding(dp(14f), dp(10f), dp(14f), dp(10f))
            maxWidth = (resources.displayMetrics.widthPixels * 0.72f).toInt()
        }
        val lp = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
            gravity = if (fromUser) Gravity.END else Gravity.START
            topMargin = dp(8f)
        }
        chatBox.addView(b, lp)
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
            askPermissions(); toast("Mic की permission दीजिए"); return
        }
        val s = Settings(this)
        if (s.anthropicKey.isBlank() || s.picovoiceKey.isBlank()) {
            toast("पहले ⚙ Settings में Picovoice और Anthropic keys डालिए")
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
