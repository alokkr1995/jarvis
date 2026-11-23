package com.alok.jarvis

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.min
import kotlin.math.sin

/** Jarvis का चमकता orb — हर state में अलग रंग और रफ़्तार */
class OrbView(context: Context) : View(context) {
    var state: String = "off"
        set(v) { field = v; invalidate() }

    private var t = 0f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    private val anim = ValueAnimator.ofFloat(0f, 1_000_000f).apply {
        duration = 1_000_000L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { t = it.animatedValue as Float; invalidate() }
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); anim.start() }
    override fun onDetachedFromWindow() { anim.cancel(); super.onDetachedFromWindow() }

    private fun colorFor(s: String) = when (s) {
        "idle" -> Ui.CYAN
        "listening" -> Ui.GREEN
        "thinking" -> Ui.VIOLET
        "speaking" -> Ui.BLUE
        "error" -> Ui.RED
        else -> Color.parseColor("#3A4559")
    }

    private fun speedFor(s: String) = when (s) {
        "idle" -> 1.2f; "listening" -> 4f; "thinking" -> 6f; "speaking" -> 5f; else -> 0.4f
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val base = min(width, height) * 0.26f
        val c = colorFor(state)
        val sp = speedFor(state)
        val sec = t / 1000f   // seconds
        val pulse = (sin(sec * sp * 2f) + 1f) / 2f   // 0..1

        // बाहरी glow
        val glowR = base * (1.9f + 0.25f * pulse)
        paint.shader = RadialGradient(cx, cy, glowR,
            intArrayOf(Color.argb(110, Color.red(c), Color.green(c), Color.blue(c)), Color.TRANSPARENT),
            null, Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, glowR, paint)

        // घूमते rings
        for (i in 0 until 3) {
            val r = base * (1.25f + i * 0.22f) + pulse * base * 0.06f * (i + 1)
            ring.strokeWidth = Ui.dp(context, 1.5f).toFloat()
            ring.color = Color.argb(140 - i * 40, Color.red(c), Color.green(c), Color.blue(c))
            val start = (sec * sp * 30f * (if (i % 2 == 0) 1.5f else -1.1f)) % 360f
            canvas.drawArc(cx - r, cy - r, cx + r, cy + r, start, 110f, false, ring)
            canvas.drawArc(cx - r, cy - r, cx + r, cy + r, start + 180f, 70f, false, ring)
        }

        // core
        val coreR = base * (0.92f + 0.08f * pulse)
        paint.shader = RadialGradient(cx - coreR * 0.3f, cy - coreR * 0.3f, coreR * 1.4f,
            intArrayOf(Color.WHITE, c, Color.argb(255, Color.red(c) / 3, Color.green(c) / 3, Color.blue(c) / 3)),
            floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, coreR, paint)
        paint.shader = null
    }
}
