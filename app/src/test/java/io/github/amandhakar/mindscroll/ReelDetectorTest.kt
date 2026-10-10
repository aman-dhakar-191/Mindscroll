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
    ) = ScrollSample(pkg, time, viewId, from, to, count, containerHeight, childHeight, screen)

    /** Number of samples the detector counted as a new reel. */
    private fun ReelDetector.count(vararg samples: ScrollSample) = samples.count { onScroll(it) }

    @Test
    fun countsEachNewReelOnceListSettles() {
        val d = ReelDetector()
        val counted = d.count(
            sample(time = 0, from = 0), // opening the feed is a baseline, not a swipe
            sample(time = 1000, from = 0, to = 1), // mid-swipe: two reels visible
            sample(time = 1100, from = 1),
            sample(time = 1150, from = 1), // repeated event for the same reel
            sample(time = 3000, from = 2),
        )
        assertEquals(2, counted)
    }

    @Test
    fun swipingBackAndForthDoesNotRecount() {
        val d = ReelDetector()
        val counted = d.count(
            sample(time = 0, from = 3),
            sample(time = 1000, from = 4), // new
            sample(time = 2000, from = 3), // back
            sample(time = 3000, from = 4), // already seen
            sample(time = 4000, from = 5), // new
        )
        assertEquals(2, counted)
    }

    @Test
    fun feedRebuiltFromTopStartsOver() {
        val d = ReelDetector()
        d.count(sample(time = 0, from = 10), sample(time = 1000, from = 11))
        assertFalse(d.onScroll(sample(time = 2000, from = 0)))
        assertTrue(d.onScroll(sample(time = 3000, from = 1)))
    }

    @Test
    fun longPauseStartsNewSession() {
        val d = ReelDetector()
        d.count(sample(time = 0, from = 10), sample(time = 1000, from = 11))
        val later = 1000 + ReelDetector.SESSION_GAP_MS + 1
        assertFalse(d.onScroll(sample(time = later, from = 2))) // baseline in a new feed
        assertTrue(d.onScroll(sample(time = later + 1000, from = 3)))
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
        // A horizontal stories row: one item fills it, but it's a strip of the screen.
        val counted = d.count(
            sample(pkg = facebook, viewId = null, time = 0, containerHeight = 400, childHeight = 400),
            sample(pkg = facebook, viewId = null, time = 2000, containerHeight = 400, childHeight = 400),
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
        assertEquals(1, counted)
    }

    @Test
    fun ignoresOtherApps() {
        val d = ReelDetector()
        assertFalse(d.onScroll(sample(pkg = "com.whatsapp", viewId = null, time = 0)))
        assertFalse(d.onScroll(sample(pkg = "com.whatsapp", viewId = null, time = 5000)))
    }

    @Test
    fun appsAreTrackedSeparately() {
        val d = ReelDetector()
        val youtube = ReelApps.all.first { it.label.startsWith("YouTube") }
        val shortsId = youtube.containerIds.first()
        val counted = d.count(
            sample(time = 0, from = 5),
            sample(pkg = youtube.packageName, viewId = shortsId, time = 100, from = 0),
            sample(time = 1000, from = 6), // Instagram session unaffected by YouTube
            sample(pkg = youtube.packageName, viewId = shortsId, time = 1100, from = 1),
        )
        assertEquals(2, counted)
    }
}
