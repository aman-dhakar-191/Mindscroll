package io.github.amandhakar.mindscroll.tracker

import android.view.accessibility.AccessibilityEvent

/**
 * A tracker measures one kind of behaviour and records it through a [Recorder]. Everything
 * it records lands in the shared `metric_event` table under its [id], so the home screen,
 * notification, widget and future features (limits, weekly stats) read it the same way.
 *
 * To add a tracker: implement this, then add it to [TrackerRegistry]. Trackers that don't
 * use accessibility events (e.g. screen time from `UsageStatsManager`) leave
 * [watchedPackages] empty and record from their own scheduler instead.
 */
interface Tracker {
    /** Stable id stored in the database. Never change it once released. */
    val id: String

    /** Shown in the UI, e.g. "Reels". */
    val title: String

    /** Packages whose accessibility events this tracker needs. [ALL_PACKAGES] for every app. */
    val watchedPackages: Set<String> get() = emptySet()

    /** [AccessibilityEvent] type mask this tracker needs. */
    val eventTypes: Int get() = 0

    /** Called on the main thread for each matching event. Must be fast. */
    fun onAccessibilityEvent(event: AccessibilityEvent, recorder: Recorder) {}

    /** Friendly name for one of this tracker's item keys (e.g. a package name). */
    fun itemLabel(itemKey: String): String = itemKey

    companion object {
        val ALL_PACKAGES: Set<String> = setOf("*")
    }
}

/** Writes a measurement for the calling tracker. Safe to call from the main thread. */
fun interface Recorder {
    fun record(itemKey: String, value: Long)
}
