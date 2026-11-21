package io.github.amandhakar.mindscroll

import io.github.amandhakar.mindscroll.display.Pt
import io.github.amandhakar.mindscroll.display.RoamPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot
import kotlin.random.Random

/** How Mo wanders the screen: somewhere, a pause, somewhere else, like a person, never the same twice. */
class LiquidPathTest {
    private val eps = 0.01f
    private val roamMs = 13_000f
    private val frames = 780 // 60 per second over the 13 s
    private val start = Pt(138f, 66f)

    private fun plan(seed: Long) =
        RoamPlan.random(Random(seed), start, minX = 22f, maxX = 338f, minY = 74f, maxY = 627f, roamMs = roamMs)

    private fun route(seed: Long): List<Pt> = plan(seed).let { p -> (0..frames).map { p.at(it / frames.toFloat()) } }

    /** Pixels per second between consecutive frames. */
    private fun speeds(route: List<Pt>) = route.zipWithNext().map { (a, b) -> hypot(b.x - a.x, b.y - a.y) * frames / (roamMs / 1000f) }

    @Test
    fun startsAndEndsWhereMoCameOut() {
        for (seed in 1L..30L) {
            val p = plan(seed)
            assertEquals(start.x, p.at(0f).x, eps); assertEquals(start.y, p.at(0f).y, eps)
            assertEquals(start.x, p.at(1f).x, eps); assertEquals(start.y, p.at(1f).y, eps)
        }
    }

    @Test
    fun staysOnTheScreen() {
        for (seed in 1L..40L) for (pt in route(seed)) {
            assertTrue("x ${pt.x} (seed $seed)", pt.x in 22f..338f)
            assertTrue("y ${pt.y} (seed $seed)", pt.y in 66f..627f) // the start may sit above the roaming area
        }
    }

    @Test
    fun theSameSeedIsTheSamePlanAndEveryOtherSeedIsNew() {
        assertEquals(route(7), route(7))
        assertNotEquals(route(1), route(2))
        val distinct = (1L..30L).map { route(it)[400] }.toSet()
        assertTrue("only ${distinct.size} different mid-points in 30 plans", distinct.size >= 28)
    }

    @Test
    fun visitsAFewSpotsNotJustOne() {
        for (seed in 1L..40L) {
            val spots = plan(seed).spots
            assertTrue("$spots spots (seed $seed)", spots in 2..5)
        }
    }

    @Test
    fun movesLikeSomethingAliveNotAnObjectOnATrack() {
        for (seed in 1L..40L) {
            val v = speeds(route(seed))
            val resting = v.count { it < 20f }.toFloat() / v.size
            val travelling = v.count { it > 60f }.toFloat() / v.size
            assertTrue("rests only ${(resting * 100).toInt()}% of the time (seed $seed)", resting >= 0.35f)
            assertTrue("travels only ${(travelling * 100).toInt()}% of the time (seed $seed)", travelling >= 0.08f)
        }
    }

    @Test
    fun hasRealPausesBetweenMoves() {
        for (seed in 1L..40L) {
            val v = speeds(route(seed))
            val need = (0.8f * frames / (roamMs / 1000f)).toInt() // 0.8 s of frames
            var pauses = 0
            var run = 0
            for (speed in v) {
                if (speed < 20f) run++ else { if (run >= need) pauses++; run = 0 }
            }
            if (run >= need) pauses++
            assertTrue("only $pauses pauses of 0.8 s or more (seed $seed)", pauses >= 2)
        }
    }

    @Test
    fun walksAtAGentlePaceAndNeverJumps() {
        for (seed in 1L..40L) {
            val v = speeds(route(seed))
            assertTrue("top speed ${v.max()} px/s (seed $seed)", v.max() < 380f)
            assertTrue("a step of ${v.max() * (roamMs / 1000f) / frames} px (seed $seed)", v.max() * (roamMs / 1000f) / frames < 8f)
        }
    }

    @Test
    fun standsStillAtItsTalkingStopAndLongEnoughToSaySomething() {
        for (seed in 1L..40L) {
            val p = plan(seed)
            assertTrue("talk window ${(p.talkTo - p.talkFrom) * roamMs / 1000f} s (seed $seed)", (p.talkTo - p.talkFrom) * roamMs >= 2_300f)
            val from = (p.talkFrom * frames).toInt()
            val to = (p.talkTo * frames).toInt()
            val r = (from..to).map { p.at(it / frames.toFloat()) }
            val walked = r.zipWithNext().sumOf { (a, b) -> hypot(b.x - a.x, b.y - a.y).toDouble() }
            assertTrue("walks ${walked.toInt()} px while talking (seed $seed)", walked < 40.0)
        }
    }

    @Test
    fun looksAroundWhereverItArrives() {
        for (seed in 1L..20L) {
            val p = plan(seed)
            assertTrue(p.arrivals.size == p.spots)
            assertTrue(p.arrivals.zipWithNext().all { (a, b) -> b > a })
            assertTrue(p.arrivals.all { it > 0f && it < 1f })
        }
    }

    @Test
    fun aStartOutsideTheBoxIsStillOnThePlan() {
        val high = Pt(500f, 20f) // above the area Mo roams in
        val p = RoamPlan.random(Random(1), high, 60f, 1020f, 300f, 2200f, roamMs)
        assertEquals(high.y, p.at(0f).y, eps)
        assertEquals(high.y, p.at(1f).y, eps)
    }

    @Test
    fun endsUpAllOverTheScreenNotJustAlongTheTopEdge() {
        // Mo starts near the top; over many plans its spots must reach the lower half too.
        val reachesLower = (1L..200L).count { seed -> route(seed).any { it.y > 400f } }
        assertTrue("only $reachesLower of 200 plans reach the lower half", reachesLower >= 50)
        val stuckAtTop = (1L..200L).count { seed -> route(seed).all { it.y < 175f } }
        assertTrue("$stuckAtTop of 200 plans never leave the top edge", stuckAtTop <= 10)
    }
}
