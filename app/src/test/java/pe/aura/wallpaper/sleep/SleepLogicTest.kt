package pe.aura.wallpaper.sleep

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import pe.aura.wallpaper.data.ActivityRepository
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class SleepLogicTest {

    @get:Rule val tmp = TemporaryFolder()

    /** La rutina real del encargo. */
    private val agenda = ActivityRepository.parse(
        """
        {"activities":[
          {"title":"Desconectar: sin pantallas","time":"22:45","days":["dom","lun","mar","mie","jue"]},
          {"title":"Desconectar: sin pantallas","time":"23:45","days":["sab"]},
          {"title":"Dormir","time":"23:15","days":["dom","lun","mar","mie","jue"]},
          {"title":"Dormir","time":"23:45","days":["vie"]},
          {"title":"Dormir (noche del sábado)","time":"00:15","days":["dom"]},
          {"title":"Despertar","time":"07:00","days":["lun","mar","mie","jue","vie"]},
          {"title":"Despertar","time":"06:00","days":["sab"]},
          {"title":"Despertar","time":"08:00","days":["dom"]},
          {"title":"Gimnasio","time":"18:00","days":["lun"]}
        ]}
        """.trimIndent(),
    )

    // 2026-11-21 es sábado.
    private val saturday = LocalDate.of(2026, 11, 21)

    @Test fun durationCrossesMidnight() {
        val d = SleepLogic.durationMin(LocalTime.of(23, 40), LocalTime.of(7, 5))
        assertEquals(445, d)
        assertEquals("7 h 25", SleepLogic.format(d))
        assertFalse(SleepEntry(saturday, LocalTime.of(23, 40), LocalTime.of(7, 5)).metGoal)
        assertTrue(SleepEntry(saturday, LocalTime.of(23, 0), LocalTime.of(6, 45)).metGoal) // 7 h 45 justo
        assertNull(SleepEntry(saturday, LocalTime.of(23, 0), null).durationMin)
    }

    @Test fun detectsTitlesIgnoringCaseAndAccents() {
        assertEquals(SleepEvent.DORMIR, SleepLogic.eventOf("Dormir (noche del sábado)"))
        assertEquals(SleepEvent.DORMIR, SleepLogic.eventOf("  DÓRMIR"))
        assertEquals(SleepEvent.DESCONECTAR, SleepLogic.eventOf("desconectar: sin pantallas"))
        assertEquals(SleepEvent.DESPERTAR, SleepLogic.eventOf("Despertar"))
        assertNull(SleepLogic.eventOf("Gimnasio"))
        assertNull(SleepLogic.eventOf("Antes de dormir: leer")) // debe EMPEZAR con la palabra
    }

    @Test fun dayWithoutDormirSchedulesNothing() {
        // El sábado no hay "Dormir" (el de esa noche cae el domingo 00:15).
        assertTrue(SleepLogic.timesOn(agenda, saturday, SleepEvent.DORMIR).isEmpty())
        val noDormir = agenda.filterNot { SleepLogic.eventOf(it.title) == SleepEvent.DORMIR }
        assertNull(SleepLogic.next(noDormir, saturday.atTime(12, 0), SleepEvent.DORMIR))
    }

    @Test fun nextEventIncludingSaturdayNight() {
        val satEvening = saturday.atTime(21, 0)
        assertEquals(saturday.atTime(23, 45), SleepLogic.next(agenda, satEvening, SleepEvent.DESCONECTAR))
        // Noche del sábado: Dormir el domingo a las 00:15.
        assertEquals(saturday.plusDays(1).atTime(0, 15), SleepLogic.next(agenda, satEvening, SleepEvent.DORMIR))
        assertEquals(saturday.plusDays(1).atTime(8, 0), SleepLogic.next(agenda, satEvening, SleepEvent.DESPERTAR))
        // Justo después de las 00:15 del domingo, el siguiente Dormir es el domingo 23:15.
        assertEquals(
            saturday.plusDays(1).atTime(23, 15),
            SleepLogic.next(agenda, saturday.plusDays(1).atTime(0, 16), SleepEvent.DORMIR),
        )
        // La hora exacta del evento no se reprograma para el mismo instante.
        assertEquals(
            LocalDate.of(2026, 11, 23).atTime(23, 15),
            SleepLogic.next(agenda, saturday.plusDays(1).atTime(23, 15), SleepEvent.DORMIR),
        )
    }

    @Test fun earlyMorningBelongsToPreviousNight() {
        assertEquals(saturday, SleepLogic.nightFor(saturday.plusDays(1).atTime(0, 15)))
        assertEquals(saturday, SleepLogic.nightFor(saturday.atTime(23, 40)))
    }

    @Test fun recordSleepAndWakeAcrossMidnight() {
        val repo = SleepRepository(tmp.root)
        repo.recordSleep(LocalDateTime.of(2026, 11, 20, 23, 40))
        val e = repo.recordWake(LocalDateTime.of(2026, 11, 21, 7, 5))
        assertEquals(LocalDate.of(2026, 11, 20), e.night)
        assertEquals(445, e.durationMin)
        val week = repo.week(LocalDate.of(2026, 11, 21))
        assertEquals(1, week.recorded)
        assertEquals(0, week.met)
        assertEquals(445, week.averageMin)
        assertEquals(LocalDate.of(2026, 11, 20), week.nights.last().first)
    }
}
