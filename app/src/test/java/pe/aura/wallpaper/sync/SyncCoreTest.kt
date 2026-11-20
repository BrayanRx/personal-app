package pe.aura.wallpaper.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import pe.aura.wallpaper.data.ActivityRepository
import pe.aura.wallpaper.data.QuoteRepository

class SyncCoreTest {

    @get:Rule val tmp = TemporaryFolder()

    private val agendaNote = "# Rutina\n```json\n{\"activities\":[{\"title\":\"Gym\",\"time\":\"18:00\"}]}\n```\n"
    private val quotesNote = "# Frases\n```json\n{\"quotes\":[{\"text\":\"Uno\"},{\"text\":\"Dos\"}]}\n```\n"

    private fun repos() = ActivityRepository(tmp.root) to QuoteRepository(tmp.root) { """{"quotes":[{"text":"Default"}]}""" }

    private fun files(agenda: String, agendaSig: String, quotes: String = quotesNote, quotesSig: String = "q1") = listOf(
        NoteFile("Rutina diaria — JSON para la app Aura.md", agendaSig) { agenda },
        NoteFile("Frases para la app personal — banco en JSON.md", quotesSig) { quotes },
        NoteFile("Otra nota.md", "x") { error("no debería leerse") },
    )

    @Test fun readsBothNotesAndReplacesQuoteBank() {
        val (acts, quotes) = repos()
        val out = SyncCore.run(files(agendaNote, "a1"), acts, quotes, null, null, force = false)
        assertTrue(out.changed)
        assertEquals(1, out.activities)
        assertEquals(2, out.quotes)
        assertEquals("a1", out.agendaSig)
        assertEquals(listOf("Uno", "Dos"), quotes.all().map { it.text })
        assertTrue(out.errors.isEmpty())
    }

    @Test fun skipsUnchangedNotes() {
        val (acts, quotes) = repos()
        val out = SyncCore.run(files(agendaNote, "a1"), acts, quotes, "a1", "q1", force = false)
        assertFalse(out.changed)
        assertNull(out.activities)
        assertTrue(acts.all().isEmpty())
    }

    @Test fun invalidNoteKeepsDataAndRetries() {
        val (acts, quotes) = repos()
        SyncCore.run(files(agendaNote, "a1"), acts, quotes, null, null, force = false)
        val broken = "```json\n{\"activities\": [\n```"
        val out = SyncCore.run(files(broken, "a2"), acts, quotes, "a1", "q1", force = false)
        assertFalse(out.changed)
        assertEquals("a1", out.agendaSig) // la firma no avanza: se reintenta en la próxima pasada
        assertTrue(out.errors.single().startsWith("Agenda:"))
        assertEquals(listOf("Gym"), acts.all().map { it.title })
    }

    @Test fun missingNoteIsReportedWithoutTouchingData() {
        val (acts, quotes) = repos()
        val out = SyncCore.run(emptyList(), acts, quotes, null, null, force = true)
        assertFalse(out.changed)
        assertEquals(2, out.errors.size)
        assertEquals(listOf("Default"), quotes.all().map { it.text })
    }
}
