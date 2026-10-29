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

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tfRegular = Typeface.create("sans-serif", Typeface.NORMAL)
    private val tfMedium = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    private val tfBold = Typeface.create("sans-serif", Typeface.BOLD)

    private var accent = Prefs.DEFAULT_ACCENT
    private var bgColor = Prefs.DEFAULT_BG
    private var dim = Prefs.DEFAULT_DIM
    private var hour24 = false
    private var bgBitmap: Bitmap? = null

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
        val f = Prefs.bgFile(ctx)
        bgBitmap?.recycle()
        bgBitmap = if (f.exists()) BitmapFactory.decodeFile(f.absolutePath) else null
    }

    private fun clamp01(v: Float) = max(0f, min(1f, v))
    private fun wrap(d: Float) = ((d % 60f) + 90f) % 60f - 30f
    private fun pad(n: Int) = if (n < 10) "0$n" else n.toString()
    private fun white(a: Float) = Color.argb((clamp01(a) * 255).toInt(), 255, 255, 255)
    private fun withAlpha(c: Int, a: Float) =
        Color.argb((clamp01(a) * 255).toInt(), Color.red(c), Color.green(c), Color.blue(c))

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
            val scale = max(W / b.width, H / b.height)
            val sw = (W / scale).toInt()
            val sh = (H / scale).toInt()
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
        val u = min(W, H * 0.62f)
        val cx = W / 2 - 0.045f * u
        val cy = H * 0.17f + 0.56f * u
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
        c.drawRoundRect(pill, ph / 2, ph / 2, paint)

        // rings
        ring(c, cx, cy, u, rSec, secPos, 0.036f * u, hideInside = false, halfH = ph / 2)
        ring(c, cx, cy, u, rMin, shownMin, 0.042f * u, hideInside = true, halfH = ph / 2)

        // minute in pill
        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = tfBold
        paint.textSize = 0.072f * u
        paint.color = Color.WHITE
        text(c, pad(minute), cx + rMin, cy + 0.003f * u)

        // hour
        var hr = cal.get(Calendar.HOUR_OF_DAY)
        val ampm = if (hr < 12) "AM" else "PM"
        if (!hour24) hr = if (hr % 12 == 0) 12 else hr % 12
        paint.textAlign = Paint.Align.RIGHT
        paint.typeface = tfMedium
        paint.textSize = 0.19f * u
        text(c, pad(hr), cx - 0.03f * u, cy + 0.008f * u)
        if (!hour24) {
            paint.textSize = 0.034f * u
            paint.letterSpacing = 0.25f
            paint.color = withAlpha(accent, 0.9f)
            text(c, ampm, cx - 0.035f * u, cy + 0.13f * u)
            paint.letterSpacing = 0f
        }

        // day + date
        val ty = H * 0.17f
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = tfMedium
        paint.textSize = 0.07f * u
        paint.letterSpacing = 0.17f
        paint.color = accent
        text(c, days[cal.get(Calendar.DAY_OF_WEEK) - 1], W / 2, ty)
        paint.typeface = tfRegular
        paint.textSize = 0.042f * u
        paint.letterSpacing = 0.38f
        paint.color = white(0.75f)
        text(
            c,
            "${pad(cal.get(Calendar.DAY_OF_MONTH))} ${months[cal.get(Calendar.MONTH)]} ${cal.get(Calendar.YEAR)}",
            W / 2, ty + 0.08f * u
        )
        paint.letterSpacing = 0f
    }

    private fun ring(
        c: Canvas, cx: Float, cy: Float, u: Float, r: Float, cur: Float,
        fs: Float, hideInside: Boolean, halfH: Float
    ) {
        paint.typeface = tfRegular
        paint.textSize = fs
        paint.textAlign = Paint.Align.CENTER
        paint.strokeCap = Paint.Cap.BUTT
        val soft = 0.02f * u
        val step = r * Math.PI.toFloat() / 30f

        for (i in 0 until 60) {
            val delta = wrap(i - cur)
            val a = delta * Math.PI.toFloat() / 30f
            val co = cos(a)
            val si = sin(a)
            val vis = clamp01((co + 0.2f) / 1.2f).pow(1.6f)   // only the right half is visible
            if (vis < 0.01f) continue
            val major = i % 5 == 0
            val onRight = co > 0

            // tick marks, kept out of the pill
            val t0 = r + 0.045f * u
            val t1 = t0 + (if (major) 0.028f else 0.014f) * u
            val tickF = if (onRight) clamp01((abs(si) * t0 - halfH - 0.012f * u) / soft) else 1f
            if (tickF > 0f) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = max(2f, (if (major) 0.0045f else 0.003f) * u)
                paint.color = white(vis * tickF * (if (major) 0.75f else 0.35f))
                c.drawLine(cx + co * t0, cy - si * t0, cx + co * t1, cy - si * t1, paint)
            }

            val dy = abs(si) * r
            val fIn = if (!hideInside && onRight) clamp01(1f - dy / (0.75f * step)) else 0f
            paint.style = Paint.Style.FILL

            if (!major) {
                if (fIn <= 0.01f) continue
                paint.color = white(fIn)
                text(c, pad(i), cx + co * r, cy - si * r)
                continue
            }

            var f = 1f
            if (onRight) {
                val outer = halfH + fs * 0.6f + 0.008f * u
                f = max(clamp01((dy - outer) / soft), fIn)
            }
            if (f <= 0.01f) continue
            paint.color = white(vis * f * (if (fIn > 0f) 1f else 0.85f))
            text(c, pad(i), cx + co * r, cy - si * r)
        }
    }
}
