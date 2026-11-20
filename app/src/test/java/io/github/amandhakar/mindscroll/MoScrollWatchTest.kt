package io.github.amandhakar.mindscroll

import io.github.amandhakar.mindscroll.mascot.MoMoments
import io.github.amandhakar.mindscroll.mascot.MoMotion
import io.github.amandhakar.mindscroll.mascot.MoPolicy
import io.github.amandhakar.mindscroll.mascot.MoProp
import io.github.amandhakar.mindscroll.mascot.MoScrollWatch
import io.github.amandhakar.mindscroll.mascot.Mood
import io.github.amandhakar.mindscroll.mascot.ScrollPattern
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MoScrollWatchTest {
    private val min = 60_000L
    private val ig = "com.instagram.android"

    private fun watch(startedAt: Long = 0): MoScrollWatch =
        MoScrollWatch().also { it.onSessionStart(startedAt, ig, null, null) }

    // --- Scrolling too fast ---

    @Test
    fun flickingThroughReelsIsTooFast() {
        val w = watch()
        val results = (0 until 5).map { w.onReel(it * 2_000L) }
        assertEquals(List(4) { null } + ScrollPattern.TOO_FAST, results)
    }

    @Test
    fun anOrdinaryPaceIsNotTooFast() {
        val w = watch()
        repeat(40) { assertNull(w.onReel(it * 6_000L)) }
    }

    @Test
    fun tooFastDoesNotRepeatForHalfAMinute() {
        val w = watch()
        (0 until 5).forEach { w.onReel(it * 1_000L) }
        // Still flicking, but Mo has already spun.
        assertNull(w.onReel(5_000L))
        assertNull(w.onReel(6_000L))
        // Half a minute later, flicking again: another spin.
        val again = (0 until 5).map { w.onReel(40_000L + it * 1_000L) }
        assertEquals(ScrollPattern.TOO_FAST, again.last())
    }

    // --- Lingering and staring ---

    @Test
    fun aReelLeftAloneIsLingeredOnThenStaredAt() {
        val w = watch()
        w.onReel(10_000)
        assertNull(w.due(10_000 + MoScrollWatch.LINGER_AFTER_MS - 1))
        assertEquals(ScrollPattern.LINGERING, w.due(10_000 + MoScrollWatch.LINGER_AFTER_MS))
        // Once per reel, not every check.
        assertNull(w.due(10_000 + MoScrollWatch.LINGER_AFTER_MS + 5_000))
        assertEquals(ScrollPattern.STARING, w.due(10_000 + MoScrollWatch.STARE_AFTER_MS))
        assertNull(w.due(10_000 + MoScrollWatch.STARE_AFTER_MS + 30_000))
    }

    @Test
    fun aNewReelStartsLingeringOver() {
        val w = watch()
        w.onReel(0)
        assertEquals(ScrollPattern.LINGERING, w.due(MoScrollWatch.LINGER_AFTER_MS))
        w.onReel(MoScrollWatch.LINGER_AFTER_MS + 1_000)
        assertNull(w.due(MoScrollWatch.LINGER_AFTER_MS + 2_000))
        assertEquals(ScrollPattern.LINGERING, w.due(MoScrollWatch.LINGER_AFTER_MS + 1_000 + MoScrollWatch.LINGER_AFTER_MS))
    }

    @Test
    fun aLateCheckReportsTheLongerPatternAndSpendsTheShorter() {
        val w = watch()
        w.onReel(0)
        assertEquals(ScrollPattern.STARING, w.due(5 * min))
        assertNull(w.due(5 * min + 1_000))
    }

    @Test
    fun nothingLingersBeforeTheFirstReelOfASession() {
        // Opening the app and reading comments is not "stuck on a reel".
        val w = watch()
        assertNull(w.due(10 * min))
    }

    // --- A long stretch ---

    @Test
    fun everyTwentyMinutesInOneSessionIsAMarathon() {
        val w = watch()
        assertNull(w.due(19 * min))
        assertEquals(ScrollPattern.MARATHON, w.due(20 * min))
        assertEquals(20, w.minutesIn(20 * min))
        assertNull(w.due(21 * min))
        assertEquals(ScrollPattern.MARATHON, w.due(40 * min))
    }

    @Test
    fun aLateCheckDoesNotFireAMarathonForEveryMissedTwentyMinutes() {
        val w = watch()
        assertEquals(ScrollPattern.MARATHON, w.due(65 * min))
        assertNull(w.due(66 * min))
        assertEquals(ScrollPattern.MARATHON, w.due(80 * min))
    }

    // --- Straight back ---

    @Test
    fun comingStraightBackToTheSameAppIsNoticed() {
        val w = MoScrollWatch()
        assertEquals(ScrollPattern.QUICK_RETURN, w.onSessionStart(100_000, ig, ig, 100_000 - 20_000))
    }

    @Test
    fun aLongerGapOrAnotherAppIsNot() {
        val w = MoScrollWatch()
        assertNull(w.onSessionStart(100_000, ig, ig, 100_000 - MoScrollWatch.QUICK_RETURN_MS))
        assertNull(w.onSessionStart(100_000, ig, "com.google.android.youtube", 100_000 - 10_000))
        assertNull(w.onSessionStart(100_000, ig, null, null))
    }

    // --- Scheduling and ending ---

    @Test
    fun nextCheckIsWhenSomethingCouldNextBecomeDue() {
        val w = watch(startedAt = 1_000)
        assertEquals(1_000 + 20 * min, w.nextCheckAt())
        w.onReel(5_000)
        assertEquals(5_000 + MoScrollWatch.LINGER_AFTER_MS, w.nextCheckAt())
        w.due(5_000 + MoScrollWatch.LINGER_AFTER_MS)
        assertEquals(5_000 + MoScrollWatch.STARE_AFTER_MS, w.nextCheckAt())
        w.due(5_000 + MoScrollWatch.STARE_AFTER_MS)
        // Only the marathon is left.
        assertEquals(1_000 + 20 * min, w.nextCheckAt())
    }

    @Test
    fun endingASessionCancelsEverythingWaiting() {
        val w = watch()
        w.onReel(0)
        w.onSessionEnd()
        assertNull(w.nextCheckAt())
        assertNull(w.due(30 * min))
    }

    @Test
    fun aNewSessionForgetsTheLastOnesReels() {
        val w = watch()
        w.onReel(0)
        w.onSessionStart(10 * min, ig, null, null)
        assertNull(w.due(10 * min + MoScrollWatch.STARE_AFTER_MS))
    }

    // --- Speech ---

    private fun moments() = MoMoments(MoPolicy())

    @Test
    fun flickingThroughIsShownNotSaid() {
        assertNull(moments().onPattern(1_000_000_000L, ScrollPattern.TOO_FAST))
    }

    @Test
    fun theOtherHabitsGetALineInMosVoice() {
        for (pattern in listOf(ScrollPattern.LINGERING, ScrollPattern.STARING, ScrollPattern.MARATHON, ScrollPattern.QUICK_RETURN)) {
            val line = moments().onPattern(1_000_000_000L, pattern, minutes = 20)
            assertNotNull("$pattern", line)
            assertFalse("$pattern left a slot unfilled: $line", "{x}" in line!! || "{n}" in line || "{app}" in line)
            assertFalse("no exclamation spam: $line", "!!" in line)
        }
    }

    @Test
    fun aMarathonLineSaysHowLong() {
        val lines = (0 until 8).map { i ->
            MoMoments(MoPolicy()).also { m -> repeat(i) { m.onPattern(1_000_000_000L + it, ScrollPattern.MARATHON, 20) } }
                .onPattern(2_000_000_000L, ScrollPattern.MARATHON, minutes = 40)
        }
        assertTrue(lines.filterNotNull().any { "40" in it })
    }

    @Test
    fun linesTakeTurnsSoMoDoesntRepeat() {
        val m = MoMoments(MoPolicy())
        val said = (0 until 4).mapNotNull { m.onPattern(1_000_000_000L + it * 10 * min, ScrollPattern.LINGERING) }
        assertEquals(said.size, said.toSet().size)
    }

    // --- Whirl: the dizzy and mesmerised eyes ---

    @Test
    fun theWhirlComesInHoldsAndEasesOut() {
        for (slow in listOf(false, true)) {
            val length = MoMotion.whirlDurationMs(slow)
            assertEquals(0f, MoMotion.whirl(0, slow), 0.001f)
            assertEquals(1f, MoMotion.whirl(length / 3, slow), 0.001f)
            assertEquals(0f, MoMotion.whirl(length, slow), 0f)
            val v = (0L until length step 16).map { MoMotion.whirl(it, slow) }
            assertTrue(v.zipWithNext().maxOf { (a, b) -> kotlin.math.abs(b - a) } < 0.2f)
        }
    }

    @Test
    fun mesmerisedIsSlowerAndLongerThanDizzy() {
        assertTrue(MoMotion.whirlDurationMs(true) > MoMotion.whirlDurationMs(false))
        assertTrue(MoMotion.whirlTurn(1_000, slow = true) < MoMotion.whirlTurn(1_000, slow = false))
    }

    @Test
    fun theSpiralKeepsTurningAndNeverGoesBackwards() {
        val turns = (0L..2_000L step 50).map { MoMotion.whirlTurn(it, slow = false) }
        assertTrue(turns.zipWithNext().all { (a, b) -> b > a })
    }

    @Test
    fun theWhirlShowsUpInThePoseAndWorriesTheFace() {
        val rest = MoMotion.pose(1_000, null, null, false)
        val mid = MoMotion.pose(1_000 + MoMotion.WHIRL_FAST_MS / 3, null, null, false, whirlStartMs = 1_000)
        assertEquals(1f, mid.whirl, 0.001f)
        assertTrue(mid.whirlTurn > 0.3f)
        assertTrue(mid.brow < rest.brow && mid.smile < rest.smile)
        val done = MoMotion.pose(1_000 + MoMotion.WHIRL_FAST_MS, null, null, false, whirlStartMs = 1_000)
        assertEquals(0f, done.whirl, 0f)
        assertEquals(rest.brow, done.brow, 0.0001f)
    }

    // --- Meme props and the side-eye ---

    @Test
    fun aPropPopsInWithOvershootHoldsThenShrinksAway() {
        assertEquals(0f, MoMotion.propPop(0f), 0.001f)
        val pop = (0..160).map { MoMotion.propPop(it / 1000f) }
        assertTrue("overshoots a little", pop.max() > 1.02f && pop.max() < 1.2f)
        assertEquals(1f, MoMotion.propPop(0.16f), 0.001f)
        assertEquals(1f, MoMotion.propPop(0.5f), 0f)
        assertEquals(0f, MoMotion.propPop(1f), 0.001f)
        assertEquals(0f, MoMotion.propPop(-1f), 0f)
        val all = (0..1000).map { MoMotion.propPop(it / 1000f) }
        assertTrue(all.zipWithNext().maxOf { (a, b) -> kotlin.math.abs(b - a) } < 0.08f)
    }

    @Test
    fun aPropIsOnlyShowingWithinItsOwnLength() {
        assertEquals(-1f, MoMotion.propProgress(-1, MoProp.QUESTION), 0f)
        assertEquals(0f, MoMotion.propProgress(0, MoProp.QUESTION), 0f)
        val q = MoProp.QUESTION
        assertEquals(0.5f, MoMotion.propProgress(q.durationMs / 2, q), 0.001f)
        assertEquals(-1f, MoMotion.propProgress(q.durationMs, q), 0f)
    }

    @Test
    fun propsComeInTogetherAndTheMaskClearsWhenTheyAreAllDone() {
        val mask = MoProp.SWEAT.bit or MoProp.SPEED_LINES.bit
        val during = MoMotion.pose(1_100, null, null, false, propStartMs = 1_000, propMask = mask)
        assertEquals(mask, during.propMask)
        assertEquals(100L, during.propSinceMs)
        val longest = maxOf(MoProp.SWEAT.durationMs, MoProp.SPEED_LINES.durationMs)
        val after = MoMotion.pose(1_000 + longest, null, null, false, propStartMs = 1_000, propMask = mask)
        assertEquals(0, after.propMask)
        assertEquals(-1L, after.propSinceMs)
        assertEquals(0, MoMotion.pose(1_000, null, null, false).propMask)
    }

    @Test
    fun everyPropHasItsOwnBit() {
        val bits = MoProp.entries.map { it.bit }
        assertEquals(bits.size, bits.toSet().size)
        assertTrue(bits.all { Integer.bitCount(it) == 1 })
    }

    @Test
    fun theSideEyeEasesInHoldsAndOut() {
        assertEquals(0f, MoMotion.sideEye(0), 0.001f)
        assertEquals(1f, MoMotion.sideEye(MoMotion.SIDE_EYE_MS / 2), 0.001f)
        assertEquals(0f, MoMotion.sideEye(MoMotion.SIDE_EYE_MS), 0f)
        val v = (0L until MoMotion.SIDE_EYE_MS step 16).map { MoMotion.sideEye(it) }
        assertTrue(v.zipWithNext().maxOf { (a, b) -> kotlin.math.abs(b - a) } < 0.2f)
    }

    @Test
    fun theSideEyeIsUnamusedNotHappy() {
        val rest = MoMotion.pose(1_000, null, null, false, mood = Mood.CHILL)
        val eye = MoMotion.pose(1_000 + MoMotion.SIDE_EYE_MS / 2, null, null, false,
            mood = Mood.CHILL, sideEyeStartMs = 1_000)
        assertEquals(1f, eye.sideEye, 0.001f)
        assertTrue(eye.smile < rest.smile - 0.4f)
        assertTrue(eye.brow < rest.brow)
    }
}
