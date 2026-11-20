package pe.aura.wallpaper.data

/**
 * Extrae el JSON de una nota Markdown (o de una respuesta de Claude):
 *  1. el contenido del primer bloque cuya apertura es ```json
 *  2. si no hay, el primer bloque de código de cualquier tipo
 *  3. si tampoco, el texto completo
 */
object NoteJson {

    private val FENCE = Regex("""^\s*```\s*([\w-]*)\s*$""")

    fun extract(markdown: String): String {
        val lines = markdown.lines()
        val blocks = mutableListOf<Pair<String, String>>() // lenguaje → contenido
        var i = 0
        while (i < lines.size) {
            val open = FENCE.matchEntire(lines[i])
            if (open != null) {
                val lang = open.groupValues[1].lowercase()
                val end = (i + 1 until lines.size).firstOrNull { FENCE.matchEntire(lines[it])?.groupValues?.get(1) == "" }
                if (end == null) break // bloque sin cerrar: se ignora
                blocks += lang to lines.subList(i + 1, end).joinToString("\n")
                i = end + 1
            } else {
                i++
            }
        }
        return blocks.firstOrNull { it.first == "json" }?.second
            ?: blocks.firstOrNull()?.second
            ?: markdown
    }
}
