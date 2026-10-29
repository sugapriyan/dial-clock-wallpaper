package com.sugapriyan.dialclock

import android.content.SharedPreferences
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder

class ClockWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = ClockEngine()

    inner class ClockEngine : Engine(), SharedPreferences.OnSharedPreferenceChangeListener {

        private val handler = Handler(Looper.getMainLooper())
        private val renderer = ClockRenderer(this@ClockWallpaperService)
        private var visible = false
        private val drawRunner = Runnable { drawFrame() }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            renderer.reload()
            Prefs.get(this@ClockWallpaperService).registerOnSharedPreferenceChangeListener(this)
        }

        override fun onSharedPreferenceChanged(p: SharedPreferences?, key: String?) {
            renderer.reload()
            if (visible) drawFrame()
        }

        override fun onVisibilityChanged(isVisible: Boolean) {
            visible = isVisible
            if (isVisible) drawFrame() else handler.removeCallbacks(drawRunner)
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            drawFrame()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            visible = false
            handler.removeCallbacks(drawRunner)
            super.onSurfaceDestroyed(holder)
        }

        override fun onDestroy() {
            handler.removeCallbacks(drawRunner)
            Prefs.get(this@ClockWallpaperService).unregisterOnSharedPreferenceChangeListener(this)
            super.onDestroy()
        }

        private fun drawFrame() {
            val holder = surfaceHolder
            var canvas: Canvas? = null
            try {
                canvas = try { holder.lockHardwareCanvas() } catch (e: Exception) { holder.lockCanvas() }
                if (canvas != null) renderer.draw(canvas, canvas.width, canvas.height)
            } catch (e: Exception) {
            } finally {
                if (canvas != null) {
                    try { holder.unlockCanvasAndPost(canvas) } catch (e: Exception) {}
                }
            }
            handler.removeCallbacks(drawRunner)
            if (visible) handler.postDelayed(drawRunner, 33)   // ~30 fps
        }
    }
}
