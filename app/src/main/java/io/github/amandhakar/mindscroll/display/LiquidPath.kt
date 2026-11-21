package io.github.amandhakar.mindscroll.display

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/** A point on the screen, in pixels. */
data class Pt(val x: Float, val y: Float)

/**
 * How Mo wanders the screen, the way a person does: goes somewhere, stays there a while, then
 * goes somewhere else, and finally heads home. A new random plan every time.
 *
 * - Mo **travels** to a spot (a stroll away, towards somewhere random on the screen) along a gentle arc (never a straight line), slow off the mark, quicker
 *   in the middle, slowing in, with a small sideways wobble. It overshoots the spot a little and
 *   settles back, like something that has just stopped.
 * - Then it **stays** a while: a slow, small drift and bob, as if it can't quite keep still, easing
 *   to a stop before it sets off again.
 * - The second spot is where Mo does its move and says how it feels ([talkFrom]..[talkTo]), so it
 *   stands still to talk, which is how it looks natural.
 *
 * Positions are for progress [u] through the roam (0..1), whose length is [roamMs]. It starts and
 * ends at the spot Mo came out of. Pure maths, so it is tested.
 */
class RoamPlan private constructor(
    private val legs: List<Leg>,
    private val roamMs: Float,
    private val minX: Float,
    private val maxX: Float,
    private val minY: Float,
    private val maxY: Float,
    private val unit: Float,
    /** The part of the roam (0..1) where Mo stands at its second spot and talks. */
    val talkFrom: Float,
    val talkTo: Float,
) {
    /** One trip to a spot and the stay there; [dwellEnd] equals [travelEnd] for the last trip home. */
    private class Leg(
        val from: Pt,
        val to: Pt,
        val control: Pt,
        val travelStart: Float,
        val travelEnd: Float,
        val dwellEnd: Float,
        val phase: Float,
    )

    /** When Mo arrives at each spot it stays at (0..1), for looking around. */
    val arrivals: List<Float> = legs.filter { it.dwellEnd > it.travelEnd }.map { it.travelEnd }

    /** How many spots Mo visits before heading home. */
    val spots: Int get() = arrivals.size

    /** Where Mo is [u] (0..1) of the way through the roam. */
    fun at(u: Float): Pt {
        val x = u.coerceIn(0f, 1f)
        val leg = legs.firstOrNull { x < it.dwellEnd } ?: legs.last()
        val p = when {
            x < leg.travelEnd -> travel(leg, x)
            leg.dwellEnd > leg.travelEnd -> stay(leg, x)
            else -> leg.to
        }
        return Pt(p.x.coerceIn(minX, maxX), p.y.coerceIn(minY, maxY))
    }

    private fun travel(leg: Leg, x: Float): Pt {
        val tau = ((x - leg.travelStart) / (leg.travelEnd - leg.travelStart)).coerceIn(0f, 1f)
        val s = smootherStep(tau)
        val inv = 1f - s
        val bx = inv * inv * leg.from.x + 2f * inv * s * leg.control.x + s * s * leg.to.x
        val by = inv * inv * leg.from.y + 2f * inv * s * leg.control.y + s * s * leg.to.y
        // A small sideways wobble while it moves, strongest at full speed: not a bead on a wire.
        val dx = leg.to.x - leg.from.x
        val dy = leg.to.y - leg.from.y
        val len = hypot(dx, dy).coerceAtLeast(1f)
        val wobble = WOBBLE_DP * unit * sin(2f * PI.toFloat() * 1.5f * tau + leg.phase) * sin(PI.toFloat() * tau)
        return Pt(bx - dy / len * wobble, by + dx / len * wobble)
    }

    private fun stay(leg: Leg, x: Float): Pt {
        val seconds = (x - leg.travelEnd) * roamMs / 1000f
        val length = (leg.dwellEnd - leg.travelEnd) * roamMs / 1000f
        // Overshoot the spot a little in the way it was going, and settle back.
        val dir = Pt(leg.to.x - leg.control.x, leg.to.y - leg.control.y).let {
            val d = hypot(it.x, it.y).coerceAtLeast(1f)
            Pt(it.x / d, it.y / d)
        }
        val settleFade = 1f - smoothStep(seconds / SETTLE_S)
        val settle = SETTLE_DP * unit * exp(-4.5f * seconds) * sin(2f * PI.toFloat() * 1.7f * seconds) * settleFade
        // Then a slow drift and bob, easing in after the settle and out before it sets off again.
        val envelope = smoothStep(seconds / EASE_S) * smoothStep((length - seconds) / EASE_S)
        val driftX = DRIFT_DP * unit * sin(0.9f * seconds + leg.phase) * envelope
        val driftY = DRIFT_DP * 0.8f * unit * sin(1.4f * seconds + 2f * leg.phase) * envelope
        return Pt(leg.to.x + dir.x * settle + driftX, leg.to.y + dir.y * settle + driftY)
    }

    companion object {
        /** How fast Mo travels, in dp per second at the average: about a leisurely walk across a phone in 2 to 3 s. */
        private const val SPEED_DP_PER_S = 150f
        private const val MIN_TRAVEL_MS = 1_000f
        private const val MIN_STAY_MS = 1_000f
        private const val MAX_STAY_MS = 1_900f
        /** Long enough to say something and have a move. */
        private const val TALK_STAY_MS = 3_300f
        private const val TALK_MARGIN_S = 0.30f
        /** Mo wanders in hops of this many dp: a stroll, not a leap across the screen. */
        private const val HOP_MIN_DP = 130f
        private const val HOP_MAX_DP = 360f
        private const val MAX_SPOTS = 5
        private const val WOBBLE_DP = 3.5f
        private const val SETTLE_DP = 7f
        private const val SETTLE_S = 0.9f
        private const val DRIFT_DP = 4f
        private const val EASE_S = 0.7f

        /**
         * A plan from [start] that fills [roamMs], visiting two to five random spots inside the box,
         * then home to [start]. [unit] is pixels per dp. [start] is always inside the box.
         */
        fun random(
            random: Random,
            start: Pt,
            minX: Float,
            maxX: Float,
            minY: Float,
            maxY: Float,
            roamMs: Float,
            unit: Float = 1f,
        ): RoamPlan {
            val lowX = minOf(minX, start.x)
            val highX = maxOf(maxX, start.x)
            val lowY = minOf(minY, start.y)
            val highY = maxOf(maxY, start.y)
            val hopMin = HOP_MIN_DP * unit
            val hopMax = HOP_MAX_DP * unit
            val speed = SPEED_DP_PER_S * unit / 1000f * (0.85f + 0.3f * random.nextFloat()) // px per ms

            class Raw(val to: Pt, val travel: Float, var stay: Float)
            val raws = mutableListOf<Raw>()
            var here = start
            var used = 0f
            while (raws.size < MAX_SPOTS) {
                var next = hop(random, here, hopMin, hopMax, lowX, highX, lowY, highY)
                var tries = 0
                while (hypot(next.x - here.x, next.y - here.y) < hopMin * 0.7f && tries < 12) {
                    next = hop(random, here, hopMin, hopMax, lowX, highX, lowY, highY)
                    tries++
                }
                val travel = maxOf(MIN_TRAVEL_MS, hypot(next.x - here.x, next.y - here.y) / speed)
                var stay = MIN_STAY_MS + random.nextFloat() * (MAX_STAY_MS - MIN_STAY_MS)
                if (raws.size == 1) stay = maxOf(stay, TALK_STAY_MS)
                val home = maxOf(MIN_TRAVEL_MS, hypot(next.x - start.x, next.y - start.y) / speed)
                if (raws.size >= 2 && used + travel + stay + home > roamMs) break
                raws += Raw(next, travel, stay)
                used += travel + stay
                here = next
            }
            val homeTravel = maxOf(MIN_TRAVEL_MS, hypot(here.x - start.x, here.y - start.y) / speed)
            val total = used + homeTravel
            var scale = 1f
            if (total <= roamMs) {
                // Spare time becomes longer pauses, at the same walking speed.
                val extra = (roamMs - total) / raws.size
                raws.forEach { it.stay += extra }
            } else {
                scale = roamMs / total // only when the first two hops were long: hurry everything a little
            }

            val legs = mutableListOf<Leg>()
            var from = start
            var clock = 0f
            for (raw in raws) {
                val travelEnd = clock + raw.travel * scale
                val dwellEnd = travelEnd + raw.stay * scale
                legs += Leg(from, raw.to, arc(random, from, raw.to), clock / roamMs, travelEnd / roamMs, dwellEnd / roamMs, random.nextFloat() * 6.28f)
                from = raw.to
                clock = dwellEnd
            }
            legs += Leg(from, start, arc(random, from, start), clock / roamMs, 1f, 1f, random.nextFloat() * 6.28f)

            // The second spot is where Mo talks: from just after it settles to just before it leaves.
            val talkLeg = legs[1]
            val margin = TALK_MARGIN_S * 1000f / roamMs
            return RoamPlan(legs, roamMs, lowX, highX, lowY, highY, unit, talkLeg.travelEnd + margin, talkLeg.dwellEnd - margin)
        }

        /**
         * A spot a stroll away from [here], towards a random place on the screen, so that over a few
         * hops Mo ends up all over it instead of piling up against the edge it started near.
         */
        private fun hop(random: Random, here: Pt, min: Float, max: Float, lowX: Float, highX: Float, lowY: Float, highY: Float): Pt {
            val targetX = lowX + random.nextFloat() * (highX - lowX)
            val targetY = lowY + random.nextFloat() * (highY - lowY)
            var vx = targetX - here.x
            var vy = targetY - here.y
            var length = hypot(vx, vy)
            if (length < 1e-3f) {
                vx = 1f
                vy = 0f
                length = 1f
            }
            val distance = length.coerceIn(min, min + random.nextFloat() * (max - min))
            return Pt(
                (here.x + vx / length * distance).coerceIn(lowX, highX),
                (here.y + vy / length * distance).coerceIn(lowY, highY),
            )
        }

        /** The control point of a gentle arc from [a] to [b]: the middle, pushed sideways by up to a quarter of the way. */
        private fun arc(random: Random, a: Pt, b: Pt): Pt {
            val dx = b.x - a.x
            val dy = b.y - a.y
            val side = (random.nextFloat() - 0.5f) * 0.5f
            return Pt((a.x + b.x) / 2f - dy * side, (a.y + b.y) / 2f + dx * side)
        }

        private fun smoothStep(v: Float): Float {
            val t = v.coerceIn(0f, 1f)
            return t * t * (3f - 2f * t)
        }

        /** Zero speed and zero acceleration at both ends: the gentlest start and stop. */
        private fun smootherStep(v: Float): Float {
            val t = v.coerceIn(0f, 1f)
            return t * t * t * (t * (6f * t - 15f) + 10f)
        }
    }
}
