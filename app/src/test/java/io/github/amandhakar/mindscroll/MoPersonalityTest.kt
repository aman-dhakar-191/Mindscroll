package io.github.amandhakar.mindscroll

import io.github.amandhakar.mindscroll.mascot.MemoryFacts
import io.github.amandhakar.mindscroll.mascot.Mo
import io.github.amandhakar.mindscroll.mascot.MoMemory
import io.github.amandhakar.mindscroll.mascot.MoMoments
import io.github.amandhakar.mindscroll.mascot.MoMotion
import io.github.amandhakar.mindscroll.mascot.MoPolicy
import io.github.amandhakar.mindscroll.mascot.MoSleep
import io.github.amandhakar.mindscroll.mascot.Mood
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MoMomentsTest {
    private val minute = 60_000L

    @Test
    fun fastRunOfSwipesGetsARemark() {
        val m = MoMoments()
        val said = (1..6).map { i -> m.onReel(i * 5_000L, total = 30L + i) }
        assertNull(said[4])
        assertNotNull(said[5]) // sixth reel within a minute
        assertTrue("{x}" !in said[5]!!)
    }

    @Test
    fun slowScrollingStaysQuiet() {
        val m = MoMoments()
        val said = (1..20).map { i -> m.onReel(i * 30_000L, total = 60L + i) } // no milestones in 61..80
        assertTrue(said.all { it == null })
    }

    @Test
    fun milestonesAreSpoken() {
        val m = MoMoments()
        assertEquals("50! Neck stretch?", m.onReel(10 * minute, total = 50))
    }

    @Test
    fun moSpeaksAtMostEveryFewMinutes() {
        val m = MoMoments()
        assertNotNull(m.onMood(0, Mood.CHILL).let { m.onMood(1_000, Mood.WOBBLY) })
        assertNull(m.onMood(2_000, Mood.DIZZY)) // too soon after the last remark
        assertNotNull(m.onMood(2_000 + MoPolicy.GAP_MS, Mood.SLEEPY))
    }

    @Test
    fun calmingDownIsNotAnnounced() {
        val m = MoMoments()
        m.onMood(0, Mood.DIZZY)
        assertNull(m.onMood(10 * minute, Mood.CHILL))
    }

    @Test
    fun everyFifthOpenIsNoticed() {
        val m = MoMoments()
        assertNull(m.onAppOpened(0, "Instagram", openNumber = 4))
        assertEquals("Instagram again? That's 5 times today.", m.onAppOpened(minute, "Instagram", openNumber = 5))
    }

    @Test
    fun comingBackAfterABreakGetsAWelcome() {
        val m = MoMoments()
        m.onReel(0, total = 3)
        val line = m.onAppOpened(MoMoments.BREAK_MS + 1, "Instagram", openNumber = 2)
        assertTrue(line in listOf(
            "Welcome back. I napped. It was great.",
            "Oh, hi. I was dreaming of grass. Real grass.",
            "You're back. I feel so rested. Thank you for that.",
            "*stretches* that break was lovely.",
        ))
    }

    @Test
    fun shortNapsWakeQuietly() {
        val m = MoMoments()
        assertNull(m.onWake(0, sleptMs = MoSleep.LONG_SLEEP_MS - 1))
        assertNotNull(m.onWake(minute, sleptMs = MoSleep.LONG_SLEEP_MS))
    }

    @Test
    fun neverMoreThanTheHourlyLimit() {
        val m = MoMoments()
        val said = (0 until 40).mapNotNull { i -> m.onRemark(i * MoPolicy.GAP_MS / 2, "hi") }
        // 40 tries over an hour; only one per GAP_MS gets through, and never more than the cap.
        assertTrue(said.size <= MoPolicy.MAX_PER_HOUR)
    }

    @Test
    fun linesTakeTurnsInsteadOfRepeating() {
        val m = MoMoments()
        val first = m.onAppOpened(0, "YouTube", 5)
        val second = m.onAppOpened(MoPolicy.GAP_MS, "YouTube", 10)
        assertTrue(first != second)
    }
}

class MoSleepTest {
    private val minute = 60_000L

    @Test
    fun dozesOffAfterAWhileWithoutReels() {
        val s = MoSleep(0)
        assertFalse(s.onTick(MoSleep.DOZE_MS - 1))
        assertTrue(s.onTick(MoSleep.DOZE_MS))
        assertTrue(s.asleep)
        assertFalse(s.onTick(MoSleep.DOZE_MS + minute)) // already asleep
    }

