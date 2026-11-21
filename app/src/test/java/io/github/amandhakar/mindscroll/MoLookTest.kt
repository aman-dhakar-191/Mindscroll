package io.github.amandhakar.mindscroll

import android.graphics.Bitmap
import android.graphics.Canvas
import io.github.amandhakar.mindscroll.mascot.MoCharacter
import io.github.amandhakar.mindscroll.mascot.MoPainter
import io.github.amandhakar.mindscroll.mascot.MoPalette
import io.github.amandhakar.mindscroll.mascot.MoPose
import io.github.amandhakar.mindscroll.mascot.Mood
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Every character in every palette draws, stays inside its square, and keeps the moods apart. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class)
class MoLookTest {
    private val size = 160

    private fun render(character: MoCharacter, palette: MoPalette, mood: Mood, stage: Float): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        MoPainter.draw(
            Canvas(bitmap), size.toFloat(), mood, pose = MoPose(),
            previousMoodColor = palette.color(mood), stage = stage, character = character, palette = palette,
        )
        return bitmap
    }

    private fun Bitmap.opaquePixels(): Int {
        val px = IntArray(width * height)
        getPixels(px, 0, width, 0, 0, width, height)
        return px.count { (it ushr 24) != 0 }
    }

    private fun Bitmap.edgeOpaque(): Boolean {
        for (i in 0 until width) {
            if ((getPixel(i, 0) ushr 24) != 0 || (getPixel(i, height - 1) ushr 24) != 0) return true
            if ((getPixel(0, i) ushr 24) != 0 || (getPixel(width - 1, i) ushr 24) != 0) return true
        }
        return false
    }

    @Test
    fun classicIsTheColourMoAlwaysHad() {
        for (mood in Mood.entries) assertEquals(mood.color.toInt(), MoPalette.CLASSIC.color(mood))
    }

    @Test
    fun everyPaletteGivesEachMoodItsOwnColour() {
        for (palette in MoPalette.entries) {
            val colors = Mood.entries.map { palette.color(it) }
            assertEquals("${palette.title} repeats a colour", colors.size, colors.toSet().size)
        }
    }

    @Test
    fun everyCharacterDrawsInEveryMoodAndPalette() {
        for (character in MoCharacter.entries) for (palette in MoPalette.entries) for (mood in Mood.entries) {
            assertTrue("$character/$palette/$mood is empty", render(character, palette, mood, 1f).opaquePixels() > 2_000)
        }
    }

    @Test
    fun theAnimatedStageLeavesRoomAroundEveryCharacter() {
        for (character in MoCharacter.entries) {
            val bitmap = render(character, MoPalette.CLASSIC, Mood.CHILL, MoPainter.ANIMATED_STAGE)
            assertTrue("$character touches the edge of its square", !bitmap.edgeOpaque())
        }
    }

    @Test
    fun theStillBitmapDoesNotClipEarsOrLeaves() {
        for (character in MoCharacter.entries) {
            assertTrue("$character is clipped", !MoPainter.bitmap(128, Mood.CHILL, character = character).edgeOpaque())
        }
    }

    @Test
    fun charactersLookDifferent() {
        val looks = MoCharacter.entries.map { c ->
            val b = render(c, MoPalette.CLASSIC, Mood.CHILL, 1f)
            IntArray(size * size).also { b.getPixels(it, 0, size, 0, 0, size, size) }.toList()
        }
        for (i in looks.indices) for (j in i + 1 until looks.size) assertNotEquals(looks[i], looks[j])
    }
}
