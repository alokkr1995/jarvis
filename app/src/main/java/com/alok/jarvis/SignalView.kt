package com.alok.jarvis

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.sin

/** Small scrolling signal graph for the HUD side panels */
class SignalView(context: Context) : View(context) {
    var level = 0.3f          // 0..1 amplitude
    var color = Ui.CYAN
    private var t = 0f
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f }
    private val grid = Paint().apply { strokeWidth = 1f }
    private val anim = ValueAnimator.ofFloat(0f, 1_000_000f).apply {
        duration = 1_000_000L; repeatCount = ValueAnimator.INFINITE; interpolator = LinearInterpolator()
        addUpdateListener { t = (it.animatedValue as Float) / 1000f; invalidate() }
    }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); anim.start() }
    override fun onDetachedFromWindow() { anim.cancel(); super.onDetachedFromWindow() }

    override fun onDraw(cv: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        grid.color = Ui.alpha(Ui.CYAN, 30)
        for (i in 1..3) cv.drawLine(0f, h * i / 4, w, h * i / 4, grid)
        var x = 0f
        while (x < w) { cv.drawLine(x, 0f, x, h, grid); x += w / 8 }
        p.color = color
        var px = 0f; var py = h / 2
        val step = 3f
        var i = 0f
        while (i <= w) {
            val y = h / 2 + (sin(i * 0.08f + t * 5f) * 0.5f + sin(i * 0.21f - t * 8f) * 0.3f +
                    sin(i * 0.5f + t * 13f) * 0.2f) * h * 0.42f * level
            if (i > 0) cv.drawLine(px, py, i, y, p)
            px = i; py = y; i += step
        }
    }
}
