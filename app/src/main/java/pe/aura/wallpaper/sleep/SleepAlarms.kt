package pe.aura.wallpaper.sleep

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import pe.aura.wallpaper.data.ActivityRepository
import pe.aura.wallpaper.data.Settings
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Programa la próxima ocurrencia de cada evento de sueño con alarmas inexactas
 * (setAndAllowWhileIdle): ±unos minutos basta y no hace falta el permiso de alarmas exactas.
 * Se reprograma al abrir la app, tras cada sincronización, tras cada alarma y al reiniciar.
 */
object SleepAlarms {
    private const val SNOOZE_CODE = 104
    const val SNOOZE_MIN = 5L

    /** Devuelve los próximos eventos programados (vacío si Sueño está desactivado). */
    fun reschedule(context: Context, now: LocalDateTime = LocalDateTime.now()): Map<SleepEvent, LocalDateTime> {
        val am = context.getSystemService(AlarmManager::class.java)
        SleepEvent.values().forEach { am.cancel(eventIntent(context, it)) }
        if (!Settings(context).sleepEnabled) return emptyMap()

        val activities = ActivityRepository(context).all()
        val scheduled = mutableMapOf<SleepEvent, LocalDateTime>()
        SleepEvent.values().forEach { e ->
            val at = SleepLogic.next(activities, now, e) ?: return@forEach
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis(at), eventIntent(context, e))
            scheduled[e] = at
        }
        return scheduled
    }

    /** "5 min más": pausa la música otra vez dentro de 5 minutos. */
    fun schedulePauseIn5(context: Context) {
        val at = LocalDateTime.now().plusMinutes(SNOOZE_MIN)
        context.getSystemService(AlarmManager::class.java)
            .setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis(at), pauseIntent(context))
    }

    private fun millis(t: LocalDateTime) = t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun eventIntent(context: Context, e: SleepEvent): PendingIntent = PendingIntent.getBroadcast(
        context, e.requestCode,
        Intent(context, SleepReceiver::class.java).setAction(SleepReceiver.ACTION_EVENT).putExtra(SleepReceiver.EXTRA_EVENT, e.name),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun pauseIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, SNOOZE_CODE,
        Intent(context, SleepReceiver::class.java).setAction(SleepReceiver.ACTION_PAUSE_MUSIC),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
