package io.github.amandhakar.mindscroll.tracker.reels

import android.graphics.Rect
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import io.github.amandhakar.mindscroll.tracker.DetectionLog
import io.github.amandhakar.mindscroll.tracker.TrackedApps
import io.github.amandhakar.mindscroll.tracker.Tracker
import io.github.amandhakar.mindscroll.tracker.TrackerHost

/**
 * Counts reels and shorts watched in the tracked apps, per app. Item key = app package name.
 * Works in any app with a full-screen, one-at-a-time feed; known apps add feed-id hints.
 */
class ReelTracker(
    private val screenHeight: () -> Int = { android.content.res.Resources.getSystem().displayMetrics.heightPixels },
) : Tracker {
    override val id = ID
    override val title = "Reels"
    override val watchedPackages: Set<String> get() = TrackedApps.packages.value

    /**
     * A tracked app with its hints, or null if reels aren't counted in [packageName]: it isn't
     * tracked, or the user keeps it to the timer only (see [TrackedApps.countsReels]).
     */
    private fun appFor(packageName: String): ReelApp? {
        if (!TrackedApps.countsReels(packageName)) return null
        val label = TrackedApps.label(packageName)
        return apps[packageName]?.takeIf { it.label == label }
            ?: ReelApps.forPackage(packageName, label).also { apps[packageName] = it }
    }

    private val apps = mutableMapOf<String, ReelApp>()
    private val detector = ReelDetector(::appFor)
    override val eventTypes = AccessibilityEvent.TYPE_VIEW_SCROLLED or
        AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
        AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED

    private val lastScreenCheck = mutableMapOf<String, Long>()
    private val lastScreenResult = mutableMapOf<String, String>()

    /** Feed view ids seen in reel-feed scrolls, so screen checks find feeds whose ids changed. */
    private val learnedIds = mutableMapOf<String, MutableSet<String>>()

    /** Settled reel-shaped scrolls seen per unknown view id, towards learning it. */
    private val feedIdHits = mutableMapOf<String, Int>()

    /**
     * Learns an unknown view id as a reel feed after [LEARN_AFTER_HITS] settled scrolls that
     * look like one, and forgets it once it shows itself to be a mixed feed. A big post briefly
     * filling the home feed used to teach `list`, after which every return to the home feed
     * or a profile counted as opening a reel.
     */
    private fun learnFeedId(sample: ScrollSample, app: ReelApp) {
        val id = sample.viewId ?: return
        if (id in app.containerIds) return
        val learned = learnedIds.getOrPut(app.packageName) { mutableSetOf() }
        if (detector.isMixedList(app.packageName, id) || !detector.isReelFeed(sample)) {
            feedIdHits.remove("${app.packageName}|$id")
            if (detector.isMixedList(app.packageName, id) && learned.remove(id)) {
                DetectionLog.add("${app.short} forgot feed id ${id.substringAfter(":id/")}: it shows several posts at once")
            }
            return
        }
        val settled = sample.toIndex < 0 || sample.toIndex == sample.fromIndex
        if (id in learned || !settled) return
        val hits = (feedIdHits["${app.packageName}|$id"] ?: 0) + 1
        feedIdHits["${app.packageName}|$id"] = hits
        if (hits >= LEARN_AFTER_HITS && learned.add(id)) {
            DetectionLog.add("${app.short} learned feed id ${id.substringAfter(":id/")}")
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent, host: TrackerHost) {
        val packageName = event.packageName?.toString() ?: return
        val app = appFor(packageName) ?: return
        try {
            handle(event, app, host)
        } catch (e: RuntimeException) {
            // Window content can vanish mid-read; never let that take the service down.
            DetectionLog.add("${app.short} error: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    override fun itemLabel(itemKey: String): String = TrackedApps.label(itemKey)

    private fun handle(event: AccessibilityEvent, app: ReelApp, host: TrackerHost) {
        val time = event.eventTime
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            detector.onAppActivity(app.packageName, time)
        } else if (detector.onTick(app.packageName, time)) {
            // Stayed on a reel in the home feed (or a profile's posts) long enough.
            host.record(app.packageName, 1)
            flushRepeatedSkips()
            lastSkipKind = null
            DetectionLog.add("${app.short} feed → COUNTED: ${detector.lastReason}")
        }
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
            feedKey = source.hashCode(),
            scrollDeltaY = event.verticalDelta(),
        )
        learnFeedId(sample, app)
        if (app.feedReelIdHints.isNotEmpty() && !sample.horizontal && !detector.isReelFeed(sample)) {
            onMixedFeedScroll(event, source, sample, app, host)
        }
        val counted = detector.onScroll(sample)
        if (counted) host.record(app.packageName, 1)
        val line = "${app.short} scroll ${sample.viewId?.substringAfter(":id/") ?: source.className}" +
            " pos ${sample.fromIndex}-${sample.toIndex}/${sample.itemCount}" +
            " h ${sample.containerHeight}/${sample.tallestChildHeight}/${sample.screenHeight}" +
            (if (sample.horizontal) " sideways" else "") +
            " → ${if (counted) "COUNTED" else "skip"}: ${detector.lastReason}"
        // Skips repeat dozens of times per gesture (e.g. scrolling comments); log the first of a
        // run and how many followed, so they don't push everything else out of the log.
        val skipKind = if (counted) null else "${app.short}|${sample.viewId}|${detector.lastReason.replace(digits, "#")}"
        if (skipKind != null && skipKind == lastSkipKind) {
            repeatedSkips++
            return
        }
        flushRepeatedSkips()
        lastSkipKind = skipKind
        DetectionLog.add(line)
    }

    /**
     * A scroll in a list that isn't a reel feed, like Instagram's home feed or a profile's
     * posts: reels play there too, one post at a time. Finds the post filling most of the list
     * and whether it's a reel, and lets the detector count it if the user stops on it.
     */
    private fun onMixedFeedScroll(
        event: AccessibilityEvent,
        source: AccessibilityNodeInfo,
        sample: ScrollSample,
        app: ReelApp,
        host: TrackerHost,
    ) {
        if (sample.containerHeight < sample.screenHeight * MIXED_FEED_MIN_SCREEN_FRACTION) return
        // Positions tell posts apart; without them a post can't be told from the next one.
        if (event.itemCount <= 0 || event.fromIndex < 0) return
        val key = sample.feedKey
        val item = dominantItem(source, event.fromIndex)
        val cache = postCache.getOrPut(key) { PostCache() }
        if (item != null && ReelDetector.isRestart(cache.lastIndex, item.first)) cache.kinds.clear()
        if (item != null) cache.lastIndex = item.first
        val isReel = item?.let { (index, node) ->
            cache.kinds.getOrPut(index) {
                val found = reelMarker(node, app)
                if (found.marker != null || loggedPostKinds < MAX_LOGGED_POST_KINDS) {
                    loggedPostKinds++
                    flushRepeatedSkips()
                    lastSkipKind = null
                    DetectionLog.add(
                        "${app.short} feed post #$index: " +
                            (found.marker?.let { "reel ($it)" } ?: "not a reel (ids ${found.ids.joinToString().ifEmpty { "none" }})"),
                    )
                }
                found.marker != null
            }
        } ?: false
        if (cache.kinds.size > MAX_CACHED_POSTS) cache.kinds.remove(cache.kinds.keys.first())
        val counted = detector.onFeedScroll(app.packageName, sample.timeMs, key, item?.first, isReel)
        if (counted) host.record(app.packageName, 1)
        // Log home-feed decisions when they change (not every scroll event), so a log shows
        // whether a reel was waited on, counted, or passed by.
        val kind = detector.lastReason.replace(digits, "#")
        val changed = detector.lastReason != lastFeedReason && (kind != lastFeedKind || isReel)
        if (counted || changed) {
            flushRepeatedSkips()
            lastSkipKind = null
            DetectionLog.add("${app.short} feed → ${if (counted) "COUNTED" else "skip"}: ${detector.lastReason}")
        }
        lastFeedReason = detector.lastReason
        lastFeedKind = kind
    }

    private var lastFeedReason = ""
    private var lastFeedKind = 

    /** What's known about the posts of one mixed feed: which positions are reels. */
    private class PostCache {
        val kinds = LinkedHashMap<Int, Boolean>()
        var lastIndex = -1
    }

    private val postCache = object : LinkedHashMap<Int?, PostCache>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int?, PostCache>?) = size > 8
    }
    private var loggedPostKinds = 0

    /** The list position and node of the visible item taking up most of [list], if one fills most of it. */
    private fun dominantItem(list: AccessibilityNodeInfo, fromIndex: Int): Pair<Int, AccessibilityNodeInfo>? {
        val listBounds = Rect()
        list.getBoundsInScreen(listBounds)
        if (listBounds.height() <= 0) return null
        var best: AccessibilityNodeInfo? = null
        var bestPosition = -1
        var bestVisible = 0
        var position = 0
        val bounds = Rect()
        for (i in 0 until minOf(list.childCount, MAX_FEED_CHILDREN)) {
            val child = list.getChild(i) ?: continue
            if (!child.isVisibleToUser) continue
            child.getBoundsInScreen(bounds)
            val visible = minOf(bounds.bottom, listBounds.bottom) - maxOf(bounds.top, listBounds.top)
            if (visible > bestVisible) {
                best = child
                bestPosition = position
                bestVisible = visible
            }
            position++
        }
        if (best == null || bestVisible < listBounds.height() * MIXED_FEED_MIN_ITEM_FRACTION) return null
        return (fromIndex + bestPosition) to best
    }

    private class Marker(val marker: String?, val ids: List<String>)

    /**
     * Whether a feed post is a reel: a view inside it whose id has one of the app's reel hints,
     * or whose label names a reel. Also returns the first few ids seen, for the log.
     */
    private fun reelMarker(post: AccessibilityNodeInfo, app: ReelApp): Marker {
        val ids = mutableListOf<String>()
        val queue = ArrayDeque<AccessibilityNodeInfo>().apply { add(post) }
        var visited = 0
        while (queue.isNotEmpty() && visited < MAX_POST_NODES) {
            val node = queue.removeFirst()
            visited++
            val id = node.viewIdResourceName?.substringAfter(":id/")
            if (id != null) {
                if (app.feedReelIdHints.any { it in id }) return Marker("id $id", ids)
                if (ids.size < 8 && id !in ids) ids.add(id)
            }
            val label = node.contentDescription?.toString()
            if (label != null && reelWord.containsMatchIn(label)) return Marker("label", ids)
            for (i in 0 until node.childCount) node.getChild(i)?.let(queue::add)
        }
        return Marker(null, ids)
    }

    private val reelWord = Regex("\\breels?\\b", RegexOption.IGNORE_CASE)

    private var lastSkipKind: String? = null
    private var repeatedSkips = 0
    private val digits = Regex("\\d+")

    private fun flushRepeatedSkips() {
        if (repeatedSkips > 0) DetectionLog.add("   … and $repeatedSkips more like that")
        repeatedSkips = 0
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
        val opened = detector.onScreen(app.packageName, time, feedShowing = feed != null, feedKeys = feed?.keys.orEmpty())
        if (opened) host.record(app.packageName, 1)

        val result = when {
            inFront != app.packageName -> "${inFront.substringAfterLast('.')} in front"
            feed == null -> "no feed"
            else -> "feed ${feed.how}"
        }
        // Checks repeat often; log only changes and counts.
        if (opened || lastScreenResult[app.packageName] != result) {
            lastScreenResult[app.packageName] = result
            flushRepeatedSkips()
            lastSkipKind = null
            DetectionLog.add("${app.short} screen: $result → ${if (opened) "COUNTED" else "skip"}: ${detector.lastReason}")
        }
    }

    /** A reel feed on screen; [keys] tell its views apart (see [ScrollSample.feedKey]). */
    private class Feed(val how: String, val keys: Set<Int> = emptySet())

    /**
     * The reel feed on screen. Apps with known or learned feed ids are matched by id only:
     * matching them by shape also finds full-screen wrappers (like a swipe-between-tabs pager)
     * on every screen, which would make the feed look open everywhere. Apps without ids
     * (Facebook, until a feed scroll teaches one) are matched by shape.
     */
    private fun findFeed(app: ReelApp, root: AccessibilityNodeInfo): Feed? {
        val screen = screenHeight()
        val ids = app.containerIds + learnedIds[app.packageName].orEmpty()
            .filterNot { detector.isMixedList(app.packageName, it) }
        if (ids.isNotEmpty()) {
            // Every matching view: a viewer opened from a profile or the home feed can sit on
            // top of the Reels tab's, and both count as visible.
            val keys = mutableSetOf<Int>()
            var how: String? = null
            for (id in ids) {
                root.findAccessibilityNodeInfosByViewId(id)
                    .filter { it.isVisibleToUser && it.screenHeight() >= screen * ReelDetector.MIN_SCREEN_FRACTION }
                    .forEach {
                        keys += it.hashCode()
                        if (how == null) how = "by id ${id.substringAfter(":id/")}"
                    }
            }
            return how?.let { Feed(if (keys.size > 1) "$it (${keys.size} views)" else it, keys) }
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
                    return Feed("by shape ${node.viewIdResourceName?.substringAfter(":id/") ?: node.className}", setOf(node.hashCode()))
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

    /**
     * How far a list moved down, px, or null if unknown: deltas exist from Android 9, -1 means
     * the app didn't set them, and 0 both ways says nothing.
     */
    private fun AccessibilityEvent.verticalDelta(): Int? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
        val dy = scrollDeltaY
        if (dy == -1 || (dy == 0 && scrollDeltaX <= 0)) return null
        return dy
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
        /** A mixed feed (like Instagram's home feed) fills at least this share of the screen. */
        const val MIXED_FEED_MIN_SCREEN_FRACTION = 0.5
        /** A post counts as the one being watched when it fills this share of the list. */
        const val MIXED_FEED_MIN_ITEM_FRACTION = 0.5
        const val MAX_FEED_CHILDREN = 12
        /** Cap on nodes looked at inside one post to tell if it's a reel. */
        const val MAX_POST_NODES = 80
        const val MAX_CACHED_POSTS = 300
        /** Non-reel posts logged with their ids per run, to tune the hints from a log. */
        const val MAX_LOGGED_POST_KINDS = 20
        /** Settled reel-shaped scrolls needed before an unknown view id counts as a reel feed. */
        const val LEARN_AFTER_HITS = 3
    }
}
