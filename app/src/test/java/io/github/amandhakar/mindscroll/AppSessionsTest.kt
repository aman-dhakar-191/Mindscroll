package io.github.amandhakar.mindscroll

import io.github.amandhakar.mindscroll.service.AppSession
import io.github.amandhakar.mindscroll.service.AppSessions
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
}
