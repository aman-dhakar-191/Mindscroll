package io.github.amandhakar.mindscroll.display

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * The timing of Mo coming out of the island, roaming around outside to stretch a little, and
 * going back in.
 *
 * [position] is how far out Mo is: 0 inside the island, 1 out. It overshoots on the way out (a
 * drop pushed out of a blob, wobbling as it settles) and winds up on the way back (it pulls out
 * a little further, then snaps back in). While Mo is out, [roam] says where it wanders. It
 * starts and ends at the spot Mo came out of, so the way back is a straight pull into the island.
 *
 * Pure maths, so the shape of the motion is tested.
 */
object LiquidTimeline {
    const val TOTAL_MS = 5_200L

    /** Fractions of the whole: Mo is on its way out until [OUT_END], roams until [HOLD_END], then goes back. */
    const val OUT_END = 0.14f
    const val HOLD_END = 0.84f

    private const val OUT_TENSION = 1.6f
    private const val BACK_TENSION = 1.4f

    /** How far out Mo is at [t] (0..1 of the whole). */
    fun position(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        return when {
            x < OUT_END -> overshoot(x / OUT_END, OUT_TENSION)
            x < HOLD_END -> 1f
            else -> 1f - anticipate((x - HOLD_END) / (1f - HOLD_END), BACK_TENSION)
        }
    }

    /** Whether Mo is out and free to roam at [t]. */
    fun isOut(t: Float): Boolean = t >= OUT_END && t < HOLD_END

    /** How far through the roam [t] is: 0 when Mo has just come out, 1 when it starts back. */
    fun roamProgress(t: Float): Float = ((t - OUT_END) / (HOLD_END - OUT_END)).coerceIn(0f, 1f)

    /**
     * Where Mo wanders while it is out. [envelope] rises from 0 to 1 and back to 0 over the roam
     * (so Mo leaves and returns to the spot it came out of); [wander] swings left and right
     * (-1..1, one and a half sweeps); [bob] lets it sink lower and rise (0..1); [stretch] peaks
     * (1) at the middle, when Mo has a good stretch.
     */
    data class Roam(val envelope: Float, val wander: Float, val bob: Float, val stretch: Float)

    fun roam(t: Float): Roam {
        val u = ((t - OUT_END) / (HOLD_END - OUT_END))
        if (u <= 0f || u >= 1f) return Roam(0f, 0f, 0f, 0f)
        val envelope = sin(PI.toFloat() * u)
        val wander = envelope * sin(2f * PI.toFloat() * 1.5f * u)
        val bob = envelope * (0.5f + 0.5f * sin(2f * PI.toFloat() * 2.2f * u + 1f))
        val away = (u - 0.5f) / 0.09f
        val stretch = exp(-away * away)
        return Roam(envelope, wander, bob, stretch)
    }

    /** 0 at the start, 1 at the end, past 1 in between: the same curve as Android's OvershootInterpolator. */
    fun overshoot(t: Float, tension: Float): Float {
        val k = t - 1f
        return k * k * ((tension + 1f) * k + tension) + 1f
    }

    /** 0 at the start, dips below 0, then 1 at the end: the same curve as Android's AnticipateInterpolator. */
    fun anticipate(t: Float, tension: Float): Float = t * t * ((tension + 1f) * t - tension)
}
