package io.github.amandhakar.mindscroll

import android.app.Notification
import io.github.amandhakar.mindscroll.display.IslandKind
import io.github.amandhakar.mindscroll.display.IslandNotification
import io.github.amandhakar.mindscroll.display.IslandRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IslandRulesTest {
    private val own = "io.github.amandhakar.mindscroll"
    private val ongoing = Notification.FLAG_ONGOING_EVENT

    @Test fun xiaomiIslandDataCounts() {
        assertEquals(
            IslandKind.XIAOMI_FOCUS,
            IslandRules.kind(own, "com.example.delivery", 0, listOf("android.title", "miui.focus.param")),
        )
    }

    @Test fun androidLiveUpdateCounts() {
        assertEquals(IslandKind.LIVE_UPDATE, IslandRules.kind(own, "com.example.ride", IslandRules.FLAG_PROMOTED or ongoing, emptyList()))
    }

    @Test fun xiaomiMarkWinsOverPromotedFlag() {
        assertEquals(
            IslandKind.XIAOMI_FOCUS,
            IslandRules.kind(own, "com.example.ride", IslandRules.FLAG_PROMOTED, listOf("miui.focus.param")),
        )
    }

    @Test fun plainOngoingIsOnlyOngoing() {
        assertEquals(IslandKind.ONGOING, IslandRules.kind(own, "com.example.music", ongoing, listOf("android.mediaSession")))
    }

    @Test fun ordinaryNotificationIsNoIsland() {
        assertNull(IslandRules.kind(own, "com.example.chat", 0, listOf("android.title", "android.text")))
    }

    @Test fun ownNotificationNeverCounts() {
        assertNull(IslandRules.kind(own, own, IslandRules.FLAG_PROMOTED or ongoing, listOf("miui.focus.param")))
    }

    @Test fun plainOngoingHidesOnlyWhenAskedFor() {
        assertFalse(IslandRules.hides(IslandKind.ONGOING, anyOngoing = false))
        assertTrue(IslandRules.hides(IslandKind.ONGOING, anyOngoing = true))
        assertTrue(IslandRules.hides(IslandKind.XIAOMI_FOCUS, anyOngoing = false))
        assertTrue(IslandRules.hides(IslandKind.LIVE_UPDATE, anyOngoing = false))
    }

    @Test fun triggerPrefersIslandMarks() {
        val music = IslandNotification("com.example.music", IslandKind.ONGOING)
        val ride = IslandNotification("com.example.ride", IslandKind.LIVE_UPDATE)
        val food = IslandNotification("com.example.food", IslandKind.XIAOMI_FOCUS)
        assertNull(IslandRules.trigger(listOf(music), anyOngoing = false))
        assertEquals(music, IslandRules.trigger(listOf(music), anyOngoing = true))
        assertEquals(ride, IslandRules.trigger(listOf(music, ride), anyOngoing = true))
        assertEquals(food, IslandRules.trigger(listOf(ride, food, music), anyOngoing = false))
        assertNull(IslandRules.trigger(emptyList(), anyOngoing = true))
    }

    @Test fun logShowsOnlyNonStandardExtraNames() {
        assertEquals(
            listOf("miui.focus.param", "miui.focus.pics"),
            IslandRules.loggableKeys(listOf("android.title", "miui.focus.pics", "android.text", "miui.focus.param")),
        )
    }
}
