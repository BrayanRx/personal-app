package pe.aura.wallpaper.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

enum class Category(val label: String, val color: Int) {
    COMIDA("Comida", 0xFFF2A06B.toInt()),
    ENTRENAMIENTO("Entrenamiento", 0xFF7FD1AE.toInt()),
    ESTUDIO("Estudio", 0xFF8AB4F8.toInt()),
    PLAN("Plan", 0xFFE6A8D7.toInt()),
    OTRO("Otro", 0xFFE0E0E0.toInt());

    companion object {
        fun parse(raw: String?): Category = when (raw?.trim()?.lowercase()) {
            null, "" -> OTRO
            "comida", "comidas", "meal", "food", "desayuno", "almuerzo", "cena" -> COMIDA
            "entrenamiento", "entreno", "gym", "workout", "training", "ejercicio", "deporte" -> ENTRENAMIENTO
            "estudio", "estudios", "study", "clase", "clases", "universidad", "curso" -> ESTUDIO
            "plan", "planes", "evento", "event", "reunion", "reunión", "cita" -> PLAN
            else -> entries.firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) } ?: OTRO
        }
    }
}

/**
 * Una actividad es:
 *  - de un día específico (date != null), o
 *  - recurrente en ciertos días de la semana (days no vacío), o
 *  - diaria (date == null y days vacío).
 */
data class Activity(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val category: Category = Category.OTRO,
    val time: LocalTime? = null,
    val days: Set<DayOfWeek> = emptySet(),
    val date: LocalDate? = null,
) {
    fun occursOn(d: LocalDate): Boolean = when {
        date != null -> date == d
        days.isEmpty() -> true
        else -> d.dayOfWeek in days
    }
}

class ActivityRepository(private val context: Context) {

    private val file get() = File(context.filesDir, "activities.json")

    fun all(): List<Activity> =
        if (file.exists()) runCatching { parse(file.readText()) }.getOrDefault(emptyList()) else emptyList()

    fun forDate(date: LocalDate): List<Activity> = forDate(all(), date)

    fun upsert(activity: Activity) {
        val list = all().toMutableList()
        val i = list.indexOfFirst { it.id == activity.id }
        if (i >= 0) list[i] = activity else list += activity
        save(list)
    }

    fun delete(id: String) = save(all().filterNot { it.id == id })

    fun clear() {
        file.delete()
    }

    /** Añade las actividades del JSON a las existentes. Devuelve cuántas se añadieron. */
    fun import(json: String): Int {
        val incoming = parse(json)
        require(incoming.isNotEmpty()) { "El JSON no contiene actividades" }
        save(all() + incoming)
        return incoming.size
    }

    private fun save(list: List<Activity>) {
        file.writeText(toJson(list))
    }

    companion object {
        fun forDate(all: List<Activity>, date: LocalDate): List<Activity> =
            all.filter { it.occursOn(date) }
                .sortedWith(compareBy<Activity, LocalTime?>(nullsFirst<LocalTime>()) { it.time }.thenBy { it.title })

        fun toJson(list: List<Activity>): String {
            val arr = JSONArray()
            list.forEach { a ->
                arr.put(JSONObject().apply {
                    put("id", a.id)
                    put("title", a.title)
                    put("category", a.category.name.lowercase())
                    a.time?.let { put("time", it.toString()) }
                    if (a.days.isNotEmpty()) put("days", JSONArray(a.days.sorted().map { DAY_CODES[it.value - 1] }))
                    a.date?.let { put("date", it.toString()) }
                })
            }
            return JSONObject().put("version", 1).put("activities", arr).toString(2)
        }

        /**
         * Formato:
         * { "activities": [ {"title":"Desayuno","category":"comida","time":"07:30","days":["lun","mar"]},
         *                   {"title":"Examen","category":"estudio","time":"10:00","date":"2026-11-25"} ] }
         * Alias: actividades/titulo/categoria/hora/dias/fecha. "days" admite "lun", "lunes", "mon",
         * "monday", números 1-7 (1 = lunes) o "diario"/"todos".
         */
        fun parse(raw: String): List<Activity> {
            val cleaned = stripCodeFences(raw).trim()
            val array = when {
                cleaned.startsWith("[") -> JSONArray(cleaned)
                cleaned.startsWith("{") -> JSONObject(cleaned).let {
                    it.optJSONArray("activities") ?: it.optJSONArray("actividades")
                        ?: throw IllegalArgumentException("Falta la lista \"activities\"")
                }
                else -> throw IllegalArgumentException("No parece JSON")
            }
            return (0 until array.length()).mapNotNull { i ->
                val o = array.optJSONObject(i) ?: return@mapNotNull null
                val title = o.str("title", "titulo", "título", "name", "nombre") ?: return@mapNotNull null
                Activity(
                    id = o.str("id") ?: UUID.randomUUID().toString(),
                    title = title,
                    category = Category.parse(o.str("category", "categoria", "categoría", "tipo")),
                    time = o.str("time", "hora")?.let { parseTime(it) },
                    days = parseDays(o.opt("days") ?: o.opt("dias") ?: o.opt("días")),
                    date = o.str("date", "fecha")?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
                )
            }
        }

        private val DAY_CODES = listOf("lun", "mar", "mie", "jue", "vie", "sab", "dom")
        private val DAY_ALIASES: Map<String, DayOfWeek> = buildMap {
            val es = listOf("lunes", "martes", "miercoles", "jueves", "viernes", "sabado", "domingo")
            val en = listOf("monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday")
            DayOfWeek.values().forEachIndexed { i, d ->
                put(DAY_CODES[i], d); put(es[i], d); put(en[i], d); put(en[i].take(3), d); put("${i + 1}", d)
            }
            put("mié", DayOfWeek.WEDNESDAY); put("miércoles", DayOfWeek.WEDNESDAY)
            put("sáb", DayOfWeek.SATURDAY); put("sábado", DayOfWeek.SATURDAY)
        }

        private fun parseDays(v: Any?): Set<DayOfWeek> {
            val tokens: List<String> = when (v) {
                is JSONArray -> (0 until v.length()).map { v.get(it).toString() }
                is String -> v.split(',', ' ', ';').filter { it.isNotBlank() }
                is Number -> listOf(v.toString())
                else -> emptyList()
            }.map { it.trim().lowercase() }
            if (tokens.any { it in setOf("diario", "todos", "all", "everyday", "daily") }) return emptySet()
            return tokens.mapNotNull { DAY_ALIASES[it] }.toSet()
        }

        private fun parseTime(s: String): LocalTime? = runCatching {
            val (h, m) = s.trim().split(":").map { it.trim().toInt() }
            LocalTime.of(h, m)
        }.getOrNull()

        private fun JSONObject.str(vararg keys: String): String? =
            keys.firstNotNullOfOrNull { k -> optString(k, "").trim().takeIf { it.isNotEmpty() } }

        internal fun stripCodeFences(s: String): String {
            val start = s.indexOf("```")
            if (start < 0) return s
            val afterFence = s.indexOf('\n', start).takeIf { it >= 0 } ?: return s
            val end = s.indexOf("```", afterFence)
            return if (end > afterFence) s.substring(afterFence + 1, end) else s
        }
    }
}
