package io.github.amandhakar.mindscroll

import io.github.amandhakar.mindscroll.mascot.Mo
import io.github.amandhakar.mindscroll.mascot.Mood
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MoTest {
    @Test
    fun moodFollowsCount() {
        assertEquals(Mood.CHILL, Mood.forCount(0))
        assertEquals(Mood.CHILL, Mood.forCount(15))
        assertEquals(Mood.CURIOUS, Mood.forCount(16))
        assertEquals(Mood.WOBBLY, Mood.forCount(41))
        assertEquals(Mood.DIZZY, Mood.forCount(81))
        assertEquals(Mood.SLEEPY, Mood.forCount(151))
        assertEquals(Mood.SLEEPY, Mood.forCount(10_000))
    }

    @Test
    fun linesFillInTheCountAndNeverLeaveAPlaceholder() {
        for (count in 0L..600L) {
            for (hour in listOf(2, 14)) {
                val line = Mo.line(count, hour)
                assertTrue(line.isNotBlank())
                assertFalse("placeholder left in '$line'", "{n}" in line)
            }
        }
    }

    @Test
    fun lineIsStableBetweenNearbyCounts() {
        // Changes every few reels, not on every single one.
        assertEquals(Mo.line(31, 14).replace("31", "N"), Mo.line(32, 14).replace("32", "N"))
    }

    @Test
    fun milestonesHaveTheirOwnLine() {
        assertTrue(Mo.line(100, 14).startsWith("100 reels"))
        assertTrue(Mo.line(100, 2).startsWith("100 reels")) // even late at night
    }
}
