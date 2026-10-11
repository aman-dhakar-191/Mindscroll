package io.github.amandhakar.mindscroll.service

/** One continuous stretch of using an app, for the live timer. Times are elapsed-realtime ms. */
data class AppSession(val packageName: String, val startedAt: Long)

/**
 * Decides when an app session starts and ends, from two signals: events from the app (it's
 * in use) and a once-a-second check of which app is in front. Pure logic, so it's testable;
 * [AppSessionWatcher] feeds it on the phone.
 */
class AppSessions(private val packages: Set<String>) {
    var current: AppSession? = null
        private set

    private var lastSeenAt = 0L
    private var ended: AppSession? = null
    private var endedAt = 0L

    /** Any event from [packageName]: it's in front and being used. */
    fun onAppEvent(packageName: String, now: Long) {
        if (packageName !in packages) return
        lastSeenAt = now
        if (current?.packageName == packageName) return
        // Back within a few seconds (replied to a message, glanced at another app): carry on.
        val resumed = ended?.takeIf { it.packageName == packageName && now - endedAt < RESUME_MS }
        current = resumed ?: AppSession(packageName, now)
        ended = null
    }

    /**
     * The once-a-second check: [front] is the package in front, or null if unknown right now;
     * [interactive] is false while the screen is off.
     */
    fun onCheck(front: String?, interactive: Boolean, now: Long) {
        val session = current ?: return
        when {
            !interactive -> end(now)
            front == session.packageName -> lastSeenAt = now
            front != null && front in packages -> onAppEvent(front, now)
            // Another app in front (or nothing known for a long while): give it a moment.
            front != null && now - lastSeenAt >= AWAY_MS -> end(now)
            front == null && now - lastSeenAt >= UNKNOWN_MS -> end(now)
        }
    }

    private fun end(now: Long) {
        ended = current
        endedAt = now
        current = null
    }

    companion object {
        /** Another app in front this long ends the session (share sheets and the like are shorter). */
        const val AWAY_MS = 3_000L
        /** Coming back within this long continues the same session and timer. */
        const val RESUME_MS = 10_000L
        /** No idea what's in front for this long: assume they left. */
        const val UNKNOWN_MS = 30_000L
    }
}
