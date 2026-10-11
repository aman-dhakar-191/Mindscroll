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

    private var day = 100L
    private fun sessions() = AppSessions(setOf(ig, yt)) { day }

    // --- Sessions ---

    @Test
    fun startsOnFirstEventAndKeepsItsStartTime() {
        val s = sessions()
        s.onAppEvent(ig, 1_000)
        s.onAppEvent(ig, 5_000)
        s.onCheck(ig, interactive = true, now = 6_000)
        assertEquals(AppSession(ig, startedAt = 1_000, timerBase = 1_000), s.current)
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
    fun switchingBetweenWatchedAppsStartsTheOtherAppsSession() {
        val s = sessions()
        s.onAppEvent(ig, 0)
        s.onAppEvent(yt, 60_000)
        assertEquals(AppSession(yt, startedAt = 60_000, timerBase = 60_000), s.current)
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

    // --- Timer resumes from today's time ---

    @Test
    fun reopeningAnAppResumesItsTimerFromTodaysTime() {
        val s = sessions()
        s.onAppEvent(ig, 0)
        s.onCheck(ig, interactive = true, now = 60_000)
        s.onCheck(launcher, interactive = true, now = 70_000) // ended after 60 s in the app
        s.onAppEvent(ig, 600_000) // ten minutes later
        // The timer shows 1:00 right away: it counts from 60 s before now.
        assertEquals(600_000L - 60_000L, s.current?.timerBase)
    }

    @Test
    fun eachAppKeepsItsOwnTimer() {
        val s = sessions()
        s.onAppEvent(ig, 0)
        s.onAppEvent(ig, 30_000)
        s.onAppEvent(yt, 31_000) // YouTube starts from its own 0:00
        assertEquals(31_000L, s.current?.timerBase)
        s.onAppEvent(yt, 41_000)
        s.onAppEvent(ig, 41_000) // back to Instagram: resumes from its 30 s
        assertEquals(41_000L - 30_000L, s.current?.timerBase)
    }

    @Test
    fun savedTimeFromEarlierTodayIsIncluded() {
        val s = sessions()
        s.addSavedToday(mapOf(ig to 20 * 60_000L)) // 20 min saved before the service started
        s.onAppEvent(ig, 1_000_000)
        assertEquals(1_000_000L - 20 * 60_000L, s.current?.timerBase)
    }

    @Test
    fun savedTimeArrivingMidSessionMovesTheRunningTimer() {
        val s = sessions()
        s.onAppEvent(ig, 1_000_000)
        s.addSavedToday(mapOf(ig to 5 * 60_000L, yt to 60_000L))
        assertEquals(1_000_000L - 5 * 60_000L, s.current?.timerBase)
    }

    @Test
    fun timersStartOverAtMidnight() {
        val s = sessions()
        s.onAppEvent(ig, 0)
        s.onCheck(ig, interactive = true, now = 60_000)
        day++ // midnight
        s.onCheck(ig, interactive = true, now = 61_000)
        assertEquals(61_000L, s.current?.timerBase) // running timer restarts at 0:00
        s.onCheck(launcher, interactive = true, now = 70_000)
        s.onAppEvent(ig, 100_000)
        assertEquals(100_000L - 1_000L, s.current?.timerBase) // only today's 1 s counts
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
    fun timeAwayBetweenSessionsIsNotCounted() {
        val s = sessions()
        s.onAppEvent(ig, 0)
        s.onCheck(ig, interactive = true, now = 10_000)
        s.onCheck(launcher, interactive = true, now = 13_000) // ended, 10 s used
        s.onAppEvent(ig, 18_000)
        s.onCheck(ig, interactive = true, now = 28_000)
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
