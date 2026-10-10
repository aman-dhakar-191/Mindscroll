package io.github.amandhakar.mindscroll.tracker.reels

import android.graphics.Rect
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import io.github.amandhakar.mindscroll.tracker.Tracker
import io.github.amandhakar.mindscroll.tracker.TrackerHost

/** Counts reels and shorts watched, per app. Item key = app package name. */
class ReelTracker(
    private val detector: ReelDetector = ReelDetector(),
    private val screenHeight: () -> Int = { android.content.res.Resources.getSystem().displayMetrics.heightPixels },
) : Tracker {
    override val id = ID
    override val title = "Reels"
    override val watchedPackages: Set<String> = ReelApps.byPackage.keys
    override val eventTypes = AccessibilityEvent.TYPE_VIEW_SCROLLED or
        AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
        AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED

    private val lastScreenCheck = mutableMapOf<String, Long>()

    override fun onAccessibilityEvent(event: AccessibilityEvent, host: TrackerHost) {
        val packageName = event.packageName?.toString() ?: return
        val app = ReelApps.byPackage[packageName] ?: return
        val time = event.eventTime
        detector.onAppActivity(packageName, time)

        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> onScroll(event, packageName, host)
            // A new screen (activity, dialog): look right away.
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> checkScreen(app, time, host, force = true)
            // Fragments (like the Reels tab) swap without a new window; video apps also send
            // these constantly while playing, so look at most every CHECK_INTERVAL_MS.
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> checkScreen(app, time, host, force = false)
        }
    }

    override fun itemLabel(itemKey: String): String = ReelApps.byPackage[itemKey]?.label ?: itemKey

    private fun onScroll(event: AccessibilityEvent, packageName: String, host: TrackerHost) {
        val source = event.source ?: return
        if (!source.isVisibleToUser) return
        val sample = ScrollSample(
            packageName = packageName,
            timeMs = event.eventTime,
            viewId = source.viewIdResourceName,
            fromIndex = event.fromIndex,
            toIndex = event.toIndex,
            itemCount = event.itemCount,
            containerHeight = source.screenHeight(),
            tallestChildHeight = source.tallestChild()?.screenHeight() ?: 0,
            screenHeight = screenHeight(),
            horizontal = event.scrollsSideways() || source.isHorizontalList(),
        )
        if (detector.onScroll(sample)) host.record(packageName, 1)
    }

    private fun checkScreen(app: ReelApp, time: Long, host: TrackerHost, force: Boolean) {
        val last = lastScreenCheck[app.packageName]
        if (!force && last != null && time - last < CHECK_INTERVAL_MS) return
        lastScreenCheck[app.packageName] = time

        val root = host.activeWindowRoot() ?: return
        val inFront = root.packageName?.toString() ?: return
        if (inFront != app.packageName) {
            detector.onScreen(app.packageName, time, feedShowing = false)
            return
        }
        val feed = findFeed(app, root)
        val opened = detector.onScreen(app.packageName, time, feed != null, feed?.let { visibleIndex(it) })
        if (opened) host.record(app.packageName, 1)
    }

    /** The reel feed on screen: by known view id first, otherwise by shape. */
    private fun findFeed(app: ReelApp, root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        for (id in app.containerIds) {
            root.findAccessibilityNodeInfosByViewId(id).firstOrNull { it.isVisibleToUser }?.let { return it }
        }
        val screen = screenHeight()
        val queue = ArrayDeque<AccessibilityNodeInfo>().apply { add(root) }
        var visited = 0
        while (queue.isNotEmpty() && visited < MAX_NODES) {
            val node = queue.removeFirst()
            visited++
            if (!node.isVisibleToUser) continue
            if (node.isScrollable && !node.isHorizontalList()) {
                val height = node.screenHeight()
                val child = node.tallestChild()?.screenHeight() ?: 0
                if (ReelDetector.looksLikeReelFeed(height, child, screen)) return node
            }
            for (i in 0 until node.childCount) node.getChild(i)?.let(queue::add)
        }
        return null
    }

    /** Position of the reel filling [feed], if the app exposes list positions. */
    private fun visibleIndex(feed: AccessibilityNodeInfo): Int? {
        if (feed.collectionInfo == null) return null
        return feed.tallestChild()?.collectionItemInfo?.rowIndex?.takeIf { it >= 0 }
    }

    /** Lists report their shape: one row and many columns means it scrolls sideways. */
    private fun AccessibilityNodeInfo.isHorizontalList(): Boolean {
        val info = collectionInfo ?: return false
        return info.rowCount <= 1 && info.columnCount > 1
    }

    /** Scroll deltas exist from Android 9; -1 means the app didn't set them. */
    private fun AccessibilityEvent.scrollsSideways(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            scrollDeltaY == 0 && scrollDeltaX != 0 && scrollDeltaX != -1

    private fun AccessibilityNodeInfo.screenHeight(): Int {
        val bounds = Rect()
        getBoundsInScreen(bounds)
        return bounds.height()
    }

    /** Only the first few children: a reel feed keeps at most a couple attached. */
    private fun AccessibilityNodeInfo.tallestChild(): AccessibilityNodeInfo? =
        (0 until minOf(childCount, 4))
            .mapNotNull { i -> getChild(i)?.takeIf { it.isVisibleToUser } }
            .maxByOrNull { it.screenHeight() }

    companion object {
        const val ID = "reels"
        const val CHECK_INTERVAL_MS = 800L
        /** Cap on nodes looked at per screen check, to keep it cheap in big layouts. */
        const val MAX_NODES = 300
    }
}
