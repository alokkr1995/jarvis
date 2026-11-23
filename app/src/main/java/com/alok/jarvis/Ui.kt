package com.alok.jarvis

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.TypedValue
import android.view.View
import android.view.WindowInsets

/** Minimal design system */
object Ui {
    val BG = Color.parseColor("#050608")
    val PANEL = Color.parseColor("#0E1014")
    val PANEL2 = Color.parseColor("#14171C")
    val LINE = Color.parseColor("#22262D")
    val TEXT = Color.parseColor("#EDEFF2")
    val MUTED = Color.parseColor("#7A8089")
    val CYAN = Color.parseColor("#5CE1E6")
    val CYAN_DIM = Color.parseColor("#2A6F73")
    val AMBER = Color.parseColor("#F2B45A")
    val GREEN = Color.parseColor("#6EE7A8")
    val RED = Color.parseColor("#FF6B6B")
    val OFF = Color.parseColor("#3A3F47")

    val LIGHT: Typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
    val REGULAR: Typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    val MEDIUM: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    // kept for screens that still use them
    val MONO: Typeface = REGULAR
    val MONO_BOLD: Typeface = MEDIUM

    fun dp(c: Context, v: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.resources.displayMetrics).toInt()

    fun alpha(color: Int, a: Int) = Color.argb(a, Color.red(color), Color.green(color), Color.blue(color))

    /** Keeps content clear of the status bar, nav bar and keyboard (Android 15 edge-to-edge) */
    fun applyInsets(v: View, l: Int, t: Int, r: Int, b: Int) {
        v.setPadding(l, t, r, b)
        if (Build.VERSION.SDK_INT >= 30) {
            v.setOnApplyWindowInsetsListener { view, insets ->
                val i = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
                view.setPadding(l + i.left, t + i.top, r + i.right, b + i.bottom)
                insets
            }
        }
    }

    /** Simple rounded surface with a hairline border (name kept for compatibility) */
    class HudPanel(
        private val c: Context,
        private val fill: Int = PANEL,
        private val line: Int = LINE,
        @Suppress("UNUSED_PARAMETER") accent: Int = CYAN,
        @Suppress("UNUSED_PARAMETER") bracketDp: Float = 10f,
        private val radiusDp: Float = 14f,
    ) : Drawable() {
        private val fillP = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val lineP = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        private val r = RectF()
        override fun draw(canvas: Canvas) {
            val s = dp(c, 1f).toFloat()
            val rad = dp(c, radiusDp).toFloat()
            r.set(bounds.left + s / 2, bounds.top + s / 2, bounds.right - s / 2, bounds.bottom - s / 2)
            fillP.color = fill; canvas.drawRoundRect(r, rad, rad, fillP)
            if (line != fill) { lineP.color = line; lineP.strokeWidth = s; canvas.drawRoundRect(r, rad, rad, lineP) }
        }
        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(colorFilter: ColorFilter?) {}
        @Deprecated("Deprecated in Java")
        override fun getOpacity() = PixelFormat.TRANSLUCENT
    }

    /** Plain background (name kept for compatibility) */
    class GridBackground(@Suppress("UNUSED_PARAMETER") c: Context) : Drawable() {
        override fun draw(canvas: Canvas) { canvas.drawColor(BG) }
        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(colorFilter: ColorFilter?) {}
        @Deprecated("Deprecated in Java")
        override fun getOpacity() = PixelFormat.OPAQUE
    }
}
