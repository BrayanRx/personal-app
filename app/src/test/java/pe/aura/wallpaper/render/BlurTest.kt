package pe.aura.wallpaper.render

import org.junit.Assert.assertEquals
import org.junit.Test

class BlurTest {
    private fun gray(v: Int) = (0xFF shl 24) or (v shl 16) or (v shl 8) or v

    @Test fun uniformImageStaysUniform() {
        val src = IntArray(20 * 10) { gray(120) }
        val dst = IntArray(src.size)
        Blur.boxBlur(src, dst, 20, 10, 3, horizontal = true)
        dst.forEach { assertEquals(gray(120), it) }
    }

    @Test fun hardEdgeBecomesGradient() {
        val w = 21
        val src = IntArray(w) { if (it < w / 2) gray(0) else gray(210) }
        val dst = IntArray(w)
        Blur.boxBlur(src, dst, w, 1, 3, horizontal = true)
        val values = dst.map { it and 0xFF }
        // Monótono y con valores intermedios: el borde quedó suavizado.
        assertEquals(values.sorted(), values)
        assertEquals(true, values.any { it in 1..209 })
        // Vertical también funciona (una columna).
        val col = IntArray(w)
        Blur.boxBlur(src, col, 1, w, 3, horizontal = false)
        assertEquals(values, col.map { it and 0xFF })
    }
}
