package io.github.amandhakar.mindscroll.mascot

/**
 * How Mo feels right now. Picked from [Fatigue] (or, before that's known, today's count).
 */
enum class Mood(
    val title: String,
    /** Body color, ARGB. */
    val color: Long,
    /** Fatigue level (same scale as a reel count) from which this mood starts. */
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

/**
 * # Who Mo is
 *
 * Mo is a small, soft blob who lives in the hole your front camera sits in. ("It's a studio
 * apartment. Great view of your face.") Mo sees every reel you see, through the same glass,
 * so a binge makes Mo dizzy and a break lets Mo recover. Mo can't stop you scrolling and
 * doesn't try; Mo just keeps count and keeps you company.
 *
 * **Voice.** Short sentences. First person. Warm, a little dramatic, gently cheeky, never
 * preachy, never guilt-tripping, never "you should". Mo talks about *us* and about Mo's own
 * feelings, not about your choices. Stage directions in asterisks for physical bits
 * (*yawns*). No exclamation-mark spam, no emoji (Mo has a face for that).
 *
 * **Loves:** naps, breaks, the sky, the number zero, being included. **Puzzled by:** the
 * algorithm, "one more", why anyone films their breakfast. **Running jokes:** the camera-hole
 * apartment; counting with tiny hands; dreaming in vertical video.
 *
 * Every line Mo says lives in this file, so the voice stays consistent. `{n}` becomes today's
 * count, `{app}` an app name, `{x}` a number for the line at hand.
 */
object Mo {
    const val NAME = "Mo"

    /**
     * What Mo says about [count] reels today at [hour] (0-23), feeling [mood]. Changes every
     * few reels so it doesn't flicker, and milestones get their own line. [rested] means Mo has
     * calmed down from a break, which Mo is happy to point out.
     */
    fun line(count: Long, hour: Int, mood: Mood = Mood.forCount(count), rested: Boolean = false): String {
        if (rested) return pick(restedLines, count / STEP).fill(count)
        milestones[count]?.let { return it.fill(count) }
        if (hour in 0..4 && count > 0) return pick(lateNight, count / STEP).fill(count)
        return pick(lines.getValue(mood), count / STEP).fill(count)
    }

    /**
     * Mo has rested when the break brought the mood below what today's count alone would
     * give: lots of reels today, but not lately.
     */
    fun isRested(count: Long, mood: Mood): Boolean = mood < Mood.forCount(count)

    /** Hello, by time of day; [seed] picks among a few so it isn't always the same. */
    fun greeting(hour: Int, seed: Long): String = pick(
        when (hour) {
            in 5..11 -> morning
            in 12..16 -> afternoon
            in 17..21 -> evening
            else -> night
        },
        seed,
    )

    private const val STEP = 5L

    internal fun <T> pick(options: List<T>, seed: Long): T = options[Math.floorMod(seed, options.size.toLong()).toInt()]

    internal fun String.fill(count: Long, app: String = "", x: Long = 0) =
        replace("{n}", count.toString()).replace("{app}", app).replace("{x}", x.toString())

    // --- Status lines (panel, notification, widget, home) ---

    private val milestones = mapOf(
        0L to "Fresh day, clean slate. I'll count, you go live a little.",
        1L to "First reel of the day. I see you. I always see you. I live in your camera.",
        10L to "10 already. Still cute numbers.",
        25L to "25 reels. That's a short film, honestly.",
        50L to "50. Half a hundred. Stretch your neck for me?",
        100L to "100 reels. I'd clap but my tiny hands are tired from counting.",
        150L to "150. I've started dreaming in vertical video.",
        200L to "200. My apartment is spinning and it's a studio, so that's everything.",
        300L to "300. I'm not mad. I'm just… lying down.",
        500L to "500. I'm writing this one down. In very wobbly handwriting.",
    )

    private val lines = mapOf(
        Mood.CHILL to listOf(
            "Barely scrolled. Proud of us.",
            "{n} reels. Easy breezy.",
            "Quiet day in the camera hole. I like it.",
            "I'm counting, but there's not much to count. Love that.",
            "Snack and a stretch? I'll stay here. I always stay here.",
            "{n} so far. The sky's still up there, by the way.",
            "Feeling fresh. Like a blob that's had water.",
            "Low numbers, high vibes.",
        ),
        Mood.CURIOUS to listOf(
            "{n} reels. Find anything good?",
            "Ooh, we're on a roll. A small one.",
            "Still fun, or just habit? Asking for me.",
            "{n} and counting. My little hands are warming up.",
            "That last one was cute. The one before, less so.",
            "We're in the 'just checking' zone. I know this zone.",
            "I've seen {n} today. You've seen {n}. We're a team.",
            "Interesting. Keep going, or go outside? I'll count either way.",
        ),
        Mood.WOBBLY to listOf(
            "{n} reels. My blob is getting wobbly.",
            "Water break? I'll hold your place. I'm good at holding still.",
            "That's a lot of thumbs. Rest them?",
            "If this is 'just one more', I'll know.",
            "My apartment's tilting a little. Is it the phone or me?",
            "{n}. I'm fine. I'm wobbly, but I'm fine.",
            "Somewhere a dog is being filmed. We'll see it either way.",
            "Our eyes could use a look at something far away.",
        ),
        Mood.DIZZY to listOf(
            "{n}… my eyes are spinning. Yours?",
            "The algorithm's having a great day. I'm having a dizzy one.",
            "Let's look at something with no swipe. A wall. A cloud.",
            "I've lost count. Kidding. It's {n}. I never lose count.",
            "*holds on to the edge of the camera hole*",
            "My vision is mostly vertical video now.",
            "We've been at this a while. I'm proud of my stamina, not yours.",
            "Could we blink together? One… two…",
        ),
        Mood.SLEEPY to listOf(
            "{n} reels. Even I need a nap.",
            "Running on fumes here. Put me down for a bit?",
            "The reels will still be there later. Promise. I checked.",
            "We've seen enough cats for today. Probably.",
            "*lies down in the camera hole*",
            "I'm too tired to be dramatic. That's how you know.",
            "{n}. I'll keep counting in my sleep. Literally.",
            "If you put the phone down, I'll dream about it for you.",
        ),
    )

    private val restedLines = listOf(
        "Ahh, that break helped. I can see straight again.",
        "Look at me, all calm. Phone-down time suits us.",
        "{n} reels today, but I've had a rest. Feeling much better.",
        "Breaks are my favorite reel.",
        "I napped. I'm a new blob.",
        "Recovered. My apartment stopped spinning.",
    )

    private val lateNight = listOf(
        "It's late. The reels will still exist tomorrow.",
        "Sleep is the best reel. {n} for today.",
        "Night owl mode: {n}. Phone down, eyes closed?",
        "*yawns* it's dark in here and out there.",
        "Even the algorithm sleeps. Okay, it doesn't. But we could.",
    )

    // --- Greetings (home screen) ---

    private val morning = listOf(
        "Morning! I slept in the camera hole. Cozy.",
        "Good morning. Fresh count, fresh blob.",
        "Morning. I stretched. You should see me stretch.",
    )
    private val afternoon = listOf(
        "Afternoon! How's the day going out there?",
        "Hey you. Lunch was good? I don't eat. I count.",
        "Afternoon check-in. I'm here. Always here.",
    )
    private val evening = listOf(
        "Evening. This is when the reels get sneaky.",
        "Hey, evening you. Long day?",
        "Evening! I put on my comfy blob.",
    )
    private val night = listOf(
        "It's late. Hi anyway.",
        "Night shift, huh? I'm here.",
        "*sleepy wave*",
    )

    // --- Things Mo says out loud in the moment (see MoMoments) ---

    internal val fastSwipes = listOf(
        "Whoa, slow down. I can't even see them.",
        "That's {x} in a minute. My eyes are doing laps.",
        "Swipe, swipe, swipe… are we looking for something?",
        "Wheee. Okay, too much whee.",
        "I blinked and missed three.",
    )
    internal val appAgain = listOf(
        "{app} again? That's {x} times today.",
        "Back to {app}. Visit number {x}. I'm keeping the guest book.",
        "{app}, take {x}. Action.",
        "{x} trips to {app} today. It must have good snacks.",
    )
    internal val moodUp = mapOf(
        Mood.CURIOUS to listOf("Ooh, we're warming up.", "I'm awake now. Curious, even."),
        Mood.WOBBLY to listOf("Getting wobbly over here.", "*wobbles* that's new."),
        Mood.DIZZY to listOf("Okay, now I'm dizzy.", "The camera hole is spinning."),
        Mood.SLEEPY to listOf("I'm running on fumes.", "*slumps* we've done a lot."),
    )
    internal val welcomeBack = listOf(
        "Welcome back. I napped. It was great.",
        "Oh, hi. I was dreaming of grass. Real grass.",
        "You're back. I feel so rested. Thank you for that.",
        "*stretches* that break was lovely.",
    )
    internal val wakeUp = listOf(
        "*yawns* oh, you're back.",
        "*stretches* mm, morning? Is it morning?",
        "I was asleep. In a good way.",
        "*blinks* hi. I dreamed you went outside.",
    )
    internal val milestoneSpoken = mapOf(
        25L to "25. That's a short film.",
        50L to "50! Neck stretch?",
        100L to "100. My hands are so tired.",
        150L to "150. Dreaming in vertical now.",
        200L to "200. The apartment is spinning.",
    )
}
