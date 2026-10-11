package io.github.amandhakar.mindscroll

import io.github.amandhakar.mindscroll.data.Durations
import io.github.amandhakar.mindscroll.data.ItemTotal
import io.github.amandhakar.mindscroll.display.AppStat
import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenTimeDisplayTest {
    @Test
    fun durationsReadNaturally() {
        assertEquals("<1m", Durations.format(0))
        assertEquals("<1m", Durations.format(59_999))
        assertEquals("1m", Durations.format(60_000))
        assertEquals("34m", Durations.format(34 * 60_000L + 59_000))
        assertEquals("1h 00m", Durations.format(60 * 60_000L))
        assertEquals("2h 05m", Durations.format(125 * 60_000L))
    }

    @Test
    fun appsWithReelsOrTimeAreMergedAndSorted() {
        val reels = listOf(ItemTotal("ig", 12), ItemTotal("yt", 3))
        val time = listOf(ItemTotal("yt", 600_000), ItemTotal("fb", 120_000), ItemTotal("ig", 60_000))
        val apps = AppStat.merge(reels, time) { it.uppercase() }
        assertEquals(
            listOf(
                AppStat("IG", 12, 60_000),
                AppStat("YT", 3, 600_000),
                AppStat("FB", 0, 120_000), // screen time but no reels still shows
            ),
            apps,
        )
    }
}
