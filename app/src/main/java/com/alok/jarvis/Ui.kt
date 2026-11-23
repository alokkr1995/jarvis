package com.alok.jarvis

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue

/** Jarvis का design system: रंग, fonts, rounded backgrounds */
object Ui {
    val BG = Color.parseColor("#070B14")
    val SURFACE = Color.parseColor("#111827")
    val SURFACE2 = Color.parseColor("#1A2333")
    val BORDER = Color.parseColor("#243047")
    val TEXT = Color.parseColor("#E6EDF7")
    val MUTED = Color.parseColor("#8A97AD")
    val CYAN = Color.parseColor("#2EE6FF")
    val BLUE = Color.parseColor("#3B82F6")
    val VIOLET = Color.parseColor("#8B5CF6")
    val GREEN = Color.parseColor("#22C55E")
    val ORANGE = Color.parseColor("#F59E0B")
    val RED = Color.parseColor("#EF4444")

    val MONO: Typeface = Typeface.create("monospace", Typeface.BOLD)
    val MEDIUM: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)

    fun dp(c: Context, v: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.resources.displayMetrics).toInt()

    fun rounded(c: Context, color: Int, radiusDp: Float, strokeColor: Int? = null): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(c, radiusDp).toFloat()
            if (strokeColor != null) setStroke(dp(c, 1f), strokeColor)
        }

    fun gradient(c: Context, colors: IntArray, radiusDp: Float): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, colors).apply {
            cornerRadius = dp(c, radiusDp).toFloat()
        }
}
