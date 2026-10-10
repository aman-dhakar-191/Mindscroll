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
)

/**
 * Decides whether a scroll moved to a new reel.
 *
 * A reel feed is a list that fills most of the screen and shows one item at a time. When the
 * app reports item positions, a reel counts once the list settles on a single item further
 * down than any seen in this session, so swiping back and forth over the same reels doesn't
 * count again. When it doesn't report positions, each separate swipe gesture counts once.
 */
class ReelDetector(private val apps: Map<String, ReelApp> = ReelApps.byPackage) {

    private class Session(
        var viewId: String?,
        var furthestIndex: Int,
        var lastScrollAt: Long,
    )

    private val sessions = mutableMapOf<String, Session>()

    /** True if [sample] means the user is now on a reel they hadn't reached before. */
    fun onScroll(sample: ScrollSample): Boolean {
        val app = apps[sample.packageName] ?: return false
        if (!isReelFeed(app, sample)) return false

        val session = sessions[sample.packageName]
            ?.takeIf { it.viewId == sample.viewId && sample.timeMs - it.lastScrollAt < SESSION_GAP_MS }
            ?: Session(sample.viewId, furthestIndex = -1, lastScrollAt = Long.MIN_VALUE / 2)
                .also { sessions[sample.packageName] = it }
        val sinceLastScroll = sample.timeMs - session.lastScrollAt
        session.lastScrollAt = sample.timeMs

        val hasPositions = sample.itemCount > 0 && sample.fromIndex >= 0
        if (!hasPositions) {
            // One count per gesture: scroll events keep coming while a swipe animates.
            return sinceLastScroll > GESTURE_GAP_MS
        }

        val settled = sample.toIndex < 0 || sample.toIndex == sample.fromIndex
        if (!settled) return false
        val index = sample.fromIndex
        return when {
            // First look at this feed, or it was rebuilt from the top: start counting from here.
            session.furthestIndex < 0 || index == 0 -> {
                session.furthestIndex = index
                false
            }
            index > session.furthestIndex -> {
                session.furthestIndex = index
                true
            }
            else -> false
        }
    }

    private fun isReelFeed(app: ReelApp, sample: ScrollSample): Boolean {
        if (sample.viewId != null && sample.viewId in app.containerIds) return true
        val fillsScreen = sample.containerHeight >= sample.screenHeight * MIN_SCREEN_FRACTION
        val oneItemFillsIt = sample.tallestChildHeight >= sample.containerHeight * MIN_ITEM_FRACTION
        return fillsScreen && oneItemFillsIt
    }

    companion object {
        const val MIN_SCREEN_FRACTION = 0.6
        const val MIN_ITEM_FRACTION = 0.9
        /** Scroll events closer together than this belong to the same swipe. */
        const val GESTURE_GAP_MS = 600L
        /** After this long without scrolling, the next scroll starts a new session. */
        const val SESSION_GAP_MS = 10 * 60_000L
    }
}