    @Test
    fun wakesWhenAReelAppIsUsedAndSaysHowLongItSlept() {
        val s = MoSleep(0)
        s.onTick(MoSleep.DOZE_MS)
        assertEquals(30 * minute, s.onActivity(MoSleep.DOZE_MS + 30 * minute))
        assertFalse(s.asleep)
        assertNull(s.onActivity(MoSleep.DOZE_MS + 31 * minute)) // already awake
    }

    @Test
    fun sleepsWithTheScreenOffAndWakesWithItOn() {
        val s = MoSleep(0)
        s.onScreenOff(minute)
        assertTrue(s.asleep)
        assertEquals(20 * minute, s.onScreenOn(21 * minute))
        // Picking up the phone counts as activity: no dozing right away.
        assertFalse(s.onTick(22 * minute))
    }
}

class MoMemoryTest {
    private fun facts(
        today: Long = 20,
        yesterdaySoFar: Long? = 40,
        yesterdayTotal: Long? = 90,
        pastDays: List<Long> = listOf(90),
        peakHour: Int? = null,
        favouriteApp: String? = null,
    ) = MemoryFacts(today, yesterdaySoFar, yesterdayTotal, pastDays, peakHour, favouriteApp)

    @Test
    fun comparesWithYesterdayAtTheSameTime() {
        assertEquals("20 fewer than yesterday at this time. I noticed.", MoMemory.insights(facts()).first())
        assertEquals("Right on pace with yesterday.", MoMemory.insights(facts(today = 42)).first())
        assertTrue(MoMemory.insights(facts(today = 60)).first().startsWith("20 more"))
    }

    @Test
    fun countsCalmDayStreaks() {
        val insights = MoMemory.insights(facts(pastDays = listOf(120, 10, 30, 5)))
        assertTrue("3 calm days in a row. I've never been so well rested." in insights)
        assertTrue(MoMemory.insights(facts(pastDays = listOf(10, 120))).none { "calm days" in it })
    }

    @Test
    fun mentionsPeakHourAndFavouriteApp() {
        val insights = MoMemory.insights(facts(peakHour = 23, favouriteApp = "Instagram"))
        assertTrue("We scroll most around 11 pm. I get sleepy then too." in insights)
        assertTrue("Instagram is our usual spot this week." in insights)
        assertEquals("midnight", MoMemory.hourName(0))
        assertEquals("noon", MoMemory.hourName(12))
    }

    @Test
    fun newUsersGetNoComparisons() {
        val insights = MoMemory.insights(facts(yesterdaySoFar = null, yesterdayTotal = null, pastDays = emptyList()))
        assertTrue(insights.isEmpty())
        assertNull(MoMemory.dayStart(facts(yesterdayTotal = null)))
    }

    @Test
    fun dayStartRemembersYesterday() {
        assertEquals("New day. Yesterday was 90 reels. Fresh count, fresh blob.", MoMemory.dayStart(facts()))
        assertEquals("New day. Yesterday was a zero. My favorite number.", MoMemory.dayStart(facts(yesterdayTotal = 0, pastDays = listOf(0))))
    }
}

class MoSleepPoseTest {
    @Test
    fun asleepPoseKeepsEyesShutAndWakingOpensThem() {
        assertEquals(1f, MoMotion.pose(0, null, null, idle = false, asleep = true).sleep, 0f)
        val waking = MoMotion.pose(500, null, null, idle = false, wakeStartMs = 0)
        assertTrue(waking.sleep in 0.01f..0.99f)
        assertTrue(waking.yawn > 0.5f)
        val awake = MoMotion.pose(MoMotion.WAKE_MS, null, null, idle = false, wakeStartMs = 0)
        assertEquals(0f, awake.sleep, 0f)
        assertEquals(0f, awake.yawn, 0f)
    }

    @Test
    fun noBlinkingInYourSleep() {
        assertEquals(0f, MoMotion.pose(90, blinkStartMs = 0, reactStartMs = null, idle = false, asleep = true).blink, 0f)
    }
}

class MoVoiceTest {
    @Test
    fun everyStatusLineFillsItsPlaceholders() {
        for (count in 0L..600L) for (hour in 0..23) for (mood in Mood.entries) for (rested in listOf(false, true)) {
            val line = Mo.line(count, hour, mood, rested)
            assertTrue(line.isNotBlank())
            assertFalse("unfilled placeholder in '$line'", "{" in line)
        }
    }

    @Test
    fun greetingsCoverTheWholeDay() {
        for (hour in 0..23) for (seed in 0L..5L) assertTrue(Mo.greeting(hour, seed).isNotBlank())
    }
}
