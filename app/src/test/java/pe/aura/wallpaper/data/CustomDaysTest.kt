package pe.aura.wallpaper.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import pe.aura.wallpaper.sleep.SleepEvent
import pe.aura.wallpaper.sleep.SleepLogic
import java.io.File
import java.time.LocalDate

class CustomDaysTest {

    @get:Rule val tmp = TemporaryFolder()

    private val sunday = LocalDate.of(2026, 11, 22)

    /** El caso del encargo: domingo 22-nov con torneo. */
    private val note = """
        # Rutina diaria
        ```json
        {"version":1,
         "customDays":["2026-11-22"],
         "activities":[
          {"title":"Compras de la semana","time":"11:00","days":["dom"]},
          {"title":"Lote","time":"12:00","days":["dom"]},
          {"title":"Agua","time":"09:00"},
          {"title":"Dormir","time":"23:15","days":["dom","lun"]},
          {"title":"Torneo de Tetris","category":"plan","time":"12:00","date":"2026-11-22"},
          {"title":"Gimnasio","time":"18:00","days":["lun","mie"],"skip":["2026-11-25"]}
         ]}
        ```
    """.trimIndent()

    @Test fun customDayShowsOnlyItsDatedActivities() {
        val parsed = ActivityRepository.parseAgenda(note)
        assertEquals(setOf(sunday), parsed.customDays)
        val titles = ActivityRepository.forDate(parsed.activities, sunday, parsed.customDays).map { it.title }
        assertEquals(listOf("Torneo de Tetris"), titles)
        // El domingo siguiente vuelve la rutina normal.
        val next = ActivityRepository.forDate(parsed.activities, sunday.plusDays(7), parsed.customDays).map { it.title }
        assertEquals(listOf("Agua", "Compras de la semana", "Lote", "Dormir"), next)
    }

    @Test fun skipHidesRecurringOnlyThatDate() {
        val acts = ActivityRepository.parse(note)
        val wed25 = LocalDate.of(2026, 11, 25)
        assertTrue(ActivityRepository.forDate(acts, wed25).none { it.title == "Gimnasio" })
        assertTrue(ActivityRepository.forDate(acts, wed25.minusDays(2)).any { it.title == "Gimnasio" }) // lunes 23
        assertTrue(ActivityRepository.forDate(acts, wed25.plusDays(7)).any { it.title == "Gimnasio" })  // miércoles 2-dic
    }

    @Test fun withoutCustomDaysOrSkipBehavesLikeV04() {
        val plain = note.replace("\"customDays\":[\"2026-11-22\"],", "").replace(",\"skip\":[\"2026-11-25\"]", "")
        val parsed = ActivityRepository.parseAgenda(plain)
        assertTrue(parsed.customDays.isEmpty())
        val titles = ActivityRepository.forDate(parsed.activities, sunday, parsed.customDays).map { it.title }
        assertEquals(listOf("Agua", "Compras de la semana", "Lote", "Torneo de Tetris", "Dormir"), titles)
        assertTrue(ActivityRepository.forDate(parsed.activities, LocalDate.of(2026, 11, 25)).any { it.title == "Gimnasio" })
    }

    @Test fun customDayWithoutOwnDormirSchedulesNothingThatDay() {
        val parsed = ActivityRepository.parseAgenda(note)
        // Desde el sábado 21 por la tarde: el domingo 22 es agenda propia y no tiene "Dormir",
        // así que el próximo es el lunes 23 a las 23:15.
        val next = SleepLogic.next(parsed.activities, sunday.minusDays(1).atTime(20, 0), SleepEvent.DORMIR, parsed.customDays)
        assertEquals(sunday.plusDays(1).atTime(23, 15), next)
    }

    @Test fun syncReplacesVaultCustomDaysAndKeepsManualOnes() {
        val repo = ActivityRepository(tmp.root)
        val manualDay = LocalDate.of(2026, 12, 25)
        repo.setManualCustomDay(manualDay, true)
        repo.replaceVault(note)
        assertEquals(setOf(sunday, manualDay), repo.agenda().customDays)

        // La nota quita el día propio: se va el de la bóveda, queda el manual.
        repo.replaceVault(note.replace("\"customDays\":[\"2026-11-22\"],", ""))
        assertEquals(setOf(manualDay), repo.agenda().customDays)
        assertEquals(emptySet<LocalDate>(), repo.agenda().vaultCustomDays)

        // JSON inválido no toca nada.
        val before = File(tmp.root, "activities.json").readText()
        assertTrue(runCatching { repo.replaceVault("```json\n{\"customDays\": [\n```") }.isFailure)
        assertEquals(before, File(tmp.root, "activities.json").readText())

        // Quitar el día manual.
        repo.setManualCustomDay(manualDay, false)
        assertTrue(repo.agenda().customDays.isEmpty())
    }

    @Test fun skipAndCustomDaysSurviveSaving() {
        val repo = ActivityRepository(tmp.root)
        repo.replaceVault(note)
        val gym = repo.all().single { it.title == "Gimnasio" }
        assertEquals(setOf(LocalDate.of(2026, 11, 25)), gym.skip)
        assertEquals(setOf(sunday), repo.agenda().vaultCustomDays)
    }
}
