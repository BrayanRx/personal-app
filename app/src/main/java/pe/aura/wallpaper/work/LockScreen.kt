package pe.aura.wallpaper.work

import android.app.WallpaperManager
import android.content.Context
import android.hardware.display.DisplayManager
import android.util.DisplayMetrics
import android.view.Display
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import pe.aura.wallpaper.data.DailySelector
import pe.aura.wallpaper.data.Settings
import pe.aura.wallpaper.render.RenderOptions
import pe.aura.wallpaper.render.WallpaperRenderer
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * Modo estático: genera una imagen (fondo + frase) y la fija SOLO en la pantalla de bloqueo.
 * Funciona en cualquier launcher/ROM, incluso donde el fondo animado no se permite en bloqueo.
 * No dibuja reloj: el sistema ya muestra el suyo y uno estático quedaría desfasado.
 */
object LockScreen {
    private const val WORK_NAME = "aura-lock-daily"

    fun apply(context: Context) {
        val (w, h) = screenSize(context)
        val settings = Settings(context)
        val bmp = WallpaperRenderer(context).render(
            w, h, DailySelector.forDate(context), LocalDateTime.now(),
            RenderOptions(showClock = false, use24h = settings.use24h, style = settings.style, showAgenda = settings.showAgenda),
        )
        WallpaperManager.getInstance(context).setBitmap(bmp, null, true, WallpaperManager.FLAG_LOCK)
        bmp.recycle()
    }

    fun schedule(context: Context) {
        val untilMidnight = Duration.between(
            LocalDateTime.now(), LocalDate.now().plusDays(1).atStartOfDay().plusMinutes(1),
        )
        val req = PeriodicWorkRequestBuilder<LockScreenWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(untilMidnight.toMillis(), TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, req)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    @Suppress("DEPRECATION")
    fun screenSize(context: Context): Pair<Int, Int> {
        val dm = context.getSystemService(DisplayManager::class.java)
        val metrics = DisplayMetrics()
        dm.getDisplay(Display.DEFAULT_DISPLAY).getRealMetrics(metrics)
        return metrics.widthPixels to metrics.heightPixels
    }
}

class LockScreenWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = runCatching {
        LockScreen.apply(applicationContext)
        Result.success()
    }.getOrElse { Result.retry() }
}
