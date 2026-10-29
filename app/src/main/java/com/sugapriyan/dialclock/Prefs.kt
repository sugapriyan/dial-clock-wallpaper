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

    const val DEFAULT_ACCENT = 0xFFFFFFFF.toInt()
    const val DEFAULT_BG = 0xFF0D141C.toInt()
    const val DEFAULT_DIM = 0.35f

    fun get(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun bgFile(ctx: Context) = File(ctx.filesDir, "bg.jpg")
}
