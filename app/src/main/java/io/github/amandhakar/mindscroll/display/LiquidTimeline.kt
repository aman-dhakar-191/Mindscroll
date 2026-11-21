package io.github.amandhakar.mindscroll.display

import io.github.amandhakar.mindscroll.mascot.Mood
import kotlin.math.PI
import kotlin.math.sin

/**
 * The timing of Mo coming out of the island, roaming the screen, and going back in.
 *
 * 1. Mo is pushed out of the island inside a drop of liquid ([position] overshoots and wobbles).
 * 2. The drop pops: it shrinks and is drawn back into the island, and Mo is out completely, free.
 * 3. Mo roams the whole screen on a random [RoamPlan]: it goes somewhere, stays a while, goes
 *    somewhere else, and so on. At its second stop it stands still and does [midMove] (shakes its body,
 *    or yawns when it is sleepy) while saying how it feels ([bubbleAlpha]). It ends at the spot it
 *    came out of.
 * 4. A new drop forms around Mo ([drop]), and Mo winds up and is pulled back in.
 *
 * Pure maths, so the shape of the motion is tested.
 */
object LiquidTimeline {
    /** 0.7 s out in the drop, 0.5 s popping, 13 s roaming the screen, then 1 s back in. */
    const val OUT_MS = 700L
    const val POP_MS = 500L
    const val ROAM_MS = 13_000L
    const val BACK_MS = 1_000L
    const val TOTAL_MS = OUT_MS + POP_MS + ROAM_MS + BACK_MS

    /** How long the new drop takes to form around Mo before it is pulled in. */
    private const val FORM_MS = 600L

    /** Fractions of the whole. Out in a drop until [OUT_END]; the drop pops by [POP_END], and Mo is free. */
    val OUT_END = OUT_MS.toFloat() / TOTAL_MS
    val POP_END = (OUT_MS + POP_MS).toFloat() / TOTAL_MS

    /** A new drop starts forming around Mo at [FORM_START] and is whole at [HOLD_END]; then Mo is pulled in. */
    val FORM_START = (OUT_MS + POP_MS + ROAM_MS - FORM_MS).toFloat() / TOTAL_MS
    val HOLD_END = (OUT_MS + POP_MS + ROAM_MS).toFloat() / TOTAL_MS

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

    /** What Mo does in the middle of the roam. */
    enum class MidMove { SHAKE, YAWN }

    /** A sleepy Mo yawns (Mo's own big yawn and stretch); every other mood shakes its body. */
    fun midMove(mood: Mood): MidMove = if (mood == Mood.SLEEPY) MidMove.YAWN else MidMove.SHAKE

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
     * When (0..1 of the roam) Mo's [move] starts: centred in the pause where it talks, which is
     * [talkFrom]..[talkTo]. Mo stands still for it, which is what makes it look natural.
     */
    fun moveStart(talkFrom: Float, talkTo: Float, move: MidMove): Float {
        val length = (if (move == MidMove.YAWN) YAWN_MS else SHAKE_MS) / ROAM_MS.toFloat()
        return (talkFrom + talkTo) / 2f - length / 2f
    }

    /**
     * Mo shaking its body, as a dog shakes off water: -1..1, swinging five times over [SHAKE_MS],
     * easing in and out, in the middle of the talking pause. Zero before and after.
     */
    fun shake(t: Float, talkFrom: Float, talkTo: Float): Float {
        val length = SHAKE_MS / ROAM_MS.toFloat()
        val v = (roamProgress(t) - moveStart(talkFrom, talkTo, MidMove.SHAKE)) / length
        if (v <= 0f || v >= 1f) return 0f
        return sin(PI.toFloat() * v) * sin(2f * PI.toFloat() * 5f * v)
    }

    /**
     * How visible Mo's line is, 0..1: it fades in as Mo settles at its talking stop, stays while Mo
     * does its move, and fades out before Mo moves on. Zero everywhere else.
     */
    fun bubbleAlpha(t: Float, talkFrom: Float, talkTo: Float): Float {
        if (!isFree(t)) return 0f
        val u = roamProgress(t)
        val fade = BUBBLE_FADE_MS / ROAM_MS.toFloat()
        return when {
            u < talkFrom || u >= talkTo -> 0f
            u < talkFrom + fade -> (u - talkFrom) / fade
            u < talkTo - fade -> 1f
            else -> (talkTo - u) / fade
        }
    }

    /** Mo's shake lasts this long, and its yawn is Mo's own 1.6 s one. */
    const val SHAKE_MS = 1_200L
    const val YAWN_MS = 1_600L
    private const val BUBBLE_FADE_MS = 400L

    /** 0 at the start, 1 at the end, past 1 in between: the same curve as Android's OvershootInterpolator. */
    fun overshoot(t: Float, tension: Float): Float {
        val k = t - 1f
        return k * k * ((tension + 1f) * k + tension) + 1f
    }

    /** 0 at the start, dips below 0, then 1 at the end: the same curve as Android's AnticipateInterpolator. */
    fun anticipate(t: Float, tension: Float): Float = t * t * ((tension + 1f) * t - tension)
}
