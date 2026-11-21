package io.github.amandhakar.mindscroll.display

/**
 * The timing of Mo coming out of the island and going back in. [position] is how far out Mo is:
 * 0 inside the island, 1 fully out. It overshoots on the way out (a drop pushed out of a
 * blob, wobbling as it settles) and winds up on the way back (it pulls out a little further,
 * then snaps back in). Pure maths, so the shape of the motion is tested.
 */
object LiquidTimeline {
    const val TOTAL_MS = 2_800L

    /** Fractions of the whole: Mo is on its way out until [OUT_END], out until [HOLD_END], then back. */
    const val OUT_END = 0.26f
    const val HOLD_END = 0.74f

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

    /** Whether Mo is out and hovering at [t]: the moment to wave. */
    fun isOut(t: Float): Boolean = t >= OUT_END && t < HOLD_END

    /** 0 at the start, 1 at the end, past 1 in between: the same curve as Android's OvershootInterpolator. */
    fun overshoot(t: Float, tension: Float): Float {
        val k = t - 1f
        return k * k * ((tension + 1f) * k + tension) + 1f
    }

    /** 0 at the start, dips below 0, then 1 at the end: the same curve as Android's AnticipateInterpolator. */
    fun anticipate(t: Float, tension: Float): Float = t * t * ((tension + 1f) * t - tension)
}
