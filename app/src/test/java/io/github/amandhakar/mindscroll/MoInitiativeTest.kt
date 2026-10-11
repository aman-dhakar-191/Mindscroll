package io.github.amandhakar.mindscroll

import io.github.amandhakar.mindscroll.mascot.InMemoryMoStore
import io.github.amandhakar.mindscroll.mascot.Initiative
import io.github.amandhakar.mindscroll.mascot.MemoryFacts
import io.github.amandhakar.mindscroll.mascot.MoInitiative
import io.github.amandhakar.mindscroll.mascot.MoInteractionState
import io.github.amandhakar.mindscroll.mascot.MoMemories
import io.github.amandhakar.mindscroll.mascot.MoMemory
import io.github.amandhakar.mindscroll.mascot.MoPolicy
import io.github.amandhakar.mindscroll.mascot.Remark
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MoInitiativeTest {
    private val minute = 60_000L
    private val zone = ZoneOffset.UTC
    private val initiative = MoInitiative(zone)
    private val fresh = MoInteractionState()
    private val longInApp = MoInitiative.CHARACTER_AFTER_MS

    private fun at(hour: Int, minuteOfHour: Int = 0, day: Int = 1): Long =
        LocalDateTime.of(2026, 3, day, hour, minuteOfHour).toInstant(zone).toEpochMilli()

    private fun peakHour(hour: Int, days: Int = 7) = MoMemory.candidates(
        MemoryFacts(0, null, null, List(days) { 10L }, hour, null), at(0),
    )

    @Test
    fun aDueCheckInComesFirstEvenOutsideApps() {
        val checkIn = MoMemories.checkIn(at(12), minutes = 30)
        assertNull(initiative.next(at(12, 29), listOf(checkIn), fresh, inAppForMs = null))
        val due = initiative.next(at(12, 30), listOf(checkIn) + peakHour(12), fresh, inAppForMs = longInApp)!!
        assertEquals(Initiative.Kind.FOLLOW_THROUGH, due.kind)
        assertEquals(checkIn.key, due.memoryKey)
        assertTrue("30" in due.text)
        assertEquals(Remark.FOLLOW_THROUGH, due.remark)
    }

    @Test
    fun aLateCheckInSaysSoAndAVeryLateOneIsDropped() {
        val checkIn = MoMemories.checkIn(at(12), minutes = 30)
        val late = initiative.next(at(13), listOf(checkIn), fresh, inAppForMs = null)!!
        assertFalse("30" in late.text)
        val tooLate = at(12, 30) + MoInitiative.LATE_LIMIT_MS
        assertNull(initiative.next(tooLate, listOf(checkIn), fresh, inAppForMs = null))
    }

    @Test
    fun peakHourIsRememberedAtThatHourOnly() {
        assertNull(initiative.next(at(21, 30), peakHour(22), fresh, inAppForMs = 0))
        val callback = initiative.next(at(22, 5), peakHour(22), fresh, inAppForMs = 0)!!
        assertEquals(Initiative.Kind.CALLBACK, callback.kind)
        assertTrue("10 pm" in callback.text)
        assertEquals(Remark.INITIATIVE, callback.remark)
    }

    @Test
    fun aHunchSoundsLikeAHunch() {
        val sure = initiative.next(at(22), peakHour(22, days = 7), fresh, 0)!!.text
        val unsure = initiative.next(at(22), peakHour(22, days = 3), fresh, 0)!!.text
        assertTrue("think" in unsure || "If I remember right" in unsure)
        assertFalse("think" in sure || "If I remember right" in sure)
    }

    @Test
    fun forgottenAndExpiredMemoriesAreNeverBroughtUp() {
        val forgotten = peakHour(22).map { MoMemories.forget(it, at(1)) }
        assertNull(initiative.next(at(22), forgotten, fresh, inAppForMs = 0))
        val expired = at(0) + MoMemories.NOTICED_FOR_MS
        assertNull(initiative.next(expired, peakHour(0), fresh, inAppForMs = 0))
    }

    @Test
    fun eachKindAtMostOnceADay() {
        val callback = initiative.next(at(22), peakHour(22), fresh, longInApp)!!
        val afterCallback = initiative.said(callback, fresh, at(22))
        val character = initiative.next(at(22, 10), peakHour(22), afterCallback, longInApp)!!
        assertEquals(Initiative.Kind.CHARACTER, character.kind)
        val afterBoth = initiative.said(character, afterCallback, at(22, 10))
        assertNull(initiative.next(at(22, 30), peakHour(22), afterBoth, longInApp))
        // Next day, both may come back.
        assertEquals(Initiative.Kind.CALLBACK, initiative.next(at(22, day = 2), peakHour(22), afterBoth, longInApp)!!.kind)
    }

    @Test
    fun moTellsItsStoryAfterAWhileInAnAppAndItMovesOn() {
        assertNull(initiative.next(at(15), emptyList(), fresh, inAppForMs = longInApp - 1))
        val first = initiative.next(at(15), emptyList(), fresh, inAppForMs = longInApp)!!
        val state = initiative.said(first, fresh, at(15))
        val second = initiative.next(at(15, day = 2), emptyList(), state, inAppForMs = longInApp)!!
        assertNotEquals(first.text, second.text)
    }

    @Test
    fun outsideAppsOnlyCheckInsComeUp() {
        assertNull(initiative.next(at(22), peakHour(22), fresh, inAppForMs = null))
    }

    @Test
    fun policyStillHasTheLastWord() {
        val store = InMemoryMoStore()
        val policy = MoPolicy(store, zone)
        val callback = initiative.next(at(22), peakHour(22), fresh, 0)!!
        policy.snooze(at(21), 2 * 60 * minute)
        assertFalse(policy.trySpeak(at(22), callback.remark))
        store.profile = store.profile.copy(initiatives = false)
        policy.unsnooze()
        assertFalse(policy.trySpeak(at(22), callback.remark))
        // A check-in the user asked for still happens with initiatives off.
        assertTrue(policy.trySpeak(at(22), Remark.FOLLOW_THROUGH))
    }
}
