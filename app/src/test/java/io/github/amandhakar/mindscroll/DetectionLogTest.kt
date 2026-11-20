package io.github.amandhakar.mindscroll

import io.github.amandhakar.mindscroll.tracker.DetectionLog
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DetectionLogTest {
    @Before @After
    fun reset() = DetectionLog.clear()

    @Test
    fun textIsOldestFirstAndOneLinePerEntry() {
        DetectionLog.add("first")
        DetectionLog.add("second")
        val lines = DetectionLog.text().lines()
        assertEquals(2, lines.size)
        assertTrue(lines[0].endsWith("first"))
        assertTrue(lines[1].endsWith("second"))
    }

    @Test
    fun aLongSessionKeepsTheNewestTwoThousandLines() {
        repeat(2_500) { DetectionLog.add("line $it") }
        val lines = DetectionLog.text().lines()
        assertEquals(2_000, lines.size)
        assertTrue(lines.first().endsWith("line 500"))
        assertTrue(lines.last().endsWith("line 2499"))
        assertEquals(2_000, DetectionLog.entries.value.size)
    }

    @Test
    fun clearEmptiesBothTheTextAndTheEntries() {
        DetectionLog.add("x")
        DetectionLog.clear()
        assertEquals("", DetectionLog.text())
        assertTrue(DetectionLog.entries.value.isEmpty())
    }
}
