package io.github.amandhakar.mindscroll

import io.github.amandhakar.mindscroll.mascot.MoMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class MoMotionTest {
    @Test
    fun blinkClosesAndReopens() {
        assertEquals(0f, MoMotion.blink(-1), 0f)
        assertEquals(0f, MoMotion.blink(0), 0.01f)
        assertEquals(1f, MoMotion.blink(MoMotion.BLINK_MS / 2), 0.01f)
        assertEquals(0f, MoMotion.blink(MoMotion.BLINK_MS), 0f)
    }

    @Test
    fun reactionPeaksQuicklyThenSettles() {
        assertEquals(1f, MoMotion.react(120), 0.01f)
        assertTrue(MoMotion.react(60) in 0.4f..0.6f)
        assertTrue(MoMotion.react(400) < MoMotion.react(200))
        assertEquals(0f, MoMotion.react(MoMotion.REACT_MS), 0f)
        assertEquals(0f, MoMotion.react(-5), 0f)
    }

    @Test
    fun phaseLoops() {
        assertEquals(0f, MoMotion.phase(0), 0f)
        assertEquals(0.5f, MoMotion.phase(MoMotion.LOOP_MS / 2), 0.01f)
        assertEquals(MoMotion.phase(100), MoMotion.phase(100 + MoMotion.LOOP_MS * 7), 0.0001f)
    }

    @Test
    fun blinkGapsVaryWithinRange() {
        val random = Random(42)
        val gaps = List(50) { MoMotion.nextBlinkDelayMs(random) }
        assertTrue(gaps.all { it in 2200L until 5400L })
        assertTrue(gaps.toSet().size > 10)
    }

    @Test
    fun poseWithoutIdleStaysStillBetweenBlinks() {
        val pose = MoMotion.pose(timeMs = 10_000, blinkStartMs = null, reactStartMs = null, idle = false)
        assertEquals(0f, pose.phase, 0f)
        assertEquals(0f, pose.blink, 0f)
        assertEquals(0f, pose.react, 0f)
    }
}
