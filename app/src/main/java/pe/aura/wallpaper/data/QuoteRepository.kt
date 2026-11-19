package pe.aura.wallpaper.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

data class Quote(
    val text: String,
    val author: String? = null,
    /** Si está presente, la frase se muestra exactamente ese día. */
    val date: LocalDate? = null,
)

/**
 * Guarda las frases en filesDir/quotes.json (formato normalizado).
 * Si el usuario no importó nada, usa assets/quotes_default.json.
 */
class QuoteRepository(private val context: Context) {

    private val file get() = File(context.filesDir, "quotes.json")

    fun all(): List<Quote> {
        val raw = if (file.exists()) file.readText()
        else context.assets.open("quotes_default.json").bufferedReader().use { it.readText() }
        return runCatching { parse(raw) }.getOrDefault(emptyList())
    }

    fun hasCustomQuotes(): Boolean = file.exists()

    /** Importa y fusiona con las existentes (sin duplicar por texto). Devuelve cuántas nuevas se añadieron. */
    fun import(json: String): Int {
        val incoming = parse(json)
        require(incoming.isNotEmpty()) { "El JSON no contiene frases" }
        val existing = if (file.exists()) parse(file.readText()) else emptyList()
        val known = existing.map { it.text.trim().lowercase() }.toHashSet()
        val added = incoming.filter { known.add(it.text.trim().lowercase()) }
        save(existing + added)
        return added.size
    }

    fun clear() {
        file.delete()
    }

    private fun save(quotes: List<Quote>) {
        val arr = JSONArray()
        quotes.forEach { q ->
            arr.put(JSONObject().apply {
                put("text", q.text)
                q.author?.let { put("author", it) }
                q.date?.let { put("date", it.toString()) }
            })
        }
        file.writeText(JSONObject().put("version", 1).put("quotes", arr).toString(2))
    }

    companion object {
        /**
         * Formatos aceptados (tolerante, pensado para salida de Claude):
         *  - { "quotes": [ {"text": "...", "author": "...", "date": "2026-11-20"} ] }
         *  - [ {"text": "..."} , ... ]
         *  - [ "frase 1", "frase 2" ]
         * Alias de campos: text|quote|frase, author|autor, date|fecha.
         * Tolera bloques ```json ... ``` alrededor.
         */
        fun parse(raw: String): List<Quote> {
            val cleaned = stripCodeFences(raw).trim()
            val array: JSONArray = when {
                cleaned.startsWith("[") -> JSONArray(cleaned)
                cleaned.startsWith("{") -> {
                    val obj = JSONObject(cleaned)
                    obj.optJSONArray("quotes") ?: obj.optJSONArray("frases")
                        ?: throw IllegalArgumentException("Falta la lista \"quotes\"")
                }
                else -> throw IllegalArgumentException("No parece JSON")
            }
            return (0 until array.length()).mapNotNull { i ->
                when (val item = array.get(i)) {
                    is String -> item.takeIf { it.isNotBlank() }?.let { Quote(it.trim()) }
                    is JSONObject -> {
                        val text = item.firstString("text", "quote", "frase") ?: return@mapNotNull null
                        Quote(
                            text = text,
                            author = item.firstString("author", "autor"),
                            date = item.firstString("date", "fecha")
                                ?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
                        )
                    }
                    else -> null
                }
            }
        }

        private fun JSONObject.firstString(vararg keys: String): String? =
            keys.firstNotNullOfOrNull { k -> optString(k, "").trim().takeIf { it.isNotEmpty() } }

        private fun stripCodeFences(s: String): String {
            val start = s.indexOf("```")
            if (start < 0) return s
            val afterFence = s.indexOf('\n', start).takeIf { it >= 0 } ?: return s
            val end = s.indexOf("```", afterFence)
            return if (end > afterFence) s.substring(afterFence + 1, end) else s
        }
    }
}
