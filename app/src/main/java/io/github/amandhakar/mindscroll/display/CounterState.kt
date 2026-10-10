package io.github.amandhakar.mindscroll.display

import io.github.amandhakar.mindscroll.data.DayTotal
import io.github.amandhakar.mindscroll.mascot.Mo
import io.github.amandhakar.mindscroll.mascot.Mood
import java.time.LocalTime

/** Everything the on-screen counters show, for the primary tracker. */
data class CounterState(
    val total: Long,
    /** Per item (app), with display labels, biggest first. */
    val byItem: List<Pair<String, Long>>,
    /** Last 7 days, oldest first, today last. */
    val week: List<DayTotal>,
) {
    val mood: Mood get() = Mood.forCount(total)

    fun line(): String = Mo.line(total, LocalTime.now().hour)
}
