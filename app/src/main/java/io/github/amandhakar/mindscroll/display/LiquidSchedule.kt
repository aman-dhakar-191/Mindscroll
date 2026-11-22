package io.github.amandhakar.mindscroll.display

/**
 * When Mo is due to come out of the island (the liquid island experiment). Pure, so it is tested.
 *
 * Mo is due once the chosen time has passed since the last time it came out, counted in wall-clock
 * time, not in time spent in apps. The overlay looks every few seconds while a tracked app is open
 * and brings Mo out when it is due.
 */
object LiquidSchedule {
    /**
     * Milliseconds until Mo is due: 0 when it is due now. Never having come out (0), or a last time
     * in the future (the phone's clock was set back), counts as due, so a clock change can't keep
     * Mo in for hours.
     */
    fun dueInMs(lastAtMs: Long, everyMs: Long, nowMs: Long): Long {
        if (lastAtMs <= 0L || lastAtMs > nowMs) return 0L
        return (lastAtMs + everyMs - nowMs).coerceAtLeast(0L)
    }
}
