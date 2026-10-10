package io.github.amandhakar.mindscroll.tracker.reels

import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import io.github.amandhakar.mindscroll.tracker.Recorder
import io.github.amandhakar.mindscroll.tracker.Tracker

/** Counts reels and shorts watched, per app. Item key = app package name. */
class ReelTracker(
    private val detector: ReelDetector = ReelDetector(),
    private val screenHeight: () -> Int = { android.content.res.Resources.getSystem().displayMetrics.heightPixels },
) : Tracker {
    override val id = ID
    override val title = "Reels"
    override val watchedPackages: Set<String> = ReelApps.byPackage.keys
    override val eventTypes = AccessibilityEvent.TYPE_VIEW_SCROLLED

    override fun onAccessibilityEvent(event: AccessibilityEvent, recorder: Recorder) {
        if (event.eventType != AccessibilityEvent.TYPE_VIEW_SCROLLED) return
        val packageName = event.packageName?.toString() ?: return
        if (packageName !in watchedPackages) return
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
            tallestChildHeight = source.tallestChildHeight(),
            screenHeight = screenHeight(),
        )
        if (detector.onScroll(sample)) recorder.record(packageName, 1)
    }

    override fun itemLabel(itemKey: String): String = ReelApps.byPackage[itemKey]?.label ?: itemKey

    private fun AccessibilityNodeInfo.screenHeight(): Int {
        val bounds = Rect()
        getBoundsInScreen(bounds)
        return bounds.height()
    }

    /** Only the first few children: a reel feed keeps at most a couple attached. */
    private fun AccessibilityNodeInfo.tallestChildHeight(): Int =
        (0 until minOf(childCount, 4)).maxOfOrNull { i ->
            getChild(i)?.takeIf { it.isVisibleToUser }?.screenHeight() ?: 0
        } ?: 0

    companion object {
        const val ID = "reels"
    }
}
