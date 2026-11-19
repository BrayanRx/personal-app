package pe.aura.wallpaper.data

import android.content.Context
import java.io.File
import java.time.LocalDate

data class DailyContent(val image: File?, val quote: Quote?)

/**
 * Selección determinista por día: no necesita tareas en segundo plano para rotar,
 * el mismo día siempre produce el mismo fondo y frase.
 */
object DailySelector {
    fun forDate(context: Context, date: LocalDate = LocalDate.now()): DailyContent {
        val day = date.toEpochDay()
        val images = ImageRepository(context).all()
        val quotes = QuoteRepository(context).all()

        val quote = quotes.firstOrNull { it.date == date }
            ?: quotes.filter { it.date == null }.takeIf { it.isNotEmpty() }
                ?.let { it[Math.floorMod(day, it.size.toLong()).toInt()] }

        val image = images.takeIf { it.isNotEmpty() }
            ?.let { it[Math.floorMod(day, it.size.toLong()).toInt()] }

        return DailyContent(image, quote)
    }
}
