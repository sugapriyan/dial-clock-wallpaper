package com.sugapriyan.dialclock

import android.content.Context
import android.content.SharedPreferences
import java.io.File

object Prefs {
    const val NAME = "dialclock"
    const val ACCENT = "accent"
    const val BG_COLOR = "bgColor"
    const val DIM = "dim"
    const val HOUR24 = "hour24"
    const val BG_STAMP = "bgStamp"   // changes whenever the background image changes
    const val FONT = "font"          // modern | thin | condensed | serif | mono
    const val GLOW = "glow"
    const val SIZE = "size"          // grid-size preset: 2x2 | 2x4 | 4x4 | 4x5
    const val FINE = "fine"          // fine-tune on top of the preset, 0.85 – 1.15
    const val POS = "pos"            // move clock up (-) / down (+), fraction of screen height

    const val DEFAULT_ACCENT = 0xFFFFFFFF.toInt()
    const val DEFAULT_BG = 0xFF0D141C.toInt()
    const val DEFAULT_DIM = 0.35f
    const val DEFAULT_FONT = "modern"
    const val DEFAULT_SIZE = "4x4"

    const val POS_MIN = -0.10f
    const val POS_MAX = 0.25f
    const val FINE_MIN = 0.85f
    const val FINE_MAX = 1.15f

    /** Size presets, named like home-screen grid sizes (columns x rows of app icons). */
    val SIZES = linkedMapOf("2x2" to 0.56f, "2x4" to 0.74f, "4x4" to 1.12f, "4x5" to 1.36f)
    val SIZE_LABELS = linkedMapOf("2x2" to "2 × 2", "2x4" to "2 × 4", "4x4" to "4 × 4", "4x5" to "Full")

    val FONT_LABELS = linkedMapOf(
        "modern" to "Modern", "thin" to "Thin", "condensed" to "Narrow", "serif" to "Serif", "mono" to "Mono"
    )

    fun get(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun bgFile(ctx: Context) = File(ctx.filesDir, "bg.jpg")
}
