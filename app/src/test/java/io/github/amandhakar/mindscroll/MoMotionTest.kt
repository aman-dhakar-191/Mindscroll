package io.github.amandhakar.mindscroll

import io.github.amandhakar.mindscroll.mascot.MoMotion
import io.github.amandhakar.mindscroll.mascot.MotionProfile
import io.github.amandhakar.mindscroll.mascot.Mood
import io.github.amandhakar.mindscroll.mascot.SessionReaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
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
        assertTrue(pop.react > 0f)
        // The crouch releases as the pop rises instead of snapping off.
        assertTrue(pop.anticipation in 0.01f..0.99f)
        assertEquals(0f, MoMotion.pose(1000 + 90 + 120, null, 1000, true).anticipation, 0f)
    }

    @Test
    fun crouchHandsOverToThePopWithoutASnap() {
        for (p in styles) {
            val len = p.anticipationMs
            val before = MoMotion.pose(1000 + len - 1, null, 1000, true, profile = p).anticipation
            val after = MoMotion.pose(1000 + len + 1, null, 1000, true, profile = p).anticipation
            assertTrue("${p.damping}: $before -> $after", abs(after - before) < 0.1f)
        }
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

    // --- Motion styles ---

    private val styles = listOf(MotionProfile.CALM, MotionProfile.BALANCED, MotionProfile.PLAYFUL)

    private fun curve(p: MotionProfile, from: Float = 0f) = (0L..p.reactMs).map { MoMotion.react(it, p, from) }

    private fun zeroCrossings(values: List<Float>) =
        values.map { if (it > 0.005f) 1 else if (it < -0.005f) -1 else 0 }.filter { it != 0 }
            .zipWithNext().count { (a, b) -> a != b }

    @Test
    fun stylesDifferInTimingNotJustSize() {
        val (calm, balanced, playful) = styles
        assertTrue(calm.riseMs > balanced.riseMs && balanced.riseMs > playful.riseMs)
        assertTrue(calm.anticipationMs < balanced.anticipationMs && balanced.anticipationMs < playful.anticipationMs)
        assertTrue(calm.settleMs < playful.settleMs)
        assertTrue(calm.reactScale < balanced.reactScale && balanced.reactScale < playful.reactScale)
        assertTrue(calm.hop < balanced.hop && balanced.hop < playful.hop)
        assertTrue(calm.gestureSpeed < balanced.gestureSpeed && balanced.gestureSpeed < playful.gestureSpeed)
        assertTrue(calm.idleFrameMs > balanced.idleFrameMs && balanced.idleFrameMs > playful.idleFrameMs)
    }

    @Test
    fun calmNeverOvershootsPlayfulBouncesMoreThanBalanced() {
        assertEquals(0, zeroCrossings(curve(MotionProfile.CALM)))
        assertTrue(curve(MotionProfile.CALM).min() > -0.01f)
        val balanced = zeroCrossings(curve(MotionProfile.BALANCED))
        val playful = zeroCrossings(curve(MotionProfile.PLAYFUL))
        assertTrue("balanced overshoots", balanced >= 1)
        assertTrue("playful bounces more ($playful vs $balanced)", playful > balanced)
    }

    @Test
    fun reactionOvershootGivesTheReboundStretch() {
        val rebound = curve(MotionProfile.PLAYFUL).min()
        assertTrue("playful rebound $rebound", rebound < -0.1f)
    }

    @Test
    fun everyStyleStartsAtRestAndEndsExactlyAtRest() {
        for (p in styles) {
            assertEquals(0f, MoMotion.react(0, p), 0f)
            assertEquals(1f, MoMotion.react(p.riseMs, p), 0.001f)
            assertEquals(0f, MoMotion.react(p.reactMs, p), 0f)
            assertEquals(0f, MoMotion.react(p.reactMs - 1, p), 0.02f)
        }
    }

    @Test
    fun reactionIsContinuousWhereTheRiseHandsOverToTheSpring() {
        for (p in styles) {
            val before = MoMotion.react(p.riseMs - 1, p)
            val at = MoMotion.react(p.riseMs, p)
            val after = MoMotion.react(p.riseMs + 1, p)
            assertTrue("${p.damping} rise->spring", abs(at - before) < 0.05f && abs(after - at) < 0.05f)
            // And the spring itself moves smoothly at display rate.
            val spring = (p.riseMs..p.reactMs step 16).map { MoMotion.react(it, p) }
            assertTrue(spring.zipWithNext().maxOf { (x, y) -> abs(y - x) } < 0.3f)
        }
    }

    @Test
    fun playfulPopIsBiggerThanBalancedThanCalm() {
        fun peak(p: MotionProfile) = (0L..p.reactTotalMs).maxOf { MoMotion.pose(it, null, 0, true, profile = p).react }
        assertTrue(peak(MotionProfile.PLAYFUL) > peak(MotionProfile.BALANCED))
        assertTrue(peak(MotionProfile.BALANCED) > peak(MotionProfile.CALM))
    }

    @Test
    fun pose_reactionStartsAfterTheCrouchAndEndsWithTheProfile() {
        for (p in styles) {
            val at = MoMotion.pose(1000 + p.anticipationMs - 1, null, 1000, true, profile = p)
            assertEquals(0f, at.react, 0f)
            assertTrue(at.anticipation > 0.9f)
            val done = MoMotion.pose(1000 + p.reactTotalMs, null, 1000, true, profile = p)
            assertEquals(0f, done.react, 0f)
            assertEquals(0f, done.anticipation, 0f)
        }
    }

    // --- Interruptions ---

    @Test
    fun interruptedReactionContinuesFromWhereItWas() {
        val p = MotionProfile.BALANCED
        val before = MoMotion.react(200, p)
        assertTrue(before > 0.3f)
        // A second reel lands: the new curve starts exactly at the old value, then rises to 1.
        assertEquals(before, MoMotion.react(0, p, from = before), 0.0001f)
        assertEquals(1f, MoMotion.react(p.riseMs, p, from = before), 0.001f)
    }

    @Test
    fun interruptionDuringReboundContinuesFromTheNegativeValue() {
        val p = MotionProfile.PLAYFUL
        val t = (p.riseMs..p.reactMs).minBy { MoMotion.react(it, p) }
        val v = MoMotion.react(t, p)
        assertTrue(v < 0f)
        assertEquals(v, MoMotion.react(0, p, from = v), 0.0001f)
    }

    @Test
    fun interruptedReactionSkipsTheCrouchButARestingOneKeepsIt() {
        val p = MotionProfile.PLAYFUL
        assertTrue(MoMotion.anticipationLength(p, 0f) > 0)
        assertEquals(0L, MoMotion.anticipationLength(p, 0.6f))
        assertEquals(0L, MoMotion.anticipationLength(p, -0.4f))
        val chained = MoMotion.pose(1000, null, 1000, true, profile = p, reactFrom = 0.6f)
        assertEquals(0f, chained.anticipation, 0f)
        assertEquals(0.6f * p.reactScale, chained.react, 0.01f)
    }

    @Test
    fun rapidReelsNeverSnapThePose() {
        // Simulate a reel every 140 ms for a second, sampling at 8 ms the way a view would.
        val p = MotionProfile.BALANCED
        var start = 0L
        var from = 0f
        var last = 0f
        for (t in 0L..1000L step 8) {
            if (t % 140L == 0L) {
                from = if (t == 0L) 0f else MoMotion.react(t - start - MoMotion.anticipationLength(p, from), p, from)
                start = t
            }
            val now = MoMotion.pose(t, null, start, true, profile = p, reactFrom = from).react
            assertTrue("jump at $t: $last -> $now", abs(now - last) < 0.6f)
            last = now
        }
    }

    @Test
    fun interruptedGestureBlendsOutRatherThanSnapping() {
        val carry = 0.8f
        assertEquals(0.8f, MoMotion.carried(0f, carry, 0), 0.0001f)
        assertEquals(0f, MoMotion.carried(0f, carry, MoMotion.CARRY_FADE_MS), 0f)
        val mid = MoMotion.carried(0f, carry, MoMotion.CARRY_FADE_MS / 2)
        assertTrue(mid > 0f && mid < carry)
        // Peek restarted while peeking: the first frame shows the old pose, not 0.
        val pose = MoMotion.pose(2000, null, null, false, peekStartMs = 2000, peekCarry = 0.9f)
        assertEquals(0.9f, pose.peek, 0.01f)
        val exitPose = MoMotion.pose(2000, null, null, false, exitStartMs = 2000, exitCarry = 0.5f)
        assertEquals(0.5f, exitPose.exit, 0.01f)
        val glancePose = MoMotion.pose(2000, null, null, false, glanceStartMs = 2000, glanceCarry = -0.6f)
        assertEquals(-0.6f, glancePose.glance, 0.01f)
    }

    // --- Expressions ---

    private fun pose(
        mood: Mood = Mood.CURIOUS,
        sinceReact: Long? = null,
        excited: Boolean = false,
        profile: MotionProfile = MotionProfile.BALANCED,
    ) = MoMotion.pose(
        timeMs = 10_000, blinkStartMs = null,
        reactStartMs = sinceReact?.let { 10_000 - it }, idle = true,
        mood = mood, profile = profile, reactExcited = excited,
    )

    @Test
    fun surpriseRaisesBrowsAndFlattensTheSmile() {
        val rest = pose()
        val surprised = pose(sinceReact = 90 + 120)
        assertTrue(surprised.brow > rest.brow + 0.5f)
        assertTrue(surprised.smile < rest.smile)
        assertEquals(0f, surprised.excite, 0f)
    }

    @Test
    fun excitementRaisesBrowsAndWidensTheSmile() {
        val rest = pose(Mood.CHILL)
        val excited = pose(Mood.CHILL, sinceReact = 90 + 120, excited = true)
        assertTrue(excited.excite > 0.5f)
        assertTrue(excited.smile > rest.smile)
        assertTrue(excited.brow > rest.brow)
    }

    @Test
    fun moodsHaveDistinctBrowsAndMouths() {
        val all = Mood.entries.map { pose(it) }
        assertEquals(Mood.entries.size, all.map { it.brow }.toSet().size)
        assertEquals(Mood.entries.size, all.map { it.smile }.toSet().size)
        assertTrue(pose(Mood.CHILL).smile > pose(Mood.WOBBLY).smile)
        assertTrue(pose(Mood.CURIOUS).brow > pose(Mood.SLEEPY).brow)
        assertTrue(pose(Mood.WOBBLY).brow < 0f)
    }

    @Test
    fun sleepinessLowersBrowsAndSlowsTheBlink() {
        assertTrue(MoMotion.blinkDurationMs(Mood.SLEEPY) > MoMotion.blinkDurationMs(Mood.CHILL))
        val asleep = MoMotion.pose(1000, null, null, true, asleep = true, mood = Mood.CURIOUS)
        assertTrue(asleep.brow < pose(Mood.CURIOUS).brow)
        // A sleepy blink is still closing when a normal one has finished.
        val since = MoMotion.BLINK_MS - 20
        assertEquals(0f, MoMotion.blink(since, MoMotion.BLINK_MS - 0), 0.5f)
        assertTrue(MoMotion.blink(MoMotion.BLINK_MS + 20, MoMotion.blinkDurationMs(Mood.SLEEPY)) > 0f)
    }

    @Test
    fun lifecycleGesturesHaveDistinctSignatures() {
        val rest = MoMotion.pose(1000, null, null, false, mood = Mood.CHILL)
        val hello = MoMotion.pose(1000 + 240, null, null, false, mood = Mood.CHILL, peekStartMs = 1000)
        val switched = MoMotion.pose(1000 + 175, null, null, false, mood = Mood.CHILL, glanceStartMs = 1000)
        val farewell = MoMotion.pose(1000 + 230, null, null, false, mood = Mood.CHILL, exitStartMs = 1000)
        // Hello leans out and smiles, a switch looks sideways, a farewell smiles and turns away.
        assertTrue(hello.peek > 0.5f && hello.smile >= rest.smile && hello.brow > rest.brow)
        assertTrue(abs(switched.gazeX) > 0.03f && switched.browSkew > 0.3f && switched.peek == 0f)
        assertTrue(farewell.exit > 0.9f && farewell.peek == 0f && farewell.gazeX == 0f)
    }

    @Test
    fun exitEasesInInsteadOfSnapping() {
        assertEquals(0f, MoMotion.exit(0), 0.001f)
        assertTrue(MoMotion.exit(40) < 0.15f)
        assertEquals(1f, MoMotion.exit((MoMotion.EXIT_MS * 0.3f).toLong()), 0.01f)
        assertEquals(0f, MoMotion.exit(MoMotion.EXIT_MS), 0f)
        val v = (0L until MoMotion.EXIT_MS step 16).map { MoMotion.exit(it) }
        assertTrue(v.zipWithNext().maxOf { (a, b) -> abs(b - a) } < 0.2f)
    }

    @Test
    fun gestureLengthsScaleWithTheStyle() {
        assertTrue(MotionProfile.CALM.exitMs > MotionProfile.BALANCED.exitMs)
        assertTrue(MotionProfile.BALANCED.exitMs > MotionProfile.PLAYFUL.exitMs)
        assertEquals(MoMotion.PEEK_MS, MotionProfile.BALANCED.peekMs)
        // A slower style stretches the gesture across its longer window and still ends at rest.
        val p = MotionProfile.CALM
        assertEquals(0f, MoMotion.pose(p.exitMs, null, null, false, exitStartMs = 0, profile = p).exit, 0.001f)
        assertTrue(MoMotion.pose(p.exitMs / 2, null, null, false, exitStartMs = 0, profile = p).exit > 0.3f)
    }

    @Test
    fun glanceSwingsBothWaysAndSettles() {
        val v = (0L..MoMotion.GLANCE_MS).map { MoMotion.glance(it) }
        assertTrue(v.max() > 0.5f && v.min() < -0.1f)
        assertEquals(0f, MoMotion.glance(0), 0.001f)
        assertEquals(0f, MoMotion.glance(MoMotion.GLANCE_MS), 0f)
    }

    // --- Synchronisation: face and body come from one clock ---

    @Test
    fun faceAndBodyReturnToTheMoodsRestPoseTogether() {
        for (p in styles) for (mood in Mood.entries) {
            val end = MoMotion.pose(
                timeMs = 5000 + MoMotion.reactionLength(p), blinkStartMs = null, reactStartMs = 5000,
                idle = false, mood = mood, profile = p, reactExcited = true,
            )
            assertEquals(0f, end.react, 0f)
            assertEquals(0f, end.excite, 0f)
            assertEquals(MoMotion.baseBrow(mood), end.brow, 0.0001f)
            assertEquals(MoMotion.baseSmile(mood), end.smile, 0.0001f)
        }
    }

    @Test
    fun faceExpressionTracksTheBodyReaction() {
        // Brow height rises and falls with the same envelope that drives the body squash/stretch.
        val p = MotionProfile.BALANCED
        val rest = MoMotion.baseBrow(Mood.CURIOUS)
        for (t in 0L..p.reactTotalMs step 10) {
            val pose = MoMotion.pose(t, null, 0, false, mood = Mood.CURIOUS, profile = p)
            val expected = (rest + pose.react.coerceAtLeast(0f) * 0.9f).coerceIn(-1f, 1f)
            assertEquals("t=$t", expected, pose.brow, 0.0001f)
        }
    }

    @Test
    fun blinkGapsFollowTheStyle() {
        val random = { Random(7) }
        val calm = MoMotion.nextBlinkDelayMs(random(), MotionProfile.CALM)
        val playful = MoMotion.nextBlinkDelayMs(random(), MotionProfile.PLAYFUL)
        assertTrue(calm > playful)
    }
}
