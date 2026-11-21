package io.github.amandhakar.mindscroll

import io.github.amandhakar.mindscroll.display.Pt
import io.github.amandhakar.mindscroll.display.RoamPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot
import kotlin.random.Random

/** Mo's random route around the screen. */
class LiquidPathTest {
    private val eps = 0.01f
    private val start = Pt(300f, 230f)

    private fun path(seed: Long, stops: Int = 5) =
        RoamPath.random(Random(seed), start, minX = 60f, maxX = 1020f, minY = 300f, maxY = 2200f, stopCount = stops)

    private fun route(seed: Long) = (0..200).map { path(seed).at(it / 200f) }

    @Test
    fun startsAndEndsWhereMoCameOut() {
        for (seed in 1L..20L) {
            val p = path(seed)
            assertEquals(start.x, p.at(0f).x, eps); assertEquals(start.y, p.at(0f).y, eps)
            assertEquals(start.x, p.at(1f).x, eps); assertEquals(start.y, p.at(1f).y, eps)
        }
    }

    @Test
    fun staysOnTheScreen() {
        for (seed in 1L..40L) for (pt in route(seed)) {
            assertTrue("x ${pt.x} (seed $seed)", pt.x in 60f..1020f)
            assertTrue("y ${pt.y} (seed $seed)", pt.y in 230f..2200f) // the start may sit above the roaming area
        }
    }

    @Test
    fun theSameSeedIsTheSameRouteAndEveryOtherSeedIsNew() {
        assertEquals(route(7), route(7))
        val distinct = (1L..30L).map { route(it).let { r -> r[100] } }.toSet()
        assertTrue("only ${distinct.size} different mid-points in 30 routes", distinct.size >= 28)
        assertNotEquals(route(1), route(2))
    }

    @Test
    fun actuallyTravelsAcrossTheScreen() {
        for (seed in 1L..20L) {
            val r = route(seed)
            val length = r.zipWithNext().sumOf { (a, b) -> hypot(b.x - a.x, b.y - a.y).toDouble() }
            assertTrue("route $seed is only ${length.toInt()} px long", length > 1500.0)
            assertTrue("route $seed hardly leaves the start", r.maxOf { hypot(it.x - start.x, it.y - start.y) } > 400f)
        }
    }

    @Test
    fun movesSmoothlyWithoutJumps() {
        for (seed in 1L..20L) {
            val r = route(seed)
            val worst = r.zipWithNext().maxOf { (a, b) -> hypot(b.x - a.x, b.y - a.y) }
            assertTrue("jump of $worst px (seed $seed)", worst < 220f)
        }
    }

    @Test
    fun setsOffAndArrivesGently() {
        for (seed in 1L..20L) {
            val steps = route(seed).zipWithNext().map { (a, b) -> hypot(b.x - a.x, b.y - a.y) }
            assertTrue("sets off at ${steps.first()} vs fastest ${steps.max()} (seed $seed)", steps.first() < 0.2f * steps.max())
            assertTrue("arrives at ${steps.last()} vs fastest ${steps.max()} (seed $seed)", steps.last() < 0.2f * steps.max())
        }
    }

    @Test
    fun aStartOutsideTheBoxIsStillOnTheRoute() {
        val high = Pt(500f, 100f) // above the area Mo roams in
        val p = RoamPath.random(Random(1), high, 60f, 1020f, 300f, 2200f)
        assertEquals(high.y, p.at(0f).y, eps)
    }
}
