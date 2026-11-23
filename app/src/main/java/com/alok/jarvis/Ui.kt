package com.alok.jarvis

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.util.TypedValue

/** HUD design system: colors, fonts, bracket panels, grid background */
object Ui {
    val BG = Color.parseColor("#02060D")
    val PANEL = Color.parseColor("#0A1622")
    val PANEL2 = Color.parseColor("#0E1F2E")
    val LINE = Color.parseColor("#173A4F")
    val TEXT = Color.parseColor("#D8F6FF")
    val MUTED = Color.parseColor("#5E8CA3")
    val CYAN = Color.parseColor("#00E5FF")
    val CYAN_DIM = Color.parseColor("#0A6E80")
    val AMBER = Color.parseColor("#FFB020")
    val GREEN = Color.parseColor("#3DFFB0")
    val RED = Color.parseColor("#FF4D5E")
    val OFF = Color.parseColor("#33505F")

    val MONO: Typeface = Typeface.create("monospace", Typeface.NORMAL)
    val MONO_BOLD: Typeface = Typeface.create("monospace", Typeface.BOLD)

    fun dp(c: Context, v: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.resources.displayMetrics).toInt()

    fun alpha(color: Int, a: Int) = Color.argb(a, Color.red(color), Color.green(color), Color.blue(color))

    /** Thin bordered panel with bright corner brackets */
    class HudPanel(
        private val c: Context,
        private val fill: Int = PANEL,
        private val line: Int = LINE,
        private val accent: Int = CYAN,
        private val bracketDp: Float = 10f,
    ) : Drawable() {
        private val fillP = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val lineP = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        override fun draw(canvas: Canvas) {
            val b = bounds
            val s = dp(c, 1f).toFloat()
            fillP.color = fill
            canvas.drawRect(b, fillP)
            lineP.color = line; lineP.strokeWidth = s
            canvas.drawRect(b.left + s / 2, b.top + s / 2, b.right - s / 2, b.bottom - s / 2, lineP)
            lineP.color = accent; lineP.strokeWidth = s * 2
            val k = dp(c, bracketDp).toFloat()
            val l = b.left + s; val t = b.top + s; val r = b.right - s; val bt = b.bottom - s
            canvas.drawLine(l, t, l + k, t, lineP); canvas.drawLine(l, t, l, t + k, lineP)
            canvas.drawLine(r, t, r - k, t, lineP); canvas.drawLine(r, t, r, t + k, lineP)
            canvas.drawLine(l, bt, l + k, bt, lineP); canvas.drawLine(l, bt, l, bt - k, lineP)
            canvas.drawLine(r, bt, r - k, bt, lineP); canvas.drawLine(r, bt, r, bt - k, lineP)
        }
        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(colorFilter: ColorFilter?) {}
        @Deprecated("Deprecated in Java")
        override fun getOpacity() = PixelFormat.TRANSLUCENT
    }

    /** Dark background with faint HUD grid */
    class GridBackground(private val c: Context) : Drawable() {
        private val p = Paint().apply { color = alpha(CYAN, 14); strokeWidth = 1f }
        override fun draw(canvas: Canvas) {
            canvas.drawColor(BG)
            val step = dp(c, 28f).toFloat()
            var x = 0f
            while (x < bounds.width()) { canvas.drawLine(x, 0f, x, bounds.height().toFloat(), p); x += step }
            var y = 0f
            while (y < bounds.height()) { canvas.drawLine(0f, y, bounds.width().toFloat(), y, p); y += step }
        }
        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(colorFilter: ColorFilter?) {}
        @Deprecated("Deprecated in Java")
        override fun getOpacity() = PixelFormat.OPAQUE
    }
}
