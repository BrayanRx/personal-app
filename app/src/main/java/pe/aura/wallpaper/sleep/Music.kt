package pe.aura.wallpaper.sleep

import android.content.Context
import android.media.AudioManager
import android.os.SystemClock
import android.view.KeyEvent

/**
 * Controla la música activa con teclas multimedia (pulsación + liberación).
 * Funciona con Spotify y cualquier app que tenga una sesión multimedia, sin APIs de terceros.
 */
object Music {
    fun pause(context: Context) = send(context, KeyEvent.KEYCODE_MEDIA_PAUSE)

    fun play(context: Context) = send(context, KeyEvent.KEYCODE_MEDIA_PLAY)

    fun isPlaying(context: Context): Boolean =
        context.getSystemService(AudioManager::class.java).isMusicActive

    private fun send(context: Context, code: Int) {
        val audio = context.getSystemService(AudioManager::class.java)
        val t = SystemClock.uptimeMillis()
        audio.dispatchMediaKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0))
        audio.dispatchMediaKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, code, 0))
    }
}
