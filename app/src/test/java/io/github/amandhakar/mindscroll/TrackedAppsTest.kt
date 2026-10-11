package io.github.amandhakar.mindscroll

import io.github.amandhakar.mindscroll.service.AppSessions
import io.github.amandhakar.mindscroll.tracker.reels.ReelApps
import io.github.amandhakar.mindscroll.tracker.reels.ReelDetector
import io.github.amandhakar.mindscroll.tracker.reels.ScrollSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackedAppsTest {
    private val snapchat = "com.snapchat.android"
    private val instagram = "com.instagram.android"

    @Test
    fun knownAppsKeepTheirHintsWithThePhonesName() {
        val ig = ReelApps.forPackage(instagram, "Instagram Beta")
        assertEquals("Instagram Beta", ig.label)
        assertEquals("IG", ig.short)
        assertTrue(ig.containerIds.isNotEmpty())
    }

    @Test
    fun otherAppsGetInitialsAndAStableColor() {
        val snap = ReelApps.forPackage(snapchat, "Snapchat")
        assertEquals("SN", snap.short)
        assertTrue(snap.containerIds.isEmpty())
        assertEquals(snap.accent, ReelApps.forPackage(snapchat, "Snapchat").accent)
        assertEquals("JV", ReelApps.initials("Josh Video"))
    }

    private fun feedScroll(pkg: String, time: Long, index: Int) =
        ScrollSample(pkg, time, viewId = null, fromIndex = index, toIndex = index, itemCount = 20,
            containerHeight = 2400, tallestChildHeight = 2400, screenHeight = 2400)

    @Test
    fun reelsCountInAnyTrackedAppByShape() {
        val tracked = setOf(snapchat)
        val d = ReelDetector { pkg -> if (pkg in tracked) ReelApps.forPackage(pkg, "Snapchat") else null }
        assertTrue(d.onScroll(feedScroll(snapchat, 0, 0)))
        assertTrue(d.onScroll(feedScroll(snapchat, 3_000, 1)))
    }

    @Test
    fun untrackedAppsAreIgnoredEvenIfKnown() {
        val d = ReelDetector { null }
        assertFalse(d.onScroll(feedScroll(instagram, 0, 0)))
        assertEquals("not a tracked app", d.lastReason)
        assertFalse(d.onScreen(instagram, 0, feedShowing = true))
    }

    @Test
    fun untrackingAnAppEndsItsSession() {
        val s = AppSessions(setOf(instagram, snapchat)) { 0L }
        s.onAppEvent(snapchat, 0)
        s.setPackages(setOf(instagram))
        assertNull(s.current)
        s.onAppEvent(snapchat, 1_000) // no longer tracked
        assertNull(s.current)
    }
}
