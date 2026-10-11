package io.github.amandhakar.mindscroll.mascot

/**
 * Whether Mo is asleep. Mo dozes off [DOZE_MS] after the last reel-app activity, and falls
 * asleep when the screen turns off. Mo wakes when a reel app is used again or the screen turns
 * back on. Pure logic; times are elapsed-realtime ms.
 */
class MoSleep(now: Long) {
    var asleep = false
        private set

    private var lastActiveAt = now
    private var asleepSince = now

    /** A reel app is in use. Returns how long Mo had slept if this woke Mo, else null. */
    fun onActivity(now: Long): Long? {
        lastActiveAt = now
        return wake(now)
    }

    fun onScreenOff(now: Long) {
        fallAsleep(now)
    }

    /** Returns how long Mo had slept if this woke Mo, else null. */
    fun onScreenOn(now: Long): Long? {
        // The screen coming on is someone picking up the phone: Mo wakes and stays up a while.
        lastActiveAt = now
        return wake(now)
    }

    /** Called now and then. Returns true if Mo just dozed off. */
    fun onTick(now: Long): Boolean {
        if (asleep || now - lastActiveAt < DOZE_MS) return false
        fallAsleep(now)
        return true
    }

    private fun fallAsleep(now: Long) {
        if (asleep) return
        asleep = true
        asleepSince = now
    }

    private fun wake(now: Long): Long? {
        if (!asleep) return null
        asleep = false
        return now - asleepSince
    }

    companion object {
        /** No reel-app activity for this long and Mo dozes off. */
        const val DOZE_MS = 15 * 60_000L
        /** Sleeps at least this long are worth a yawn and a word on waking. */
        const val LONG_SLEEP_MS = 10 * 60_000L
    }
}
