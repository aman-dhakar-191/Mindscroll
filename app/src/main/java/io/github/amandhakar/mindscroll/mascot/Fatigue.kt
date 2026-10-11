package io.github.amandhakar.mindscroll.mascot

import kotlin.math.pow

/**
 * How worn out Mo is. Every reel adds 1, and that wears off by half every [HALF_LIFE_MS] of
 * not watching. Binge and it climbs; put the phone down and Mo calms back down. Computed from
 * reel timestamps, so it's right after restarts too, and it's on the same scale as the reel
 * counts that pick Mo's mood ([Mood.forLevel]).
 */
object Fatigue {
    /** 20 minutes away halves it: an hour's break takes Mo from dizzy back to chill. */
    const val HALF_LIFE_MS = 20 * 60_000L

    /** Older reels have worn off (less than 0.0002 each), so only these are needed. */
    const val WINDOW_MS = 12 * HALF_LIFE_MS

    fun level(reelTimes: List<Long>, now: Long): Double =
        reelTimes.sumOf { time ->
            val age = (now - time).coerceAtLeast(0)
            if (age > WINDOW_MS) 0.0 else 0.5.pow(age.toDouble() / HALF_LIFE_MS)
        }
}
