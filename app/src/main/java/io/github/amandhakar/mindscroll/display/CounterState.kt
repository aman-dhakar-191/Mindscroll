package io.github.amandhakar.mindscroll.display

import io.github.amandhakar.mindscroll.data.DayTotal
import io.github.amandhakar.mindscroll.data.ItemTotal
import io.github.amandhakar.mindscroll.mascot.Mo
import io.github.amandhakar.mindscroll.mascot.Mood
import java.time.LocalTime

/** One app's numbers for today. */
data class AppStat(val label: String, val reels: Long, val timeMs: Long) {
    companion object {
        /**
         * One row per app that has reels or screen time today, most reels first, then most
         * time. [label] turns a package name into a display name.
         */
        fun merge(reels: List<ItemTotal>, time: List<ItemTotal>, label: (String) -> String): List<AppStat> {
            val reelsBy = reels.associate { it.itemKey to it.total }
            val timeBy = time.associate { it.itemKey to it.total }
            return (reelsBy.keys + timeBy.keys)
                .map { key -> AppStat(label(key), reelsBy[key] ?: 0, timeBy[key] ?: 0) }
                .sortedWith(compareByDescending<AppStat> { it.reels }.thenByDescending { it.timeMs })
        }
    }
}

/** Everything the on-screen counters show. */
data class CounterState(
    /** Today's reels. */
    val total: Long,
    /** Per app: reels and screen time. */
    val apps: List<AppStat>,
    /** Reels for the last 7 days, oldest first, today last. */
    val week: List<DayTotal>,
    /** How Mo feels: climbs with reels, calms down during breaks (see Fatigue). */
    val mood: Mood = Mood.forCount(total),
) {
    /** Mo has calmed down from a break. */
    val rested: Boolean get() = Mo.isRested(total, mood)

    val screenTimeMs: Long get() = apps.sumOf { it.timeMs }

    fun line(): String = Mo.line(total, LocalTime.now().hour, mood, rested)
}
