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
    }
}
