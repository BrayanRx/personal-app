package pe.aura.wallpaper.sleep

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import pe.aura.wallpaper.data.Settings
import java.time.LocalDateTime

/** Recibe las alarmas de sueño y los botones de las notificaciones. */
class SleepReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val settings = Settings(context)
        val now = LocalDateTime.now()
        when (intent.action) {
            ACTION_EVENT -> {
                val event = intent.getStringExtra(EXTRA_EVENT)?.let { runCatching { SleepEvent.valueOf(it) }.getOrNull() }
                if (event != null && settings.sleepEnabled) {
                    if (event == SleepEvent.DESCONECTAR) {
                        val pause = settings.pauseMusic
                        val wasPlaying = pause && Music.isPlaying(context)
                        if (pause) Music.pause(context)
                        SleepNotifier.show(
                            context, event,
                            text = if (wasPlaying) "Pausé la música. Sin pantallas desde ahora." else "Sin pantallas desde ahora.",
                            withAction = wasPlaying,
                        )
                    } else {
                        SleepNotifier.show(context, event)
                    }
                }
                SleepAlarms.reschedule(context, now)
            }
            ACTION_SLEEP_NOW -> {
                SleepRepository(context).recordSleep(now)
                SleepNotifier.cancel(context, SleepEvent.DORMIR)
                settings.bumpDataVersion()
                Toast.makeText(context, "Buenas noches. Registré tu hora de dormir.", Toast.LENGTH_SHORT).show()
            }
            ACTION_WOKE -> {
                val entry = SleepRepository(context).recordWake(now)
                SleepNotifier.cancel(context, SleepEvent.DESPERTAR)
                settings.bumpDataVersion()
                val msg = entry.durationMin?.let { "Dormiste ${SleepLogic.format(it)}." } ?: "Registré tu hora de despertar."
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }
            ACTION_SNOOZE -> {
                // "5 min más" (una sola vez): reanuda y vuelve a pausar en 5 minutos.
                Music.play(context)
                SleepAlarms.schedulePauseIn5(context)
                SleepNotifier.show(context, SleepEvent.DESCONECTAR, text = "La música se pausará en ${SleepAlarms.SNOOZE_MIN} minutos.", withAction = false)
            }
            ACTION_PAUSE_MUSIC -> {
                Music.pause(context)
                SleepNotifier.cancel(context, SleepEvent.DESCONECTAR)
            }
        }
    }

    companion object {
        const val ACTION_EVENT = "pe.aura.wallpaper.sleep.EVENT"
        const val ACTION_SLEEP_NOW = "pe.aura.wallpaper.sleep.SLEEP_NOW"
        const val ACTION_WOKE = "pe.aura.wallpaper.sleep.WOKE"
        const val ACTION_SNOOZE = "pe.aura.wallpaper.sleep.SNOOZE"
        const val ACTION_PAUSE_MUSIC = "pe.aura.wallpaper.sleep.PAUSE_MUSIC"
        const val EXTRA_EVENT = "event"
    }
}
