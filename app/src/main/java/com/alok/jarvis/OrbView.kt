package com.alok.jarvis

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/** Minimal orb: soft core + two thin rotating arcs. Tap = talk. */
class OrbView(context: Context) : View(context) {
    var state: String = "off"
        set(v) { field = v; invalidate() }
    var onCore: (() -> Unit)? = null

    private var ms = 0f
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val rect = RectF()
    private var pressed = false

    private val anim = ValueAnimator.ofFloat(0f, 1_000_000f).apply {
        duration = 1_000_000L; repeatCount = ValueAnimator.INFINITE; interpolator = LinearInterpolator()
        addUpdateListener { ms = it.animatedValue as Float; invalidate() }
    }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); anim.start() }
    override fun onDetachedFromWindow() { anim.cancel(); super.onDetachedFromWindow() }

    private fun color() = when (state) {
        "idle" -> Ui.CYAN; "listening" -> Ui.GREEN; "thinking" -> Ui.AMBER
        "speaking" -> Ui.CYAN; "error" -> Ui.RED; else -> Ui.OFF
    }
    private fun speed() = when (state) {
        "idle" -> 0.6f; "listening" -> 1.6f; "thinking" -> 2.4f; "speaking" -> 1.6f; else -> 0.15f
    }

    override fun onDraw(cv: Canvas) {
        val cx = width / 2f; val cy = height / 2f
        val R = min(width, height) * 0.40f
        val c = color()
        val t = ms / 1000f * speed()
        val d = Ui.dp(context, 1f).toFloat()
        val pulse = (sin(t * 3f) + 1f) / 2f
        val active = state == "listening" || state == "speaking"

        // soft halo
        fill.color = Ui.alpha(c, if (active) 40 else 22)
        cv.drawCircle(cx, cy, R * (0.78f + 0.06f * pulse), fill)

        // core
        fill.color = Ui.alpha(c, if (pressed) 255 else 225)
        cv.drawCircle(cx, cy, R * (0.48f + (if (active) 0.05f else 0.02f) * pulse), fill)

        // two thin arcs
        stroke.strokeWidth = d * 1.5f
        stroke.color = Ui.alpha(c, 170)
        rect.set(cx - R, cy - R, cx + R, cy + R)
        cv.drawArc(rect, t * 40f, 110f, false, stroke)
        cv.drawArc(rect, t * 40f + 180f, 60f, false, stroke)
        stroke.color = Ui.alpha(c, 90)
        val r2 = R * 0.9f
        rect.set(cx - r2, cy - r2, cx + r2, cy + r2)
        cv.drawArc(rect, -t * 55f, 80f, false, stroke)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val inside = hypot(e.x - width / 2f, e.y - height / 2f) < min(width, height) * 0.45f
        when (e.action) {
            MotionEvent.ACTION_DOWN -> { pressed = inside; invalidate() }
            MotionEvent.ACTION_UP -> { if (pressed && inside) { onCore?.invoke(); performClick() }; pressed = false; invalidate() }
            MotionEvent.ACTION_CANCEL -> { pressed = false; invalidate() }
        }
        return true
    }
    override fun performClick(): Boolean { super.performClick(); return true }
}
