package io.github.amandhakar.mindscroll

import io.github.amandhakar.mindscroll.mascot.IdleBehavior
import io.github.amandhakar.mindscroll.mascot.MoIdle
import io.github.amandhakar.mindscroll.mascot.MoMotion
import io.github.amandhakar.mindscroll.mascot.MoTemperament
import io.github.amandhakar.mindscroll.mascot.MotionProfile
import io.github.amandhakar.mindscroll.mascot.Mood
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class MoTemperamentTest {
    // --- Fatigue and habituation ---

    @Test
    fun aWornOutMoHasLessEnergy() {
        val energies = Mood.entries.map(MoTemperament::energy)
        assertEquals(energies.sortedDescending(), energies)
        assertEquals(1f, MoTemperament.energy(Mood.CHILL), 0f)
        assertTrue(MoTemperament.energy(Mood.SLEEPY) < 0.5f)
    }

    @Test
    fun theFirstReelOfARunIsAFullSurprise() {
        val r = MoTemperament.reaction(Mood.CHILL, streak = 1)
        assertEquals(1f, r.size, 0.0001f)
        assertEquals(1f, r.tempo, 0.0001f)
    }

    @Test
    fun steadyScrollingGetsMoUsedToItThenLevelsOff() {
        val sizes = (1..30).map { MoTemperament.reaction(Mood.CHILL, it).size }
        assertTrue("each reel startles no more than the last", sizes.zipWithNext().all { (a, b) -> b <= a + 1e-6f })
        assertTrue(sizes[0] > sizes[4] && sizes[4] > sizes[8])
        // A long run settles at a small nod, never to nothing.
        assertEquals(sizes[12], sizes[29], 1e-6f)
        assertEquals(0.45f, sizes.last(), 0.001f)
        assertEquals(MoTemperament.MIN_NOVELTY, MoTemperament.novelty(100), 0f)
        // It takes a real run, not a couple of reels, to get used to it.
        assertTrue(sizes[2] > 0.75f)
    }

    @Test
    fun aTiredMoReactsSmallerAndSlower() {
        val rested = MoTemperament.reaction(Mood.CHILL, 1)
        val tired = MoTemperament.reaction(Mood.SLEEPY, 1)
        assertTrue(tired.size < rested.size)
        assertTrue(tired.tempo > rested.tempo)
    }

    @Test
    fun usedToItMeansABriskerNod() {
        val fresh = MoTemperament.reaction(Mood.CHILL, 1)
        val usedTo = MoTemperament.reaction(Mood.CHILL, 30)
        assertTrue(usedTo.tempo < fresh.tempo)
    }

    @Test
    fun sizeAndTempoStayInSafeBounds() {
        for (mood in Mood.entries) for (streak in 1..60) {
            val r = MoTemperament.reaction(mood, streak)
            assertTrue("$mood $streak size ${r.size}", r.size in 0.2f..1f)
            assertTrue("$mood $streak tempo ${r.tempo}", r.tempo in 0.7f..1.4f)
        }
    }

    // --- How a smaller, paced reaction plays ---

    @Test
    fun aPacedProfileStretchesTimingAndKeepsTheRest() {
        val p = MotionProfile.BALANCED
        val slow = p.paced(1.5f)
        assertEquals((p.riseMs * 1.5f).toLong(), slow.riseMs)
        assertEquals((p.settleMs * 1.5f).toLong(), slow.settleMs)
        assertEquals(p.damping, slow.damping, 0f)
        assertEquals(p.reactScale, slow.reactScale, 0f)
        assertTrue(p.paced(1f) === p)
        // Never so short it can't be seen.
        assertTrue(p.paced(0.01f).riseMs >= 40 && p.paced(0.01f).settleMs >= 120)
    }

    @Test
    fun aSmallReactionIsSmallButStillEndsAtRest() {
        val p = MotionProfile.BALANCED
        val big = (0L..p.reactTotalMs).maxOf { MoMotion.pose(it, null, 0, false).react }
        val small = (0L..p.reactTotalMs).maxOf { MoMotion.pose(it, null, 0, false, reactionIntensity = 0.4f).react }
        assertTrue(small < big * 0.6f)
        val brisk = p.paced(0.75f)
        val end = MoMotion.pose(MoMotion.reactionLength(brisk), null, 0, false, reactionIntensity = 0.4f, reactProfile = brisk)
        assertEquals(0f, end.react, 0f)
        assertTrue(MoMotion.reactionLength(brisk) < MoMotion.reactionLength(p))
    }

    @Test
    fun reactionSizeHasAFloorAndACeiling() {
        val p = MotionProfile.PLAYFUL
        assertEquals(0.25f, MoMotion.reactAmount(0.01f, p), 0f)
        assertEquals(1.8f, MoMotion.reactAmount(5f, p), 0f)
    }

    // --- An idle stretch is not falling asleep ---

    @Test
    fun anIdleYawnStretchesWithoutSendingMoToSleep() {
        val mid = MoMotion.pose(1000 + MoMotion.WAKE_MS / 2, null, null, false, yawnStartMs = 1000)
        assertTrue(mid.yawn > 0.9f)
        assertEquals(0f, mid.sleep, 0f)
        assertEquals(0f, MoMotion.pose(1000 + MoMotion.WAKE_MS, null, null, false, yawnStartMs = 1000).yawn, 0f)
        assertEquals(0f, MoMotion.pose(500, null, null, false, yawnStartMs = 1000).yawn, 0f)
    }

    // --- The idle director ---

    private fun picks(mood: Mood, hour: Int = 14, n: Int = 4000, seed: Int = 1): Map<IdleBehavior, Int> {
        val idle = MoIdle(Random(seed))
        var last: IdleBehavior? = null
        val counts = mutableMapOf<IdleBehavior, Int>()
        repeat(n) {
            val b = idle.pick(mood, hour, last)
            counts[b] = (counts[b] ?: 0) + 1
            last = b
        }
        return counts
    }

    @Test
    fun aSleepyMoMostlyYawns() {
        val c = picks(Mood.SLEEPY)
        assertTrue(c.getValue(IdleBehavior.YAWN) > c.values.sum() / 2)
    }

    @Test
    fun aCuriousMoLooksAroundMoreThanItYawns() {
        val c = picks(Mood.CURIOUS)
        assertTrue(c.getValue(IdleBehavior.LOOK_AROUND) > c.getValue(IdleBehavior.YAWN) * 4)
    }

    @Test
    fun everyBehaviourHappensForEveryMood() {
        for (mood in Mood.entries) assertEquals("$mood", IdleBehavior.entries.size, picks(mood).size)
    }

    @Test
    fun lateAtNightMoYawnsMore() {
        val day = picks(Mood.CHILL, hour = 14).getValue(IdleBehavior.YAWN)
        val night = picks(Mood.CHILL, hour = 23).getValue(IdleBehavior.YAWN)
        val early = picks(Mood.CHILL, hour = 3).getValue(IdleBehavior.YAWN)
        assertTrue("night $night vs day $day", night > day * 1.5)
        assertTrue(early > day * 1.5)
    }

    @Test
    fun moRarelyRepeatsItselfBackToBack() {
        val idle = MoIdle(Random(7))
        var last: IdleBehavior? = null
        var repeats = 0
        val n = 4000
        repeat(n) {
            val b = idle.pick(Mood.CHILL, 14, last)
            if (b == last) repeats++
            last = b
        }
        // Independent picks from CHILL's weights would repeat about 27% of the time.
        assertTrue("repeats $repeats of $n", repeats < n * 0.16)
    }

    @Test
    fun theSameSeedGivesTheSameBehaviour() {
        assertEquals(picks(Mood.WOBBLY, seed = 3), picks(Mood.WOBBLY, seed = 3))
    }

    @Test
    fun aTiredMoFidgetsLessOftenAndAPlayfulOneMore() {
        fun average(mood: Mood, profile: MotionProfile): Double {
            val idle = MoIdle(Random(5))
            return (1..500).map { idle.nextDelayMs(mood, profile) }.average()
        }
        assertTrue(average(Mood.SLEEPY, MotionProfile.BALANCED) > average(Mood.CURIOUS, MotionProfile.BALANCED))
        assertTrue(average(Mood.CHILL, MotionProfile.PLAYFUL) < average(Mood.CHILL, MotionProfile.BALANCED))
        assertTrue(average(Mood.CHILL, MotionProfile.BALANCED) < average(Mood.CHILL, MotionProfile.CALM))
    }

    @Test
    fun delaysAreNeverTooShortToBeAnnoying() {
        val idle = MoIdle(Random(9))
        for (mood in Mood.entries) repeat(300) {
            assertTrue(idle.nextDelayMs(mood, MotionProfile.PLAYFUL) >= 4_000L)
        }
    }
}
