package com.sugapriyan.dialclock

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/** Draws the dial clock onto any Canvas. Same design as the HTML version. */
class ClockRenderer(private val ctx: Context) {

    /** Regular / medium / bold faces for one font style. */
    private class Faces(val regular: Typeface, val medium: Typeface, val bold: Typeface)

    private val fonts = mapOf(
        "modern" to Faces(
            Typeface.create("sans-serif", Typeface.NORMAL),
            Typeface.create("sans-serif-medium", Typeface.NORMAL),
            Typeface.create("sans-serif", Typeface.BOLD)
        ),
        "thin" to Faces(
            Typeface.create("sans-serif-thin", Typeface.NORMAL),
            Typeface.create("sans-serif-light", Typeface.NORMAL),
            Typeface.create("sans-serif-light", Typeface.NORMAL)
        ),
        "condensed" to Faces(
            Typeface.create("sans-serif-condensed", Typeface.NORMAL),
            Typeface.create("sans-serif-condensed", Typeface.NORMAL),
            Typeface.create("sans-serif-condensed", Typeface.BOLD)
        ),
        "serif" to Faces(
            Typeface.create(Typeface.SERIF, Typeface.NORMAL),
            Typeface.create(Typeface.SERIF, Typeface.NORMAL),
            Typeface.create(Typeface.SERIF, Typeface.BOLD)
        ),
        "mono" to Faces(
            Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL),
            Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL),
            Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        )
    )

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var accent = Prefs.DEFAULT_ACCENT
    private var bgColor = Prefs.DEFAULT_BG
    private var dim = Prefs.DEFAULT_DIM
    private var hour24 = false
    private var glowOn = true
    private var faces: Faces = fonts.getValue(Prefs.DEFAULT_FONT)
    private var scale = Prefs.SIZES.getValue(Prefs.DEFAULT_SIZE)
    private var pos = 0f
    private var bgBitmap: Bitmap? = null
    private var bgStamp = Long.MIN_VALUE

    private var shownMin = -1f
    private val pill = RectF()
    private val src = Rect()
    private val dst = Rect()

    private val days = arrayOf("SUNDAY", "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY")
    private val months = arrayOf("JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC")

    fun reload() {
        val p = Prefs.get(ctx)
        accent = p.getInt(Prefs.ACCENT, Prefs.DEFAULT_ACCENT)
        bgColor = p.getInt(Prefs.BG_COLOR, Prefs.DEFAULT_BG)
        dim = p.getFloat(Prefs.DIM, Prefs.DEFAULT_DIM)
        hour24 = p.getBoolean(Prefs.HOUR24, false)
        glowOn = p.getBoolean(Prefs.GLOW, true)
        faces = fonts[p.getString(Prefs.FONT, null) ?: Prefs.DEFAULT_FONT] ?: fonts.getValue(Prefs.DEFAULT_FONT)
        val preset = Prefs.SIZES[p.getString(Prefs.SIZE, null) ?: Prefs.DEFAULT_SIZE] ?: Prefs.SIZES.getValue(Prefs.DEFAULT_SIZE)
        val fine = p.getFloat(Prefs.FINE, 1f).coerceIn(Prefs.FINE_MIN, Prefs.FINE_MAX)
        scale = preset * fine
        pos = p.getFloat(Prefs.POS, 0f).coerceIn(Prefs.POS_MIN, Prefs.POS_MAX)

        // only decode the background image again when it actually changed
        val stamp = p.getLong(Prefs.BG_STAMP, 0L)
        if (stamp != bgStamp) {
            bgStamp = stamp
            bgBitmap?.recycle()
            val f = Prefs.bgFile(ctx)
            bgBitmap = if (f.exists()) BitmapFactory.decodeFile(f.absolutePath) else null
        }
    }

    private fun clamp01(v: Float) = max(0f, min(1f, v))
    private fun wrap(d: Float) = ((d % 60f) + 90f) % 60f - 30f
    private fun pad(n: Int) = if (n < 10) "0$n" else n.toString()
    private fun white(a: Float) = Color.argb((clamp01(a) * 255).toInt(), 255, 255, 255)
    private fun withAlpha(c: Int, a: Float) =
        Color.argb((clamp01(a) * 255).toInt(), Color.red(c), Color.green(c), Color.blue(c))

    /** Soft accent glow around what is drawn next. */
    private fun glow(on: Boolean, u: Float, k: Float = 1f) {
        if (on && glowOn && k > 0f) paint.setShadowLayer(max(0.5f, 0.035f * u * k), 0f, 0f, withAlpha(accent, 0.85f))
        else paint.clearShadowLayer()
    }

    /** Soft dark edge behind dial numbers, so they stay readable on photos. */
    private fun soften(on: Boolean, u: Float) {
        if (on && bgBitmap != null) paint.setShadowLayer(max(0.5f, 0.012f * u), 0f, 0f, 0x99000000.toInt())
        else paint.clearShadowLayer()
    }

    /** Draws text with its vertical centre at y. */
    private fun text(c: Canvas, s: String, x: Float, y: Float) {
        val fm = paint.fontMetrics
        c.drawText(s, x, y - (fm.ascent + fm.descent) / 2f, paint)
    }

    fun draw(c: Canvas, w: Int, h: Int) {
        val W = w.toFloat()
        val H = h.toFloat()

        // ---- background ----
        c.drawColor(bgColor)
        bgBitmap?.let { b ->
            val sc = max(W / b.width, H / b.height)
            val sw = (W / sc).toInt()
            val sh = (H / sc).toInt()
            val sx = (b.width - sw) / 2
            val sy = (b.height - sh) / 2
            src.set(sx, sy, sx + sw, sy + sh)
            dst.set(0, 0, w, h)
            paint.reset(); paint.isAntiAlias = true; paint.isFilterBitmap = true
            c.drawBitmap(b, src, dst, paint)
            c.drawColor(Color.argb((dim * 255).toInt(), 0, 0, 0))
        }
        paint.reset(); paint.isAntiAlias = true

        // ---- time ----
        val cal = Calendar.getInstance()
        val ss = cal.get(Calendar.SECOND)
        val ms = cal.get(Calendar.MILLISECOND)
        val minute = cal.get(Calendar.MINUTE)
        val t = clamp01(ms / 300f)
        val secPos = (ss - 1 + (1 - (1 - t).pow(3)) + 60) % 60

        if (shownMin < 0) shownMin = minute.toFloat()
        val d = wrap(minute - shownMin)
        shownMin = if (abs(d) < 0.002f) minute.toFloat() else (shownMin + d * 0.12f + 60f) % 60f

        // ---- layout ----
        val u = min(W, H * 0.62f) * scale
        val ty = H * (0.17f + pos)
        val cx = W / 2 - 0.045f * u
        val cy = ty + 0.56f * u   // just below the date, with a small gap
        val rMin = 0.14f * u
        val rSec = 0.27f * u

        // pill
        val ph = 0.11f * u
        pill.set(cx + rMin - 0.075f * u, cy - ph / 2, cx + rSec + 0.06f * u, cy + ph / 2)
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(115, 0, 0, 0)
        c.drawRoundRect(pill, ph / 2, ph / 2, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = max(3f, 0.006f * u)
        paint.color = accent
        glow(true, u)
        c.drawRoundRect(pill, ph / 2, ph / 2, paint)
        glow(false, u)

        // rings
        ring(c, cx, cy, u, rSec, secPos, 0.036f * u, hideInside = false, halfH = ph / 2)
        ring(c, cx, cy, u, rMin, shownMin, 0.042f * u, hideInside = true, halfH = ph / 2)

        // minute in pill
        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = faces.bold
        paint.textSize = 0.072f * u
        paint.color = accent
        glow(true, u, 0.8f)
        text(c, pad(minute), cx + rMin, cy + 0.003f * u)
        glow(false, u)

        // hour
        var hr = cal.get(Calendar.HOUR_OF_DAY)
        val ampm = if (hr < 12) "AM" else "PM"
        if (!hour24) hr = if (hr % 12 == 0) 12 else hr % 12
        paint.textAlign = Paint.Align.RIGHT
        paint.typeface = faces.medium
        paint.textSize = 0.19f * u
        paint.color = accent
        glow(true, u, 0.7f)
        text(c, pad(hr), cx - 0.03f * u, cy + 0.008f * u)
        glow(false, u)
        if (!hour24) {
            paint.textSize = 0.034f * u
            paint.letterSpacing = 0.25f
            paint.color = withAlpha(accent, 0.9f)
            text(c, ampm, cx - 0.035f * u, cy + 0.13f * u)
            paint.letterSpacing = 0f
        }

        // date (large) on top, day (small) below — both in the theme colour
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = faces.bold
        paint.textSize = 0.07f * u
        paint.letterSpacing = 0.17f
        paint.color = accent
        glow(true, u, 0.8f)
        text(
            c,
            "${pad(cal.get(Calendar.DAY_OF_MONTH))} ${months[cal.get(Calendar.MONTH)]} ${cal.get(Calendar.YEAR)}",
            W / 2, ty
        )
        glow(false, u)
        paint.typeface = faces.regular
        paint.textSize = 0.042f * u
        paint.letterSpacing = 0.38f
        paint.color = withAlpha(accent, 0.85f)
        text(c, days[cal.get(Calendar.DAY_OF_WEEK) - 1], W / 2, ty + 0.08f * u)
        paint.letterSpacing = 0f
    }

    private fun ring(
        c: Canvas, cx: Float, cy: Float, u: Float, r: Float, cur: Float,
        fs: Float, hideInside: Boolean, halfH: Float
    ) {
        paint.typeface = faces.regular
        paint.textSize = fs
        paint.textAlign = Paint.Align.CENTER
        paint.strokeCap = Paint.Cap.BUTT
        val soft = 0.02f * u
        val step = r * Math.PI.toFloat() / 30f
        val inner = halfH - fs * 0.6f
        val outer = halfH + fs * 0.6f + 0.008f * u

        for (i in 0 until 60) {
            val delta = wrap(i - cur)
            val a = delta * Math.PI.toFloat() / 30f
            val co = cos(a)
            val si = sin(a)
            val vis = clamp01((co + 0.2f) / 1.2f).pow(1.6f)   // only the right half is visible
            if (vis < 0.01f) continue
            val seen = vis.pow(0.6f)                          // brighter fall-off for the outer numbers
            val major = i % 5 == 0
            val onRight = co > 0

            // tick marks, kept out of the pill
            val t0 = r + 0.045f * u
            val t1 = t0 + (if (major) 0.028f else 0.014f) * u
            val tickF = if (onRight) clamp01((abs(si) * t0 - halfH - 0.012f * u) / soft) else 1f
            if (tickF > 0f) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = max(2f, (if (major) 0.0045f else 0.003f) * u)
                paint.color = white(seen * tickF * (if (major) 0.85f else 0.45f))
                soften(true, u)
                c.drawLine(cx + co * t0, cy - si * t0, cx + co * t1, cy - si * t1, paint)
                soften(false, u)
            }

            val dy = abs(si) * r
            // inside the pill only the current second shows; neighbours cross-fade as the ring steps
            val fIn = if (!hideInside && onRight) clamp01(1f - dy / (0.75f * step)) else 0f
            paint.style = Paint.Style.FILL

            if (!major) {
                if (fIn <= 0.01f) continue
                val focus = 1f - clamp01(dy / inner) * 0.7f
                paint.color = withAlpha(accent, fIn * focus)   // current second uses the theme colour
                glow(fIn > 0.5f, u, 0.6f * fIn)
                text(c, pad(i), cx + co * r, cy - si * r)
                glow(false, u)
                continue
            }

            var f = 1f
            if (onRight) f = max(clamp01((dy - outer) / soft), fIn)
            if (f <= 0.01f) continue
            if (fIn > 0f) {
                val focus = 1f - clamp01(dy / inner) * 0.7f
                paint.color = withAlpha(accent, vis * f * focus)
                glow(fIn > 0.5f, u, 0.6f * fIn)
                text(c, pad(i), cx + co * r, cy - si * r)
                glow(false, u)
            } else {
                paint.color = white(seen * f * 0.92f)
                soften(true, u)
                text(c, pad(i), cx + co * r, cy - si * r)
                soften(false, u)
            }
        }
    }
}
