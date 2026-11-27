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
        key: Int? = null,
        dy: Int? = null,
    ) = ScrollSample(pkg, time, viewId, from, to, count, containerHeight, childHeight, screen, horizontal, key, dy)

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

    // --- Swiping back ---

    @Test
    fun swipingBackToTheFirstReelDoesNotCount() {
        val d = ReelDetector()
        val counted = d.count(
            sample(time = 0, from = 0), // new
            sample(time = 2000, from = 1), // new
            sample(time = 4000, from = 0), // back to the first one
            sample(time = 6000, from = 1), // seen
        )
        assertEquals(2, counted)
        assertEquals("already seen #1 (furthest #1)", d.lastReason)
    }

    @Test
    fun swipingBackStepByStepToTheTopDoesNotCount() {
        val d = ReelDetector()
        val counted = d.count(
            sample(time = 0, from = 0),
            sample(time = 2000, from = 1),
            sample(time = 4000, from = 2),
            sample(time = 6000, from = 1),
            sample(time = 8000, from = 0),
        )
        assertEquals(3, counted)
    }

    @Test
    fun withScrollDistanceSwipingBackDoesNotCount() {
        val d = ReelDetector()
        val fb = { t: Long, dy: Int -> sample(pkg = facebook, viewId = null, time = t, dy = dy) }
        val counted = d.count(
            fb(0, 0), // landing in the feed: new
            fb(3000, 1200), fb(3100, 1200), // forward: new
            fb(6000, 1300), fb(6100, 1100), // forward: new
            fb(9000, -1200), fb(9100, -1200), // back: seen
            fb(12_000, 2400), // forward again to the one already seen
            fb(15_000, 2400), // forward to a new one
        )
        assertEquals(4, counted)
    }

    @Test
    fun withScrollDistanceADragThatSpringsBackDoesNotCount() {
        val d = ReelDetector()
        assertTrue(d.onScroll(sample(pkg = facebook, viewId = null, time = 0, dy = 0)))
        // Pulled a little, let go: it springs back.
        assertFalse(d.onScroll(sample(pkg = facebook, viewId = null, time = 3000, dy = 300)))
        assertFalse(d.onScroll(sample(pkg = facebook, viewId = null, time = 3100, dy = -300)))
        assertTrue(d.onScroll(sample(pkg = facebook, viewId = null, time = 6000, dy = 2000)))
    }

    @Test
    fun withScrollDistanceAPagerJumpingBackAfterASwipeStillCounts() {
        val d = ReelDetector()
        d.onScroll(sample(pkg = facebook, viewId = null, time = 0, dy = 0))
        val counted = d.count(
            sample(pkg = facebook, viewId = null, time = 3000, dy = 1500),
            sample(pkg = facebook, viewId = null, time = 3100, dy = 900),
            sample(pkg = facebook, viewId = null, time = 3200, dy = -2400), // re-centred
        )
        assertEquals(1, counted)
    }

    // --- Viewers opened on top of the Reels tab ---

    private val reelsTab = 11
    private val profileViewer = 22

    @Test
    fun reelOpenedFromAProfileOverTheReelsTabCounts() {
        val d = ReelDetector()
        assertTrue(d.onScreen(instagram, 0, feedShowing = true, feedKeys = setOf(reelsTab)))
        d.count(
            sample(time = 200, from = 0, key = reelsTab),
            sample(time = 2000, from = 30, key = reelsTab), // well down the Reels tab
        )
        // Went to a profile (the Reels tab stays underneath and still counts as showing),
        // tapped one of its reels: a new viewer starting at its own #0.
        assertFalse(d.onScreen(instagram, 20_000, feedShowing = true, feedKeys = setOf(reelsTab)))
        assertTrue(d.onScreen(instagram, 30_000, feedShowing = true, feedKeys = setOf(reelsTab, profileViewer)))
        val counted = d.count(
            sample(time = 30_200, from = 0, key = profileViewer), // its layout
            sample(time = 33_000, from = 0, to = 1, key = profileViewer),
            sample(time = 33_100, from = 1, key = profileViewer),
            sample(time = 36_000, from = 2, key = profileViewer),
            sample(time = 39_000, from = 1, key = profileViewer), // back
        )
        assertEquals(2, counted)
    }

    @Test
    fun viewerNoticedFirstByItsScrollsCountsOnce() {
        val d = ReelDetector()
        assertTrue(d.onScreen(instagram, 0, feedShowing = true, feedKeys = setOf(reelsTab)))
        d.onScroll(sample(time = 2000, from = 5, key = reelsTab))
        // The profile's viewer scrolls before a screen check finds it.
        assertTrue(d.onScroll(sample(time = 30_000, from = 3, key = profileViewer)))
        assertFalse(d.onScreen(instagram, 30_300, feedShowing = true, feedKeys = setOf(reelsTab, profileViewer)))
        assertTrue(d.onScroll(sample(time = 33_000, from = 4, key = profileViewer)))
    }

    @Test
    fun backToTheReelsTabFromAViewerDoesNotRecount() {
        val d = ReelDetector()
        assertTrue(d.onScreen(instagram, 0, feedShowing = true, feedKeys = setOf(reelsTab)))
        d.count(sample(time = 200, from = 0, key = reelsTab), sample(time = 2000, from = 4, key = reelsTab))
        assertTrue(d.onScreen(instagram, 10_000, feedShowing = true, feedKeys = setOf(reelsTab, profileViewer)))
        assertFalse(d.onScreen(instagram, 20_000, feedShowing = true, feedKeys = setOf(reelsTab))) // closed it
        assertFalse(d.onScroll(sample(time = 22_000, from = 4, key = reelsTab)))
        assertTrue(d.onScroll(sample(time = 24_000, from = 5, key = reelsTab)))
    }

    @Test
    fun anotherReelOpenedRightAfterClosingOneCounts() {
        val d = ReelDetector()
        assertTrue(d.onScreen(instagram, 0, feedShowing = true, feedKeys = setOf(profileViewer)))
        assertFalse(d.onScreen(instagram, 5000, feedShowing = false)) // back on the profile
        assertTrue(d.onScreen(instagram, 6000, feedShowing = true, feedKeys = setOf(33))) // next reel
    }

    // --- Coming back ---

    @Test
    fun comingBackToTheSameFeedViewDoesNotRecount() {
        val d = ReelDetector()
        assertTrue(d.onScreen(instagram, 0, feedShowing = true, feedKeys = setOf(reelsTab)))
        // Screen off for a few minutes, or another app.
        val back = ReelDetector.AWAY_GAP_MS + 3 * 60_000L
        assertFalse(d.onScreen(instagram, back, feedShowing = true, feedKeys = setOf(reelsTab)))
        assertEquals("back on the same feed after 225s", d.lastReason)
    }

    @Test
    fun switchingTabsAndBackDoesNotRecount() {
        val d = ReelDetector()
        assertTrue(d.onScreen(instagram, 0, feedShowing = true, feedKeys = setOf(reelsTab)))
        d.count(sample(time = 200, from = 0, key = reelsTab), sample(time = 3000, from = 1, key = reelsTab))
        assertFalse(d.onScreen(instagram, 10_000, feedShowing = false)) // home tab
        assertFalse(d.onScreen(instagram, 40_000, feedShowing = true, feedKeys = setOf(reelsTab))) // Reels tab again
        assertFalse(d.onScroll(sample(time = 40_500, from = 1, key = reelsTab)))
        assertTrue(d.onScroll(sample(time = 43_000, from = 2, key = reelsTab)))
    }

    @Test
    fun comingBackAfterALongTimeCountsTheReelShown() {
        val d = ReelDetector()
        assertTrue(d.onScreen(instagram, 0, feedShowing = true, feedKeys = setOf(reelsTab)))
        assertTrue(d.onScreen(instagram, ReelDetector.STALE_MS + 60_000, feedShowing = true, feedKeys = setOf(reelsTab)))
    }

    @Test
    fun scrollingInAFeedViewLeftEarlierContinuesFromWhereItWas() {
        val d = ReelDetector()
        d.count(sample(time = 0, from = 0, key = reelsTab), sample(time = 2000, from = 3, key = reelsTab))
        d.onScreen(instagram, 5000, feedShowing = false)
        // Back to the tab and swiping before a screen check sees it.
        assertFalse(d.onScroll(sample(time = 60_000, from = 3, key = reelsTab)))
        assertTrue(d.onScroll(sample(time = 62_000, from = 4, key = reelsTab)))
    }

    // --- Reels in the home feed ---

    private val homeFeed = 44

    @Test
    fun feedReelCountsWhenYouStopOnIt() {
        val d = ReelDetector()
        assertFalse(d.onFeedScroll(instagram, 0, homeFeed, index = 3, isReel = true))
        assertFalse(d.onFeedScroll(instagram, 100, homeFeed, index = 3, isReel = true))
        // The list stopped; the reel keeps playing.
        assertFalse(d.onTick(instagram, 800))
        assertTrue(d.onTick(instagram, 1400))
        assertEquals("reel in the feed #3", d.lastReason)
        assertFalse(d.onTick(instagram, 2000))
    }

    @Test
    fun scrollingStraightPastFeedReelsDoesNotCount() {
        val d = ReelDetector()
        var t = 0L
        for (index in 0..20) {
            assertFalse(d.onFeedScroll(instagram, t, homeFeed, index, isReel = index % 2 == 0))
            t += 150
        }
    }

    @Test
    fun feedReelCountsWhenYouScrollOnAfterStayingOnIt() {
        val d = ReelDetector()
        d.onFeedScroll(instagram, 0, homeFeed, index = 5, isReel = true)
        // No other events arrived while it played; then the next scroll moves on.
        assertTrue(d.onFeedScroll(instagram, 5000, homeFeed, index = 6, isReel = false))
        assertEquals("reel in the feed #5", d.lastReason)
    }

    @Test
    fun scrollingBackUpToAFeedReelDoesNotCountItAgain() {
        val d = ReelDetector()
        d.onFeedScroll(instagram, 0, homeFeed, index = 5, isReel = true)
        assertTrue(d.onTick(instagram, 2000))
        d.onFeedScroll(instagram, 3000, homeFeed, index = 6, isReel = false)
        assertFalse(d.onFeedScroll(instagram, 6000, homeFeed, index = 5, isReel = true))
        assertEquals("feed reel #5 already counted", d.lastReason)
        assertFalse(d.onTick(instagram, 9000))
    }

    @Test
    fun photosInTheFeedDoNotCount() {
        val d = ReelDetector()
        d.onFeedScroll(instagram, 0, homeFeed, index = 2, isReel = false)
        assertFalse(d.onTick(instagram, 5000))
        assertFalse(d.onFeedScroll(instagram, 6000, homeFeed, index = 3, isReel = false))
    }

    @Test
    fun feedRefreshedFromTheTopCountsItsReelsAgain() {
        val d = ReelDetector()
        d.onFeedScroll(instagram, 0, homeFeed, index = 0, isReel = true)
        assertTrue(d.onTick(instagram, 2000))
        d.onFeedScroll(instagram, 3000, homeFeed, index = 1, isReel = false)
        d.onFeedScroll(instagram, 3500, homeFeed, index = 9, isReel = false)
        // Home tapped: back at the top with new posts.
        d.onFeedScroll(instagram, 10_000, homeFeed, index = 0, isReel = true)
        assertTrue(d.onTick(instagram, 12_000))
    }

    @Test
    fun openingTheFeedReelJustCountedDoesNotCountAgain() {
        val d = ReelDetector()
        d.onFeedScroll(instagram, 0, homeFeed, index = 4, isReel = true)
        assertTrue(d.onTick(instagram, 2000))
        // Tapped it: it opens full screen.
        assertFalse(d.onScreen(instagram, 4000, feedShowing = true, feedKeys = setOf(55)))
        assertEquals("opened the feed reel just counted", d.lastReason)
        // Swiping on from there counts as usual.
        d.onScroll(sample(time = 4200, from = 0, key = 55))
        assertTrue(d.onScroll(sample(time = 8000, from = 1, key = 55)))
    }

    @Test
    fun openingAnotherReelAfterScrollingOnCounts() {
        val d = ReelDetector()
        d.onFeedScroll(instagram, 0, homeFeed, index = 4, isReel = true)
        assertTrue(d.onTick(instagram, 2000))
        d.onFeedScroll(instagram, 3000, homeFeed, index = 7, isReel = false)
        assertTrue(d.onScreen(instagram, 5000, feedShowing = true, feedKeys = setOf(55)))
    }

    // --- From a phone log (v1.18.0) ---

    private val homeList = "com.instagram.android:id/list"

    @Test
    fun aListShowingSeveralPostsIsNeverAReelFeed() {
        val d = ReelDetector()
        // The home feed: several posts at once.
        assertFalse(d.onScroll(sample(time = 0, viewId = homeList, from = 138, to = 143, count = 165, childHeight = 1643, key = 1)))
        assertTrue(d.isMixedList(instagram, homeList))
        // Later one big post fills it, settled: still not a reel feed.
        assertFalse(d.onScroll(sample(time = 2000, viewId = homeList, from = 150, count = 165, childHeight = 2398, containerHeight = 2398, key = 1)))
        assertEquals("not a reel feed", d.lastReason)
        // A profile's posts use the same id.
        assertFalse(d.onScroll(sample(time = 4000, viewId = homeList, from = 0, count = 21, childHeight = 2398, containerHeight = 2398, key = 2)))
    }

    @Test
    fun reelOpenedFromAProfileCountsOnceWhenItsListLoadsLate() {
        val d = ReelDetector()
        assertTrue(d.onScreen(instagram, 0, feedShowing = true, feedKeys = setOf(reelsTab)))
        // Tapped a reel on a profile: the viewer lays out before its list loads...
        assertTrue(d.onScroll(sample(time = 10_000, from = 0, count = -1, key = profileViewer)))
        // ...then jumps to the tapped reel 1.6 s later. Not a swipe.
        assertFalse(d.onScroll(sample(time = 11_600, from = 1, count = 13, key = profileViewer)))
        assertEquals("learned position #1", d.lastReason)
        assertTrue(d.onScroll(sample(time = 12_500, from = 2, count = 13, key = profileViewer)))
    }

    @Test
    fun homeFeedReelCountsAfterStoppingThenScrollingOn() {
        val d = ReelDetector()
        // Scrolling the home feed: posts pass, then it stops on reel #145 and no other events come.
        d.onFeedScroll(instagram, 0, homeFeed, index = 143, isReel = true)
        d.onFeedScroll(instagram, 150, homeFeed, index = 144, isReel = false)
        d.onFeedScroll(instagram, 300, homeFeed, index = 145, isReel = true)
        d.onFeedScroll(instagram, 400, homeFeed, index = 145, isReel = true)
        // Five seconds later, small scrolls while it's still the main post: it counts.
        assertTrue(d.onFeedScroll(instagram, 5400, homeFeed, index = 145, isReel = true))
        assertFalse(d.onFeedScroll(instagram, 5500, homeFeed, index = 145, isReel = true))
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
