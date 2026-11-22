package pe.aura.wallpaper.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
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

/** De dónde viene una actividad: la nota de Obsidian (se reemplaza al sincronizar) o la app. */
enum class Source {
    MANUAL, VAULT;

    companion object {
        fun parse(raw: String?): Source = if (raw.equals("vault", ignoreCase = true)) VAULT else MANUAL
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
    val source: Source = Source.MANUAL,
    /** Fechas en las que esta actividad recurrente NO aplica (viajes, feriados…). */
    val skip: Set<LocalDate> = emptySet(),
) {
    fun occursOn(d: LocalDate): Boolean = when {
        date != null -> date == d
        d in skip -> false
        days.isEmpty() -> true
        else -> d.dayOfWeek in days
    }
}

/**
 * La agenda completa: actividades + días con agenda propia.
 * En un día de [customDays] solo cuentan las actividades con `date` igual a ese día;
 * las recurrentes y diarias se ocultan. Las de la nota y las de la app se guardan aparte
 * para que la sincronización reemplace solo las suyas.
 */
data class Agenda(
    val activities: List<Activity> = emptyList(),
    val manualCustomDays: Set<LocalDate> = emptySet(),
    val vaultCustomDays: Set<LocalDate> = emptySet(),
) {
    val customDays: Set<LocalDate> get() = manualCustomDays + vaultCustomDays

    fun forDate(date: LocalDate): List<Activity> = ActivityRepository.forDate(activities, date, customDays)
}

/** Resultado de leer un JSON de agenda: actividades y el campo opcional "customDays". */
data class ParsedAgenda(val activities: List<Activity>, val customDays: Set<LocalDate>)

/**
 * Guarda la agenda en `<dir>/activities.json`.
 * Recibe el directorio (no un Context) para poder probarlo en la JVM.
 */
class ActivityRepository(private val dir: File) {

    constructor(context: Context) : this(context.filesDir)

    private val file get() = File(dir, "activities.json")

    fun agenda(): Agenda =
        if (file.exists()) runCatching { readStored(file.readText()) }.getOrDefault(Agenda()) else Agenda()

    fun all(): List<Activity> = agenda().activities

    fun forDate(date: LocalDate): List<Activity> = agenda().forDate(date)

    fun upsert(activity: Activity) {
        val a = agenda()
        val list = a.activities.toMutableList()
        val i = list.indexOfFirst { it.id == activity.id }
        if (i >= 0) list[i] = activity else list += activity
        save(a.copy(activities = list))
    }

    fun delete(id: String) = agenda().let { save(it.copy(activities = it.activities.filterNot { a -> a.id == id })) }

    /** "Solo agenda propia este día" desde la app (solo afecta a los días manuales). */
    fun setManualCustomDay(date: LocalDate, on: Boolean) = agenda().let {
        save(it.copy(manualCustomDays = if (on) it.manualCustomDays + date else it.manualCustomDays - date))
    }

    fun clear() {
        file.delete()
    }

    /**
     * Import manual (archivo o "Pegar JSON"): fusiona deduplicando por ID estable, así
     * importar dos veces lo mismo no duplica. Devuelve cuántas actividades eran nuevas.
     * Lanza excepción (sin tocar el archivo) si el JSON es inválido o está vacío.
     */
    fun import(json: String): Int {
        val parsed = parseAgenda(json)
        val incoming = parsed.activities.map { it.copy(source = Source.MANUAL) }
        require(incoming.isNotEmpty()) { "El JSON no contiene actividades" }
        val a = agenda()
        val known = a.activities.map { it.id }.toSet()
        save(a.copy(activities = mergeById(a.activities, incoming), manualCustomDays = a.manualCustomDays + parsed.customDays))
        return incoming.map { it.id }.distinct().count { it !in known }
    }

    /**
     * Sincronización desde la nota: reemplaza todas las actividades VAULT y conserva las MANUAL.
     * Lanza excepción (sin tocar el archivo) si el JSON es inválido. Devuelve cuántas se leyeron.
     */
    fun replaceVault(json: String): Int {
        val parsed = parseAgenda(json)
        val vault = parsed.activities.map { it.copy(source = Source.VAULT) }.distinctBy { it.id }
        val a = agenda()
        save(a.copy(activities = replaceVault(a.activities, vault), vaultCustomDays = parsed.customDays))
        return vault.size
    }

    private fun save(agenda: Agenda) {
        dir.mkdirs()
        // Escritura atómica: un fallo a mitad no deja el archivo corrupto.
        val tmp = File(dir, "activities.json.tmp")
        tmp.writeText(toJson(agenda.activities, agenda.manualCustomDays, agenda.vaultCustomDays))
        if (!tmp.renameTo(file)) {
            file.writeText(tmp.readText()); tmp.delete()
        }
    }

