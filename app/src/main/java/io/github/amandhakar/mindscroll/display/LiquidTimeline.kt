package io.github.amandhakar.mindscroll.display

import kotlin.math.PI
import kotlin.math.sin

/**
 * The timing of Mo coming out of the island, roaming around outside to stretch, and going back in.
 *
 * 1. Mo is pushed out of the island inside a drop of liquid ([position] overshoots and wobbles).
 * 2. The drop pops: it shrinks and is drawn back into the island, and Mo is out completely, free.
 * 3. Mo [roam]s around outside, shakes its body ([shake]) and asks how you are doing ([bubbleAlpha]).
 *    It starts and ends at the spot it came out of.
 * 4. A new drop forms around Mo ([drop]), and Mo winds up and is pulled back in.
 *
 * Pure maths, so the shape of the motion is tested.
 */
object LiquidTimeline {
    const val TOTAL_MS = 5_200L

    /** Fractions of the whole. Out in a drop until [OUT_END]; the drop pops by [POP_END], and Mo is free. */
    const val OUT_END = 0.14f
    const val POP_END = 0.24f

    /** A new drop starts forming around Mo at [FORM_START] and is whole at [HOLD_END]; then Mo is pulled in. */
    const val FORM_START = 0.74f
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

    /** Whether Mo is out of the island at [t], in a drop or free. */
    fun isOut(t: Float): Boolean = t >= OUT_END && t < HOLD_END

    /** Whether Mo is out of its drop and free to roam at [t]. */
    fun isFree(t: Float): Boolean = t >= POP_END && t < HOLD_END

    /** How far through the roam [t] is: 0 when the drop has just popped, 1 when Mo starts back. */
    fun roamProgress(t: Float): Float = ((t - POP_END) / (HOLD_END - POP_END)).coerceIn(0f, 1f)

    /**
     * The drop of liquid Mo is in. [scale] is its size (1 whole, 0 gone); [attach] is how much it
     * sits around Mo (1) rather than back in the island's edge (0). It is whole while Mo comes out,
     * pops (shrinks into the island's edge) so Mo is free, stays gone while Mo roams, and
     * re-forms around Mo, with a wobble, as Mo comes back.
     */
    data class Drop(val scale: Float, val attach: Float)

    fun drop(t: Float): Drop = when {
        t < OUT_END -> Drop(1f, 1f)
        t < POP_END -> {
            val u = (t - OUT_END) / (POP_END - OUT_END)
            val s = 1f - u * u * (3f - 2f * u)
            Drop(s, s)
        }
        t < FORM_START -> Drop(0f, 0f)
        t < HOLD_END -> Drop(overshoot((t - FORM_START) / (HOLD_END - FORM_START), 1.2f), 1f)
        else -> Drop(1f, 1f)
    }

    /**
     * Where Mo wanders while it is free. [envelope] rises from 0 to 1 and back to 0 over the roam
     * (so Mo leaves and returns to the spot it came out of); [wander] swings left and right
     * (-1..1, one and a half sweeps); [bob] lets it sink lower and rise (0..1).
     */
    data class Roam(val envelope: Float, val wander: Float, val bob: Float)

    fun roam(t: Float): Roam {
        val u = ((t - POP_END) / (HOLD_END - POP_END))
        if (u <= 0f || u >= 1f) return Roam(0f, 0f, 0f)
        val envelope = sin(PI.toFloat() * u)
        val wander = envelope * sin(2f * PI.toFloat() * 1.5f * u)
        val bob = envelope * (0.5f + 0.5f * sin(2f * PI.toFloat() * 2.2f * u + 1f))
        return Roam(envelope, wander, bob)
    }

    /**
     * Mo shaking its body, as a dog shakes off water: -1..1, swinging five times in the middle of
     * the roam and easing in and out. Zero before and after.
     */
    fun shake(t: Float): Float {
        val v = (roamProgress(t) - SHAKE_FROM) / SHAKE_LENGTH
        if (v <= 0f || v >= 1f) return 0f
        return sin(PI.toFloat() * v) * sin(2f * PI.toFloat() * 5f * v)
    }

    /**
     * How visible the "how are you doing?" bubble is, 0..1: it fades in just before Mo shakes, stays
     * while it does, and fades out after.
     */
    fun bubbleAlpha(t: Float): Float {
        val u = roamProgress(t)
        if (!isFree(t)) return 0f
        return when {
            u < BUBBLE_IN -> 0f
            u < BUBBLE_IN + BUBBLE_FADE -> (u - BUBBLE_IN) / BUBBLE_FADE
            u < BUBBLE_OUT -> 1f
            u < BUBBLE_OUT + BUBBLE_FADE -> 1f - (u - BUBBLE_OUT) / BUBBLE_FADE
            else -> 0f
        }
    }

    private const val SHAKE_FROM = 0.40f
    private const val SHAKE_LENGTH = 0.20f
    private const val BUBBLE_IN = 0.30f
    private const val BUBBLE_OUT = 0.64f
    private const val BUBBLE_FADE = 0.06f

    /** 0 at the start, 1 at the end, past 1 in between: the same curve as Android's OvershootInterpolator. */
    fun overshoot(t: Float, tension: Float): Float {
        val k = t - 1f
        return k * k * ((tension + 1f) * k + tension) + 1f
    }

    /** 0 at the start, dips below 0, then 1 at the end: the same curve as Android's AnticipateInterpolator. */
    fun anticipate(t: Float, tension: Float): Float = t * t * ((tension + 1f) * t - tension)
}
