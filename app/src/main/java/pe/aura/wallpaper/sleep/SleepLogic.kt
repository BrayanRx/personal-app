package pe.aura.wallpaper.sleep

import pe.aura.wallpaper.data.Activity
import java.text.Normalizer
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Eventos de sueño que se detectan en la agenda por el inicio del título. */
enum class SleepEvent(val prefix: String, val requestCode: Int) {
    DESCONECTAR("desconectar", 101),
    DORMIR("dormir", 102),
    DESPERTAR("despertar", 103),
}

/** Lógica pura (sin Android): detección, próximos eventos y duraciones. */
object SleepLogic {
    /** Meta de sueño: 7 h 45 min. */
    const val GOAL_MIN = 465

    /** Minúsculas y sin tildes: "Dormir (noche del sábado)" → "dormir (noche del sabado)". */
    fun normalize(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase().trim()

    fun eventOf(title: String): SleepEvent? {
        val t = normalize(title)
        return SleepEvent.values().firstOrNull { t.startsWith(it.prefix) }
    }

    /** Horas del evento en un día concreto (puede haber varias, p. ej. domingo 00:15 y 23:15). */
    fun timesOn(activities: List<Activity>, date: LocalDate, event: SleepEvent): List<LocalTime> =
        activities.filter { it.time != null && it.occursOn(date) && eventOf(it.title) == event }
            .mapNotNull { it.time }
            .sorted()

    /** Próxima ocurrencia estrictamente posterior a [from] (busca hasta 8 días). */
    fun next(activities: List<Activity>, from: LocalDateTime, event: SleepEvent): LocalDateTime? =
        (0L..8L).asSequence()
            .map { from.toLocalDate().plusDays(it) }
            .flatMap { d -> timesOn(activities, d, event).map { d.atTime(it) } }
            .firstOrNull { it.isAfter(from) }

    /**
     * Noche a la que pertenece una hora de acostarse: la fecha del día en que te acostaste,
     * contando la madrugada (antes de las 12:00) como parte de la noche anterior.
     * Así "Dormir (noche del sábado)" a las 00:15 del domingo cuenta como noche del sábado.
     */
    fun nightFor(bedtime: LocalDateTime): LocalDate =
        if (bedtime.hour < 12) bedtime.toLocalDate().minusDays(1) else bedtime.toLocalDate()

    /** Duración en minutos; cruza la medianoche (23:40 → 07:05 = 445). */
    fun durationMin(sleep: LocalTime, wake: LocalTime): Int =
        Math.floorMod(Duration.between(sleep, wake).toMinutes(), 1440L).toInt()

    /** 445 → "7 h 25"; 420 → "7 h 00". */
    fun format(min: Int): String = "%d h %02d".format(min / 60, min % 60)
}
