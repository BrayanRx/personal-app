package pe.aura.wallpaper.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.THURSDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.LocalDate
import java.time.LocalTime

class ActivityParserTest {

    private val json = """
        ```json
        {"activities":[
          {"title":"Gimnasio","category":"gym","time":"18:00","days":["lun","jueves"]},
          {"titulo":"Examen","categoria":"estudio","hora":"7:05","fecha":"2026-11-25"},
          {"title":"Agua","days":"diario"},
          {"title":"Clase","category":"clase","days":[3]},
          {"sin":"titulo"}
        ]}
        ```
    """.trimIndent()

    @Test fun parsesAliasesAndDays() {
        val list = ActivityRepository.parse(json)
        assertEquals(4, list.size)
        val gym = list[0]
        assertEquals(Category.ENTRENAMIENTO, gym.category)
        assertEquals(LocalTime.of(18, 0), gym.time)
        assertEquals(setOf(MONDAY, THURSDAY), gym.days)
        assertEquals(LocalDate.of(2026, 11, 25), list[1].date)
        assertEquals(LocalTime.of(7, 5), list[1].time)
        assertTrue(list[2].days.isEmpty())
        assertEquals(setOf(WEDNESDAY), list[3].days)
    }

    @Test fun forDateFiltersAndSorts() {
        val list = ActivityRepository.parse(json)
        val wed25 = LocalDate.of(2026, 11, 25) // miércoles
        val titles = ActivityRepository.forDate(list, wed25).map { it.title }
        assertEquals(listOf("Agua", "Clase", "Examen"), titles)
        val thu26 = ActivityRepository.forDate(list, wed25.plusDays(1)).map { it.title }
        assertEquals(listOf("Agua", "Gimnasio"), thu26)
    }

    @Test fun roundTripsThroughJson() {
        val list = ActivityRepository.parse(json)
        assertEquals(list, ActivityRepository.parse(ActivityRepository.toJson(list)))
    }

    @Test fun occursOn() {
        val once = Activity(title = "x", date = LocalDate.of(2026, 1, 1))
        assertTrue(once.occursOn(LocalDate.of(2026, 1, 1)))
        assertFalse(once.occursOn(LocalDate.of(2026, 1, 2)))
    }
}
