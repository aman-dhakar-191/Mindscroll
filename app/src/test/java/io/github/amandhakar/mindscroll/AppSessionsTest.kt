package io.github.amandhakar.mindscroll

import io.github.amandhakar.mindscroll.service.AppSession
import io.github.amandhakar.mindscroll.service.AppSessions
import io.github.amandhakar.mindscroll.service.AppUsage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppSessionsTest {
    private val ig = "com.instagram.android"
    private val yt = "com.google.android.youtube"
    private val launcher = "com.android.launcher3"

    private fun sessions() = AppSessions(setOf(ig, yt))

    @Test
    fun startsOnFirstEventAndKeepsItsStartTime() {
        val s = sessions()
        s.onAppEvent(ig, 1_000)
        s.onAppEvent(ig, 5_000)
        s.onCheck(ig, interactive = true, now = 6_000)
        assertEquals(AppSession(ig, 1_000), s.current)
    }

    @Test
    fun ignoresAppsItDoesNotWatch() {
        val s = sessions()
        s.onAppEvent("com.whatsapp", 0)
        assertNull(s.current)
    }

    @Test
    fun endsAfterAnotherAppIsInFrontForAFewSeconds() {
        val s = sessions()
        s.onAppEvent(ig, 0)
        s.onCheck(launcher, interactive = true, now = 1_000) // just left
        assertEquals(ig, s.current?.packageName) // a share sheet or glance doesn't end it yet
        s.onCheck(launcher, interactive = true, now = AppSessions.AWAY_MS + 1)
        assertNull(s.current)
    }

    @Test
    fun endsRightAwayWhenTheScreenTurnsOff() {
        val s = sessions()
        s.onAppEvent(ig, 0)
        s.onCheck(ig, interactive = false, now = 1_000)
        assertNull(s.current)
    }

    @Test
    fun comingBackQuicklyContinuesTheTimer() {
        val s = sessions()
        s.onAppEvent(ig, 0)
        s.onCheck(launcher, interactive = true, now = 4_000) // ended
        assertNull(s.current)
        s.onAppEvent(ig, 4_000 + AppSessions.RESUME_MS - 1)
        assertEquals(AppSession(ig, 0), s.current)
    }

    @Test
    fun comingBackLaterStartsAFreshTimer() {
        val s = sessions()
        s.onAppEvent(ig, 0)
        s.onCheck(launcher, interactive = true, now = 4_000)
        val later = 4_000 + AppSessions.RESUME_MS + 1
        s.onAppEvent(ig, later)
        assertEquals(AppSession(ig, later), s.current)
    }

    @Test
    fun switchingBetweenWatchedAppsStartsTheOtherAppsTimer() {
        val s = sessions()
        s.onAppEvent(ig, 0)
        s.onAppEvent(yt, 60_000)
        assertEquals(AppSession(yt, 60_000), s.current)
    }

    @Test
    fun unknownFrontAppOnlyEndsAfterALongWhile() {
        val s = sessions()
        s.onAppEvent(ig, 0)
        s.onCheck(null, interactive = true, now = 10_000)
        assertEquals(ig, s.current?.packageName)
        s.onCheck(null, interactive = true, now = AppSessions.UNKNOWN_MS)
        assertNull(s.current)
    }

    // --- Screen time ledger ---

    @Test
    fun usageCountsUpToTheLastMomentTheAppWasInFront() {
        val s = sessions()
        s.onAppEvent(ig, 0)
        s.onCheck(ig, interactive = true, now = 20_000)
        s.onCheck(launcher, interactive = true, now = 21_000)
        s.onCheck(launcher, interactive = true, now = 24_000) // ended; the 4 s away don't count
        assertNull(s.current)
        assertEquals(listOf(AppUsage(ig, 20_000)), s.drainUsage())
        assertEquals(emptyList<AppUsage>(), s.drainUsage())
    }

    @Test
    fun longSessionsAreHandedOutInChunksWithoutDoubleCounting() {
        val s = sessions()
        s.onAppEvent(ig, 0)
        s.onCheck(ig, interactive = true, now = 60_000)
        assertEquals(listOf(AppUsage(ig, 60_000)), s.drainUsage())
        s.onCheck(ig, interactive = true, now = 90_000)
        s.onCheck(ig, interactive = false, now = 91_000) // screen off
        assertEquals(listOf(AppUsage(ig, 30_000)), s.drainUsage())
    }

    @Test
    fun resumedSessionDoesNotCountTheGap() {
        val s = sessions()
        s.onAppEvent(ig, 0)
        s.onCheck(ig, interactive = true, now = 10_000)
        s.onCheck(launcher, interactive = true, now = 13_000) // ended, 10 s used
        s.onAppEvent(ig, 18_000) // back within RESUME_MS: same timer...
        s.onCheck(ig, interactive = true, now = 28_000)
        assertEquals(0L, s.current?.startedAt)
        // ...but only time actually in the app counts: 10 s + 10 s.
        assertEquals(20_000L, s.drainUsage().sumOf { it.ms })
    }

    @Test
    fun switchingAppsSplitsTheTime() {
        val s = sessions()
        s.onAppEvent(ig, 0)
        s.onAppEvent(ig, 30_000)
        s.onAppEvent(yt, 31_000)
        s.onCheck(yt, interactive = true, now = 41_000)
        assertEquals(listOf(AppUsage(ig, 30_000), AppUsage(yt, 10_000)), s.drainUsage())
    }
}
