package io.github.amandhakar.mindscroll.mascot

/** A habit Mo notices from how the phone is being used. Each has its own reaction. */
enum class ScrollPattern(val label: String) {
    /** Reels flicked past almost as fast as they come. */
    TOO_FAST("scrolling too fast"),
    /** Still on the same reel after a while. */
    LINGERING("lingering on one reel"),
    /** Still on the same reel after a very long while: mesmerised. */
    STARING("staring at one reel"),
    /** One long unbroken stretch in an app. */
    MARATHON("a long stretch without a break"),
    /** Left an app and came straight back. */
    QUICK_RETURN("checking again straight away"),
}

/**
 * Spots those habits from reel timestamps and app sessions. Pure logic on one monotonic clock
 * (the caller uses elapsed realtime), so it is exact to test.
 *
 * It never polls. [nextCheckAt] says when something could next become due (a reel left alone long
 * enough, a stretch that has run long enough), so the caller sleeps until then and asks [due].
 * Each pattern fires once per occasion: a reel that is lingered on fires once, not every check.
 */
class MoScrollWatch {
    private val reels = ArrayDeque<Long>()
    private var lastFastAt = NEVER

    private var sessionStartedAt: Long? = null
    private var lastReelAt: Long? = null
    private var lingerFired = false
    private var stareFired = false
    private var marathonsFired = 0

    /** A reel was counted [now]. Returns [ScrollPattern.TOO_FAST] if this is a flick-through. */
    fun onReel(now: Long): ScrollPattern? {
        lastReelAt = now
        lingerFired = false
        stareFired = false
        reels.addLast(now)
        while (reels.isNotEmpty() && now - reels.first() > FAST_WINDOW_MS) reels.removeFirst()
        if (reels.size >= FAST_COUNT && now - lastFastAt >= FAST_COOLDOWN_MS) {
            lastFastAt = now
            return ScrollPattern.TOO_FAST
        }
        return null
    }

    /**
     * A session in [packageName] started [now]. [previousPackage] and [previousEndedAt] describe
     * the session before it, if any. Returns [ScrollPattern.QUICK_RETURN] for a trip straight back.
     */
    fun onSessionStart(now: Long, packageName: String, previousPackage: String?, previousEndedAt: Long?): ScrollPattern? {
        sessionStartedAt = now
        lastReelAt = null
        lingerFired = false
        stareFired = false
        marathonsFired = 0
        reels.clear()
        val quick = previousPackage == packageName && previousEndedAt != null && now - previousEndedAt in 0 until QUICK_RETURN_MS
        return if (quick) ScrollPattern.QUICK_RETURN else null
    }

    fun onSessionEnd() {
        sessionStartedAt = null
        lastReelAt = null
        reels.clear()
    }

    /** The pattern that has become due by [now], or null. A pattern is reported once. */
    fun due(now: Long): ScrollPattern? {
        val started = sessionStartedAt ?: return null
        lastReelAt?.let { last ->
            // Both can be due if the check came late: the longer wins, and the shorter is spent.
            if (!stareFired && now - last >= STARE_AFTER_MS) {
                stareFired = true
                lingerFired = true
                return ScrollPattern.STARING
            }
            if (!lingerFired && now - last >= LINGER_AFTER_MS) {
                lingerFired = true
                return ScrollPattern.LINGERING
            }
        }
        if (now - started >= MARATHON_EVERY_MS * (marathonsFired + 1)) {
            marathonsFired = ((now - started) / MARATHON_EVERY_MS).toInt()
            return ScrollPattern.MARATHON
        }
        return null
    }

    /** Minutes the current stretch has run at [now]. */
    fun minutesIn(now: Long): Long = sessionStartedAt?.let { (now - it) / 60_000L } ?: 0L

    /** When [due] could next return something, or null if nothing is waiting. */
    fun nextCheckAt(): Long? {
        val started = sessionStartedAt ?: return null
        val candidates = mutableListOf(started + MARATHON_EVERY_MS * (marathonsFired + 1))
        lastReelAt?.let { last ->
            if (!lingerFired) candidates += last + LINGER_AFTER_MS
            if (!stareFired) candidates += last + STARE_AFTER_MS
        }
        return candidates.min()
    }

    companion object {
        private const val NEVER = Long.MIN_VALUE / 2

        /** This many reels within [FAST_WINDOW_MS] is flicking through them. */
        const val FAST_COUNT = 5
        const val FAST_WINDOW_MS = 10_000L
        /** Mo doesn't spin again until this long after the last time. */
        const val FAST_COOLDOWN_MS = 30_000L

        /** No new reel this long after the last: lingering, then staring. */
        const val LINGER_AFTER_MS = 45_000L
        const val STARE_AFTER_MS = 120_000L

        /** Every this long in one session, Mo suggests a stretch. */
        const val MARATHON_EVERY_MS = 20 * 60_000L

        /** Back in the same app within this long of leaving it. */
        const val QUICK_RETURN_MS = 45_000L
    }
}
