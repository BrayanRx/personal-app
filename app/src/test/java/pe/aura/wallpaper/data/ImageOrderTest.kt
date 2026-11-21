package pe.aura.wallpaper.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class ImageOrderTest {

    private val base = ImageOrder(listOf("a.jpg", "b.jpg", "c.jpg"))
    // epochDay 20779 (2026-11-22): 20779 mod 3 = 1 y 20779 mod 2 = 1
    private val day = LocalDate.ofEpochDay(20779)

    @Test fun rotationIsDeterministic() {
        assertEquals("b.jpg", base.pick(day))
        assertEquals("c.jpg", base.pick(day.plusDays(1)))
        assertEquals("a.jpg", base.pick(day.plusDays(2)))
        assertEquals(base.pick(day), base.pick(day)) // mismo día, mismo fondo
    }

    @Test fun moveChangesOrderWithinBounds() {
        assertEquals(listOf("b.jpg", "a.jpg", "c.jpg"), base.move("b.jpg", -1).order)
        assertEquals(listOf("a.jpg", "c.jpg", "b.jpg"), base.move("b.jpg", +1).order)
        assertEquals(base.order, base.move("a.jpg", -1).order) // ya es el primero
        assertEquals(base.order, base.move("zzz.jpg", 1).order)
    }

    @Test fun removeKeepsRotationDeterministic() {
        val removed = base.remove("b.jpg")
        assertEquals(listOf("a.jpg", "c.jpg"), removed.order)
        assertEquals("c.jpg", removed.pick(day)) // 20779 mod 2 = 1
        assertEquals(removed.pick(day), removed.pick(day))
    }

    @Test fun useTodayOnlyAffectsThatDay() {
        val pinned = base.pin("a.jpg", day)
        assertEquals("a.jpg", pinned.pick(day))
        assertEquals(base.pick(day.plusDays(1)), pinned.pick(day.plusDays(1))) // mañana vuelve la rotación
        assertEquals(base.order, pinned.order)                                   // el orden no cambia
        // Si se elimina la fijada, se olvida el pin.
        assertEquals("c.jpg", pinned.remove("a.jpg").pick(day))
    }

    @Test fun reconcileAndJsonRoundTrip() {
        val r = ImageOrder(listOf("x.jpg", "b.jpg", "a.jpg")).reconcile(listOf("a.jpg", "b.jpg", "new.jpg"))
        assertEquals(listOf("b.jpg", "a.jpg", "new.jpg"), r.order)
        val pinned = r.pin("a.jpg", day)
        assertEquals(pinned, ImageOrder.parse(pinned.toJson()))
    }
}
