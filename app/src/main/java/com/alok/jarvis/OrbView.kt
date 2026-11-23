package com.alok.jarvis

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/**
 * HUD dial: outer ring of tappable modules + rotating rings + hexagon core.
 * Tap a module → onModule(name). Tap the core → onCore().
 */
class OrbView(context: Context) : View(context) {
    var state: String = "off"
        set(v) { field = v; invalidate() }
    var onModule: ((String) -> Unit)? = null
    var onCore: (() -> Unit)? = null

    val modules = listOf("NOTES", "TASKS", "REMIND", "ASK GPT", "BRIEF", "RULES", "UPDATE", "SETUP")
    private var pressed = -1

    private var ms = 0f
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Ui.MONO_BOLD; textAlign = Paint.Align.CENTER }
    private val rect = RectF()
    private val path = Path()

    private val anim = ValueAnimator.ofFloat(0f, 1_000_000f).apply {
        duration = 1_000_000L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { ms = it.animatedValue as Float; invalidate() }
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); anim.start() }
    override fun onDetachedFromWindow() { anim.cancel(); super.onDetachedFromWindow() }

    private fun color() = when (state) {
        "idle" -> Ui.CYAN; "listening" -> Ui.GREEN; "thinking" -> Ui.AMBER
        "speaking" -> Ui.CYAN; "error" -> Ui.RED; else -> Ui.OFF
    }
    private fun speed() = when (state) {
        "idle" -> 1f; "listening" -> 2.5f; "thinking" -> 4f; "speaking" -> 2.5f; "error" -> 0.5f; else -> 0.25f
    }
    private fun label() = when (state) {
        "idle" -> "STANDBY"; "listening" -> "LISTENING"; "thinking" -> "PROCESSING"
        "speaking" -> "RESPONDING"; "error" -> "FAULT"; else -> "OFFLINE"
    }

    private fun geom(): Triple<Float, Float, Float> {
        val cx = width / 2f; val cy = height / 2f
        return Triple(cx, cy, min(width, height) * 0.48f)
    }

    private fun arc(cv: Canvas, cx: Float, cy: Float, r: Float, start: Float, sweep: Float, w: Float, a: Int, c: Int) {
        stroke.strokeWidth = w; stroke.color = Ui.alpha(c, a)
        rect.set(cx - r, cy - r, cx + r, cy + r)
        cv.drawArc(rect, start, sweep, false, stroke)
    }

    override fun onDraw(cv: Canvas) {
        val (cx, cy, R) = geom()
        val c = color()
        val accent = if (state == "off") Ui.OFF else Ui.CYAN
        val t = ms / 1000f * speed()
        val d = Ui.dp(context, 1f).toFloat()
        val pulse = (sin(t * 3f) + 1f) / 2f

        // ---- module ring (outer band) ----
        val rOut = R; val rIn = R * 0.80f
        val seg = 360f / modules.size
        for (i in modules.indices) {
            val start = -90f - seg / 2 + i * seg + 1.5f
            val sweep = seg - 3f
            path.reset()
            rect.set(cx - rOut, cy - rOut, cx + rOut, cy + rOut); path.arcTo(rect, start, sweep)
            rect.set(cx - rIn, cy - rIn, cx + rIn, cy + rIn); path.arcTo(rect, start + sweep, -sweep)
            path.close()
            fill.color = if (i == pressed) Ui.alpha(accent, 110) else Ui.alpha(Ui.PANEL2, 235)
            cv.drawPath(path, fill)
            stroke.color = Ui.alpha(accent, 150); stroke.strokeWidth = d
            cv.drawPath(path, stroke)
            // bright edge tick
            arc(cv, cx, cy, rOut - d * 2, start, sweep * 0.25f, d * 3, 230, accent)
            val mid = Math.toRadians((start + sweep / 2).toDouble())
            val lr = (rOut + rIn) / 2
            text.textSize = d * 9.5f
            text.color = if (modules[i] == "UPDATE") Ui.AMBER else Ui.TEXT
            cv.drawText(modules[i], cx + cos(mid).toFloat() * lr, cy + sin(mid).toFloat() * lr + d * 3.5f, text)
        }

        // ---- inner instrument (scaled) ----
        val r = R * 0.76f
        stroke.color = Ui.alpha(c, 110)
        for (i in 0 until 72) {
            val ang = Math.toRadians((i * 5.0) + t * 4)
            val inner = if (i % 6 == 0) r * 0.90f else r * 0.95f
            stroke.strokeWidth = if (i % 6 == 0) d * 2 else d
            cv.drawLine(cx + cos(ang).toFloat() * inner, cy + sin(ang).toFloat() * inner,
                cx + cos(ang).toFloat() * r, cy + sin(ang).toFloat() * r, stroke)
        }
        for (i in 0 until 12) arc(cv, cx, cy, r * 0.82f, i * 30f + t * 20f, 20f, d * 5, 170, c)
        arc(cv, cx, cy, r * 0.71f, -t * 35f, 120f, d * 2, 230, c)
        arc(cv, cx, cy, r * 0.71f, -t * 35f + 180f, 60f, d * 2, 160, c)
        arc(cv, cx, cy, r * 0.65f, t * 50f, 200f, d, 120, c)
        for (i in 0 until 36) arc(cv, cx, cy, r * 0.57f, i * 10f - t * 10f, 5f, d * 3, 140, c)

        if (state == "speaking" || state == "listening") {
            stroke.strokeWidth = d * 2; stroke.color = Ui.alpha(c, 210)
            for (i in 0 until 48) {
                val ang = Math.toRadians(i * 7.5)
                val lvl = (sin(t * 6f + i * 0.9f) + 1f) / 2f
                val r1 = r * 0.45f; val r2 = r1 + r * 0.09f * lvl
                cv.drawLine(cx + cos(ang).toFloat() * r1, cy + sin(ang).toFloat() * r1,
                    cx + cos(ang).toFloat() * r2, cy + sin(ang).toFloat() * r2, stroke)
            }
        }

        // ---- hexagon core ----
        val coreR = r * (0.36f + 0.03f * pulse)
        fill.color = Ui.alpha(c, 45); cv.drawCircle(cx, cy, coreR * 1.15f, fill)
        fill.color = Ui.alpha(c, 75); cv.drawCircle(cx, cy, coreR, fill)
        stroke.color = Ui.alpha(c, 255); stroke.strokeWidth = d * 2; cv.drawCircle(cx, cy, coreR, stroke)
        path.reset()
        val hr = coreR * 0.55f
        for (k in 0 until 6) {
            val a = Math.toRadians(60.0 * k - 90 + t * 15)
            val x = cx + cos(a).toFloat() * hr; val y = cy + sin(a).toFloat() * hr
            if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        fill.color = Ui.alpha(c, 235); cv.drawPath(path, fill)
        fill.color = Ui.alpha(Color.WHITE, 210); cv.drawCircle(cx, cy, coreR * 0.18f, fill)

        text.color = Ui.alpha(c, 240); text.textSize = d * 10
        cv.drawText(label(), cx, cy - coreR - d * 8, text)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val (cx, cy, R) = geom()
        val dist = hypot(e.x - cx, e.y - cy)
        val idx = if (dist in (R * 0.80f)..(R * 1.02f)) {
            var ang = Math.toDegrees(atan2((e.y - cy).toDouble(), (e.x - cx).toDouble())).toFloat() + 90f + 360f / modules.size / 2
            ang = ((ang % 360f) + 360f) % 360f
            (ang / (360f / modules.size)).toInt().coerceIn(0, modules.size - 1)
        } else -1
        when (e.action) {
            MotionEvent.ACTION_DOWN -> { pressed = idx; invalidate() }
            MotionEvent.ACTION_UP -> {
                pressed = -1; invalidate()
                if (idx >= 0) onModule?.invoke(modules[idx])
                else if (dist < R * 0.5f) onCore?.invoke()
                performClick()
            }
            MotionEvent.ACTION_CANCEL -> { pressed = -1; invalidate() }
        }
        return true
    }

    override fun performClick(): Boolean { super.performClick(); return true }
}
