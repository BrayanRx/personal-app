package pe.aura.wallpaper.sleep

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Una noche. [night] es la fecha del día en que te acostaste. */
data class SleepEntry(
    val night: LocalDate,
    val sleep: LocalTime? = null,
    val wake: LocalTime? = null,
    val goalMin: Int = SleepLogic.GOAL_MIN,
) {
    val durationMin: Int? get() = if (sleep != null && wake != null) SleepLogic.durationMin(sleep, wake) else null
    val metGoal: Boolean get() = (durationMin ?: 0) >= goalMin
}

data class WeekStats(val nights: List<Pair<LocalDate, SleepEntry?>>) {
    private val durations = nights.mapNotNull { it.second?.durationMin }
    val averageMin: Int? = durations.takeIf { it.isNotEmpty() }?.average()?.toInt()
    val recorded: Int = durations.size
    val met: Int = nights.count { it.second?.durationMin != null && it.second!!.metGoal }
}

/** Guarda las noches en `<dir>/sleep.json`. */
class SleepRepository(private val dir: File) {

    constructor(context: Context) : this(context.filesDir)

    private val file get() = File(dir, "sleep.json")

    fun all(): List<SleepEntry> =
        if (file.exists()) runCatching { parse(file.readText()) }.getOrDefault(emptyList()) else emptyList()

    fun forNight(night: LocalDate): SleepEntry? = all().firstOrNull { it.night == night }

    fun upsert(entry: SleepEntry) = save(all().filterNot { it.night == entry.night } + entry)

    fun delete(night: LocalDate) = save(all().filterNot { it.night == night })

    /** "Me voy a dormir": registra la hora de acostarse. */
    fun recordSleep(now: LocalDateTime): SleepEntry {
        val night = SleepLogic.nightFor(now)
        val entry = (forNight(night) ?: SleepEntry(night)).copy(sleep = now.toLocalTime().withSecond(0).withNano(0), wake = null)
        upsert(entry)
        return entry
    }

    /** "Desperté": cierra la noche abierta más reciente, o crea la de ayer sin hora de dormir. */
    fun recordWake(now: LocalDateTime): SleepEntry {
        val wake = now.toLocalTime().withSecond(0).withNano(0)
        val open = all().filter { it.sleep != null && it.wake == null && !it.night.isBefore(now.toLocalDate().minusDays(1)) }
            .maxByOrNull { it.night }
        val entry = (open ?: forNight(now.toLocalDate().minusDays(1)) ?: SleepEntry(now.toLocalDate().minusDays(1)))
            .copy(wake = wake)
        upsert(entry)
        return entry
    }

    /** Las 7 noches anteriores a [today] (de la más antigua a anoche). */
    fun week(today: LocalDate): WeekStats {
        val byNight = all().associateBy { it.night }
        return WeekStats((7 downTo 1).map { today.minusDays(it.toLong()) }.map { it to byNight[it] })
    }

    private fun save(list: List<SleepEntry>) {
        dir.mkdirs()
        val tmp = File(dir, "sleep.json.tmp")
        tmp.writeText(toJson(list.sortedBy { it.night }))
        if (!tmp.renameTo(file)) {
            file.writeText(tmp.readText()); tmp.delete()
        }
    }

    companion object {
        fun toJson(list: List<SleepEntry>): String {
            val arr = JSONArray()
            list.forEach { e ->
                arr.put(JSONObject().apply {
                    put("night", e.night.toString())
                    e.sleep?.let { put("sleep", it.toString()) }
                    e.wake?.let { put("wake", it.toString()) }
                    put("goalMin", e.goalMin)
                })
            }
            return arr.toString(2)
        }

        fun parse(raw: String): List<SleepEntry> {
            val arr = JSONArray(raw)
            return (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val night = runCatching { LocalDate.parse(o.getString("night")) }.getOrNull() ?: return@mapNotNull null
                SleepEntry(
                    night = night,
                    sleep = o.optString("sleep", "").takeIf { it.isNotEmpty() }?.let { LocalTime.parse(it) },
                    wake = o.optString("wake", "").takeIf { it.isNotEmpty() }?.let { LocalTime.parse(it) },
                    goalMin = o.optInt("goalMin", SleepLogic.GOAL_MIN),
                )
            }
        }
    }
}
