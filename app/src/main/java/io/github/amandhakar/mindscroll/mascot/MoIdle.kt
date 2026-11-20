package io.github.amandhakar.mindscroll.mascot

import kotlin.random.Random

/** The small things Mo does when nothing is happening, so Mo isn't a single looping animation. */
enum class IdleBehavior(val label: String) {
    LOOK_AROUND("look around"),
    PEEK("curious peek"),
    YAWN("stretch and yawn"),
    WIGGLE("little wiggle"),
}

/**
 * Decides what Mo does while idle and when. Behaviours are picked at random, weighted by mood and
 * the time of day (a sleepy Mo yawns; Mo yawns more late at night), never the same twice in a row
 * if it can be helped, and spaced out by mood and motion style. The [random] source is injected,
 * so tests are exact. Nothing here runs per frame: the caller asks once per behaviour, on a timer.
 */
class MoIdle(private val random: Random = Random) {
    /** Milliseconds until the next behaviour, for [mood]. A livelier [profile] fidgets more often. */
    fun nextDelayMs(mood: Mood, profile: MotionProfile): Long {
        val (min, max) = when (mood) {
            Mood.CHILL -> 8_000L to 18_000L
            Mood.CURIOUS -> 6_000L to 14_000L
            Mood.WOBBLY -> 8_000L to 16_000L
            Mood.DIZZY -> 10_000L to 20_000L
            Mood.SLEEPY -> 12_000L to 24_000L
        }
        return ((min + random.nextLong(max - min)) * profile.blinkGapScale).toLong()
    }

    /** The next behaviour for [mood] at [hour] (0-23), given what Mo did [last]. */
    fun pick(mood: Mood, hour: Int, last: IdleBehavior?): IdleBehavior {
        val night = hour >= 22 || hour < 6
        val weights = IdleBehavior.entries.map { behavior ->
            var w = baseWeight(mood, behavior)
            if (behavior == IdleBehavior.YAWN && night) w *= 2.5
            if (behavior == last) w *= REPEAT_PENALTY
            w
        }
        var roll = random.nextDouble() * weights.sum()
        for ((i, w) in weights.withIndex()) {
            roll -= w
            if (roll < 0) return IdleBehavior.entries[i]
        }
        return IdleBehavior.entries.last()
    }

    private fun baseWeight(mood: Mood, behavior: IdleBehavior): Double = when (mood) {
        Mood.CHILL -> when (behavior) {
            IdleBehavior.LOOK_AROUND -> 4.0; IdleBehavior.PEEK -> 2.0; IdleBehavior.YAWN -> 1.0; IdleBehavior.WIGGLE -> 3.0
        }
        Mood.CURIOUS -> when (behavior) {
            IdleBehavior.LOOK_AROUND -> 5.0; IdleBehavior.PEEK -> 4.0; IdleBehavior.YAWN -> 0.5; IdleBehavior.WIGGLE -> 2.0
        }
        Mood.WOBBLY -> when (behavior) {
            IdleBehavior.LOOK_AROUND -> 3.0; IdleBehavior.PEEK -> 1.0; IdleBehavior.YAWN -> 1.5; IdleBehavior.WIGGLE -> 3.0
        }
        Mood.DIZZY -> when (behavior) {
            IdleBehavior.LOOK_AROUND -> 2.0; IdleBehavior.PEEK -> 0.5; IdleBehavior.YAWN -> 1.0; IdleBehavior.WIGGLE -> 4.0
        }
        Mood.SLEEPY -> when (behavior) {
            IdleBehavior.LOOK_AROUND -> 1.0; IdleBehavior.PEEK -> 0.3; IdleBehavior.YAWN -> 6.0; IdleBehavior.WIGGLE -> 0.5
        }
    }

    private companion object {
        /** Doing the same thing twice in a row looks like a loop; make it unlikely, not impossible. */
        const val REPEAT_PENALTY = 0.25
    }
}
