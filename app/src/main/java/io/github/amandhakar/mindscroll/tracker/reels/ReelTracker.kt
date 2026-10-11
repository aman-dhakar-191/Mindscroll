package io.github.amandhakar.mindscroll.tracker.reels

import android.graphics.Rect
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import io.github.amandhakar.mindscroll.tracker.DetectionLog
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
    private val lastScreenResult = mutableMapOf<String, String>()

    /** Feed view ids seen in reel-feed scrolls, so screen checks find feeds whose ids changed. */
    private val learnedIds = mutableMapOf<String, MutableSet<String>>()

    override fun onAccessibilityEvent(event: AccessibilityEvent, host: TrackerHost) {
        val packageName = event.packageName?.toString() ?: return
        val app = ReelApps.byPackage[packageName] ?: return
        try {
            handle(event, app, host)
        } catch (e: RuntimeException) {
            // Window content can vanish mid-read; never let that take the service down.
            DetectionLog.add("${app.short} error: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    override fun itemLabel(itemKey: String): String = ReelApps.byPackage[itemKey]?.label ?: itemKey

    private fun handle(event: AccessibilityEvent, app: ReelApp, host: TrackerHost) {
        val time = event.eventTime
        detector.onAppActivity(app.packageName, time)
        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> onScroll(event, app, host)
            // A new screen (activity, dialog): look right away.
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> checkScreen(app, time, host, force = true)
            // Fragments (like the Reels tab) swap without a new window; video apps also send
            // these constantly while playing, so look at most every CHECK_INTERVAL_MS.
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> checkScreen(app, time, host, force = false)
        }
    }

    private fun onScroll(event: AccessibilityEvent, app: ReelApp, host: TrackerHost) {
        val source = event.source ?: return
        if (!source.isVisibleToUser) return
        val sample = ScrollSample(
            packageName = app.packageName,
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
        if (sample.viewId != null && sample.viewId !in app.containerIds && detector.isReelFeed(sample)) {
            if (learnedIds.getOrPut(app.packageName) { mutableSetOf() }.add(sample.viewId)) {
                DetectionLog.add("${app.short} learned feed id ${sample.viewId.substringAfter(":id/")}")
            }
        }
        val counted = detector.onScroll(sample)
        if (counted) host.record(app.packageName, 1)
        DetectionLog.add(
            "${app.short} scroll ${sample.viewId?.substringAfter(":id/") ?: source.className}" +
                " pos ${sample.fromIndex}-${sample.toIndex}/${sample.itemCount}" +
                " h ${sample.containerHeight}/${sample.tallestChildHeight}/${sample.screenHeight}" +
                (if (sample.horizontal) " sideways" else "") +
                " → ${if (counted) "COUNTED" else "skip"}: ${detector.lastReason}",
        )
    }

    private fun checkScreen(app: ReelApp, time: Long, host: TrackerHost, force: Boolean) {
        val last = lastScreenCheck[app.packageName]
        if (!force && last != null && time - last < CHECK_INTERVAL_MS) return
        lastScreenCheck[app.packageName] = time

        val root = host.activeWindowRoot() ?: return
        val inFront = root.packageName?.toString() ?: return
        val feed = if (inFront == app.packageName) findFeed(app, root) else null
        // Screen checks don't pass a list position: positions read from the screen and from
        // scroll events don't always agree, and a wrong one can block counting.
        val opened = detector.onScreen(app.packageName, time, feedShowing = feed != null)
        if (opened) host.record(app.packageName, 1)

        val result = when {
            inFront != app.packageName -> "${inFront.substringAfterLast('.')} in front"
            feed == null -> "no feed"
            else -> "feed ${feed.how}"
        }
        // Checks repeat often; log only changes and counts.
        if (opened || lastScreenResult[app.packageName] != result) {
            lastScreenResult[app.packageName] = result
            DetectionLog.add("${app.short} screen: $result → ${if (opened) "COUNTED" else "skip"}: ${detector.lastReason}")
        }
    }

    private class Feed(val how: String)

    /**
     * The reel feed on screen. Apps with known or learned feed ids are matched by id only:
     * matching them by shape also finds full-screen wrappers (like a swipe-between-tabs pager)
     * on every screen, which would make the feed look open everywhere. Apps without ids
     * (Facebook, until a feed scroll teaches one) are matched by shape.
     */
    private fun findFeed(app: ReelApp, root: AccessibilityNodeInfo): Feed? {
        val screen = screenHeight()
        val ids = app.containerIds + learnedIds[app.packageName].orEmpty()
        if (ids.isNotEmpty()) {
            for (id in ids) {
                val found = root.findAccessibilityNodeInfosByViewId(id).any {
                    it.isVisibleToUser && it.screenHeight() >= screen * ReelDetector.MIN_SCREEN_FRACTION
                }
                if (found) return Feed("by id ${id.substringAfter(":id/")}")
            }
            return null
        }
        val queue = ArrayDeque<AccessibilityNodeInfo>().apply { add(root) }
        var visited = 0
        while (queue.isNotEmpty() && visited < MAX_NODES) {
            val node = queue.removeFirst()
            visited++
            if (!node.isVisibleToUser) continue
            if (node.isScrollable && !node.isHorizontalList()) {
                val height = node.screenHeight()
                val child = node.tallestChild()?.screenHeight() ?: 0
                if (ReelDetector.looksLikeReelFeed(height, child, screen)) {
                    return Feed("by shape ${node.viewIdResourceName?.substringAfter(":id/") ?: node.className}")
                }
            }
            for (i in 0 until node.childCount) node.getChild(i)?.let(queue::add)
        }
        return null
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
        const val MAX_NODES = 200
    }
}
