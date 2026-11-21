package io.github.amandhakar.mindscroll

import io.github.amandhakar.mindscroll.display.LiquidTimeline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The shape of Mo coming out of the island and going back in. */
class LiquidTimelineTest {
    private val eps = 0.0001f

    @Test
    fun startsAndEndsInsideTheIsland() {
        assertEquals(0f, LiquidTimeline.position(0f), eps)
        assertEquals(0f, LiquidTimeline.position(1f), eps)
    }

    @Test
    fun hoversFullyOutInTheMiddle() {
        val mid = (LiquidTimeline.OUT_END + LiquidTimeline.HOLD_END) / 2f
        assertEquals(1f, LiquidTimeline.position(mid), eps)
        assertTrue(LiquidTimeline.isOut(mid))
        assertFalse(LiquidTimeline.isOut(0.05f))
        assertFalse(LiquidTimeline.isOut(0.95f))
    }

    @Test
    fun overshootsOnTheWayOutLikeALiquid() {
        val samples = (0..100).map { LiquidTimeline.position(it / 100f * LiquidTimeline.OUT_END) }
        assertTrue("pushed out past the hover position", samples.max() > 1.05f)
        assertEquals(1f, LiquidTimeline.position(LiquidTimeline.OUT_END), eps)
    }

    @Test
    fun windsUpBeforeSnappingBackIn() {
        val samples = (0..100).map { LiquidTimeline.position(LiquidTimeline.HOLD_END + it / 100f * (1f - LiquidTimeline.HOLD_END)) }
        assertTrue("pulls out a little further first", samples.max() > 1.02f)
        assertTrue("then comes all the way back", samples.last() < 0.001f)
        // After the wind-up it only moves inward.
        val peak = samples.indices.maxBy { samples[it] }
        for (i in peak until samples.lastIndex) assertTrue(samples[i + 1] <= samples[i] + eps)
    }

    @Test
    fun theCurvesMatchAndroidsOvershootAndAnticipate() {
        // OvershootInterpolator(2f) at 0.5 is 1.125; AnticipateInterpolator(2f) at 0.5 is -0.125.
        assertEquals(1.125f, LiquidTimeline.overshoot(0.5f, 2f), eps)
        assertEquals(-0.125f, LiquidTimeline.anticipate(0.5f, 2f), eps)
    }

    @Test
    fun valuesOutsideTheRangeAreClamped() {
        assertEquals(0f, LiquidTimeline.position(-1f), eps)
        assertEquals(0f, LiquidTimeline.position(2f), eps)
    }

    // --- Roaming while out ---

    private fun roamAt(u: Float) = LiquidTimeline.roam(LiquidTimeline.POP_END + u * (LiquidTimeline.HOLD_END - LiquidTimeline.POP_END))

    @Test
    fun roamsOnlyWhileOutAndReturnsToWhereItCameOut() {
        val before = LiquidTimeline.roam(LiquidTimeline.OUT_END / 2f)
        val after = LiquidTimeline.roam((1f + LiquidTimeline.HOLD_END) / 2f)
        for (r in listOf(before, after, roamAt(0f), roamAt(1f))) {
            assertEquals(0f, r.envelope, eps); assertEquals(0f, r.wander, eps)
            assertEquals(0f, r.bob, eps); assertEquals(0f, r.stretch, eps)
        }
        // Just inside either end it is still next to where it started.
        assertTrue(roamAt(0.01f).envelope < 0.05f)
        assertTrue(roamAt(0.99f).envelope < 0.05f)
    }

    @Test
    fun wandersBothWaysAndStaysInRange() {
        val samples = (1..99).map { roamAt(it / 100f) }
        assertTrue("goes one way", samples.minOf { it.wander } < -0.3f)
        assertTrue("and the other", samples.maxOf { it.wander } > 0.3f)
        for (r in samples) {
            assertTrue(r.envelope in 0f..1f)
            assertTrue(r.wander in -1f..1f)
            assertTrue(r.bob in 0f..1f)
            assertTrue(r.stretch in 0f..1f)
        }
    }

    @Test
    fun hasItsBigStretchInTheMiddle() {
        assertEquals(1f, roamAt(0.5f).stretch, eps)
        assertTrue(roamAt(0.2f).stretch < 0.1f)
        assertTrue(roamAt(0.8f).stretch < 0.1f)
    }

    @Test
    fun roamProgressRunsFromZeroToOneWhileOut() {
        assertEquals(0f, LiquidTimeline.roamProgress(LiquidTimeline.POP_END), eps)
        assertEquals(1f, LiquidTimeline.roamProgress(LiquidTimeline.HOLD_END), eps)
        assertEquals(0f, LiquidTimeline.roamProgress(0f), eps)
        assertEquals(1f, LiquidTimeline.roamProgress(1f), eps)
    }

    // --- The drop Mo comes out of ---

    @Test
    fun moComesOutInAWholeDropThatThenPopsSoMoIsFree() {
        assertEquals(1f, LiquidTimeline.drop(0f).scale, eps)
        assertEquals(1f, LiquidTimeline.drop(LiquidTimeline.OUT_END).scale, eps)
        assertEquals(0f, LiquidTimeline.drop(LiquidTimeline.POP_END).scale, eps)
        val mid = LiquidTimeline.drop((LiquidTimeline.OUT_END + LiquidTimeline.POP_END) / 2f)
        assertTrue(mid.scale > 0.1f && mid.scale < 0.9f)
        assertFalse(LiquidTimeline.isFree(LiquidTimeline.OUT_END + 0.01f))
        assertTrue(LiquidTimeline.isFree(LiquidTimeline.POP_END))
    }

    @Test
    fun thePoppedDropIsDrawnBackIntoTheIslandNotLeftBehindMo() {
        val pop = (0..20).map { LiquidTimeline.drop(LiquidTimeline.OUT_END + it / 20f * (LiquidTimeline.POP_END - LiquidTimeline.OUT_END)) }
        assertEquals(1f, pop.first().attach, eps)
        assertEquals(0f, pop.last().attach, eps)
        for (i in 0 until pop.lastIndex) {
            assertTrue(pop[i + 1].attach <= pop[i].attach + eps)
            assertTrue(pop[i + 1].scale <= pop[i].scale + eps)
        }
    }

    @Test
    fun noDropWhileMoRoams() {
        for (i in 0..10) {
            val t = LiquidTimeline.POP_END + i / 10f * (LiquidTimeline.FORM_START - LiquidTimeline.POP_END)
            assertEquals(0f, LiquidTimeline.drop(t).scale, eps)
        }
    }

    @Test
    fun aNewDropFormsAroundMoWithAWobbleAsItComesBack() {
        val form = (0..100).map { LiquidTimeline.drop(LiquidTimeline.FORM_START + it / 100f * (LiquidTimeline.HOLD_END - LiquidTimeline.FORM_START)) }
        assertEquals(0f, form.first().scale, eps)
        assertEquals(1f, form.last().scale, eps)
        assertTrue("wobbles past whole", form.maxOf { it.scale } > 1.02f)
        assertTrue(form.all { it.attach == 1f })
        assertEquals(1f, LiquidTimeline.drop(1f).scale, eps)
    }
}
