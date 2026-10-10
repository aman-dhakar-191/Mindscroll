package io.github.amandhakar.mindscroll.tracker

import io.github.amandhakar.mindscroll.tracker.reels.ReelTracker

/** Every tracker in the app. New trackers are added here. */
object TrackerRegistry {
    val reels = ReelTracker()

    val all: List<Tracker> = listOf(reels)

    /** The tracker whose daily total the notification, overlay and widget show. */
    val primary: Tracker = reels

    /** Packages the accessibility service should listen to, or null for every app. */
    fun watchedPackages(): Array<String>? {
        val packages = all.flatMap { it.watchedPackages }.toSet()
        return if (Tracker.ALL_PACKAGES.first() in packages) null else packages.toTypedArray()
    }

    fun eventTypes(): Int = all.fold(0) { mask, tracker -> mask or tracker.eventTypes }
}
