package com.alok.jarvis

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import android.view.animation.LinearInterpolator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Holographic HUD core: segmented rings, tick marks, rotating arcs, pulsing core */
class OrbView(context: Context) : View(context) {
    var state: String = "off"
        set(v) { field = v; invalidate() }

    private var ms = 0f
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.BUTT }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Ui.MONO; textAlign = Paint.Align.CENTER }
    private val rect = RectF()
    private val clock = SimpleDateFormat("HH:mm:ss", Locale.ENGLISH)

    private val anim = ValueAnimator.ofFloat(0f, 1_000_000f).apply {
        duration = 1_000_000L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { ms = it.animatedValue as Float; invalidate() }
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); anim.start() }
    override fun onDetachedFromWindow() { anim.cancel(); super.onDetachedFromWindow() }

    private fun color() = when (state) {
        "idle" -> Ui.CYAN
        "listening" -> Ui.GREEN
        "thinking" -> Ui.AMBER
        "speaking" -> Ui.CYAN
        "error" -> Ui.RED
        else -> Ui.OFF
    }

    private fun speed() = when (state) {
        "idle" -> 1f; "listening" -> 2.5f; "thinking" -> 4f; "speaking" -> 2.5f; "error" -> 0.5f; else -> 0.2f
    }

    private fun label() = when (state) {
        "idle" -> "STANDBY"; "listening" -> "LISTENING"; "thinking" -> "PROCESSING"
        "speaking" -> "RESPONDING"; "error" -> "FAULT"; else -> "OFFLINE"
    }

    private fun arc(cx: Float, cy: Float, r: Float, start: Float, sweep: Float, w: Float, a: Int, c: Int) {
        stroke.strokeWidth = w
        stroke.color = Ui.alpha(c, a)
        rect.set(cx - r, cy - r, cx + r, cy + r)
        canvas?.drawArc(rect, start, sweep, false, stroke)
    }

    private var canvas: Canvas? = null

    override fun onDraw(cv: Canvas) {
        canvas = cv
        val cx = width / 2f
        val cy = height / 2f
        val R = min(width, height) * 0.46f
        val c = color()
        val t = ms / 1000f * speed()
        val d = Ui.dp(context, 1f).toFloat()
        val pulse = ((sin(t * 3f) + 1f) / 2f)

        // 1. outer tick ring
        stroke.color = Ui.alpha(c, 110)
        for (i in 0 until 72) {
            val ang = Math.toRadians((i * 5.0) + t * 4)
            val inner = if (i % 6 == 0) R * 0.90f else R * 0.94f
            stroke.strokeWidth = if (i % 6 == 0) d * 2 else d
            cv.drawLine(cx + cos(ang).toFloat() * inner, cy + sin(ang).toFloat() * inner,
                cx + cos(ang).toFloat() * R, cy + sin(ang).toFloat() * R, stroke)
        }

        // 2. segmented ring (rotating)
        for (i in 0 until 12) arc(cx, cy, R * 0.82f, i * 30f + t * 20f, 22f, d * 5, 170, c)

        // 3. counter-rotating long arcs
        arc(cx, cy, R * 0.72f, -t * 35f, 120f, d * 2, 220, c)
        arc(cx, cy, R * 0.72f, -t * 35f + 180f, 60f, d * 2, 160, c)
        arc(cx, cy, R * 0.66f, t * 50f, 200f, d, 120, c)

        // 4. inner dashed ring
        for (i in 0 until 36) arc(cx, cy, R * 0.58f, i * 10f - t * 10f, 5f, d * 3, 140, c)

        // 5. speaking / listening: radial level bars
        if (state == "speaking" || state == "listening") {
            stroke.strokeWidth = d * 2
            for (i in 0 until 48) {
                val ang = Math.toRadians(i * 7.5)
                val lvl = (sin(t * 6f + i * 0.9f) + 1f) / 2f
                val r1 = R * 0.46f
                val r2 = r1 + R * 0.08f * lvl
                stroke.color = Ui.alpha(c, 200)
                cv.drawLine(cx + cos(ang).toFloat() * r1, cy + sin(ang).toFloat() * r1,
                    cx + cos(ang).toFloat() * r2, cy + sin(ang).toFloat() * r2, stroke)
            }
        }

        // 6. core
        val coreR = R * (0.36f + 0.03f * pulse)
        fill.color = Ui.alpha(c, 40)
        cv.drawCircle(cx, cy, coreR * 1.15f, fill)
        fill.color = Ui.alpha(c, 70)
        cv.drawCircle(cx, cy, coreR, fill)
        stroke.color = Ui.alpha(c, 255); stroke.strokeWidth = d * 2
        cv.drawCircle(cx, cy, coreR, stroke)
        fill.color = Ui.alpha(c, 230)
        cv.drawCircle(cx, cy, coreR * 0.42f, fill)
        fill.color = Ui.alpha(android.graphics.Color.WHITE, 200)
        cv.drawCircle(cx, cy, coreR * 0.18f, fill)

        // 7. readouts
        text.color = Ui.alpha(c, 230); text.textSize = d * 11
        cv.drawText(label(), cx, cy - coreR - d * 10, text)
        text.color = Ui.alpha(c, 150); text.textSize = d * 9
        cv.drawText(clock.format(Date()), cx, cy + coreR + d * 18, text)
        text.textAlign = Paint.Align.LEFT
        cv.drawText("SYS.CORE", d * 4, d * 14, text)
        text.textAlign = Paint.Align.RIGHT
        cv.drawText("V0.3", width - d * 4, d * 14, text)
        text.textAlign = Paint.Align.CENTER
    }
}
