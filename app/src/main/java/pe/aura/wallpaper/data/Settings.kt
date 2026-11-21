package pe.aura.wallpaper.data

import android.content.Context
import android.content.SharedPreferences

enum class Style { MINIMAL, GLASS }

/** SharedPreferences: lectura síncrona desde el WallpaperService y listener de cambios. */
class Settings(context: Context) {
    val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("aura", Context.MODE_PRIVATE)

    var showClock: Boolean
        get() = prefs.getBoolean(KEY_SHOW_CLOCK, false)
        set(v) = prefs.edit().putBoolean(KEY_SHOW_CLOCK, v).apply()

    var use24h: Boolean
        get() = prefs.getBoolean(KEY_24H, true)
        set(v) = prefs.edit().putBoolean(KEY_24H, v).apply()

    var style: Style
        get() = runCatching { Style.valueOf(prefs.getString(KEY_STYLE, null)!!) }.getOrDefault(Style.MINIMAL)
        set(v) = prefs.edit().putString(KEY_STYLE, v.name).apply()

    var showAgenda: Boolean
        get() = prefs.getBoolean(KEY_SHOW_AGENDA, true)
        set(v) = prefs.edit().putBoolean(KEY_SHOW_AGENDA, v).apply()

    /** Actualizar automáticamente el fondo estático de bloqueo cada medianoche. */
    var lockAutoUpdate: Boolean
        get() = prefs.getBoolean(KEY_LOCK_AUTO, false)
        set(v) = prefs.edit().putBoolean(KEY_LOCK_AUTO, v).apply()

    // ---------- Sueño ----------

    /** Recordatorios de Desconectar / Dormir / Despertar (se activa desde la pestaña Sueño). */
    var sleepEnabled: Boolean
        get() = prefs.getBoolean(KEY_SLEEP_ENABLED, false)
        set(v) = prefs.edit().putBoolean(KEY_SLEEP_ENABLED, v).apply()

    var pauseMusic: Boolean
        get() = prefs.getBoolean(KEY_PAUSE_MUSIC, true)
        set(v) = prefs.edit().putBoolean(KEY_PAUSE_MUSIC, v).apply()

    /** Línea "Anoche: 6 h 50" en la tarjeta de agenda del fondo. */
    var showLastNight: Boolean
        get() = prefs.getBoolean(KEY_SHOW_LAST_NIGHT, true)
        set(v) = prefs.edit().putBoolean(KEY_SHOW_LAST_NIGHT, v).apply()

    /** Última noche por la que ya se preguntó "¿A qué hora te dormiste?". */
    var sleepAskedNight: String?
        get() = prefs.getString(KEY_SLEEP_ASKED, null)
        set(v) = prefs.edit().putString(KEY_SLEEP_ASKED, v).apply()

    // ---------- Sincronización con la bóveda de Obsidian ----------

    var syncTreeUri: String?
        get() = prefs.getString(KEY_SYNC_TREE, null)
        set(v) = prefs.edit().putString(KEY_SYNC_TREE, v).apply()

    var syncAgendaSig: String?
        get() = prefs.getString(KEY_SYNC_AGENDA_SIG, null)
        set(v) = prefs.edit().putString(KEY_SYNC_AGENDA_SIG, v).apply()

    var syncQuotesSig: String?
        get() = prefs.getString(KEY_SYNC_QUOTES_SIG, null)
        set(v) = prefs.edit().putString(KEY_SYNC_QUOTES_SIG, v).apply()

    /** Momento del último intento (para limitar la frecuencia desde el fondo animado). */
    var syncLastAttempt: Long
        get() = prefs.getLong(KEY_SYNC_LAST_ATTEMPT, 0)
        set(v) = prefs.edit().putLong(KEY_SYNC_LAST_ATTEMPT, v).apply()

    fun saveSyncStatus(time: Long, activities: Int?, quotes: Int?, error: String?) {
        prefs.edit().apply {
            putLong(KEY_SYNC_LAST_TIME, time)
            activities?.let { putInt(KEY_SYNC_ACTIVITY_COUNT, it) }
            quotes?.let { putInt(KEY_SYNC_QUOTE_COUNT, it) }
            if (error == null) remove(KEY_SYNC_ERROR) else putString(KEY_SYNC_ERROR, error)
        }.apply()
    }

    val syncLastTime: Long get() = prefs.getLong(KEY_SYNC_LAST_TIME, 0)
    val syncActivityCount: Int get() = prefs.getInt(KEY_SYNC_ACTIVITY_COUNT, -1)
    val syncQuoteCount: Int get() = prefs.getInt(KEY_SYNC_QUOTE_COUNT, -1)
    val syncError: String? get() = prefs.getString(KEY_SYNC_ERROR, null)

    /** Fuerza que la próxima sincronización vuelva a leer las notas aunque no hayan cambiado. */
    fun resetSyncSignatures() {
        prefs.edit().remove(KEY_SYNC_AGENDA_SIG).remove(KEY_SYNC_QUOTES_SIG).apply()
    }

    fun clearSync() {
        prefs.edit().apply {
            listOf(
                KEY_SYNC_TREE, KEY_SYNC_AGENDA_SIG, KEY_SYNC_QUOTES_SIG, KEY_SYNC_LAST_ATTEMPT,
                KEY_SYNC_LAST_TIME, KEY_SYNC_ACTIVITY_COUNT, KEY_SYNC_QUOTE_COUNT, KEY_SYNC_ERROR,
            ).forEach { remove(it) }
        }.apply()
    }

    /** Se incrementa tras importar/borrar datos para que el fondo animado se redibuje. */
    fun bumpDataVersion() {
        prefs.edit().putLong(KEY_DATA_VERSION, prefs.getLong(KEY_DATA_VERSION, 0) + 1).apply()
    }

    companion object {
        const val KEY_SHOW_CLOCK = "show_clock"
        const val KEY_24H = "use_24h"
        const val KEY_STYLE = "style"
        const val KEY_SHOW_AGENDA = "show_agenda"
        const val KEY_LOCK_AUTO = "lock_auto"
        const val KEY_DATA_VERSION = "data_version"

        const val KEY_SLEEP_ENABLED = "sleep_enabled"
        const val KEY_PAUSE_MUSIC = "sleep_pause_music"
        const val KEY_SHOW_LAST_NIGHT = "sleep_show_last_night"
        const val KEY_SLEEP_ASKED = "sleep_asked_night"

        /** Todas las claves de sincronización empiezan así; el fondo animado las ignora. */
        const val SYNC_PREFIX = "sync_"
        const val KEY_SYNC_TREE = "sync_tree_uri"
        const val KEY_SYNC_AGENDA_SIG = "sync_agenda_sig"
        const val KEY_SYNC_QUOTES_SIG = "sync_quotes_sig"
        const val KEY_SYNC_LAST_ATTEMPT = "sync_last_attempt"
        const val KEY_SYNC_LAST_TIME = "sync_last_time"
        const val KEY_SYNC_ACTIVITY_COUNT = "sync_activity_count"
        const val KEY_SYNC_QUOTE_COUNT = "sync_quote_count"
        const val KEY_SYNC_ERROR = "sync_error"
    }
}
