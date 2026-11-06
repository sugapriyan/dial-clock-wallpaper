package com.sugapriyan.dialclock

import android.app.Activity
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class SettingsActivity : Activity() {

    private val hues = intArrayOf(0, 20, 35, 50, 90, 140, 170, 195, 215, 250, 280, 320)

    /** Theme colours: 12 hues x 5 shades, plus a white-to-grey row. */
    private val accentPalette: List<IntArray> by lazy {
        val rows = intArrayOf(88, 78, 68, 58, 48).map { l -> IntArray(12) { hsl(hues[it], 90, l) } }
        val greys = intArrayOf(100, 90, 80, 70, 60, 50, 42, 35, 28, 22, 16, 10)
        rows + listOf(IntArray(12) { hsl(0, 0, greys[it]) })
    }

    /** Background colours: 12 hues x 4 dark shades, plus a grey-to-black row. */
    private val bgPalette: List<IntArray> by lazy {
        val rows = intArrayOf(22, 16, 11, 7).map { l -> IntArray(12) { hsl(hues[it], 45, l) } }
        val greys = intArrayOf(30, 25, 20, 16, 13, 10, 8, 6, 4, 3, 2, 0)
        rows + listOf(IntArray(12) { hsl(215, 15, greys[it]) })
    }

    private lateinit var preview: PreviewView
    private lateinit var accentGrid: LinearLayout
    private lateinit var bgGrid: LinearLayout
    private lateinit var fontRow: LinearLayout
    private lateinit var sizeRow: LinearLayout
    private lateinit var fineLabel: TextView
    private val pickImage = 42

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private val prefs get() = Prefs.get(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.setBackgroundColor(0xFF12161C.toInt())

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        preview = PreviewView(this)
        root.addView(preview, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, (resources.displayMetrics.heightPixels * 0.42).toInt()))

        val scroll = ScrollView(this)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(14), dp(18), dp(28))
        }
        scroll.addView(col)
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        // Set wallpaper
        col.addView(Button(this).apply {
            text = "Set as live wallpaper"
            setOnClickListener { setWallpaper() }
        })

        // Accent
        col.addView(label("Accent / theme colour"))
        accentGrid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(accentGrid)
        col.addView(hexInput("Any colour: type a hex code, e.g. #22D3EE", Prefs.ACCENT))

        // Background colour
        col.addView(label("Background colour"))
        bgGrid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(bgGrid)
        col.addView(hexInput("Any colour: type a hex code, e.g. #101820", Prefs.BG_COLOR))

        // Background image
        col.addView(label("Background image"))
        val imgRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        imgRow.addView(Button(this).apply {
            text = "Choose image"
            setOnClickListener {
                startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE); type = "image/*"
                }, pickImage)
            }
        })
        imgRow.addView(Button(this).apply {
            text = "Remove"
            setOnClickListener {
                Prefs.bgFile(this@SettingsActivity).delete()
                prefs.edit().putLong(Prefs.BG_STAMP, System.currentTimeMillis()).apply()
                preview.reload()
            }
        })
        col.addView(imgRow)

        col.addView(label("Image dimming"))
        col.addView(slider(85, (prefs.getFloat(Prefs.DIM, Prefs.DEFAULT_DIM) * 100).roundToInt()) { p ->
            prefs.edit().putFloat(Prefs.DIM, p / 100f).apply()
        })

        // Font
        col.addView(label("Clock font"))
        fontRow = segRow()
        col.addView(fontRow)

        // Glow
        col.addView(Switch(this).apply {
            text = "Accent glow"
            setTextColor(Color.WHITE)
            setPadding(0, dp(16), 0, 0)
            isChecked = prefs.getBoolean(Prefs.GLOW, true)
            setOnCheckedChangeListener { _, on -> prefs.edit().putBoolean(Prefs.GLOW, on).apply(); preview.reload() }
        })

        // Size presets + fine-tune
        col.addView(label("Clock size"))
        sizeRow = segRow()
        col.addView(sizeRow)
        fineLabel = label("")
        col.addView(fineLabel)
        val fine = prefs.getFloat(Prefs.FINE, 1f).coerceIn(Prefs.FINE_MIN, Prefs.FINE_MAX)
        col.addView(slider(30, ((fine - Prefs.FINE_MIN) * 100).roundToInt()) { p ->
            prefs.edit().putFloat(Prefs.FINE, Prefs.FINE_MIN + p / 100f).apply()
            updateFineLabel()
        })

        // Position
        col.addView(label("Clock position (up / down)"))
        val pos = prefs.getFloat(Prefs.POS, 0f).coerceIn(Prefs.POS_MIN, Prefs.POS_MAX)
        col.addView(slider(35, ((pos - Prefs.POS_MIN) * 100).roundToInt()) { p ->
            prefs.edit().putFloat(Prefs.POS, Prefs.POS_MIN + p / 100f).apply()
        })

        // 12 / 24
        col.addView(label("Time format"))
        col.addView(Switch(this).apply {
            text = "24-hour format"
            setTextColor(Color.WHITE)
            isChecked = prefs.getBoolean(Prefs.HOUR24, false)
            setOnCheckedChangeListener { _, on -> prefs.edit().putBoolean(Prefs.HOUR24, on).apply(); preview.reload() }
        })

        setContentView(root)
        refresh()
    }

    // ---------- small UI helpers ----------

    private fun label(t: String) = TextView(this).apply {
        text = t.uppercase()
        setTextColor(0xFF8B97A3.toInt())
        textSize = 12f
        letterSpacing = 0.1f
        setPadding(0, dp(18), 0, dp(8))
    }

    private fun updateFineLabel() {
        val f = prefs.getFloat(Prefs.FINE, 1f)
        fineLabel.text = "FINE-TUNE SIZE  ${(f * 100).roundToInt()}%"
    }

    /** SeekBar from 0..max that saves through [onChange] and refreshes the preview. */
    private fun slider(maxValue: Int, start: Int, onChange: (Int) -> Unit) = SeekBar(this).apply {
        this.max = maxValue
        progress = start.coerceIn(0, maxValue)
        setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                if (fromUser) { onChange(p); preview.reload() }
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
    }

    private fun segRow() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(dp(3), dp(3), dp(3), dp(3))
        background = GradientDrawable().apply { setColor(0xFF26303A.toInt()); cornerRadius = dp(10).toFloat() }
    }

    /** Fills a segmented row with one option per entry; the selected one is highlighted. */
    private fun fillSeg(row: LinearLayout, options: Map<String, String>, key: String, def: String) {
        row.removeAllViews()
        val current = prefs.getString(key, null) ?: def
        for ((value, caption) in options) {
            val on = value == current
            row.addView(TextView(this).apply {
                text = caption
                gravity = Gravity.CENTER
                textSize = 13f
                setTextColor(if (on) 0xFF111111.toInt() else 0xFFAAB4BE.toInt())
                setPadding(0, dp(9), 0, dp(9))
                background = if (on) GradientDrawable().apply {
                    setColor(0xFFE8EDF2.toInt()); cornerRadius = dp(8).toFloat()
                } else null
                setOnClickListener { prefs.edit().putString(key, value).apply(); refresh() }
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
    }

    /** Fills a colour grid: one row of 12 equal cells per palette row. */
    private fun fillPalette(grid: LinearLayout, palette: List<IntArray>, key: String, def: Int) {
        grid.removeAllViews()
        val current = prefs.getInt(key, def)
        for (rowColors in palette) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for (c in rowColors) {
                val on = c == current
                row.addView(View(this).apply {
                    background = GradientDrawable().apply {
                        setColor(c)
                        cornerRadius = dp(5).toFloat()
                        if (on) setStroke(dp(2), Color.WHITE)
                    }
                    setOnClickListener { prefs.edit().putInt(key, c).apply(); refresh() }
                }, LinearLayout.LayoutParams(0, dp(26), 1f).apply { setMargins(dp(2), dp(2), dp(2), dp(2)) })
            }
            grid.addView(row)
        }
    }

    private fun hexInput(hint: String, key: String) = EditText(this).apply {
        this.hint = hint
        setHintTextColor(0xFF5D6873.toInt())
        setTextColor(Color.WHITE)
        textSize = 14f
        isSingleLine = true
        setOnEditorActionListener { v, _, _ ->
            try {
                val c = Color.parseColor(v.text.toString().trim().let { if (it.startsWith("#")) it else "#$it" })
                prefs.edit().putInt(key, c or 0xFF000000.toInt()).apply()
                refresh()
            } catch (e: Exception) {
                Toast.makeText(this@SettingsActivity, "Use a colour like #22D3EE", Toast.LENGTH_SHORT).show()
            }
            false
        }
    }

    private fun refresh() {
        fillPalette(accentGrid, accentPalette, Prefs.ACCENT, Prefs.DEFAULT_ACCENT)
        fillPalette(bgGrid, bgPalette, Prefs.BG_COLOR, Prefs.DEFAULT_BG)
        fillSeg(fontRow, Prefs.FONT_LABELS, Prefs.FONT, Prefs.DEFAULT_FONT)
        fillSeg(sizeRow, Prefs.SIZE_LABELS, Prefs.SIZE, Prefs.DEFAULT_SIZE)
        updateFineLabel()
        preview.reload()
    }

    /** HSL (h 0–360, s and l 0–100) to an opaque colour int. */
    private fun hsl(h: Int, s: Int, l: Int): Int {
        val sf = s / 100f
        val lf = l / 100f
        val c = (1 - abs(2 * lf - 1)) * sf
        val x = c * (1 - abs((h / 60f) % 2 - 1))
        val m = lf - c / 2
        val (r, g, b) = when {
            h < 60 -> Triple(c, x, 0f)
            h < 120 -> Triple(x, c, 0f)
            h < 180 -> Triple(0f, c, x)
            h < 240 -> Triple(0f, x, c)
            h < 300 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        fun ch(v: Float) = (min(1f, max(0f, v + m)) * 255).roundToInt()
        return Color.rgb(ch(r), ch(g), ch(b))
    }

    private fun setWallpaper() {
        try {
            startActivity(Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).putExtra(
                WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                ComponentName(this, ClockWallpaperService::class.java)))
        } catch (e: Exception) {
            startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != pickImage || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / sample > 2400) sample *= 2
            val bmp = contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return
            Prefs.bgFile(this).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            bmp.recycle()
            prefs.edit().putLong(Prefs.BG_STAMP, System.currentTimeMillis()).apply()
            preview.reload()
        } catch (e: Exception) {
            Toast.makeText(this, "Couldn't load that image", Toast.LENGTH_SHORT).show()
        }
    }

    /** Live preview of the wallpaper inside the settings screen. */
    class PreviewView(ctx: Context) : View(ctx) {
        private val renderer = ClockRenderer(ctx)
        fun reload() { renderer.reload(); invalidate() }
        override fun onDraw(canvas: Canvas) {
            // render at phone proportions, scaled to fit this box
            val ph = resources.displayMetrics.heightPixels.toFloat()
            val pw = resources.displayMetrics.widthPixels.toFloat()
            val s = height / ph
            canvas.save()
            canvas.translate((width - pw * s) / 2f, 0f)
            canvas.scale(s, s)
            canvas.clipRect(0f, 0f, pw, ph)
            renderer.draw(canvas, pw.toInt(), ph.toInt())
            canvas.restore()
            postInvalidateOnAnimation()
        }
    }
}
