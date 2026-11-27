package io.github.amandhakar.mindscroll.tracker.reels

import kotlin.math.abs

/**
 * What the detector needs from one scroll event, taken out of [android.view.accessibility]
 * types so the rules can be unit tested.
 */
data class ScrollSample(
    val packageName: String,
    val timeMs: Long,
    /** Resource name of the scrolled view, e.g. `com.instagram.android:id/clips_viewer_view_pager`. */
    val viewId: String?,
    /** First and last visible item positions, -1 if the app doesn't report them. */
    val fromIndex: Int,
    val toIndex: Int,
    val itemCount: Int,
    /** Height of the scrolled view on screen, px. */
    val containerHeight: Int,
    /** Height of its tallest visible child, px, or 0 if unknown. */
    val tallestChildHeight: Int,
    val screenHeight: Int,
    /** The list scrolls sideways (e.g. Instagram stories), which isn't a reel feed. */
    val horizontal: Boolean = false,
    /**
     * Which list on screen scrolled, or null if unknown. Positions only compare within one list:
     * a reel opened from a profile or the home feed gets a viewer of its own, numbered from
     * wherever it starts, while the Reels tab's viewer can stay open underneath.
     */
    val feedKey: Int? = null,
    /** How far the list moved, px, positive towards later items; null if the app doesn't say. */
    val scrollDeltaY: Int? = null,
)

/**
 * Decides when the user is looking at a reel they hadn't seen yet.
 *
 * A reel feed is a list that fills most of the screen and shows one item at a time. A *visit*
 * starts when a feed appears (opening the Reels tab, tapping a reel in the feed, coming back
 * to the app after a long while) and its first reel counts. After that:
 *
 * - when the app reports item positions, a reel counts once the list settles on an item
 *   further down than any counted in that list, so swiping back and forth doesn't recount;
 * - when it doesn't but says how far the list moved, a swipe counts only if it goes past the
 *   furthest reel reached, so swiping back (and forward again) doesn't count;
 * - when it says neither, each separate swipe gesture counts once.
 *
 * Within a visit each feed view is tracked on its own: a viewer opened on top of another (a
 * reel tapped on a profile or in Explore while the Reels tab stays underneath) counts its
 * first reel and then numbers its reels separately.
 *
 * Reels that play inside a mixed feed (Instagram's home feed, a profile's posts) are counted by
 * [onFeedScroll]: once each, when the user stops on one.
 *
 * Signals: [onScroll], [onScreen] (whether a feed is showing right now, which catches opening a
 * feed before anything scrolls), [onFeedScroll], and [onAppActivity] / [onTick] (any event from
 * the app, so a long silence reads as having left it; a tick can also count a feed reel the user
 * stopped on).
 */
