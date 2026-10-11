package io.github.amandhakar.mindscroll.mascot

import io.github.amandhakar.mindscroll.mascot.Mo.fill

/**
 * When Mo says something out loud (a speech bubble from the camera patch), and what. Mo
 * speaks at real moments (a fast run of swipes, a milestone, the fifth trip to an app, coming
 * back from a break, waking up) and otherwise keeps quiet: one remark per
 * [GAP_MS] at most ([BIG_GAP_MS] for big moments like milestones and waking), and no more
 * than [MAX_PER_HOUR] an hour. Pure logic; times are elapsed-realtime ms.
 */
class MoMoments {
    private val spokenAt = ArrayDeque<Long>()
    private val recentReels = ArrayDeque<Long>()
    private var lastFastAt = Long.MIN_VALUE / 2
    private var lastReelAt: Long? = null
    private var mood: Mood? = null
    private val rotation = mutableMapOf<String, Long>()

    /** A reel was counted; today's total is now [total]. */
    fun onReel(now: Long, total: Long): String? {
        lastReelAt = now
        recentReels.addLast(now)
        while (recentReels.isNotEmpty() && now - recentReels.first() > FAST_WINDOW_MS) recentReels.removeFirst()

        Mo.milestoneSpoken[total]?.let { return say(now, it, big = true) }
        if (recentReels.size >= FAST_COUNT && now - lastFastAt >= FAST_REPEAT_MS) {
            val line = say(now, next("fast", Mo.fastSwipes).fill(total, x = recentReels.size.toLong()))
            if (line != null) lastFastAt = now
            return line
        }
        return null
    }

    /** Mo's mood is now [newMood]. Getting more frazzled is worth a word; calming down isn't. */
    fun onMood(now: Long, newMood: Mood): String? {
        val previous = mood
        mood = newMood
        if (previous == null || newMood <= previous) return null
        val options = Mo.moodUp[newMood] ?: return null
        return say(now, next("mood", options))
    }

    /**
     * A watched app was opened ([label], its [openNumber]th time today). After a long break
     * Mo says welcome back; every fifth open, Mo notices.
     */
    fun onAppOpened(now: Long, label: String, openNumber: Int): String? {
        val sinceReel = lastReelAt?.let { now - it }
        if (sinceReel != null && sinceReel >= BREAK_MS) {
            return say(now, next("welcome", Mo.welcomeBack))
        }
        if (openNumber >= 5 && openNumber % 5 == 0) {
            return say(now, next("again", Mo.appAgain).fill(0, app = label, x = openNumber.toLong()))
        }
        return null
    }

    /** Mo just woke up after sleeping [sleptMs]. */
    fun onWake(now: Long, sleptMs: Long): String? {
        if (sleptMs < MoSleep.LONG_SLEEP_MS) return null
        return say(now, next("wake", Mo.wakeUp), big = true)
    }

    /** Something Mo has been waiting to say, e.g. a memory at the start of the day. */
    fun onRemark(now: Long, text: String): String? = say(now, text)

    private fun say(now: Long, text: String, big: Boolean = false): String? {
        while (spokenAt.isNotEmpty() && now - spokenAt.first() > HOUR_MS) spokenAt.removeFirst()
        val last = spokenAt.lastOrNull()
        if (last != null && now - last < if (big) BIG_GAP_MS else GAP_MS) return null
        if (spokenAt.size >= MAX_PER_HOUR) return null
        spokenAt.addLast(now)
        return text
    }

    /** Lines in a category take turns, so Mo doesn't repeat the same one. */
    private fun next(category: String, options: List<String>): String {
        val turn = rotation[category] ?: 0
        rotation[category] = turn + 1
        return Mo.pick(options, turn)
    }

    companion object {
        const val GAP_MS = 3 * 60_000L
        const val BIG_GAP_MS = 45_000L
        const val MAX_PER_HOUR = 10
        private const val HOUR_MS = 60 * 60_000L

        /** This many reels within [FAST_WINDOW_MS] is a fast run of swipes. */
        const val FAST_COUNT = 6
        const val FAST_WINDOW_MS = 60_000L
        /** Mo comments on fast swiping at most this often. */
        const val FAST_REPEAT_MS = 10 * 60_000L

        /** This long without a reel counts as a break worth welcoming back from. */
        const val BREAK_MS = 20 * 60_000L
    }
}
