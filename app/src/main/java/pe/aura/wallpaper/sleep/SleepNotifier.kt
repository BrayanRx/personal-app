package pe.aura.wallpaper.sleep

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import pe.aura.wallpaper.R
import pe.aura.wallpaper.ui.MainActivity

/** Notificaciones del canal "Sueño". */
object SleepNotifier {
    private const val CHANNEL = "sleep"

    fun canNotify(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return context.getSystemService(NotificationManager::class.java).areNotificationsEnabled()
    }

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Sueño", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Recordatorios para desconectar, dormir y despertar"
                },
            )
        }
    }

    fun show(context: Context, event: SleepEvent, text: String? = null, withAction: Boolean = true) {
        if (!canNotify(context)) return
        ensureChannel(context)
        val (title, body, actionLabel, action) = when (event) {
            SleepEvent.DESCONECTAR -> Quad("Hora de desconectar", text ?: "Pausé la música. Sin pantallas desde ahora.", "5 min más", SleepReceiver.ACTION_SNOOZE)
            SleepEvent.DORMIR -> Quad("Hora de dormir", text ?: "Meta: ${SleepLogic.format(SleepLogic.GOAL_MIN)}", "Me voy a dormir", SleepReceiver.ACTION_SLEEP_NOW)
            SleepEvent.DESPERTAR -> Quad("Buenos días", text ?: "Toca «Desperté» para registrar tu noche.", "Desperté", SleepReceiver.ACTION_WOKE)
        }
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_TAB, 2),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_aura)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
        if (withAction) {
            val pi = PendingIntent.getBroadcast(
                context, event.requestCode + 10,
                Intent(context, SleepReceiver::class.java).setAction(action),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            builder.addAction(0, actionLabel, pi)
        }
        // canNotify() ya verificó POST_NOTIFICATIONS.
        if (Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        ) {
            context.getSystemService(NotificationManager::class.java).notify(event.requestCode, builder.build())
        }
    }

    fun cancel(context: Context, event: SleepEvent) {
        context.getSystemService(NotificationManager::class.java).cancel(event.requestCode)
    }

    private data class Quad(val a: String, val b: String, val c: String, val d: String)
}
