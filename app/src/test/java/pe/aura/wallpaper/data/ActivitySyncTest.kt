package pe.aura.wallpaper.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.LocalTime

class ActivitySyncTest {

    @get:Rule val tmp = TemporaryFolder()

    private val agenda = """
        {"version":1,"activities":[
          {"title":"Desayuno","category":"comida","time":"07:30","days":["lun","mar","mie"]},
          {"title":"Gym","category":"entrenamiento","time":"18:00","days":["vie","lun"]},
          {"title":"Examen","category":"estudio","time":"10:00","date":"2026-11-25"}
        ]}
    """.trimIndent()

    @Test fun importingTwiceDoesNotDuplicate() {
        val repo = ActivityRepository(tmp.root)
        assertEquals(3, repo.import(agenda))
        assertEquals(0, repo.import(agenda))
        assertEquals(3, repo.all().size)
    }

    @Test fun stableIdIgnoresDayOrder() {
        val t = LocalTime.of(18, 0)
        assertEquals(
            ActivityRepository.stableId("Gym", t, linkedSetOf(MONDAY, FRIDAY, WEDNESDAY), null),
            ActivityRepository.stableId("Gym", t, linkedSetOf(FRIDAY, WEDNESDAY, MONDAY), null),
        )
        // Y un JSON con los días en otro orden da los mismos IDs.
        val reordered = agenda.replace("[\"vie\",\"lun\"]", "[\"lun\",\"vie\"]")
        assertEquals(
            ActivityRepository.parse(agenda).map { it.id },
            ActivityRepository.parse(reordered).map { it.id },
        )
    }

    @Test fun vaultSyncReplacesVaultAndKeepsManual() {
        val repo = ActivityRepository(tmp.root)
        repo.upsert(Activity(title = "Llamar a mamá"))                // manual, creada en la app
        assertEquals(3, repo.replaceVault(agenda))
        assertEquals(4, repo.all().size)

        // La nota cambia: solo queda "Siesta" de la bóveda; la manual sigue.
        repo.replaceVault("""{"activities":[{"title":"Siesta","time":"15:00"}]}""")
        val all = repo.all()
        assertEquals(listOf("Llamar a mamá", "Siesta"), all.map { it.title })
        assertEquals(listOf(Source.MANUAL, Source.VAULT), all.map { it.source })

        // Sincronizar dos veces la misma nota no duplica.
        repo.replaceVault("""{"activities":[{"title":"Siesta","time":"15:00"}]}""")
        assertEquals(2, repo.all().size)
    }

    @Test fun invalidJsonFailsAndKeepsData() {
        val repo = ActivityRepository(tmp.root)
        repo.replaceVault(agenda)
        val before = File(tmp.root, "activities.json").readText()

        val result = runCatching { repo.replaceVault("```json\n{\"activities\": [ {\"title\": \n```") }
        assertTrue(result.isFailure)
        assertEquals(before, File(tmp.root, "activities.json").readText())
        assertTrue(runCatching { repo.import("no es json") }.isFailure)
        assertEquals(before, File(tmp.root, "activities.json").readText())
    }
}