class ReelDetector(
    /** The tracked app for a package, or null to ignore it. */
    private val appFor: (String) -> ReelApp? = ReelApps.known::get,
) {

    /** One feed view's progress within a visit. */
    private class Feed(
        /** Furthest item position counted, -1 if not known yet. */
        var furthestIndex: Int,
        /** When the feed appeared; scrolls right after are its first layout. */
        val openedAt: Long,
        /** Position the list last settled on, -1 if none yet. */
        var lastIndex: Int = -1,
        // Without positions: reels moved through since the feed opened, by swipe direction.
        var page: Int = 0,
        var furthestPage: Int = 0,
        var gestureDelta: Int = 0,
        /** This gesture already moved a whole reel (counted or not). */
        var gestureDone: Boolean = false,
    )

    private class Visit(
        /** When the visit started (its first reel counted). */
        val openedAt: Long,
        var lastActivityAt: Long,
        var lastScrollAt: Long = Long.MIN_VALUE / 2,
        /** When a screen check last found the feed gone, or null while it's showing. */
        var goneAt: Long? = null,
    ) {
        /** Feed views seen in this visit, oldest first. */
        val feeds = LinkedHashMap<Int?, Feed>()

        fun add(key: Int?, feed: Feed): Feed {
            feeds.remove(key)
            feeds[key] = feed
            while (feeds.size > MAX_FEEDS) feeds.remove(feeds.keys.first())
            return feed
        }

        val hasKeys: Boolean get() = feeds.keys.any { it != null }
    }

    private val visits = mutableMapOf<String, Visit>()

    /** Why the last decision was made, for the detection log. */
    var lastReason: String = ""
        private set

    /** Any event from [packageName]. Long silences mean the user left the app. */
    fun onAppActivity(packageName: String, timeMs: Long) {
        val visit = visits[packageName] ?: return
        if (visit.goneAt == null && timeMs - visit.lastActivityAt > AWAY_GAP_MS) {
            visit.goneAt = visit.lastActivityAt
        }
        visit.lastActivityAt = timeMs
    }

    /**
     * Result of looking at the screen: whether a reel feed is showing, at item [index] if the
     * app says which, and which feed views are on screen ([feedKeys], as in [ScrollSample.feedKey]).
     * Returns true if a feed was just opened, which counts its first reel.
     */
    fun onScreen(
        packageName: String,
        timeMs: Long,
        feedShowing: Boolean,
        index: Int? = null,
        feedKeys: Set<Int> = emptySet(),
    ): Boolean {
        if (appFor(packageName) == null) return no("not a tracked app")
        onAppActivity(packageName, timeMs)
        val visit = visits[packageName]
        if (!feedShowing) {
            if (visit != null && visit.goneAt == null) visit.goneAt = timeMs
            return no("no feed on screen")
        }
        if (visit != null) {
            val fresh = feedKeys.filter { it !in visit.feeds }
            val goneFor = visit.goneAt?.let { timeMs - it }
            if (goneFor == null || goneFor < REOPEN_GAP_MS) {
                visit.goneAt = null
                val onTop = fresh.isNotEmpty() && visit.hasKeys
                fresh.forEach { visit.add(it, Feed(furthestIndex = -1, openedAt = timeMs)) }
                // A viewer opened on top of a feed that is still there underneath, or in
                // place of one just closed.
                if (onTop) return openedFromFeedReel(packageName, timeMs) ?: yes("another feed opened")
                return no(if (goneFor == null) "feed still showing" else "feed back after ${goneFor}ms (sheet or dialog)")
            }
            // Back on the exact reel they left (e.g. the screen was off): not a new one.
            if (index != null && index == visit.feeds.values.lastOrNull()?.furthestIndex) {
                visit.goneAt = null
                return no("back on the same reel #$index")
            }
            // Back to a feed view still in place (another app, the screen off, another tab):
            // it shows the reel the user left.
            if (feedKeys.isNotEmpty() && fresh.isEmpty() && goneFor < STALE_MS) {
                visit.goneAt = null
                return no("back on the same feed after ${goneFor / 1000}s")
            }
        }
        val opened = Visit(openedAt = timeMs, lastActivityAt = timeMs)
        if (feedKeys.isEmpty()) opened.add(null, Feed(furthestIndex = index ?: -1, openedAt = timeMs))
        feedKeys.forEach { opened.add(it, Feed(furthestIndex = index ?: -1, openedAt = timeMs)) }
        visits[packageName] = opened
        return openedFromFeedReel(packageName, timeMs) ?: yes("feed opened")
    }

    /** True if [sample] means the user is now on a reel they hadn't reached before. */
    fun onScroll(sample: ScrollSample): Boolean {
        val app = appFor(sample.packageName) ?: return no("not a tracked app")
        if (!isReelFeed(app, sample)) return no("not a reel feed")

        val time = sample.timeMs
        val key = sample.feedKey
        onAppActivity(sample.packageName, time)
        val hasPositions = sample.itemCount > 0 && sample.fromIndex >= 0
        val settled = sample.toIndex < 0 || sample.toIndex == sample.fromIndex

        val visit = visits[sample.packageName]?.takeIf { visit ->
            val goneFor = visit.goneAt?.let { time - it } ?: return@takeIf true
            // A screen check briefly missed the feed, or the user came back to a feed view
            // still in place: scrolling in it continues the visit.
            val continues = goneFor < REOPEN_GAP_MS || (key != null && key in visit.feeds && goneFor < STALE_MS)
            continues.also { if (it) visit.goneAt = null }
        }
        if (visit == null) {
            // Scrolling in a feed no screen check has seen: the reel it lands on is new.
            if (hasPositions && !settled) return no("new visit, mid-swipe")
            val opened = Visit(openedAt = time, lastActivityAt = time, lastScrollAt = time)
            opened.add(key, newFeedByScrolling(sample, hasPositions))
            visits[sample.packageName] = opened
            return openedFromFeedReel(sample.packageName, time) ?: yes("new visit by scrolling")
        }

        val sinceLastScroll = time - visit.lastScrollAt
        visit.lastScrollAt = time

        val feed = visit.feeds[key] ?: if (key == null || !visit.hasKeys) {
            // The feed this visit opened with, scrolling for the first time.
            visit.add(key, Feed(furthestIndex = -1, openedAt = visit.openedAt))
        } else {
            // A viewer opened on top of another feed (a reel tapped on a profile, in Explore
            // or in the home feed) before a screen check noticed it: the reel it shows is new.
            if (hasPositions && !settled) return no("new feed, mid-swipe")
            visit.add(key, newFeedByScrolling(sample, hasPositions))
            return openedFromFeedReel(sample.packageName, time) ?: yes("another feed opened")
        }

        // Scroll events right after opening are the feed laying out, not a swipe.
        val justOpened = time - feed.openedAt < OPEN_SETTLE_MS
        if (!hasPositions) return swipeWithoutPositions(sample, feed, justOpened, sinceLastScroll)

        if (!settled) return no("mid-swipe")
        val index = sample.fromIndex
        val previous = feed.lastIndex
        feed.lastIndex = index
        return when {
            // Opened without knowing the position: learn it. Soon after opening it's still the
            // opening reel (a viewer opened from a profile jumps to the tapped reel once its
            // list loads, which can take over a second); later it's one the user swiped to.
            feed.furthestIndex < 0 -> {
                feed.furthestIndex = index
                if (time - feed.openedAt < FIRST_POSITION_SETTLE_MS) no("learned position #$index") else yes("swiped to #$index")
            }
            index > feed.furthestIndex -> {
                feed.furthestIndex = index
                yes("new reel #$index")
            }
            // Feed rebuilt from the top (pull to refresh, tab tapped again): it jumps there.
            // Swiping back to the top steps through #1 first and doesn't count.
            isRestart(previous, index) && feed.furthestIndex > 0 -> {
                feed.furthestIndex = 0
                yes("feed restarted at #0")
            }
            else -> no("already seen #$index (furthest #${feed.furthestIndex})")
        }
    }

    private fun newFeedByScrolling(sample: ScrollSample, hasPositions: Boolean) = Feed(
        furthestIndex = if (hasPositions) sample.fromIndex else -1,
        openedAt = sample.timeMs,
        lastIndex = if (hasPositions) sample.fromIndex else -1,
        // This gesture brought the reel in, and it's counted.
        gestureDone = true,
    )

    private fun swipeWithoutPositions(sample: ScrollSample, feed: Feed, justOpened: Boolean, sinceLastScroll: Long): Boolean {
        if (justOpened) return no("layout right after opening")
        val newGesture = sinceLastScroll > GESTURE_GAP_MS
        val delta = sample.scrollDeltaY
            // One count per gesture: scroll events keep coming while a swipe animates.
            ?: return if (newGesture) yes("new swipe") else no("same swipe (${sinceLastScroll}ms)")

        if (newGesture) {
            feed.gestureDelta = 0
            feed.gestureDone = false
        }
        feed.gestureDelta += delta
        if (feed.gestureDone) return no("same swipe (${sinceLastScroll}ms)")
        val reelHeight = (if (sample.containerHeight > 0) sample.containerHeight else sample.screenHeight).coerceAtLeast(1)
        // A drag that springs back moves less than this. The first big move of a gesture
        // decides its direction (some pagers jump back at the end of a swipe).
        if (abs(feed.gestureDelta) < reelHeight * SWIPE_FRACTION) return no("small move (${feed.gestureDelta}px)")
        feed.gestureDone = true
        val forward = feed.gestureDelta > 0
        feed.page += if (forward) 1 else -1
        return when {
            feed.page > feed.furthestPage -> {
                feed.furthestPage = feed.page
                yes("swiped forward to a new reel")
            }
            !forward -> no("swiped back (${feed.furthestPage - feed.page} behind the furthest)")
            else -> no("swiped forward to a reel already seen (${feed.furthestPage - feed.page} behind)")
        }
    }

    // --- Reels inside a mixed feed ---

    private class MixedFeed {
        val counted = LinkedHashSet<Int>()
        var lastIndex = -1
    }

    /** A feed reel the user is on, counted once they stay on it for [FEED_REEL_DWELL_MS]. */
    private class Candidate(val feedKey: Int?, val index: Int, val since: Long, var lastSeen: Long)

    private val mixedFeeds = LinkedHashMap<Pair<String, Int?>, MixedFeed>()
    private val candidates = mutableMapOf<String, Candidate>()

    /** When a feed reel was last counted, per app, while the user is still on it. */
    private val feedReelCountedAt = mutableMapOf<String, Long>()

    /**
     * A scroll in a mixed feed (posts of different sizes, not a reel feed). [index] is the
     * position of the item filling most of the list, or null if none does; [isReel] says
     * whether that item is a reel. Returns true when a feed reel counts: the user stayed on it
     * for [FEED_REEL_DWELL_MS] (scrolling straight past doesn't count), and it hadn't counted
     * before in this feed (scrolling back up to it doesn't count again).
     */
    fun onFeedScroll(packageName: String, timeMs: Long, feedKey: Int?, index: Int?, isReel: Boolean): Boolean {
        if (appFor(packageName) == null) return no("not a tracked app")
        onAppActivity(packageName, timeMs)
        val feed = mixedFeeds.getOrPut(packageName to feedKey) {
            if (mixedFeeds.size >= MAX_FEEDS * 2) mixedFeeds.remove(mixedFeeds.keys.first())
            MixedFeed()
        }
        if (index != null) {
            if (isRestart(feed.lastIndex, index)) feed.counted.clear()
            if (index != feed.lastIndex) feedReelCountedAt.remove(packageName)
            feed.lastIndex = index
        }

        val candidate = candidates[packageName]
        if (candidate != null && candidate.feedKey == feedKey && candidate.index == index) {
            candidate.lastSeen = timeMs
            if (timeMs - candidate.since >= FEED_REEL_DWELL_MS) return commit(packageName, candidate, timeMs)
            return no("feed reel #$index, waiting to see if you stay")
        }
        // Moved off the candidate: it counts if the user stayed on it before moving.
        candidates.remove(packageName)
        val stayed = candidate != null &&
            (timeMs - candidate.lastSeen >= FEED_REEL_DWELL_MS || candidate.lastSeen - candidate.since >= FEED_REEL_DWELL_MS)
        val counted = stayed && commit(packageName, candidate!!, timeMs)
        val countReason = lastReason
        if (index != null && isReel && index !in feed.counted) {
            candidates[packageName] = Candidate(feedKey, index, since = timeMs, lastSeen = timeMs)
        }
        return when {
            counted -> yes(countReason)
            index == null -> no("no feed item fills the screen")
            !isReel -> no("feed item #$index isn't a reel")
            index in feed.counted -> no("feed reel #$index already counted")
            else -> no("feed reel #$index, waiting to see if you stay")
        }
    }

    /**
     * Any event from [packageName] other than a scroll: if the user is still stopped on a feed
     * reel, it counts once they've stayed long enough. Also does what [onAppActivity] does.
     */
    fun onTick(packageName: String, timeMs: Long): Boolean {
        onAppActivity(packageName, timeMs)
        val candidate = candidates[packageName] ?: return false
        if (timeMs - candidate.lastSeen > CANDIDATE_TIMEOUT_MS) {
            candidates.remove(packageName)
            return false
        }
        if (timeMs - candidate.since < FEED_REEL_DWELL_MS) return false
        return commit(packageName, candidate, timeMs)
    }

    private fun commit(packageName: String, candidate: Candidate, timeMs: Long): Boolean {
        candidates.remove(packageName)
        val feed = mixedFeeds[packageName to candidate.feedKey] ?: return no("feed gone")
        if (!feed.counted.add(candidate.index)) return no("feed reel #${candidate.index} already counted")
        while (feed.counted.size > MAX_COUNTED_FEED_REELS) feed.counted.remove(feed.counted.first())
        feedReelCountedAt[packageName] = timeMs
        return yes("reel in the feed #${candidate.index}")
    }

    /**
     * Tapping a feed reel opens it full screen: that's the reel just counted in the feed, so
     * opening it doesn't count again. Returns the decision if so, null otherwise.
     */
    private fun openedFromFeedReel(packageName: String, timeMs: Long): Boolean? {
        val countedAt = feedReelCountedAt.remove(packageName) ?: return null
        if (timeMs - countedAt > FEED_REEL_OPEN_MS) return null
        return no("opened the feed reel just counted")
    }

    private fun yes(reason: String): Boolean {
        lastReason = reason
        return true
    }

    private fun no(reason: String): Boolean {
        lastReason = reason
        return false
    }

    /** Whether [sample] scrolled a reel feed (by known id or by shape). */
    fun isReelFeed(sample: ScrollSample): Boolean = appFor(sample.packageName)?.let { isReelFeed(it, sample) } ?: false

    /**
     * Ids of lists seen showing three or more items at once, per app: they're mixed feeds
     * (like Instagram's home feed, id `list`), even when one big post briefly fills them.
     */
    private val mixedListIds = mutableMapOf<String, MutableSet<String>>()

    /** Whether the list with [viewId] in [packageName] has shown itself to be a mixed feed. */
    fun isMixedList(packageName: String, viewId: String): Boolean = mixedListIds[packageName]?.contains(viewId) == true

    private fun isReelFeed(app: ReelApp, sample: ScrollSample): Boolean {
        val id = sample.viewId
        if (id != null && id in app.containerIds) return true
        if (sample.horizontal) return false
        if (id != null) {
            // A reel feed shows one reel, or two mid-swipe; never three.
            if (sample.fromIndex >= 0 && sample.toIndex - sample.fromIndex >= 2) {
                mixedListIds.getOrPut(sample.packageName) { mutableSetOf() }.add(id)
            }
            if (isMixedList(sample.packageName, id)) return false
        }
        return looksLikeReelFeed(sample.containerHeight, sample.tallestChildHeight, sample.screenHeight)
    }

    companion object {
        const val MIN_SCREEN_FRACTION = 0.6
        const val MIN_ITEM_FRACTION = 0.9
        /** Scroll events closer together than this belong to the same swipe. */
        const val GESTURE_GAP_MS = 600L
        /** Scrolls this soon after opening a feed are its first layout, not a swipe. */
        const val OPEN_SETTLE_MS = 1500L
        /** The first position a feed reports this soon after opening is where it opened. */
        const val FIRST_POSITION_SETTLE_MS = 3000L
        /** A feed hidden for less than this (a sheet or dialog on top) is the same visit. */
        const val REOPEN_GAP_MS = 4000L
        /** No events from the app for this long means the user left it (or the screen went off). */
        const val AWAY_GAP_MS = 45_000L
        /** Back on a feed view left alone for longer than this, it may well show something new. */
        const val STALE_MS = 10 * 60_000L
        /** Without positions, a swipe moves at least this share of a reel's height. */
        const val SWIPE_FRACTION = 0.3
        /** Feed views remembered per visit. */
        const val MAX_FEEDS = 8
        /** A jump from this far down straight to the top is the feed restarting. */
        const val RESTART_JUMP = 2

        /** How long the user stays on a reel in a mixed feed before it counts. */
        const val FEED_REEL_DWELL_MS = 1200L
        /** A feed reel opened full screen this soon after counting is the same reel. */
        const val FEED_REEL_OPEN_MS = 20_000L
        /** A feed reel waiting to count is dropped after this long without news. */
        const val CANDIDATE_TIMEOUT_MS = 30_000L
        const val MAX_COUNTED_FEED_REELS = 500

        /** A list going to its top from [RESTART_JUMP] or further down without the steps between. */
        fun isRestart(previous: Int, index: Int): Boolean = index == 0 && previous >= RESTART_JUMP

        /** A list filling most of the screen whose visible item fills the list. */
        fun looksLikeReelFeed(containerHeight: Int, tallestChildHeight: Int, screenHeight: Int): Boolean =
            containerHeight >= screenHeight * MIN_SCREEN_FRACTION &&
                tallestChildHeight >= containerHeight * MIN_ITEM_FRACTION
    }
}
