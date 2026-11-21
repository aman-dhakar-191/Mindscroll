package io.github.amandhakar.mindscroll.display

import kotlin.math.hypot
import kotlin.random.Random

/** A point on the screen, in pixels. */
data class Pt(val x: Float, val y: Float)

/**
 * A random, smooth route for Mo to roam: out from where it came out of the island, through a few
 * stops placed anywhere on the screen, and home again. A new one is made every time, so Mo never
 * wanders the same way twice. [at] gives where Mo is [u] of the way along it (0..1): it sets off
 * and arrives gently, and the curve passes through every stop without kinks.
 */
class RoamPath(
    private val stops: List<Pt>,
    private val minX: Float,
    private val maxX: Float,
    private val minY: Float,
    private val maxY: Float,
) {
    init {
        require(stops.size >= 2) { "a path needs somewhere to start and end" }
    }

    /** Where Mo is [u] (0..1) of the way along the route: the start at 0, and home again at 1. */
    fun at(u: Float): Pt {
        val eased = ease(u.coerceIn(0f, 1f))
        val segments = stops.size - 1
        val f = eased * segments
        val i = f.toInt().coerceAtMost(segments - 1)
        val t = f - i
        val p0 = stops[(i - 1).coerceAtLeast(0)]
        val p1 = stops[i]
        val p2 = stops[i + 1]
        val p3 = stops[(i + 2).coerceAtMost(stops.size - 1)]
        // The spline can swing a little past a stop; keep Mo on the screen.
        return Pt(
            catmullRom(p0.x, p1.x, p2.x, p3.x, t).coerceIn(minX, maxX),
            catmullRom(p0.y, p1.y, p2.y, p3.y, t).coerceIn(minY, maxY),
        )
    }

    companion object {
        /**
         * A route from [start] through [stopCount] random stops inside the box, and back to [start].
         * Stops are kept a good way apart so Mo actually travels. [start] is always inside the box.
         */
        fun random(
            random: Random,
            start: Pt,
            minX: Float,
            maxX: Float,
            minY: Float,
            maxY: Float,
            stopCount: Int = 5,
        ): RoamPath {
            val lowX = minOf(minX, start.x)
            val highX = maxOf(maxX, start.x)
            val lowY = minOf(minY, start.y)
            val highY = maxOf(maxY, start.y)
            val farEnough = hypot(highX - lowX, highY - lowY) * 0.22f
            val stops = mutableListOf(start)
            var last = start
            repeat(stopCount) {
                var next = Pt(lowX + random.nextFloat() * (highX - lowX), lowY + random.nextFloat() * (highY - lowY))
                var tries = 0
                while (hypot(next.x - last.x, next.y - last.y) < farEnough && tries < 12) {
                    next = Pt(lowX + random.nextFloat() * (highX - lowX), lowY + random.nextFloat() * (highY - lowY))
                    tries++
                }
                stops += next
                last = next
            }
            stops += start
            return RoamPath(stops, lowX, highX, lowY, highY)
        }

        /** Slow at both ends, quicker in the middle. */
        private fun ease(u: Float) = u * u * (3f - 2f * u)

        private fun catmullRom(p0: Float, p1: Float, p2: Float, p3: Float, t: Float): Float =
            0.5f * (2f * p1 + (-p0 + p2) * t + (2f * p0 - 5f * p1 + 4f * p2 - p3) * t * t + (-p0 + 3f * p1 - 3f * p2 + p3) * t * t * t)
    }
}
