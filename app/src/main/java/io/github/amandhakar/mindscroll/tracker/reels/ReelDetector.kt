package io.github.amandhakar.mindscroll.tracker.reels

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
)

/**
 * Decides when the user is looking at a reel they hadn't seen yet.
 *
 * A reel feed is a list that fills most of the screen and shows one item at a time. A *visit*
 * starts when a feed appears (opening the Reels tab, tapping a reel in the feed, coming back
 * to the app) and its first reel counts. After that:
 *
 * - when the app reports item positions, a reel counts once the list settles on an item
 *   further down than any counted in this visit, so swiping back and forth doesn't recount;
 * - when it doesn't, each separate swipe gesture counts once.
 *
 * Three signals feed it: [onScroll], [onScreen] (whether a feed is showing right now, which
 * catches opening a feed before anything scrolls) and [onAppActivity] (any event from the app,
 * so a long silence reads as having left it).
 */
class ReelDetector(
    /** The tracked app for a package, or null to ignore it. */
    private val appFor: (String) -> ReelApp? = ReelApps.known::get,
) {

    private class Visit(
        /** Furthest item position counted, -1 if not known yet. */
        var furthestIndex: Int,
        /** When the feed was opened (its first reel counted). */
        val openedAt: Long,
        var lastActivityAt: Long,
        var lastScrollAt: Long = Long.MIN_VALUE / 2,
        /** When a screen check last found the feed gone, or null while it's showing. */
        var goneAt: Long? = null,
    )

    private val visits = mutableMapOf<String, Visit>()

    /** Why the last [onScreen] or [onScroll] call decided what it did, for the detection log. */
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
     * app says which. Returns true if a feed was just opened, which counts its first reel.
     */
    fun onScreen(packageName: String, timeMs: Long, feedShowing: Boolean, index: Int? = null): Boolean {
        if (appFor(packageName) == null) return no("not a tracked app")
        onAppActivity(packageName, timeMs)
        val visit = visits[packageName]
        if (!feedShowing) {
            if (visit != null && visit.goneAt == null) visit.goneAt = timeMs
            return no("no feed on screen")
        }
        if (visit != null) {
            val goneFor = visit.goneAt?.let { timeMs - it }
            if (goneFor == null) return no("feed still showing")
            if (goneFor < REOPEN_GAP_MS) {
                visit.goneAt = null
                return no("feed back after ${goneFor}ms (sheet or dialog)")
            }
            // Back on the exact reel they left (e.g. the screen was off): not a new one.
            if (index != null && index == visit.furthestIndex) {
                visit.goneAt = null
                return no("back on the same reel #$index")
            }
        }
        visits[packageName] = Visit(furthestIndex = index ?: -1, openedAt = timeMs, lastActivityAt = timeMs)
        return yes("feed opened")
    }

    /** True if [sample] means the user is now on a reel they hadn't reached before. */
    fun onScroll(sample: ScrollSample): Boolean {
        val app = appFor(sample.packageName) ?: return no("not a tracked app")
        if (!isReelFeed(app, sample)) return no("not a reel feed")

        val time = sample.timeMs
        onAppActivity(sample.packageName, time)
        val hasPositions = sample.itemCount > 0 && sample.fromIndex >= 0
        val settled = sample.toIndex < 0 || sample.toIndex == sample.fromIndex

        val visit = visits[sample.packageName]?.takeIf { visit ->
            val goneAt = visit.goneAt ?: return@takeIf true
            // A screen check briefly missed the feed; scrolling in it means it's still there.
            (time - goneAt < REOPEN_GAP_MS).also { if (it) visit.goneAt = null }
        }
        if (visit == null) {
            // Scrolling in a feed no screen check has seen: the reel it lands on is new.
            if (hasPositions && !settled) return no("new visit, mid-swipe")
            visits[sample.packageName] = Visit(
                furthestIndex = if (hasPositions) sample.fromIndex else -1,
                openedAt = time,
                lastActivityAt = time,
                lastScrollAt = time,
            )
            return yes("new visit by scrolling")
        }

        val sinceLastScroll = time - visit.lastScrollAt
        visit.lastScrollAt = time
        // Scroll events right after opening are the feed laying out, not a swipe.
        val justOpened = time - visit.openedAt < OPEN_SETTLE_MS

        if (!hasPositions) {
            if (justOpened) return no("layout right after opening")
            // One count per gesture: scroll events keep coming while a swipe animates.
            return if (sinceLastScroll > GESTURE_GAP_MS) yes("new swipe") else no("same swipe (${sinceLastScroll}ms)")
        }

        if (!settled) return no("mid-swipe")
        val index = sample.fromIndex
        return when {
            // Opened without knowing the position: learn it. Right after opening it's still
            // the opening reel; later it's one the user swiped to.
            visit.furthestIndex < 0 -> {
                visit.furthestIndex = index
                if (justOpened) no("learned position #$index") else yes("swiped to #$index")
            }
            index > visit.furthestIndex -> {
                visit.furthestIndex = index
                yes("new reel #$index")
            }
            // Feed rebuilt from the top (pull to refresh, tab tapped again): a fresh reel.
            index == 0 && visit.furthestIndex > 0 -> {
                visit.furthestIndex = 0
                yes("feed restarted at #0")
            }
            else -> no("already seen #$index (furthest #${visit.furthestIndex})")
        }
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

    private fun isReelFeed(app: ReelApp, sample: ScrollSample): Boolean {
        if (sample.viewId != null && sample.viewId in app.containerIds) return true
        if (sample.horizontal) return false
        return looksLikeReelFeed(sample.containerHeight, sample.tallestChildHeight, sample.screenHeight)
    }

    companion object {
        const val MIN_SCREEN_FRACTION = 0.6
        const val MIN_ITEM_FRACTION = 0.9
        /** Scroll events closer together than this belong to the same swipe. */
        const val GESTURE_GAP_MS = 600L
        /** Scrolls this soon after opening a feed are its first layout, not a swipe. */
        const val OPEN_SETTLE_MS = 1500L
        /** A feed hidden for less than this (a sheet or dialog on top) is the same visit. */
        const val REOPEN_GAP_MS = 4000L
        /** No events from the app for this long means the user left it (or the screen went off). */
        const val AWAY_GAP_MS = 45_000L

        /** A list filling most of the screen whose visible item fills the list. */
        fun looksLikeReelFeed(containerHeight: Int, tallestChildHeight: Int, screenHeight: Int): Boolean =
            containerHeight >= screenHeight * MIN_SCREEN_FRACTION &&
                tallestChildHeight >= containerHeight * MIN_ITEM_FRACTION
    }
}
