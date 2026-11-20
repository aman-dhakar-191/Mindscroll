package io.github.amandhakar.mindscroll

import io.github.amandhakar.mindscroll.mascot.MoMotion
import io.github.amandhakar.mindscroll.mascot.SessionReaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class MoMotionTest {
    @Test
    fun blinkClosesAndReopens() {
        assertEquals(0f, MoMotion.blink(-1), 0f)
        assertEquals(0f, MoMotion.blink(0), 0.01f)
        assertEquals(0.5f, MoMotion.blink(MoMotion.BLINK_MS / 4), 0.01f)
        assertEquals(1f, MoMotion.blink(MoMotion.BLINK_MS / 2), 0.01f)
        assertEquals(0.5f, MoMotion.blink(MoMotion.BLINK_MS * 3 / 4), 0.01f)
        assertEquals(0f, MoMotion.blink(MoMotion.BLINK_MS), 0f)
    }

    @Test
    fun reactionPeaksQuicklyThenSettles() {
        assertEquals(1f, MoMotion.react(120), 0.01f)
        assertTrue(MoMotion.react(60) > 0.8f)
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
    fun reactionHasAnticipationBeforeThePop() {
        val anticipation = MoMotion.pose(1045, null, 1000, true)
        assertTrue(anticipation.anticipation > 0f)
        assertEquals(0f, anticipation.react, 0f)
        val pop = MoMotion.pose(1120, null, 1000, true)
        assertEquals(0f, pop.anticipation, 0f)
        assertTrue(pop.react > 0f)
    }

    @Test
    fun moodTransitionEasesToComplete() {
        val start = MoMotion.pose(1000, null, null, true, moodChangedAtMs = 1000)
        val middle = MoMotion.pose(1210, null, null, true, moodChangedAtMs = 1000)
        val end = MoMotion.pose(1500, null, null, true, moodChangedAtMs = 1000)
        assertEquals(0f, start.moodTransition, 0f)
        assertTrue(middle.moodTransition in 0f..1f)
        assertEquals(1f, end.moodTransition, 0f)
    }

    @Test
    fun curiousMoodUsesMoreExpressiveEyeMovementThanChill() {
        val chill = MoMotion.pose(900, null, null, true, mood = io.github.amandhakar.mindscroll.mascot.Mood.CHILL)
        val curious = MoMotion.pose(900, null, null, true, mood = io.github.amandhakar.mindscroll.mascot.Mood.CURIOUS)
        assertTrue(kotlin.math.abs(curious.gazeX) >= kotlin.math.abs(chill.gazeX))
    }

    @Test
    fun peekStartsAndSettlesWithinItsWindow() {
        assertEquals(0f, MoMotion.peek(-1), 0f)
        assertEquals(0f, MoMotion.peek(0), 0.01f)
        assertTrue(MoMotion.peek(250) > 0.8f)
        assertEquals(1f, MoMotion.peek(MoMotion.PEEK_MS / 2), 0.01f)
        assertTrue(MoMotion.peek(600) in 0f..1f)
        assertEquals(0f, MoMotion.peek(MoMotion.PEEK_MS), 0f)
    }


    @Test
    fun sessionTransitionsHaveDistinctReactions() {
        assertEquals(
            SessionReaction.FIRST_ENTRY,
            MoMotion.sessionReaction(null, "com.example.a", null, 1_000L, false),
        )
        assertEquals(
            SessionReaction.APP_SWITCH,
            MoMotion.sessionReaction("com.example.a", "com.example.b", null, 2_000L, true),
        )
        assertEquals(
            SessionReaction.RETURN_AFTER_BREAK,
            MoMotion.sessionReaction(
                "com.example.a",
                "com.example.a",
                1_000L,
                1_000L + MoMotion.RETURN_BREAK_THRESHOLD_MS,
                true,
            ),
        )
    }

    @Test
    fun shortBreakDoesNotBecomeWelcomeBack() {
        assertEquals(
            SessionReaction.FIRST_ENTRY,
            MoMotion.sessionReaction(
                "com.example.a",
                "com.example.a",
                1_000L,
                1_000L + MoMotion.RETURN_BREAK_THRESHOLD_MS - 1,
                true,
            ),
        )
    }

    @Test
    fun closingTrackedAppTriggersExitReaction() {
        assertEquals(
            SessionReaction.APP_EXIT,
            MoMotion.sessionReaction("com.example.a", null, 1_000L, 2_000L, true),
        )
    }

    @Test
    fun noSessionAtStartupDoesNotTriggerExitReaction() {
        assertEquals(
            SessionReaction.NONE,
            MoMotion.sessionReaction(null, null, null, 2_000L, false),
        )
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
