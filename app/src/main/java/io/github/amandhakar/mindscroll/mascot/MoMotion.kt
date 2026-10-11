package io.github.amandhakar.mindscroll.mascot

import kotlin.math.abs
import kotlin.random.Random

/** One frame of Mo's expression. All values are 0..1. */
data class MoPose(
    /** Position in the mood's idle loop (breathing, glancing, swaying, spinning, nodding). */
    val phase: Float = 0f,
    /** 0 = eyes open, 1 = eyes shut. */
    val blink: Float = 0f,
    /** Surprise when a new reel counts: squash, wide eyes, "o" mouth. */
    val react: Float = 0f,
)

/** Timing for Mo's animations, shared by the app (Compose) and the overlays (views). */
object MoMotion {
    /** Length of one idle loop. */
    const val LOOP_MS = 2600L
    const val BLINK_MS = 180L
    const val REACT_MS = 750L
    private const val REACT_RISE_MS = 120L

    fun phase(timeMs: Long): Float = (timeMs.mod(LOOP_MS)).toFloat() / LOOP_MS

    /** Eyelids close and reopen over [BLINK_MS]. */
    fun blink(sinceBlinkMs: Long): Float {
        if (sinceBlinkMs < 0 || sinceBlinkMs >= BLINK_MS) return 0f
        return 1f - abs(2f * sinceBlinkMs / BLINK_MS - 1f)
    }

    /** A quick rise, then an eased settle over the rest of [REACT_MS]. */
    fun react(sinceReactMs: Long): Float {
        if (sinceReactMs < 0 || sinceReactMs >= REACT_MS) return 0f
        if (sinceReactMs < REACT_RISE_MS) return sinceReactMs.toFloat() / REACT_RISE_MS
        val t = (sinceReactMs - REACT_RISE_MS).toFloat() / (REACT_MS - REACT_RISE_MS)
        val eased = t * t * (3 - 2 * t)
        return 1f - eased
    }

    /** Irregular gaps between blinks look alive; a fixed rhythm looks mechanical. */
    fun nextBlinkDelayMs(random: Random = Random): Long = 2200L + random.nextLong(3200L)

    fun pose(timeMs: Long, blinkStartMs: Long?, reactStartMs: Long?, idle: Boolean) = MoPose(
        phase = if (idle) phase(timeMs) else 0f,
        blink = blinkStartMs?.let { blink(timeMs - it) } ?: 0f,
        react = reactStartMs?.let { react(timeMs - it) } ?: 0f,
    )
}
