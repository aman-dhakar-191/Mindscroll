package io.github.amandhakar.mindscroll.display

import io.github.amandhakar.mindscroll.data.Settings.AppInfo
import io.github.amandhakar.mindscroll.data.Settings.TimerReveal

/** What fills the island's information slot right now. There is never more than one thing, and it never scrolls. */
enum class IslandContent { COUNTER, TIMER, NOTHING }

/**
 * The rules for what the camera island shows, in one place. Pure, so they are tested and the
 * overlay only does what this says.
 *
 * - **Counter**: the reel counter, always.
 * - **Timer**: the session timer, always, or only now and then (the timer-only [TimerReveal]).
 * - **Both**: the reel counter, with the session timer showing in its place every so often.
 * - **Nothing**: just Mo.
 *
 * An app that doesn't count reels never shows the counter: its Counter and Both fall back to Timer.
 */
object IslandInfo {
    /** [choice] after the "no counter without reels" rule. */
    fun effective(choice: AppInfo, countsReels: Boolean): AppInfo =
        if (!countsReels && (choice == AppInfo.COUNTER || choice == AppInfo.BOTH)) AppInfo.TIMER else choice

    /** What shows now. [timerUp] is whether this is one of the moments the timer is brought up. */
    fun content(choice: AppInfo, countsReels: Boolean, timerOnly: TimerReveal, timerUp: Boolean): IslandContent =
        when (effective(choice, countsReels)) {
            AppInfo.COUNTER -> IslandContent.COUNTER
            AppInfo.NOTHING -> IslandContent.NOTHING
            AppInfo.TIMER -> if (timerOnly == TimerReveal.ALWAYS || timerUp) IslandContent.TIMER else IslandContent.NOTHING
            AppInfo.BOTH -> if (timerUp) IslandContent.TIMER else IslandContent.COUNTER
        }

    /**
     * How often the timer is brought up for this choice, in ms; null when it isn't (the counter
     * or nothing, or a timer that is always on). Both uses [counterTimer], and never "always": a
     * single slot can't hold the counter and the timer at once, so it falls back to five minutes.
     */
    fun rhythmMs(choice: AppInfo, countsReels: Boolean, timerOnly: TimerReveal, counterTimer: TimerReveal): Long? =
        when (effective(choice, countsReels)) {
            AppInfo.TIMER -> timerOnly.intervalMs
            AppInfo.BOTH -> (counterTimer.takeIf { it != TimerReveal.ALWAYS } ?: TimerReveal.EVERY_5_MIN).intervalMs
            AppInfo.COUNTER, AppInfo.NOTHING -> null
        }

    /** Milliseconds until the next multiple of [intervalMs] of the session's [elapsedMs]; a full interval from the start. */
    fun nextRevealInMs(intervalMs: Long, elapsedMs: Long): Long = intervalMs - (elapsedMs.coerceAtLeast(0L) % intervalMs)

    /** Whether the session timer is part of this choice at all (so it needs to be running). */
    fun usesTimer(choice: AppInfo, countsReels: Boolean): Boolean =
        effective(choice, countsReels).let { it == AppInfo.TIMER || it == AppInfo.BOTH }
}
