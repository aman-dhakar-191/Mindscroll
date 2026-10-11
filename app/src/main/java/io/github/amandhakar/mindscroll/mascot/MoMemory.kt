package io.github.amandhakar.mindscroll.mascot

/** What Mo knows about recent days, gathered from the database. */
data class MemoryFacts(
    /** Reels today so far. */
    val today: Long,
    /** Reels yesterday up to this same time of day, or null without data for yesterday. */
    val yesterdaySoFar: Long?,
    /** Reels yesterday in total, or null without data. */
    val yesterdayTotal: Long?,
    /** Reels per day before today, oldest first, starting with the first day that has data. */
    val pastDays: List<Long>,
    /** Hour of day (0-23) with the most reels over the last week, if there's enough to tell. */
    val peakHour: Int?,
    /** App with the most reels this week, if any. */
    val favouriteApp: String?,
)

/** Turns [MemoryFacts] into things Mo remembers, in Mo's voice. */
object MoMemory {
    /** A day under this many reels is a calm day (Mo never got wobbly). */
    val CALM_DAY = Mood.WOBBLY.from

    fun insights(facts: MemoryFacts): List<String> = buildList {
        comparedToYesterday(facts)?.let(::add)
        calmStreak(facts.pastDays)?.let { add("$it calm days in a row. I've never been so well rested.") }
        facts.peakHour?.let { add("We scroll most around ${hourName(it)}. I get sleepy then too.") }
        facts.favouriteApp?.let { add("$it is our usual spot this week.") }
    }

    /** One line for the start of the day, or null if there's nothing to say yet. */
    fun dayStart(facts: MemoryFacts): String? {
        val yesterday = facts.yesterdayTotal ?: return null
        val streak = calmStreak(facts.pastDays)
        return when {
            streak != null -> "New day. That's $streak calm days behind us. Let's keep it cozy."
            yesterday == 0L -> "New day. Yesterday was a zero. My favorite number."
            else -> "New day. Yesterday was $yesterday reels. Fresh count, fresh blob."
        }
    }

    private fun comparedToYesterday(facts: MemoryFacts): String? {
        val then = facts.yesterdaySoFar ?: return null
        val diff = then - facts.today
        return when {
            diff >= 5 -> "$diff fewer than yesterday at this time. I noticed."
            diff <= -5 -> "${-diff} more than yesterday by now. Just saying. Gently."
            else -> "Right on pace with yesterday."
        }
    }

    /** Calm days in a row up to yesterday, if it's at least two. */
    private fun calmStreak(pastDays: List<Long>): Int? {
        val streak = pastDays.asReversed().takeWhile { it < CALM_DAY }.size
        return streak.takeIf { it >= 2 }
    }

    internal fun hourName(hour: Int): String = when (hour) {
        0 -> "midnight"
        12 -> "noon"
        in 1..11 -> "$hour am"
        else -> "${hour - 12} pm"
    }
}
