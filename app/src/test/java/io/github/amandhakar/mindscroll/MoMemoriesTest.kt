package io.github.amandhakar.mindscroll

import io.github.amandhakar.mindscroll.mascot.Confidence
import io.github.amandhakar.mindscroll.mascot.MemoryFacts
import io.github.amandhakar.mindscroll.mascot.MemorySource
import io.github.amandhakar.mindscroll.mascot.MoMemories
import io.github.amandhakar.mindscroll.mascot.MoMemory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MoMemoriesTest {
    private val day = 24 * 60 * 60_000L
    private val now = 100 * day

    private fun facts(days: Int, peakHour: Int? = 22, app: String? = "Instagram") = MemoryFacts(
        today = 5, yesterdaySoFar = null, yesterdayTotal = null,
        pastDays = List(days) { 10L }, peakHour = peakHour, favouriteApp = app,
    )

    private fun peak(at: Long = now, hour: Int = 22, days: Int = 7) =
        MoMemory.candidates(facts(days, hour), at).first { it.key == MoMemories.PEAK_HOUR }

    @Test
    fun patternsAreNoticedNotToldAndFade() {
        val all = MoMemory.candidates(facts(7), now)
        assertEquals(listOf(MoMemories.PEAK_HOUR, MoMemories.FAVOURITE_APP), all.map { it.key })
        assertTrue(all.all { it.source == MemorySource.NOTICED && it.expiresAt == now + MoMemories.NOTICED_FOR_MS })
        assertEquals("We scroll most around 10 pm", all[0].content)
        assertTrue(all[0].isLive(now + MoMemories.NOTICED_FOR_MS - 1))
        assertFalse(all[0].isLive(now + MoMemories.NOTICED_FOR_MS))
    }

    @Test
    fun moreDaysMakeMoSurer() {
        assertEquals(Confidence.LOW, peak(days = 1).confidence)
        assertEquals(Confidence.MEDIUM, peak(days = 3).confidence)
        assertEquals(Confidence.HIGH, peak(days = 6).confidence)
        assertFalse(peak(days = 3).sure)
        assertTrue(peak(days = 6).sure)
    }

    @Test
    fun nothingToNoticeWithoutPatterns() {
        assertTrue(MoMemory.candidates(facts(7, peakHour = null, app = null), now).isEmpty())
    }

    @Test
    fun noticingAgainRefreshesButKeepsWhenMoFirstKnew() {
        val first = peak()
        val again = MoMemories.merge(first, peak(at = now + day), now + day)!!
        assertEquals(first.createdAt, again.createdAt)
        assertEquals(now + day + MoMemories.NOTICED_FOR_MS, again.expiresAt)
        val changed = MoMemories.merge(first, peak(at = now + day, hour = 21), now + day)!!
        assertEquals("21", changed.value)
        assertEquals(now + day, changed.createdAt)
    }

    @Test
    fun keptMemoriesStayAsTheUserKeptThem() {
        val kept = MoMemories.keep(peak())
        assertNull(kept.expiresAt)
        assertTrue(kept.sure)
        assertNull(MoMemories.merge(kept, peak(at = now + day, hour = 21), now + day))
    }

    @Test
    fun forgottenMemoriesAreNotNoticedAgainForAWhile() {
        val forgotten = MoMemories.forget(peak(), now)
        assertFalse(forgotten.isLive(now))
        assertEquals("", forgotten.content)
        assertNull(MoMemories.merge(forgotten, peak(at = now + day), now + day))
        val later = now + MoMemories.FORGOTTEN_FOR_MS
        assertEquals("22", MoMemories.merge(forgotten, peak(at = later), later)!!.value)
    }

    @Test
    fun expiredAndLongForgottenMemoriesAreDone() {
        val memory = peak()
        assertFalse(MoMemories.isDone(memory, now))
        assertTrue(MoMemories.isDone(memory, now + MoMemories.NOTICED_FOR_MS))
        val forgotten = MoMemories.forget(memory, now)
        assertFalse(MoMemories.isDone(forgotten, now + day))
        assertTrue(MoMemories.isDone(forgotten, now + MoMemories.FORGOTTEN_FOR_MS))
        assertFalse(MoMemories.isDone(MoMemories.keep(memory), now + 365 * day))
    }

    @Test
    fun checkInsAreToldAndDue() {
        val checkIn = MoMemories.checkIn(now, minutes = 30)
        assertEquals(MemorySource.TOLD_ME, checkIn.source)
        assertEquals(now + 30 * 60_000L, checkIn.dueAt)
        assertTrue(checkIn.sure)
    }
}
