package com.alok.jarvis

import android.app.Activity
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * One-tap setup: opens the key page, user taps Copy, comes back —
 * Jarvis reads the clipboard and saves the key automatically.
 */
class QuickSetupActivity : Activity() {
    private lateinit var s: Settings
    private lateinit var gemStatus: TextView
    private lateinit var picoStatus: TextView
    private fun dp(v: Float) = Ui.dp(this, v)

    private fun mono(t: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = t; textSize = size; setTextColor(color)
        typeface = if (bold) Ui.MONO_BOLD else Ui.MONO
    }

    private fun bigButton(label: String, accent: Int, onClick: () -> Unit) = mono(label, 13f, accent, bold = true).apply {
        gravity = Gravity.CENTER; letterSpacing = 0.08f
        background = Ui.HudPanel(this@QuickSetupActivity, Ui.PANEL, Ui.LINE, accent, 10f)
        setOnClickListener { onClick() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Ui.BG
        window.navigationBarColor = Ui.BG
        s = Settings(this)

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            }
        Ui.applyInsets(col, dp(20f), dp(24f), dp(20f), dp(40f))
        col.addView(mono("QUICK SETUP", 22f, Ui.CYAN, bold = true).apply { letterSpacing = 0.25f })
        col.addView(mono("No typing. Tap a button, tap COPY on the page, come back.\nJarvis saves the key by itself.", 11f, Ui.MUTED).apply {
            setLineSpacing(0f, 1.3f); setPadding(0, dp(6f), 0, dp(20f))
        })

        // Step 1 — Gemini (required, free)
        col.addView(mono("// STEP 1 — BRAIN (FREE, REQUIRED)", 11f, Ui.CYAN, bold = true))
        col.addView(mono("Opens Google AI Studio → tap \"Create API key\" → tap COPY → come back here.", 10f, Ui.MUTED).apply {
            setPadding(0, dp(4f), 0, dp(8f)); setLineSpacing(0f, 1.25f)
        })
        col.addView(bigButton("GET FREE GEMINI KEY", Ui.CYAN) {
            open("https://aistudio.google.com/apikey")
        }, LinearLayout.LayoutParams(MATCH_PARENT, dp(54f)))
        gemStatus = mono("", 11f, Ui.MUTED, bold = true).apply { setPadding(0, dp(8f), 0, dp(22f)) }
        col.addView(gemStatus)

        // Step 2 — Picovoice (optional)
        col.addView(mono("// STEP 2 — WAKE WORD \"JARVIS\" (FREE, OPTIONAL)", 11f, Ui.CYAN, bold = true))
        col.addView(mono("Skip this and you can still talk by tapping the core.\nOpens Picovoice → sign in → tap COPY next to AccessKey → come back.", 10f, Ui.MUTED).apply {
            setPadding(0, dp(4f), 0, dp(8f)); setLineSpacing(0f, 1.25f)
        })
        col.addView(bigButton("GET FREE WAKE-WORD KEY", Ui.AMBER) {
            open("https://console.picovoice.ai/")
        }, LinearLayout.LayoutParams(MATCH_PARENT, dp(54f)))
        picoStatus = mono("", 11f, Ui.MUTED, bold = true).apply { setPadding(0, dp(8f), 0, dp(22f)) }
        col.addView(picoStatus)

        col.addView(mono("DONE — START JARVIS", 13f, Ui.BG, bold = true).apply {
            gravity = Gravity.CENTER; letterSpacing = 0.12f
            background = Ui.HudPanel(this@QuickSetupActivity, Ui.CYAN, Ui.CYAN, Ui.TEXT, 10f)
            setOnClickListener {
                if (!s.hasBrain) Toast.makeText(this@QuickSetupActivity, "Step 1 is still needed", Toast.LENGTH_LONG).show()
                else finish()
            }
        }, LinearLayout.LayoutParams(MATCH_PARENT, dp(54f)))

        setContentView(ScrollView(this).apply { background = Ui.GridBackground(this@QuickSetupActivity); addView(col) })
        refresh()
    }

    private fun open(url: String) {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            when (KeyGrabber.grab(this)) {
                "gemini" -> Toast.makeText(this, "Gemini key saved ✓", Toast.LENGTH_SHORT).show()
                "picovoice" -> Toast.makeText(this, "Wake-word key saved ✓", Toast.LENGTH_SHORT).show()
            }
            refresh()
        }
    }

    private fun refresh() {
        gemStatus.text = if (s.geminiKey.isNotBlank()) "✓ GEMINI KEY SAVED" else "○ WAITING FOR KEY…"
        gemStatus.setTextColor(if (s.geminiKey.isNotBlank()) Ui.GREEN else Ui.MUTED)
        picoStatus.text = if (s.picovoiceKey.isNotBlank()) "✓ WAKE-WORD KEY SAVED" else "○ OPTIONAL — NOT SET"
        picoStatus.setTextColor(if (s.picovoiceKey.isNotBlank()) Ui.GREEN else Ui.MUTED)
    }
}

/** Reads the clipboard and saves any recognised key. Returns which key was saved, or null. */
object KeyGrabber {
    private val GEMINI = Regex("^(AIza[0-9A-Za-z_\\-]{30,}|AQ\\.[0-9A-Za-z_\\-.]{30,})$")
    private val PICO = Regex("^[A-Za-z0-9+/]{40,}={0,2}$")

    fun grab(a: Activity): String? {
        return try {
            val cm = a.getSystemService(ClipboardManager::class.java)
            val text = cm.primaryClip?.getItemAt(0)?.coerceToText(a)?.toString()?.trim() ?: return null
            val s = Settings(a)
            when {
                GEMINI.matches(text) && text != s.geminiKey -> { s.geminiKey = text; "gemini" }
                PICO.matches(text) && !text.startsWith("AIza") && !text.startsWith("AQ.") && text != s.picovoiceKey -> { s.picovoiceKey = text; "picovoice" }
                else -> null
            }
        } catch (e: Exception) { null }
    }
}
