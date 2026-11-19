package pe.aura.wallpaper.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class QuoteParserTest {

    @Test fun objectFormat() {
        val q = QuoteRepository.parse(
            """{"version":1,"quotes":[{"text":"Hola","author":"Yo","date":"2026-11-20"},{"text":"Dos"}]}""",
        )
        assertEquals(2, q.size)
        assertEquals(Quote("Hola", "Yo", LocalDate.of(2026, 11, 20)), q[0])
        assertNull(q[1].author)
    }

    @Test fun arrayOfStringsAndSpanishAliases() {
        assertEquals(listOf(Quote("a"), Quote("b")), QuoteRepository.parse("""["a", " b ", ""]"""))
        val q = QuoteRepository.parse("""{"frases":[{"frase":"Uno","autor":"X","fecha":"mal"}]}""")
        assertEquals(Quote("Uno", "X", null), q.single())
    }

    @Test fun toleratesMarkdownCodeFence() {
        val raw = "Aquí tienes:\n```json\n{\"quotes\":[{\"text\":\"Hola\"}]}\n```\n"
        assertEquals("Hola", QuoteRepository.parse(raw).single().text)
    }

    @Test(expected = Exception::class) fun rejectsGarbage() {
        QuoteRepository.parse("no soy json")
    }
}
