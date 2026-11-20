package pe.aura.wallpaper.data

import org.junit.Assert.assertEquals
import org.junit.Test

class NoteJsonTest {

    @Test fun jsonBlockWithTextAroundIt() {
        val md = """
            # Rutina diaria

            Notas sobre la rutina.

            ```json
            {"activities":[{"title":"Gym"}]}
            ```

            Más texto después.
        """.trimIndent()
        assertEquals("""{"activities":[{"title":"Gym"}]}""", NoteJson.extract(md))
    }

    @Test fun prefersJsonBlockOverEarlierBlocks() {
        val md = "```bash\necho hola\n```\ntexto\n```json\n[1]\n```"
        assertEquals("[1]", NoteJson.extract(md))
    }

    @Test fun blockWithoutLanguage() {
        val md = "Intro\n```\n{\"quotes\":[]}\n```\nfin"
        assertEquals("{\"quotes\":[]}", NoteJson.extract(md))
    }

    @Test fun noBlockReturnsWholeText() {
        assertEquals("{\"a\":1}", NoteJson.extract("{\"a\":1}"))
    }

    @Test fun parsersAcceptWholeNote() {
        val md = "---\ntags: aura\n---\n# Frases\n```json\n{\"quotes\":[{\"text\":\"Hola\"}]}\n```\n"
        assertEquals("Hola", QuoteRepository.parse(md).single().text)
    }
}
