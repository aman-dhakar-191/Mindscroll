package io.github.amandhakar.mindscroll

import io.github.amandhakar.mindscroll.mascot.InMemoryMoStore
import io.github.amandhakar.mindscroll.mascot.MoMoments
import io.github.amandhakar.mindscroll.mascot.MoPolicy
import io.github.amandhakar.mindscroll.mascot.QuietHours
import io.github.amandhakar.mindscroll.mascot.Remark
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MoPolicyTest {
    private val minute = 60_000L
    private val hour = 60 * minute
    private val zone = ZoneOffset.UTC
    private val store = InMemoryMoStore()

    private fun at(hourOfDay: Int, minuteOfHour: Int = 0, day: Int = 1): Long =
        LocalDateTime.of(2026, 3, day, hourOfDay, minuteOfHour).toInstant(zone).toEpochMilli()

    private fun policy() = MoPolicy(store, zone)

    @Test
    fun cooldownSurvivesARestart() {
        assertTrue(policy().trySpeak(at(12), Remark.REACTION))
        // A new policy over the same store, as after the service or process restarts.
        val restarted = policy()
        assertFalse(restarted.trySpeak(at(12, 1), Remark.REACTION))
        assertTrue(restarted.trySpeak(at(12) + MoPolicy.GAP_MS, Remark.REACTION))
    }

    @Test
    fun newMomentsInstanceDoesNotRepeatARemarkRightAway() {
        assertNotNull(MoMoments(policy()).onRemark(at(12), "New day."))
        assertNull(MoMoments(policy()).onRemark(at(12) + 1_000, "New day."))
    }

    @Test
    fun bigMomentsNeedOnlyAShortGap() {
        val p = policy()
        assertTrue(p.trySpeak(at(12), Remark.REACTION))
        assertFalse(p.trySpeak(at(12) + MoPolicy.BIG_GAP_MS - 1, Remark.BIG))
        assertTrue(p.trySpeak(at(12) + MoPolicy.BIG_GAP_MS, Remark.BIG))
    }

    @Test
    fun hourlyCapHoldsAcrossRestarts() {
        val start = at(12)
        repeat(MoPolicy.MAX_PER_HOUR) { i ->
            assertTrue(policy().trySpeak(start + i * MoPolicy.BIG_GAP_MS, Remark.BIG))
        }
        assertFalse(policy().trySpeak(start + MoPolicy.MAX_PER_HOUR * MoPolicy.BIG_GAP_MS, Remark.BIG))
        assertTrue(policy().trySpeak(start + hour + 1, Remark.BIG))
    }

    @Test
    fun snoozeSilencesEverythingUntilItEnds() {
        val p = policy()
        p.snooze(at(12), hour)
        assertFalse(p.trySpeak(at(12, 30), Remark.BIG))
        assertEquals(at(13), p.snoozedUntil(at(12, 30)))
        assertTrue(p.trySpeak(at(13), Remark.BIG))
        assertNull(p.snoozedUntil(at(13)))
    }

    @Test
    fun unsnoozeLetsMoTalkAgain() {
        val p = policy()
        p.snooze(at(12), hour)
        p.unsnooze()
        assertTrue(p.trySpeak(at(12, 1), Remark.REACTION))
    }

    @Test
    fun quietHoursWrapPastMidnight() {
        store.profile = store.profile.copy(quietHours = QuietHours.NIGHT)
        val p = policy()
        assertTrue(p.quiet(at(23)))
        assertTrue(p.quiet(at(2)))
        assertTrue(p.quiet(at(7, 59)))
        assertFalse(p.quiet(at(8)))
        assertFalse(p.quiet(at(21, 59)))
        assertFalse(p.trySpeak(at(23), Remark.BIG))
        assertTrue(p.trySpeak(at(9), Remark.BIG))
    }

    @Test
    fun quietHoursFollowLocalTime() {
        store.profile = store.profile.copy(quietHours = QuietHours.NIGHT)
        // 23:00 UTC is 4:30 the next morning in India: still quiet there. 12:00 UTC is 17:30.
        val india = MoPolicy(store, java.time.ZoneId.of("Asia/Kolkata"))
        assertTrue(india.quiet(at(23)))
        assertFalse(india.quiet(at(12)))
    }

    @Test
    fun greetingIsClaimedOncePerDayEvenAfterARestart() {
        assertTrue(policy().claimGreeting(at(9)))
        assertFalse(policy().claimGreeting(at(15)))
        assertTrue(policy().claimGreeting(at(0, 5, day = 2))) // just after midnight
    }

    @Test
    fun initiativesHaveTheirOwnGapAndDailyBudget() {
        val p = policy()
        assertTrue(p.trySpeak(at(9), Remark.INITIATIVE))
        assertFalse(p.trySpeak(at(9, 30), Remark.INITIATIVE)) // too soon after the last one
        assertTrue(p.trySpeak(at(9, 30), Remark.REACTION)) // reactions only need the usual gap
        assertTrue(p.trySpeak(at(10, 30), Remark.INITIATIVE))
        assertTrue(p.trySpeak(at(12), Remark.INITIATIVE))
        assertFalse(p.trySpeak(at(18), Remark.INITIATIVE)) // budget spent for today
        assertTrue(p.trySpeak(at(9, day = 2), Remark.INITIATIVE)) // fresh budget tomorrow
        assertEquals(1, store.interaction.dailyInitiativeCount)
    }

    @Test
    fun clockSetBackDoesNotSilenceMoForLong() {
        val p = policy()
        assertTrue(p.trySpeak(at(12, day = 5), Remark.REACTION))
        p.snooze(at(12, day = 5), hour)
        // The clock jumps back three days: the old snooze and remark count as "now", not days ahead.
        val back = at(12, day = 2)
        assertNull(p.snoozedUntil(back))
        assertFalse(p.trySpeak(back, Remark.REACTION))
        assertTrue(p.trySpeak(back + MoPolicy.GAP_MS, Remark.REACTION))
    }

    @Test
    fun beingWavedAwayPausesInitiativesForTheDay() {
        val p = policy()
        repeat(MoPolicy.DISMISSALS_TO_PAUSE - 1) { p.speechEnded(at(9), dismissed = true) }
        p.speechEnded(at(9), dismissed = false) // one left to run its course resets the streak
        repeat(MoPolicy.DISMISSALS_TO_PAUSE - 1) { p.speechEnded(at(9), dismissed = true) }
        assertTrue(p.allows(at(10), Remark.INITIATIVE))
        p.speechEnded(at(10), dismissed = true)
        assertFalse(p.allows(at(11), Remark.INITIATIVE))
        assertTrue(p.allows(at(11), Remark.REACTION)) // reactions and check-ins carry on
        assertTrue(p.allows(at(11), Remark.FOLLOW_THROUGH))
        assertTrue(p.allows(at(9, day = 2), Remark.INITIATIVE))
    }

    @Test
    fun followThroughSkipsTheInitiativeBudgetButNotQuiet() {
        val p = policy()
        repeat(MoPolicy.MAX_INITIATIVES_PER_DAY) { i -> assertTrue(p.trySpeak(at(9 + i * 2), Remark.INITIATIVE)) }
        assertFalse(p.allows(at(16), Remark.INITIATIVE))
        assertTrue(p.trySpeak(at(16), Remark.FOLLOW_THROUGH))
        p.snooze(at(17), hour)
        assertFalse(p.allows(at(17, 30), Remark.FOLLOW_THROUGH))
    }
}
