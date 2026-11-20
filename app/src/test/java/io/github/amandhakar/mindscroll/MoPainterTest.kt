package io.github.amandhakar.mindscroll

import android.graphics.Bitmap
import android.graphics.Canvas
import io.github.amandhakar.mindscroll.mascot.MoMotion
import io.github.amandhakar.mindscroll.mascot.MoPainter
import io.github.amandhakar.mindscroll.mascot.MoPose
import io.github.amandhakar.mindscroll.mascot.MotionProfile
import io.github.amandhakar.mindscroll.mascot.Mood
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Draws Mo for real (Robolectric native graphics) and checks the result, so expression and
 * motion changes are verified in pixels and not only in numbers.
 *
 * Set `-Dmo.sheets=<dir>` to also write PNG contact sheets of the frames, to look at.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class)
class MoPainterTest {
    private val size = 160

    private fun render(mood: Mood, pose: MoPose): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        MoPainter.draw(Canvas(bitmap), size.toFloat(), mood, pose = pose, stage = MoPainter.ANIMATED_STAGE)
        return bitmap
    }

    private fun poseAt(
        mood: Mood,
        sinceMs: Long,
        profile: MotionProfile = MotionProfile.BALANCED,
        excited: Boolean = false,
        gesture: String? = null,
    ): MoPose = when (gesture) {
        "peek" -> MoMotion.pose(sinceMs, null, null, false, mood = mood, peekStartMs = 0, profile = profile)
        "glance" -> MoMotion.pose(sinceMs, null, null, false, mood = mood, glanceStartMs = 0, profile = profile)
        "exit" -> MoMotion.pose(sinceMs, null, null, false, mood = mood, exitStartMs = 0, profile = profile)
        else -> MoMotion.pose(sinceMs, null, 0, false, mood = mood, profile = profile, reactExcited = excited)
    }

    private fun Bitmap.differsFrom(other: Bitmap) = !sameAs(other)

    private fun Bitmap.opaquePixels(): Int {
        val px = IntArray(width * height)
        getPixels(px, 0, width, 0, 0, width, height)
        return px.count { (it ushr 24) != 0 }
    }

    private fun Bitmap.edgePixels(): Int {
        var n = 0
        for (i in 0 until width) {
            if ((getPixel(i, 0) ushr 24) != 0) n++
            if ((getPixel(i, height - 1) ushr 24) != 0) n++
            if ((getPixel(0, i) ushr 24) != 0) n++
            if ((getPixel(width - 1, i) ushr 24) != 0) n++
        }
        return n
    }

    @Test
    fun everyMoodDrawsEveryExpression() {
        for (mood in Mood.entries) {
            val poses = listOf(
                MoPose(),
                poseAt(mood, 90 + 120),
                poseAt(mood, 90 + 120, excited = true),
                poseAt(mood, 240, gesture = "peek"),
                poseAt(mood, 175, gesture = "glance"),
                poseAt(mood, 230, gesture = "exit"),
                MoPose(sleep = 1f),
                MoPose(yawn = 1f),
            )
            for (pose in poses) assertTrue("$mood $pose draws", render(mood, pose).opaquePixels() > 500)
        }
    }

    @Test
    fun theSameFaceRendersIdentically() {
        for (mood in Mood.entries) {
            val a = render(mood, poseAt(mood, 200, excited = true))
            val b = render(mood, poseAt(mood, 200, excited = true))
            assertTrue("$mood is deterministic", a.sameAs(b))
        }
    }

    @Test
    fun expressionsLookDifferentFromEachOtherAndFromRest() {
        val rest = render(Mood.CURIOUS, MoPose(brow = MoMotion.baseBrow(Mood.CURIOUS), smile = MoMotion.baseSmile(Mood.CURIOUS)))
        val surprised = render(Mood.CURIOUS, poseAt(Mood.CURIOUS, 90 + 120))
        val excited = render(Mood.CURIOUS, poseAt(Mood.CURIOUS, 90 + 120, excited = true))
        val hello = render(Mood.CURIOUS, poseAt(Mood.CURIOUS, 240, gesture = "peek"))
        val farewell = render(Mood.CURIOUS, poseAt(Mood.CURIOUS, 230, gesture = "exit"))
        val glance = render(Mood.CURIOUS, poseAt(Mood.CURIOUS, 175, gesture = "glance"))
        val all = listOf(rest, surprised, excited, hello, farewell, glance)
        for (i in all.indices) for (j in i + 1 until all.size) {
            assertTrue("expression $i and $j differ", all[i].differsFrom(all[j]))
        }
    }

    @Test
    fun browsMoveWithTheBrowValue() {
        val low = render(Mood.CURIOUS, MoPose(brow = -0.6f, smile = 0.25f))
        val high = render(Mood.CURIOUS, MoPose(brow = 0.9f, smile = 0.25f))
        val none = render(Mood.CURIOUS, MoPose(brow = 0f, smile = 0.25f))
        assertTrue(low.differsFrom(high) && low.differsFrom(none) && high.differsFrom(none))
    }

    @Test
    fun mouthFollowsTheSmile() {
        val flat = render(Mood.CHILL, MoPose(smile = 0.2f))
        val wide = render(Mood.CHILL, MoPose(smile = 1f))
        assertTrue(flat.differsFrom(wide))
    }

    @Test
    fun squashAndStretchChangeTheSilhouette() {
        val rest = render(Mood.CHILL, MoPose())
        val stretched = render(Mood.CHILL, MoPose(react = 1f))
        val squashed = render(Mood.CHILL, MoPose(react = -0.3f))
        assertTrue(rest.differsFrom(stretched) && rest.differsFrom(squashed) && stretched.differsFrom(squashed))
    }

    @Test
    fun nothingTouchesTheViewEdgeThroughAnyStyleOfReaction() {
        // The view clips at its own bounds, so the hop must stay inside them at every moment.
        for (profile in listOf(MotionProfile.CALM, MotionProfile.BALANCED, MotionProfile.PLAYFUL)) {
            for (mood in Mood.entries) for (excited in listOf(false, true)) {
                for (t in 0L..profile.reactTotalMs step 20) {
                    val bitmap = render(mood, poseAt(mood, t, profile, excited))
                    assertFalse("$mood ${profile.damping} t=$t touches the edge", bitmap.edgePixels() > 0)
                }
            }
        }
    }

    @Test
    fun writeContactSheets() {
        val dir = System.getProperty("mo.sheets")?.let(::File) ?: return
        dir.mkdirs()
        fun sheet(name: String, columns: Int, frames: List<Bitmap>) {
            val rows = (frames.size + columns - 1) / columns
            val out = Bitmap.createBitmap(columns * size, rows * size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(out)
            canvas.drawColor(0xFF2A2A3C.toInt())
            frames.forEachIndexed { i, f -> canvas.drawBitmap(f, (i % columns * size).toFloat(), (i / columns * size).toFloat(), null) }
            File(dir, "$name.png").outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        // Expressions: one row per mood.
        val names = listOf("rest", "surprise", "excited", "hello", "glance", "farewell", "asleep", "yawn")
        sheet(
            "expressions", names.size,
            Mood.entries.flatMap { mood ->
                listOf(
                    MoPose(brow = MoMotion.baseBrow(mood), smile = MoMotion.baseSmile(mood)),
                    poseAt(mood, 90 + 120), poseAt(mood, 90 + 120, excited = true),
                    poseAt(mood, 240, gesture = "peek"), poseAt(mood, 175, gesture = "glance"),
                    poseAt(mood, 230, gesture = "exit"), MoPose(sleep = 1f), MoPose(yawn = 1f),
                ).map { render(mood, it) }
            },
        )
        // Styles: the same reaction over time for each style, one row per style.
        val steps = 12
        sheet(
            "styles", steps,
            listOf(MotionProfile.CALM, MotionProfile.BALANCED, MotionProfile.PLAYFUL).flatMap { p ->
                (0 until steps).map { i -> render(Mood.CHILL, poseAt(Mood.CHILL, p.reactTotalMs * i / steps, p)) }
            },
        )
    }
}
