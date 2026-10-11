package io.github.amandhakar.mindscroll.service

/**
 * One continuous stretch of using an app. Times are elapsed-realtime ms. [timerBase] is where
 * the live timer counts from: earlier than [startedAt] by the time already spent in the app
 * today, so reopening the app resumes the timer instead of restarting it at 0:00.
 */
data class AppSession(
    val packageName: String,
    val startedAt: Long,
    val timerBase: Long = startedAt,
    /** Which open of this app today this is: 1 for the first. */
    val openNumber: Int = 1,
)

/** Time spent in an app that hasn't been saved yet. */
data class AppUsage(val packageName: String, val ms: Long)

/**
 * Decides when an app session starts and ends, from two signals: events from the app (it's
 * in use) and a once-a-second check of which app is in front. Pure logic, so it's testable;
 * [AppSessionWatcher] feeds it on the phone.
 *
 * It also keeps a ledger of time used, for screen time: only up to the last moment the app
 * was seen in front, so the grace period after leaving isn't counted. [drainUsage] hands out
 * what's accumulated since the last call, and today's running total per app sets each new
 * session's [AppSession.timerBase]. [today] gives the current day, so totals reset overnight.
 */
class AppSessions(
    private val packages: Set<String>,
    private val today: () -> Long,
) {
    var current: AppSession? = null
        private set

    private var lastSeenAt = 0L
    /** Time up to which the current session's usage was already handed out. */
    private var countedUntil = 0L
    private val pending = mutableListOf<AppUsage>()

    private var day = today()
    private val usedToday = mutableMapOf<String, Long>()
    private val opensToday = mutableMapOf<String, Int>()

    /** Any event from [packageName]: it's in front and being used. */
    fun onAppEvent(packageName: String, now: Long) {
        if (packageName !in packages) return
        rollOverDay(now)
        if (current?.packageName == packageName) {
            lastSeenAt = now
            return
        }
        // Switching straight from another watched app: close that one first.
        if (current != null) end()
        val openNumber = (opensToday[packageName] ?: 0) + 1
        opensToday[packageName] = openNumber
        current = AppSession(
            packageName,
            startedAt = now,
            timerBase = now - (usedToday[packageName] ?: 0),
            openNumber = openNumber,
        )
        lastSeenAt = now
        countedUntil = now
    }

    /**
     * The once-a-second check: [front] is the package in front, or null if unknown right now;
     * [interactive] is false while the screen is off.
     */
    fun onCheck(front: String?, interactive: Boolean, now: Long) {
        val session = current ?: return
        rollOverDay(now)
        when {
            !interactive -> end()
            front == session.packageName -> lastSeenAt = now
            front != null && front in packages -> onAppEvent(front, now)
            // Another app in front (or nothing known for a long while): give it a moment.
            front != null && now - lastSeenAt >= AWAY_MS -> end()
            front == null && now - lastSeenAt >= UNKNOWN_MS -> end()
        }
    }

    /**
     * Today's time per app as saved before this started (e.g. read from the database when the
     * service starts). Added to what's been used since; a session already running moves its
     * timer back by the same amount.
     */
    fun addSavedToday(saved: Map<String, Long>) {
        saved.forEach { (packageName, ms) -> usedToday[packageName] = (usedToday[packageName] ?: 0) + ms }
        current?.let { session ->
            val extra = saved[session.packageName] ?: return
            current = session.copy(timerBase = session.timerBase - extra)
        }
    }

    /** Time used since the last call, per app, including sessions that have ended since. */
    fun drainUsage(): List<AppUsage> {
        current?.let { session -> takeCurrentUsage(session.packageName) }
        return pending.toList().also { pending.clear() }
    }

    private fun takeCurrentUsage(packageName: String) {
        val ms = lastSeenAt - countedUntil
        if (ms > 0) {
            pending += AppUsage(packageName, ms)
            usedToday[packageName] = (usedToday[packageName] ?: 0) + ms
        }
        countedUntil = maxOf(countedUntil, lastSeenAt)
    }

    /** At midnight, today's totals start over; a running session's timer restarts at 0:00. */
    private fun rollOverDay(now: Long) {
        val newDay = today()
        if (newDay == day) return
        current?.let { takeCurrentUsage(it.packageName) }
        day = newDay
        usedToday.clear()
        opensToday.clear()
        current = current?.copy(timerBase = now)
    }

    private fun end() {
        val session = current ?: return
        takeCurrentUsage(session.packageName)
        current = null
    }

    companion object {
        /** Another app in front this long ends the session (share sheets and the like are shorter). */
        const val AWAY_MS = 3_000L
        /** No idea what's in front for this long: assume they left. */
        const val UNKNOWN_MS = 30_000L
    }
}
