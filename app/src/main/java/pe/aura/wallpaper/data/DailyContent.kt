package pe.aura.wallpaper.data

import android.content.Context
import pe.aura.wallpaper.sleep.SleepRepository
import java.io.File
import java.time.LocalDate

data class DailyContent(
    val image: File?,
    val quote: Quote?,
    val activities: List<Activity> = emptyList(),
    /** Duración de la noche anterior, si está registrada completa. */
    val lastNightMin: Int? = null,
)

/**
 * Selección determinista por día: no necesita tareas en segundo plano para rotar,
 * el mismo día siempre produce el mismo fondo y frase.
 */
object DailySelector {
    fun forDate(context: Context, date: LocalDate = LocalDate.now()): DailyContent {
        val day = date.toEpochDay()
        val quotes = QuoteRepository(context).all()

        val quote = quotes.firstOrNull { it.date == date }
            ?: quotes.filter { it.date == null }.takeIf { it.isNotEmpty() }
                ?.let { it[Math.floorMod(day, it.size.toLong()).toInt()] }

        return DailyContent(
            image = ImageRepository(context).forDate(date),
            quote = quote,
            activities = ActivityRepository(context).forDate(date),
            lastNightMin = SleepRepository(context).forNight(date.minusDays(1))?.durationMin,
        )
    }
}
