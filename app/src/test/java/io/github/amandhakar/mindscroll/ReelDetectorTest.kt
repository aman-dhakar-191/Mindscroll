package io.github.amandhakar.mindscroll

import io.github.amandhakar.mindscroll.tracker.reels.ReelApps
import io.github.amandhakar.mindscroll.tracker.reels.ReelDetector
import io.github.amandhakar.mindscroll.tracker.reels.ScrollSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReelDetectorTest {
    private val screen = 2400
    private val instagram = "com.instagram.android"
    private val instagramPager = "com.instagram.android:id/clips_viewer_view_pager"
    private val facebook = "com.facebook.katana"

    private fun sample(
        pkg: String = instagram,
        time: Long,
        viewId: String? = instagramPager,
        from: Int = -1,
        to: Int = from,
        count: Int = if (from >= 0) 50 else -1,
        containerHeight: Int = screen,
        childHeight: Int = screen,
        horizontal: Boolean = false,
    ) = ScrollSample(pkg, time, viewId, from, to, count, containerHeight, childHeight, screen, horizontal)

    /** Number of samples the detector counted as a new reel. */
    private fun ReelDetector.count(vararg samples: ScrollSample) = samples.count { onScroll(it) }

    // --- Opening a feed ---

    @Test
    fun openingFeedCountsTheFirstReel() {
        val d = ReelDetector()
        assertTrue(d.onScreen(instagram, 0, feedShowing = true))
        // More screen checks while it stays open don't add more.
        assertFalse(d.onScreen(instagram, 800, feedShowing = true))
        assertFalse(d.onScreen(instagram, 1600, feedShowing = true))
    }

    @Test
    fun openThenSwipeCountsBoth() {
        val d = ReelDetector()
        assertTrue(d.onScreen(instagram, 0, feedShowing = true, index = 0))
        val counted = d.count(
            sample(time = 200, from = 0), // layout of the opening reel
            sample(time = 5000, from = 0, to = 1), // mid-swipe
            sample(time = 5100, from = 1),
        )
        assertEquals(1, counted)
    }

    @Test
    fun openWithoutPositionLearnsItFromFirstScroll() {
        val d = ReelDetector()
        assertTrue(d.onScreen(instagram, 0, feedShowing = true))
        assertFalse(d.onScroll(sample(time = 300, from = 7))) // still the opening reel
        assertTrue(d.onScroll(sample(time = 4000, from = 8)))
    }

    @Test
    fun firstScrollAfterOpeningWithoutPositionIsASwipeIfLate() {
        val d = ReelDetector()
        assertTrue(d.onScreen(instagram, 0, feedShowing = true))
        // Watched the opening reel for a while, then swiped: the next reel counts too.
        assertTrue(d.onScroll(sample(time = 9000, from = 4)))
    }

    @Test
    fun scrollingIntoUnseenFeedCountsTheReelLandedOn() {
        val d = ReelDetector()
        assertTrue(d.onScroll(sample(time = 0, from = 3)))
        assertTrue(d.onScroll(sample(time = 2000, from = 4)))
    }

    @Test
    fun sheetOverReelDoesNotRecount() {
        val d = ReelDetector()
        assertTrue(d.onScreen(instagram, 0, feedShowing = true))
        assertFalse(d.onScreen(instagram, 1000, feedShowing = false)) // comments open
        assertFalse(d.onScreen(instagram, 3000, feedShowing = true)) // closed again
    }

    @Test
    fun leavingFeedAndOpeningAnotherCountsAgain() {
        val d = ReelDetector()
        assertTrue(d.onScreen(instagram, 0, feedShowing = true))
        assertFalse(d.onScreen(instagram, 10_000, feedShowing = false)) // back to the home feed
        assertTrue(d.onScreen(instagram, 30_000, feedShowing = true)) // tapped another reel
    }

    @Test
    fun comingBackToTheAppAfterAWhileCountsTheReelShown() {
        val d = ReelDetector()
        assertTrue(d.onScreen(instagram, 0, feedShowing = true, index = 5))
        // No events at all while the user was in another app.
        val back = ReelDetector.AWAY_GAP_MS + 10_000
        assertTrue(d.onScreen(instagram, back, feedShowing = true, index = 9))
    }

    @Test
    fun comingBackToTheSameReelDoesNotRecount() {
        val d = ReelDetector()
        assertTrue(d.onScreen(instagram, 0, feedShowing = true, index = 5))
        val back = ReelDetector.AWAY_GAP_MS + 10_000 // e.g. screen was off
        assertFalse(d.onScreen(instagram, back, feedShowing = true, index = 5))
    }

    @Test
    fun steadyAppActivityKeepsTheVisitOpen() {
        val d = ReelDetector()
        assertTrue(d.onScreen(instagram, 0, feedShowing = true))
        // A long reel keeps the app busy (progress updates) without anything scrolling.
        for (t in 10_000L..120_000L step 10_000L) d.onAppActivity(instagram, t)
        assertFalse(d.onScreen(instagram, 125_000, feedShowing = true))
    }

    // --- Swiping ---

    @Test
    fun countsEachNewReelOnceListSettles() {
        val d = ReelDetector()
        val counted = d.count(
            sample(time = 0, from = 0), // landing in the feed
            sample(time = 1000, from = 0, to = 1), // mid-swipe: two reels visible
            sample(time = 1100, from = 1),
            sample(time = 1150, from = 1), // repeated event for the same reel
            sample(time = 3000, from = 2),
        )
        assertEquals(3, counted)
    }

    @Test
    fun swipingBackAndForthDoesNotRecount() {
        val d = ReelDetector()
        val counted = d.count(
            sample(time = 0, from = 3), // new
            sample(time = 1000, from = 4), // new
            sample(time = 2000, from = 3), // back
            sample(time = 3000, from = 4), // already seen
            sample(time = 4000, from = 5), // new
        )
        assertEquals(3, counted)
    }

    @Test
    fun feedRefreshedToTopCountsTheNewTopReel() {
        val d = ReelDetector()
        d.count(sample(time = 0, from = 10), sample(time = 1000, from = 11))
        assertTrue(d.onScroll(sample(time = 2000, from = 0)))
        assertTrue(d.onScroll(sample(time = 3000, from = 1)))
    }

    @Test
    fun withoutPositionsCountsOncePerGesture() {
        val d = ReelDetector()
        val counted = d.count(
            sample(pkg = facebook, viewId = null, time = 0),
            sample(pkg = facebook, viewId = null, time = 100), // same swipe still animating
            sample(pkg = facebook, viewId = null, time = 250),
            sample(pkg = facebook, viewId = null, time = 2000), // next swipe
        )
        assertEquals(2, counted)
    }

    @Test
    fun withoutPositionsLayoutRightAfterOpeningIsNotASwipe() {
        val d = ReelDetector()
        assertTrue(d.onScreen(facebook, 0, feedShowing = true))
        assertFalse(d.onScroll(sample(pkg = facebook, viewId = null, time = 300)))
        assertTrue(d.onScroll(sample(pkg = facebook, viewId = null, time = 5000)))
    }

    // --- What counts as a reel feed ---

    @Test
    fun ignoresFeedsWhereSeveralItemsShow() {
        val d = ReelDetector()
        // Facebook news feed: full-height list, but each post is a fraction of it.
        val counted = d.count(
            sample(pkg = facebook, viewId = null, time = 0, childHeight = 700),
            sample(pkg = facebook, viewId = null, time = 2000, childHeight = 700),
        )
        assertEquals(0, counted)
    }

    @Test
    fun ignoresSmallScrollers() {
        val d = ReelDetector()
        val counted = d.count(
            sample(pkg = facebook, viewId = null, time = 0, containerHeight = 400, childHeight = 400),
            sample(pkg = facebook, viewId = null, time = 2000, containerHeight = 400, childHeight = 400),
        )
        assertEquals(0, counted)
    }

    @Test
    fun ignoresSidewaysPagersLikeStories() {
        val d = ReelDetector()
        val counted = d.count(
            sample(viewId = null, time = 0, from = 0, horizontal = true),
            sample(viewId = null, time = 2000, from = 1, horizontal = true),
        )
        assertEquals(0, counted)
    }

    @Test
    fun knownContainerCountsEvenIfSizesAreOdd() {
        val d = ReelDetector()
        val counted = d.count(
            sample(time = 0, from = 0, containerHeight = 0, childHeight = 0),
            sample(time = 1000, from = 1, containerHeight = 0, childHeight = 0),
        )
        assertEquals(2, counted)
    }

    @Test
    fun ignoresOtherApps() {
        val d = ReelDetector()
        assertFalse(d.onScroll(sample(pkg = "com.whatsapp", viewId = null, time = 0)))
        assertFalse(d.onScreen("com.whatsapp", 0, feedShowing = true))
    }

    @Test
    fun appsAreTrackedSeparately() {
        val d = ReelDetector()
        val youtube = ReelApps.all.first { it.label.startsWith("YouTube") }
        val shortsId = youtube.containerIds.first()
        val counted = d.count(
            sample(time = 0, from = 5), // Instagram: new
            sample(pkg = youtube.packageName, viewId = shortsId, time = 100, from = 0), // YouTube: new
            sample(time = 1000, from = 6), // Instagram: new, unaffected by YouTube
            sample(time = 1500, from = 5), // Instagram: back, already seen
            sample(pkg = youtube.packageName, viewId = shortsId, time = 1100, from = 1), // YouTube: new
        )
        assertEquals(4, counted)
    }

    @Test
    fun decisionsCarryAReasonForTheLog() {
        val d = ReelDetector()
        d.onScroll(sample(time = 0, from = 3))
        assertEquals("new visit by scrolling", d.lastReason)
        d.onScroll(sample(time = 1000, from = 3, to = 4))
        assertEquals("mid-swipe", d.lastReason)
        d.onScroll(sample(time = 2000, from = 2))
        assertEquals("already seen #2 (furthest #3)", d.lastReason)
        d.onScroll(sample(pkg = facebook, viewId = null, time = 0, childHeight = 700))
        assertEquals("not a reel feed", d.lastReason)
    }
}
