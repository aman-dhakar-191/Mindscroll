package io.github.amandhakar.mindscroll.data

/** Short, readable durations for screen time: "<1m", "34m", "1h 05m". */
object Durations {
    fun format(ms: Long): String {
        val minutes = ms / 60_000
        return when {
            minutes < 1 -> "<1m"
            minutes < 60 -> "${minutes}m"
            else -> "${minutes / 60}h ${(minutes % 60).toString().padStart(2, '0')}m"
        }
    }
}
