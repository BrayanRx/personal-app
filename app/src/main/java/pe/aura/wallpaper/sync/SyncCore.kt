package pe.aura.wallpaper.sync

import pe.aura.wallpaper.data.ActivityRepository
import pe.aura.wallpaper.data.QuoteRepository

/** Una nota encontrada en la carpeta. [signature] cambia cuando cambia el archivo. */
class NoteFile(val name: String, val signature: String, val read: () -> String)

data class SyncOutcome(
    /** Se guardaron datos nuevos: hay que redibujar. */
    val changed: Boolean,
    val agendaSig: String?,
    val quotesSig: String?,
    /** Cantidades leídas en esta pasada (null = esa nota no se procesó). */
    val activities: Int?,
    val quotes: Int?,
    val errors: List<String>,
)

/**
 * Lógica de sincronización sin dependencias de Android (probable en la JVM).
 * Reglas:
 *  - Solo procesa una nota si su firma cambió (o si [force]).
 *  - La agenda reemplaza las actividades "vault" y conserva las "manual".
 *  - Las frases reemplazan el banco completo.
 *  - Si algo falla, NO toca los datos guardados ni avanza la firma (se reintentará).
 */
object SyncCore {
    const val AGENDA_MARKER = "Rutina diaria"
    const val QUOTES_MARKER = "Frases para la app"

    fun findNote(files: List<NoteFile>, marker: String): NoteFile? =
        files.filter { it.name.endsWith(".md", ignoreCase = true) && it.name.contains(marker, ignoreCase = true) }
            .minByOrNull { it.name }

    fun run(
        files: List<NoteFile>,
        activities: ActivityRepository,
        quotes: QuoteRepository,
        prevAgendaSig: String?,
        prevQuotesSig: String?,
        force: Boolean,
    ): SyncOutcome {
        val errors = mutableListOf<String>()
        var changed = false
        var agendaSig = prevAgendaSig
        var quotesSig = prevQuotesSig
        var activityCount: Int? = null
        var quoteCount: Int? = null

        val agenda = findNote(files, AGENDA_MARKER)
        if (agenda == null) {
            errors += "No encontré la nota de agenda (nombre con «$AGENDA_MARKER»)."
        } else if (force || agenda.signature != prevAgendaSig) {
            runCatching { activities.replaceVault(agenda.read()) }
                .onSuccess { activityCount = it; agendaSig = agenda.signature; changed = true }
                .onFailure { errors += "Agenda: ${describe(it)}" }
        }

        val bank = findNote(files, QUOTES_MARKER)
        if (bank == null) {
            errors += "No encontré la nota de frases (nombre con «$QUOTES_MARKER»)."
        } else if (force || bank.signature != prevQuotesSig) {
            runCatching { quotes.replaceAll(bank.read()) }
                .onSuccess { quoteCount = it; quotesSig = bank.signature; changed = true }
                .onFailure { errors += "Frases: ${describe(it)}" }
        }

        return SyncOutcome(changed, agendaSig, quotesSig, activityCount, quoteCount, errors)
    }

    private fun describe(t: Throwable): String = when (t) {
        is org.json.JSONException -> "JSON inválido (${t.message?.take(80)})"
        else -> t.message?.take(120) ?: t.javaClass.simpleName
    }
}