    companion object {
        /**
         * Actividades de un día. Si [date] está en [customDays], solo las que tienen esa `date`;
         * si no, todas las que ocurren ese día (respetando "skip").
         */
        fun forDate(all: List<Activity>, date: LocalDate, customDays: Set<LocalDate> = emptySet()): List<Activity> =
            all.filter { if (date in customDays) it.date == date else it.occursOn(date) }
                .sortedWith(compareBy<Activity, LocalTime?>(nullsFirst<LocalTime>()) { it.time }.thenBy { it.title })

        /** Las entrantes sustituyen a las existentes con el mismo ID; el resto se añade al final. */
        fun mergeById(existing: List<Activity>, incoming: List<Activity>): List<Activity> {
            val byId = LinkedHashMap<String, Activity>()
            existing.forEach { byId[it.id] = it }
            incoming.forEach { byId[it.id] = it }
            return byId.values.toList()
        }

        fun replaceVault(existing: List<Activity>, vault: List<Activity>): List<Activity> =
            existing.filter { it.source == Source.MANUAL } + vault

        /**
         * ID determinista a partir del contenido: la misma actividad siempre produce el mismo ID,
         * aunque cambie el orden de "days". Así reimportar no duplica y la UI no parpadea.
         */
        fun stableId(title: String, time: LocalTime?, days: Set<DayOfWeek>, date: LocalDate?): String {
            val key = listOf(
                title.trim(),
                time?.toString().orEmpty(),
                days.sorted().joinToString(",") { DAY_CODES[it.value - 1] },
                date?.toString().orEmpty(),
            ).joinToString("|")
            val digest = MessageDigest.getInstance("SHA-1").digest(key.toByteArray(Charsets.UTF_8))
            return "a-" + digest.take(8).joinToString("") { "%02x".format(it) }
        }

        fun toJson(
            list: List<Activity>,
            manualCustomDays: Set<LocalDate> = emptySet(),
            vaultCustomDays: Set<LocalDate> = emptySet(),
        ): String {
            val arr = JSONArray()
            list.forEach { a ->
                arr.put(JSONObject().apply {
                    put("id", a.id)
                    put("title", a.title)
                    put("category", a.category.name.lowercase())
                    a.time?.let { put("time", it.toString()) }
                    if (a.days.isNotEmpty()) put("days", JSONArray(a.days.sorted().map { DAY_CODES[it.value - 1] }))
                    a.date?.let { put("date", it.toString()) }
                    put("source", a.source.name.lowercase())
                    if (a.skip.isNotEmpty()) put("skip", JSONArray(a.skip.sorted().map { it.toString() }))
                })
            }
            return JSONObject().put("version", 1).put("activities", arr).apply {
                if (manualCustomDays.isNotEmpty()) put("customDays", JSONArray(manualCustomDays.sorted().map { it.toString() }))
                if (vaultCustomDays.isNotEmpty()) put("vaultCustomDays", JSONArray(vaultCustomDays.sorted().map { it.toString() }))
            }.toString(2)
        }

        /** Lee el archivo guardado (incluye los días propios de la nota por separado). */
        private fun readStored(raw: String): Agenda {
            val parsed = parseAgenda(raw)
            val root = runCatching { JSONObject(raw.trim()) }.getOrNull()
            return Agenda(parsed.activities, parsed.customDays, parseDates(root?.opt("vaultCustomDays")))
        }

        /** Como [parse], pero también devuelve "customDays" (alias "diasPropios") de la raíz. */
        fun parseAgenda(raw: String): ParsedAgenda {
            val cleaned = NoteJson.extract(raw).trim()
            val root = if (cleaned.startsWith("{")) JSONObject(cleaned) else null
            val custom = parseDates(root?.opt("customDays") ?: root?.opt("diasPropios"))
            return ParsedAgenda(parse(raw), custom)
        }

        private fun parseDates(v: Any?): Set<LocalDate> {
            val tokens = when (v) {
                is JSONArray -> (0 until v.length()).map { v.get(it).toString() }
                is String -> v.split(',', ' ', ';').filter { it.isNotBlank() }
                else -> emptyList()
            }
            return tokens.mapNotNull { runCatching { LocalDate.parse(it.trim()) }.getOrNull() }.toSet()
        }

        /**
         * Formato:
         * { "activities": [ {"title":"Desayuno","category":"comida","time":"07:30","days":["lun","mar"]},
         *                   {"title":"Examen","category":"estudio","time":"10:00","date":"2026-11-25"} ] }
         * Alias: actividades/titulo/categoria/hora/dias/fecha. "days" admite "lun", "lunes", "mon",
         * "monday", números 1-7 (1 = lunes) o "diario"/"todos".
         * Acepta también una nota Markdown: se usa su bloque ```json.
         */
        fun parse(raw: String): List<Activity> {
            val cleaned = NoteJson.extract(raw).trim()
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
                val time = o.str("time", "hora")?.let { parseTime(it) }
                val days = parseDays(o.opt("days") ?: o.opt("dias") ?: o.opt("días"))
                val date = o.str("date", "fecha")?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                Activity(
                    id = o.str("id") ?: stableId(title, time, days, date),
                    title = title,
                    category = Category.parse(o.str("category", "categoria", "categoría", "tipo")),
                    time = time,
                    days = days,
                    date = date,
                    source = Source.parse(o.str("source")),
                    skip = parseDates(o.opt("skip") ?: o.opt("omitir")),
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
    }
}
