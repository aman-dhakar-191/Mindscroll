package io.github.amandhakar.mindscroll

import io.github.amandhakar.mindscroll.mascot.Fatigue
import io.github.amandhakar.mindscroll.mascot.Mo
import io.github.amandhakar.mindscroll.mascot.Mood
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FatigueTest {
    private val minute = 60_000L
    private val half = Fatigue.HALF_LIFE_MS

    /** [count] reels, one every [everyMs], the last one at [end]. */
    private fun binge(count: Int, end: Long, everyMs: Long = 15_000L) =
        List(count) { i -> end - (count - 1 - i) * everyMs }

    @Test
    fun eachRecentReelCountsAboutOne() {
        val now = 10 * half
        assertEquals(3.0, Fatigue.level(listOf(now, now, now), now), 0.001)
    }

    @Test
    fun wearsOffByHalfEveryHalfLife() {
        val now = 10 * half
        assertEquals(50.0, Fatigue.level(List(100) { now - half }, now), 0.001)
        assertEquals(25.0, Fatigue.level(List(100) { now - 2 * half }, now), 0.001)
    }

    @Test
    fun oldReelsAreIgnored() {
        val now = 100 * half
        assertEquals(0.0, Fatigue.level(listOf(now - Fatigue.WINDOW_MS - 1), now), 0.0)
    }

    @Test
    fun aBreakCalmsMoDown() {
        val end = 1_000 * minute
        val reels = binge(100, end) // 100 reels in 25 minutes: the early ones already wore off a bit
        assertEquals(67.0, Fatigue.level(reels, end), 1.0)
        assertEquals(Mood.WOBBLY, Mood.forLevel(Fatigue.level(reels, end)))
        assertEquals(Mood.CURIOUS, Mood.forLevel(Fatigue.level(reels, end + 30 * minute)))
        assertEquals(Mood.CHILL, Mood.forLevel(Fatigue.level(reels, end + 60 * minute)))
    }

    @Test
    fun aFastBingeMakesMoDizzy() {
        val end = 1_000 * minute
        val reels = binge(100, end, everyMs = 5_000L) // 100 reels in about 8 minutes
        assertEquals(Mood.DIZZY, Mood.forLevel(Fatigue.level(reels, end)))
    }

    @Test
    fun steadyBingeKeepsMoFrazzled() {
        // A reel every 10 s for two hours doesn't wear off as fast as it piles up.
        val end = 1_000 * minute
        val reels = binge(720, end, everyMs = 10_000L)
        assertEquals(Mood.SLEEPY, Mood.forLevel(Fatigue.level(reels, end)))
    }

    @Test
    fun restedWhenTheBreakBroughtTheMoodBelowTodaysCount() {
        assertTrue(Mo.isRested(count = 120, mood = Mood.CURIOUS))
        assertFalse(Mo.isRested(count = 120, mood = Mood.DIZZY))
        assertFalse(Mo.isRested(count = 5, mood = Mood.CHILL)) // never got worked up
    }

    @Test
    fun restedMoSaysSoInsteadOfAMilestone() {
        val line = Mo.line(100, 14, Mood.CHILL, rested = true)
        assertFalse(line.startsWith("100 reels"))
        assertFalse("{n}" in line)
    }

    @Test
    fun moodPicksTheLinesWhenGiven() {
        // 33 reels would be curious by count, but a calm Mo talks like chill Mo.
        assertEquals(Mo.line(33, 14, Mood.CHILL), Mo.line(33, 14, Mood.CHILL, rested = false))
        assertTrue(Mo.line(33, 14, Mood.CHILL) != Mo.line(33, 14, Mood.DIZZY))
    }
}
