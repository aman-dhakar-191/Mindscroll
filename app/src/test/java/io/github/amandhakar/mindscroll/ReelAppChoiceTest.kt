package io.github.amandhakar.mindscroll

import io.github.amandhakar.mindscroll.tracker.TrackedApps
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The reel counter belongs to some apps; the timer and screen time belong to every tracked app. */
class ReelAppChoiceTest {
    private val instagram = "com.instagram.android"
    private val youtube = "com.google.android.youtube"
    private val rvx = "app.rvx.android.youtube"
    private val revanced = "app.revanced.android.youtube"
    private val netflix = "com.netflix.mediaclient"
    private val telegram = "org.telegram.messenger"
    private val claude = "com.anthropic.claude"

    @Before
    fun setUp() {
        TrackedApps.set(setOf(instagram, youtube, rvx, netflix, telegram, claude))
        TrackedApps.setReelPackages(null)
    }

    @After
    fun tearDown() {
        TrackedApps.set(emptySet())
        TrackedApps.setReelPackages(null)
    }

    @Test
    fun untilTheUserChoosesOnlyReelAppsCountReels() {
        assertTrue(TrackedApps.countsReels(instagram))
        assertTrue(TrackedApps.countsReels(youtube))
        assertFalse(TrackedApps.countsReels(netflix))
        assertFalse(TrackedApps.countsReels(telegram))
        assertFalse(TrackedApps.countsReels(claude))
    }

    @Test
    fun patchedYouTubeBuildsCountToo() {
        assertTrue(TrackedApps.countsReels(rvx))
        assertTrue(TrackedApps.looksLikeReelApp(revanced))
    }

    @Test
    fun everyTrackedAppStillGetsTheTimer() {
        // Counting reels is a narrower choice than tracking: all of these are tracked.
        for (pkg in listOf(instagram, youtube, rvx, netflix, telegram, claude)) assertTrue(TrackedApps.isTracked(pkg))
    }

    @Test
    fun theUsersChoiceReplacesTheDefault() {
        // Only RVX, nothing else: Instagram and YouTube no longer count, Telegram does if chosen.
        TrackedApps.setReelPackages(setOf(rvx, telegram))
        assertTrue(TrackedApps.countsReels(rvx))
        assertTrue(TrackedApps.countsReels(telegram))
        assertFalse(TrackedApps.countsReels(instagram))
        assertFalse(TrackedApps.countsReels(youtube))
    }

    @Test
    fun anEmptyChoiceMeansNoReelCounting() {
        TrackedApps.setReelPackages(emptySet())
        assertFalse(TrackedApps.countsReels(instagram))
    }

    @Test
    fun anUntrackedAppNeverCountsEvenIfChosen() {
        TrackedApps.set(setOf(instagram))
        TrackedApps.setReelPackages(setOf(instagram, telegram))
        assertTrue(TrackedApps.countsReels(instagram))
        assertFalse(TrackedApps.countsReels(telegram))
    }

    @Test
    fun untrackingThenTrackingAgainKeepsTheChoice() {
        TrackedApps.setReelPackages(setOf(telegram))
        TrackedApps.set(setOf(instagram))
        assertFalse(TrackedApps.countsReels(telegram))
        TrackedApps.set(setOf(instagram, telegram))
        assertEquals(true, TrackedApps.countsReels(telegram))
    }
}
