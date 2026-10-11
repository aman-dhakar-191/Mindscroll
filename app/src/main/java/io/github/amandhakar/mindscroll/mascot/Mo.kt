package io.github.amandhakar.mindscroll.mascot

/**
 * Mo, Mindscroll's mascot: a small blob who watches the reels with you. Mo is on your side,
 * cheeky but never preachy, and gets more frazzled the more you scroll. Mo shows up in the
 * app, the notification, the camera patch, the bubble and the widget.
 */
enum class Mood(
    val title: String,
    /** Body color, ARGB. */
    val color: Long,
    /** Today's count from which this mood starts. */
    val from: Long,
) {
    CHILL("Chill", 0xFF5EE6A8, 0),
    CURIOUS("Curious", 0xFF5EC8FF, 16),
    WOBBLY("Wobbly", 0xFFFFD25E, 41),
    DIZZY("Dizzy", 0xFFFF9A5E, 81),
    SLEEPY("Running on fumes", 0xFFC58BFF, 151);

    companion object {
        fun forCount(count: Long): Mood = entries.last { count >= it.from }

        /** Mood for a [Fatigue] level, which uses the same scale as reel counts. */
        fun forLevel(level: Double): Mood = entries.last { level >= it.from }
    }
}

object Mo {
    const val NAME = "Mo"

    /**
     * What Mo says about [count] reels today at [hour] (0-23), feeling [mood]. Changes every
     * few reels so it doesn't flicker, and milestones get their own line. [rested] means Mo has
     * calmed down from a break, which Mo is happy to point out.
     */
    fun line(count: Long, hour: Int, mood: Mood = Mood.forCount(count), rested: Boolean = false): String {
        if (rested) return restedLines[(count / STEP % restedLines.size).toInt()].format(count)
        milestones[count]?.let { return it.format(count) }
        if (hour in 0..4 && count > 0) return lateNight[(count / STEP % lateNight.size).toInt()].format(count)
        val lines = lines.getValue(mood)
        return lines[(count / STEP % lines.size).toInt()].format(count)
    }

    /**
     * Mo has rested when the break brought the mood below what today's count alone would
     * give: lots of reels today, but not lately.
     */
    fun isRested(count: Long, mood: Mood): Boolean = mood < Mood.forCount(count)

    private const val STEP = 5L

    private fun String.format(count: Long) = replace("{n}", count.toString())

    private val milestones = mapOf(
        0L to "Fresh day, clean slate. I'll keep count, you go live a little.",
        1L to "First reel of the day. I see you.",
        10L to "10 already. Still cute numbers.",
        25L to "25 reels. That's a short film, honestly.",
        50L to "50! Half a hundred. Stretch your neck for me?",
        100L to "100 reels. I'd clap but my hands are tired from counting.",
        200L to "200. I've started dreaming in vertical video.",
        500L to "500. I'm not mad, I'm just… impressed? Concerned? Both.",
    )

    private val lines = mapOf(
        Mood.CHILL to listOf(
            "Barely scrolled. Proud of us.",
            "{n} reels. Easy breezy.",
            "We're doing great. Snack and a stretch?",
            "I'm counting, but there's not much to count. Love that.",
        ),
        Mood.CURIOUS to listOf(
            "{n} reels. Find anything good?",
            "Ooh, we're on a roll. A small one.",
            "Still fun, or just habit? Asking for me.",
            "{n} and counting. I'm keeping an eye out.",
        ),
        Mood.WOBBLY to listOf(
            "{n} reels. My blob is getting wobbly.",
            "Water break? I'll hold your place.",
            "That's a lot of thumbs. Rest them?",
            "If one more reel is 'just one more', I'll know.",
        ),
        Mood.DIZZY to listOf(
            "{n}… my eyes are spinning. Yours?",
            "The algorithm's having a great day. Are you?",
            "How about we look at something far away for a sec?",
            "I'm dizzy. Let's do something with no swiping.",
        ),
        Mood.SLEEPY to listOf(
            "{n} reels. Even I need a nap.",
            "Running on fumes here. Put me down for a bit?",
            "The reels will still be there later. Promise.",
            "We've seen enough cats for today. Probably.",
        ),
    )

    private val restedLines = listOf(
        "Ahh, that break helped. I can see straight again.",
        "Look at me, all calm. Phone-down time suits us.",
        "{n} reels today, but I've had a rest. Feeling much better.",
        "Breaks are my favorite reel.",
    )

    private val lateNight = listOf(
        "It's late. The reels will still exist tomorrow.",
        "Sleep is the best reel. {n} for today.",
        "Night owl mode: {n}. Phone down, eyes closed?",
    )
}
