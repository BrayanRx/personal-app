package pe.aura.wallpaper.sleep

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Reprograma las alarmas de sueño tras reiniciar, actualizar la app o cambiar la hora del sistema. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED -> SleepAlarms.reschedule(context)
        }
    }
}
