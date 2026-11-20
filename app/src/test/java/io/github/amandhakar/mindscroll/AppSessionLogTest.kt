package io.github.amandhakar.mindscroll

import io.github.amandhakar.mindscroll.service.AppSessions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The detection log explains why a session started or ended, so "why did the patch change?" can be answered. */
class AppSessionLogTest {
    private val claude = "com.anthropic.claude"
    private val instagram = "com.instagram.android"

    private fun sessions(vararg packages: String): Pair<AppSessions, MutableList<String>> {
        val lines = mutableListOf<String>()
        return AppSessions(packages.toSet()) { 0L }.also { it.log = { line -> lines += line } } to lines
    }

    @Test
    fun startingASessionIsLogged() {
        val (s, lines) = sessions(claude)
        s.onAppEvent(claude, 0)
        assertEquals(listOf("session: started $claude (open #1 today)"), lines)
    }

    @Test
    fun eventsFromTheSameAppDoNotRepeatTheLine() {
        val (s, lines) = sessions(claude)
        repeat(5) { s.onAppEvent(claude, it * 100L) }
        assertEquals(1, lines.size)
    }

    @Test
    fun aSystemWindowInFrontIsNamedWhenItEndsTheSession() {
        val (s, lines) = sessions(claude)
        s.onAppEvent(claude, 0)
        s.onCheck("com.android.systemui", interactive = true, now = AppSessions.AWAY_MS)
        assertNull(s.current)
        assertTrue(lines.last(), lines.last() == "session: ended $claude — com.android.systemui in front for 3s")
    }

    @Test
    fun anotherUntrackedAppIsNotNamed() {
        val (s, lines) = sessions(claude)
        s.onAppEvent(claude, 0)
        s.onCheck("com.some.private.app", interactive = true, now = AppSessions.AWAY_MS)
        assertEquals("session: ended $claude — another app in front for 3s", lines.last())
        assertFalse(lines.any { "private" in it })
    }

    @Test
    fun screenOffAndUnknownFrontAreExplained() {
        val (a, aLines) = sessions(claude)
        a.onAppEvent(claude, 0)
        a.onCheck(claude, interactive = false, now = 500)
        assertEquals("session: ended $claude — screen off", aLines.last())

        val (b, bLines) = sessions(claude)
        b.onAppEvent(claude, 0)
        b.onCheck(null, interactive = true, now = AppSessions.UNKNOWN_MS)
        assertEquals("session: ended $claude — couldn't tell what was in front for 30s", bLines.last())
    }

    @Test
    fun switchingAndUntrackingAreExplained() {
        val (s, lines) = sessions(claude, instagram)
        s.onAppEvent(claude, 0)
        s.onAppEvent(instagram, 1_000)
        assertTrue(lines.contains("session: ended $claude — switched to $instagram"))
        assertTrue(lines.last() == "session: started $instagram (open #1 today)")
        s.setPackages(setOf(claude))
        assertEquals("session: ended $instagram — no longer tracked", lines.last())
    }
}
