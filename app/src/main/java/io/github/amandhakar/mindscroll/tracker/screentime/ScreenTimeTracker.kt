package io.github.amandhakar.mindscroll.tracker.screentime

import io.github.amandhakar.mindscroll.tracker.Tracker
import io.github.amandhakar.mindscroll.tracker.reels.ReelApps

/**
 * Time spent in each watched app. Item key = app package, value = milliseconds. It needs no
 * accessibility events of its own: the service's app session watcher, which also drives the
 * live timer, records the time under [ID].
 */
class ScreenTimeTracker : Tracker {
    override val id = ID
    override val title = "Screen time"

    override fun itemLabel(itemKey: String): String = ReelApps.byPackage[itemKey]?.label ?: itemKey

    companion object {
        const val ID = "screen_time"
    }
}
