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
}
