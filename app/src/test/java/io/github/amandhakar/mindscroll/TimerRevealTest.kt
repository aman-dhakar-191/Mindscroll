package io.github.amandhakar.mindscroll

import io.github.amandhakar.mindscroll.data.Settings.TimerReveal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** When the session timer next shows, for "every 5 minutes" and "every 10 minutes". */
class TimerRevealTest {
    private val min = 60_000L

    @Test
    fun alwaysIsTheDefaultAndNeverSchedulesAReveal() {
        assertNull(TimerReveal.ALWAYS.nextRevealInMs(3 * min))
        assertNull(TimerReveal.ALWAYS.intervalMs)
    }

    @Test
    fun aFreshSessionWaitsTheWholeInterval() {
        assertEquals(5 * min, TimerReveal.EVERY_5_MIN.nextRevealInMs(0))
        assertEquals(10 * min, TimerReveal.EVERY_10_MIN.nextRevealInMs(0))
    }

    @Test
    fun theNextRevealIsTheNextMultipleOfTheInterval() {
        assertEquals(2 * min, TimerReveal.EVERY_5_MIN.nextRevealInMs(3 * min))
        assertEquals(7 * min, TimerReveal.EVERY_10_MIN.nextRevealInMs(13 * min))
        assertEquals(4 * min + 30_000, TimerReveal.EVERY_5_MIN.nextRevealInMs(5 * min + 30_000))
    }

    @Test
    fun exactlyOnTheMarkWaitsForTheNextOne() {
        assertEquals(5 * min, TimerReveal.EVERY_5_MIN.nextRevealInMs(10 * min))
    }

    @Test
    fun aClockThatReadsAheadOfTheBaseDoesNotGoNegative() {
        assertEquals(5 * min, TimerReveal.EVERY_5_MIN.nextRevealInMs(-1_000))
    }
}
