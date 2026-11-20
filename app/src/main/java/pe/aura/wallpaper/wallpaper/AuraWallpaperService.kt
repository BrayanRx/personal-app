package pe.aura.wallpaper.wallpaper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.Canvas
import android.os.Handler
import android.os.HandlerThread
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import androidx.core.content.ContextCompat
import pe.aura.wallpaper.data.DailyContent
import pe.aura.wallpaper.data.DailySelector
import pe.aura.wallpaper.data.Settings
import pe.aura.wallpaper.render.RenderOptions
import pe.aura.wallpaper.render.WallpaperRenderer
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Fondo animado: redibuja cada minuto (reloj) y cambia imagen/frase al cambiar el día.
 * Solo trabaja mientras es visible, así el consumo de batería es mínimo.
 */
class AuraWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = AuraEngine()

    private inner class AuraEngine : Engine(), SharedPreferences.OnSharedPreferenceChangeListener {

        private val thread = HandlerThread("aura-render").apply { start() }
        private val handler = Handler(thread.looper)
        private val settings = Settings(this@AuraWallpaperService)
        private val renderer = WallpaperRenderer(this@AuraWallpaperService)

        private var content: DailyContent? = null
        private var contentDate: LocalDate? = null
        @Volatile private var width = 0
        @Volatile private var height = 0
        @Volatile private var visible = false
        @Volatile private var dirty = false
        private var receiverRegistered = false

        private val timeReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = requestDraw()
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            settings.prefs.registerOnSharedPreferenceChangeListener(this)
        }

        override fun onDestroy() {
            settings.prefs.unregisterOnSharedPreferenceChangeListener(this)
            unregisterTimeReceiver()
            thread.quitSafely()
            super.onDestroy()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
            if (visible) {
                registerTimeReceiver()
                requestDraw()
            } else {
                unregisterTimeReceiver()
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            this.width = width
            this.height = height
            requestDraw(force = true)
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            visible = false
            unregisterTimeReceiver()
            super.onSurfaceDestroyed(holder)
        }

        override fun onSharedPreferenceChanged(prefs: SharedPreferences?, key: String?) {
            dirty = true
            requestDraw(force = true)
        }

        private fun requestDraw(force: Boolean = false) {
            handler.removeCallbacksAndMessages(null)
            handler.post { drawFrame(force) }
        }

        private fun drawFrame(force: Boolean) {
            if ((!visible && !force) || width == 0 || height == 0) return
            if (dirty) {
                dirty = false
                content = null
                renderer.invalidate()
            }
            val today = LocalDate.now()
            if (content == null || contentDate != today) {
                content = DailySelector.forDate(this@AuraWallpaperService, today)
                contentDate = today
            }
            val opts = RenderOptions(settings.showClock, settings.use24h, settings.style, settings.showAgenda)
            val holder = surfaceHolder
            var canvas: Canvas? = null
            try {
                canvas = runCatching { holder.lockHardwareCanvas() }.getOrNull() ?: holder.lockCanvas()
                canvas?.let { renderer.draw(it, width, height, content!!, LocalDateTime.now(), opts) }
            } finally {
                canvas?.let { runCatching { holder.unlockCanvasAndPost(it) } }
            }
        }

        private fun registerTimeReceiver() {
            if (receiverRegistered) return
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_TIME_TICK)
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
                addAction(Intent.ACTION_DATE_CHANGED)
            }
            ContextCompat.registerReceiver(
                this@AuraWallpaperService, timeReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            receiverRegistered = true
        }

        private fun unregisterTimeReceiver() {
            if (!receiverRegistered) return
            runCatching { unregisterReceiver(timeReceiver) }
            receiverRegistered = false
        }
    }
}
