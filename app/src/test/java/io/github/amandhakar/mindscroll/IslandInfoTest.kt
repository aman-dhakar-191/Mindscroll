package io.github.amandhakar.mindscroll

import io.github.amandhakar.mindscroll.data.Settings.AppInfo
import io.github.amandhakar.mindscroll.data.Settings.InfoChoices
import io.github.amandhakar.mindscroll.data.Settings.TimerReveal
import io.github.amandhakar.mindscroll.display.IslandContent
import io.github.amandhakar.mindscroll.display.IslandInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the camera island shows, per app: the rules in one place. */
class IslandInfoTest {
    private val min = 60_000L

    private fun content(choice: AppInfo, countsReels: Boolean, timerOnly: TimerReveal = TimerReveal.ALWAYS, up: Boolean = false) =
        IslandInfo.content(choice, countsReels, timerOnly, up)

    // --- The bug: an app without reels must never show the counter ---

    @Test
    fun anAppWithoutReelsNeverShowsTheCounterWhateverWasChosen() {
        for (choice in AppInfo.entries) for (up in listOf(false, true)) {
            assertTrue(
                "$choice, timer up=$up",
                content(choice, countsReels = false, up = up) != IslandContent.COUNTER,
            )
        }
    }

    @Test
    fun counterAndBothFallBackToTheTimerWithoutReels() {
        assertEquals(AppInfo.TIMER, IslandInfo.effective(AppInfo.COUNTER, countsReels = false))
        assertEquals(AppInfo.TIMER, IslandInfo.effective(AppInfo.BOTH, countsReels = false))
        assertEquals(AppInfo.NOTHING, IslandInfo.effective(AppInfo.NOTHING, countsReels = false))
    }

    // --- What shows ---

    @Test
    fun timerOnlyShowsTheTimerAlwaysOrOnlyWhenBroughtUp() {
        assertEquals(IslandContent.TIMER, content(AppInfo.TIMER, true, TimerReveal.ALWAYS))
        assertEquals(IslandContent.NOTHING, content(AppInfo.TIMER, true, TimerReveal.EVERY_5_MIN, up = false))
        assertEquals(IslandContent.TIMER, content(AppInfo.TIMER, true, TimerReveal.EVERY_5_MIN, up = true))
    }

    @Test
    fun counterAndTimerKeepsTheCounterAndShowsTheTimerInItsPlace() {
        assertEquals(IslandContent.COUNTER, content(AppInfo.BOTH, true, up = false))
        assertEquals(IslandContent.TIMER, content(AppInfo.BOTH, true, up = true))
    }

    @Test
    fun counterAloneAndNothingNeverChange() {
        for (up in listOf(false, true)) {
            assertEquals(IslandContent.COUNTER, content(AppInfo.COUNTER, true, up = up))
            assertEquals(IslandContent.NOTHING, content(AppInfo.NOTHING, true, up = up))
        }
    }

    // --- How often the timer comes up ---

    @Test
    fun theRhythmFollowsTheChoice() {
        assertNull(IslandInfo.rhythmMs(AppInfo.COUNTER, true, TimerReveal.EVERY_5_MIN, TimerReveal.EVERY_5_MIN))
        assertNull(IslandInfo.rhythmMs(AppInfo.NOTHING, true, TimerReveal.EVERY_5_MIN, TimerReveal.EVERY_5_MIN))
        assertNull(IslandInfo.rhythmMs(AppInfo.TIMER, true, TimerReveal.ALWAYS, TimerReveal.EVERY_5_MIN))
        assertEquals(10 * min, IslandInfo.rhythmMs(AppInfo.TIMER, true, TimerReveal.EVERY_10_MIN, TimerReveal.EVERY_5_MIN))
        assertEquals(10 * min, IslandInfo.rhythmMs(AppInfo.BOTH, true, TimerReveal.ALWAYS, TimerReveal.EVERY_10_MIN))
    }

    @Test
    fun counterAndTimerNeverUsesAlwaysOn() {
        // One slot can't hold both at once, so "always" falls back to every five minutes.
        assertEquals(5 * min, IslandInfo.rhythmMs(AppInfo.BOTH, true, TimerReveal.ALWAYS, TimerReveal.ALWAYS))
    }

    @Test
    fun theNextRevealIsTheNextMultipleOfTheInterval() {
        assertEquals(5 * min, IslandInfo.nextRevealInMs(5 * min, 0))
        assertEquals(2 * min, IslandInfo.nextRevealInMs(5 * min, 3 * min))
        assertEquals(5 * min, IslandInfo.nextRevealInMs(5 * min, 10 * min))
        assertEquals(5 * min, IslandInfo.nextRevealInMs(5 * min, -1_000))
    }

    @Test
    fun theTimerRunsWhenItIsPartOfTheChoice() {
        assertTrue(IslandInfo.usesTimer(AppInfo.TIMER, true))
        assertTrue(IslandInfo.usesTimer(AppInfo.BOTH, true))
        assertTrue(IslandInfo.usesTimer(AppInfo.BOTH, false)) // falls back to the timer
        assertFalse(IslandInfo.usesTimer(AppInfo.COUNTER, true))
        assertFalse(IslandInfo.usesTimer(AppInfo.NOTHING, false))
    }

    // --- Per-app choices and their defaults ---

    @Test
    fun anAppWithoutAChoiceGetsTheDefaultForItsKind() {
        val choices = InfoChoices(reelDefault = AppInfo.BOTH, otherDefault = AppInfo.TIMER)
        assertEquals(AppInfo.BOTH, choices.choiceFor("com.instagram.android", countsReels = true))
        assertEquals(AppInfo.TIMER, choices.choiceFor("com.anthropic.claude", countsReels = false))
    }

    @Test
    fun anAppsOwnChoiceWinsButStillCannotShowACounterWithoutReels() {
        val choices = InfoChoices(perApp = mapOf("a" to AppInfo.COUNTER, "b" to AppInfo.NOTHING))
        assertEquals(AppInfo.COUNTER, choices.choiceFor("a", countsReels = true))
        assertEquals(AppInfo.TIMER, choices.choiceFor("a", countsReels = false))
        assertEquals(AppInfo.NOTHING, choices.choiceFor("b", countsReels = false))
    }

    @Test
    fun withNoAppInMindTheReelDefaultApplies() {
        assertEquals(AppInfo.BOTH, InfoChoices().choiceFor(null, countsReels = true))
    }
}
