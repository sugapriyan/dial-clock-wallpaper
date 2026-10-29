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
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import kotlin.math.max

class SettingsActivity : Activity() {

    private val accents = intArrayOf(
        0xFFFFFFFF.toInt(), 0xFF7DD3FC.toInt(), 0xFFFBBF24.toInt(), 0xFFF472B6.toInt(),
        0xFF86EFAC.toInt(), 0xFFC4B5FD.toInt(), 0xFFFB923C.toInt(), 0xFFF87171.toInt()
    )
    private val bgs = intArrayOf(
        0xFF0D141C.toInt(), 0xFF000000.toInt(), 0xFF1A1A2E.toInt(),
        0xFF0F2A24.toInt(), 0xFF2B1A12.toInt(), 0xFF2A1030.toInt()
    )

    private lateinit var preview: PreviewView
    private lateinit var accentRow: LinearLayout
    private lateinit var bgRow: LinearLayout
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
        accentRow = swatchRow()
        col.addView(scrollRow(accentRow))
        col.addView(hexInput("Custom accent hex, e.g. #22D3EE", Prefs.ACCENT))

        // Background colour
        col.addView(label("Background colour"))
        bgRow = swatchRow()
        col.addView(scrollRow(bgRow))
        col.addView(hexInput("Custom background hex, e.g. #101820", Prefs.BG_COLOR))

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
                refresh()
            }
        })
        col.addView(imgRow)

        col.addView(label("Image dimming"))
        col.addView(SeekBar(this).apply {
            this.max = 85
            progress = (prefs.getFloat(Prefs.DIM, Prefs.DEFAULT_DIM) * 100).toInt()
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    if (fromUser) { prefs.edit().putFloat(Prefs.DIM, p / 100f).apply(); refresh() }
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        })

        // 12 / 24
        col.addView(label("Time format"))
        col.addView(Switch(this).apply {
            text = "24-hour format"
            setTextColor(Color.WHITE)
            isChecked = prefs.getBoolean(Prefs.HOUR24, false)
            setOnCheckedChangeListener { _, on -> prefs.edit().putBoolean(Prefs.HOUR24, on).apply(); refresh() }
        })

        setContentView(root)
        refresh()
    }

    private fun label(t: String) = TextView(this).apply {
        text = t.uppercase()
        setTextColor(0xFF8B97A3.toInt())
        textSize = 12f
        letterSpacing = 0.1f
        setPadding(0, dp(18), 0, dp(8))
    }

    private fun swatchRow() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    private fun scrollRow(row: LinearLayout) = HorizontalScrollView(this).apply {
        isHorizontalScrollBarEnabled = false
        addView(row)
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

    private fun fillSwatches(row: LinearLayout, colors: IntArray, key: String, def: Int) {
        row.removeAllViews()
        val current = prefs.getInt(key, def)
        for (c in colors) {
            val v = View(this)
            v.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(c)
                setStroke(dp(if (c == current) 3 else 1), if (c == current) Color.WHITE else 0x40FFFFFF)
            }
            v.setOnClickListener { prefs.edit().putInt(key, c).apply(); refresh() }
            row.addView(v, LinearLayout.LayoutParams(dp(36), dp(36)).apply { rightMargin = dp(12) })
        }
    }

    private fun refresh() {
        fillSwatches(accentRow, accents, Prefs.ACCENT, Prefs.DEFAULT_ACCENT)
        fillSwatches(bgRow, bgs, Prefs.BG_COLOR, Prefs.DEFAULT_BG)
        preview.reload()
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
            refresh()
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
