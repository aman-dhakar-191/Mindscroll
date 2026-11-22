package io.github.amandhakar.mindscroll

import io.github.amandhakar.mindscroll.data.Settings
import io.github.amandhakar.mindscroll.display.LiquidSchedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** When Mo is due to come out: the chosen time after the last time, in wall-clock time. */
class LiquidScheduleTest {
    private val min = 60_000L
    private val now = 1_000_000_000_000L

    @Test
    fun neverHavingComeOutMeansDueNow() {
        assertEquals(0L, LiquidSchedule.dueInMs(0L, 15 * min, now))
    }

    @Test
    fun notDueUntilTheChosenTimeHasPassedSinceTheLastTime() {
        assertEquals(10 * min, LiquidSchedule.dueInMs(now - 5 * min, 15 * min, now))
        assertEquals(1L, LiquidSchedule.dueInMs(now - 15 * min + 1, 15 * min, now))
    }

    @Test
    fun dueExactlyAtTheChosenTimeAndAfterwards() {
        assertEquals(0L, LiquidSchedule.dueInMs(now - 15 * min, 15 * min, now))
        assertEquals(0L, LiquidSchedule.dueInMs(now - 3 * 60 * min, 15 * min, now))
    }

    @Test
    fun aClockSetBackDoesNotKeepMoInForHours() {
        // The last time looks like it is in the future: treat Mo as due instead of waiting that long.
        assertEquals(0L, LiquidSchedule.dueInMs(now + 5 * 60 * min, 60 * min, now))
    }

    @Test
    fun aShorterChoiceIsDueSooner() {
        val last = now - 7 * min
        assertEquals(0L, LiquidSchedule.dueInMs(last, 5 * min, now))
        assertEquals(8 * min, LiquidSchedule.dueInMs(last, 15 * min, now))
        assertEquals(53 * min, LiquidSchedule.dueInMs(last, 60 * min, now))
    }

    @Test
    fun theChoicesRunFromFiveMinutesUpAndTheHourIsStillTheDefault() {
        val choices = Settings.LiquidEvery.entries
        assertEquals(5 * min, Settings.LiquidEvery.FIVE_MINUTES.ms)
        assertEquals(choices.map { it.ms }, choices.map { it.ms }.sorted())
        assertEquals(Settings.LiquidEvery.FIVE_MINUTES, choices.first())
        assertTrue(choices.size == 6)
    }
}
