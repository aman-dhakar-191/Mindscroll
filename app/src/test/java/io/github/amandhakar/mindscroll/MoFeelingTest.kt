package io.github.amandhakar.mindscroll

import io.github.amandhakar.mindscroll.mascot.Mo
import io.github.amandhakar.mindscroll.mascot.Mood
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** What Mo says about how Mo feels, which keeps to Mo's voice. */
class MoFeelingTest {
    private fun allLines(): List<String> {
        val seeds = 0L..40L
        return Mood.entries.flatMap { mood -> seeds.map { Mo.feeling(mood, rested = false, seed = it) } } +
            seeds.map { Mo.feeling(Mood.CHILL, rested = true, seed = it) }
    }

    @Test
    fun everyMoodHasSeveralDifferentLines() {
        for (mood in Mood.entries) {
            val lines = (0L..40L).map { Mo.feeling(mood, rested = false, seed = it) }.toSet()
            assertTrue("$mood has only ${lines.size}", lines.size >= 4)
        }
        assertTrue((0L..40L).map { Mo.feeling(Mood.WOBBLY, rested = true, seed = it) }.toSet().size >= 3)
    }

    @Test
    fun linesTalkAboutMoNotTheCountAndLeaveNoPlaceholder() {
        for (line in allLines()) {
            assertFalse("placeholder in: $line", "{" in line || "}" in line)
            assertFalse("a count in: $line", line.any { it.isDigit() })
        }
    }

    @Test
    fun linesKeepToMosVoice() {
        for (line in allLines()) {
            assertTrue("empty", line.isNotBlank())
            assertTrue("too long for a bubble: $line", line.length <= 60)
            assertFalse("exclamation mark: $line", "!" in line)
            assertFalse("emoji in: $line", line.any { Character.isSurrogate(it) || it.code in 0x2600..0x27BF })
        }
    }

    @Test
    fun aRestedMoSaysSoWhateverItsMood() {
        val rested = (0L..40L).map { Mo.feeling(Mood.DIZZY, rested = true, seed = it) }.toSet()
        val tired = (0L..40L).map { Mo.feeling(Mood.DIZZY, rested = false, seed = it) }.toSet()
        assertTrue(rested.intersect(tired).isEmpty())
    }

    @Test
    fun theSameSeedGivesTheSameLine() {
        assertEquals(Mo.feeling(Mood.SLEEPY, false, 12), Mo.feeling(Mood.SLEEPY, false, 12))
    }
}
