package io.github.amandhakar.mindscroll.mascot

import kotlin.math.max
import kotlin.math.pow

/**
 * How Mo's body responds, beyond which face Mo wears. Two things change a reaction:
 *
 * - **Energy.** A rested Mo is springy; a worn-out Mo (see [Fatigue]) moves smaller and slower.
 * - **Novelty (habituation).** The first reel after a break is a surprise. Steady scrolling gets
 *   Mo used to it: each reel in a run startles a bit less, then settles at a small nod, and the
 *   surprise comes back after a minute without a reel. Real creatures stop flinching at the
 *   same stimulus; so does Mo.
 *
 * Pure functions of the mood and the length of the current run, so they are easy to test.
 */
object MoTemperament {
    /** A gap longer than this ends a run: the next reel is a surprise again. */
    const val STEADY_GAP_MS = 60_000L

    /** Each reel in a run keeps this much of the previous reel's novelty. */
    private const val NOVELTY_DECAY = 0.88

    /** Even a long run never goes below this: Mo still notices. */
    const val MIN_NOVELTY = 0.4f

    /** What a reaction should look like: [size] scales how big, [tempo] stretches its timing (above 1 = slower). */
    data class Reaction(val size: Float, val tempo: Float)

    /** 1 = bursting with energy, lower the more worn out [mood] is. */
    fun energy(mood: Mood): Float = when (mood) {
        Mood.CHILL -> 1.0f
        Mood.CURIOUS -> 0.9f
        Mood.WOBBLY -> 0.72f
        Mood.DIZZY -> 0.55f
        Mood.SLEEPY -> 0.4f
    }

    /** 1 for the first reel of a run, falling to [MIN_NOVELTY] as the run goes on. [streak] is 1 for the first. */
    fun novelty(streak: Int): Float =
        max(MIN_NOVELTY.toDouble(), NOVELTY_DECAY.pow((streak - 1).coerceAtLeast(0))).toFloat()

    fun reaction(mood: Mood, streak: Int): Reaction {
        val energy = energy(mood)
        val novelty = novelty(streak)
        // Tired is smaller; used-to-it is smaller still: a long run settles at under half a fresh startle.
        val habit = (novelty - MIN_NOVELTY) / (1f - MIN_NOVELTY)
        val size = lerp(0.6f, 1f, energy) * lerp(0.45f, 1f, habit)
        // Tired is slower; used-to-it is brisker (a quick nod, not a full performance).
        val tempo = lerp(1.35f, 1f, energy) * lerp(0.75f, 1f, habit)
        return Reaction(size, tempo)
    }

    private fun lerp(from: Float, to: Float, t: Float) = from + (to - from) * t
}
