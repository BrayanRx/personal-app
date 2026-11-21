package pe.aura.wallpaper.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * Orden de rotación de los fondos (lógica pura, guardada en images/order.json).
 * La rotación es determinista: el día D usa order[D mod n], salvo que haya una imagen
 * fijada con "Usar hoy" para esa fecha exacta.
 */
data class ImageOrder(
    val order: List<String> = emptyList(),
    val pinDate: LocalDate? = null,
    val pinFile: String? = null,
) {
    /** Ajusta el índice a los archivos que existen: quita los que faltan y añade al final los nuevos. */
    fun reconcile(existing: Collection<String>): ImageOrder {
        val set = existing.toSet()
        val kept = order.filter { it in set }.distinct()
        val added = (set - kept.toSet()).sorted()
        return copy(order = kept + added).dropStalePin()
    }

    fun add(names: List<String>): ImageOrder = copy(order = order + names.filterNot { it in order })

    fun remove(name: String): ImageOrder = copy(order = order - name).dropStalePin()

    /** Mueve [name] [delta] posiciones (−1 = antes, +1 = después), sin salirse de la lista. */
    fun move(name: String, delta: Int): ImageOrder {
        val i = order.indexOf(name)
        if (i < 0) return this
        val j = (i + delta).coerceIn(0, order.lastIndex)
        if (i == j) return this
        val list = order.toMutableList()
        list.removeAt(i)
        list.add(j, name)
        return copy(order = list)
    }

    fun pin(name: String, date: LocalDate): ImageOrder =
        if (name in order) copy(pinDate = date, pinFile = name) else this

    fun pick(date: LocalDate): String? {
        if (order.isEmpty()) return null
        if (pinDate == date && pinFile != null && pinFile in order) return pinFile
        return order[Math.floorMod(date.toEpochDay(), order.size.toLong()).toInt()]
    }

    private fun dropStalePin(): ImageOrder = if (pinFile != null && pinFile !in order) copy(pinDate = null, pinFile = null) else this

    fun toJson(): String = JSONObject().apply {
        put("order", JSONArray(order))
        if (pinDate != null && pinFile != null) put("pin", JSONObject().put("date", pinDate.toString()).put("file", pinFile))
    }.toString(2)

    companion object {
        fun parse(raw: String): ImageOrder {
            val o = JSONObject(raw)
            val arr = o.optJSONArray("order") ?: JSONArray()
            val pin = o.optJSONObject("pin")
            return ImageOrder(
                order = (0 until arr.length()).map { arr.getString(it) },
                pinDate = pin?.optString("date")?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
                pinFile = pin?.optString("file")?.takeIf { it.isNotEmpty() },
            )
        }
    }
}
