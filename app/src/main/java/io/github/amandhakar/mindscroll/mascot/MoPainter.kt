package io.github.amandhakar.mindscroll.mascot

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Draws Mo into a square. One drawing shared by Compose (via the native canvas), the overlay
 * views, and bitmaps for the widget and notification.
 */
object MoPainter {
    private val INK = 0xFF1B1B2F.toInt()

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val path = Path()
    private val rect = RectF()

    /** Draws Mo filling a [size] × [size] square at ([left], [top]). Main thread only. */
    fun draw(canvas: Canvas, size: Float, mood: Mood, left: Float = 0f, top: Float = 0f) {
        canvas.save()
        canvas.translate(left, top)
        val s = size
        val body = mood.color.toInt()

        // The curl on top: a little scroll, as in "scroll".
        stroke.color = body
        stroke.strokeWidth = s * 0.07f
        path.reset()
        path.moveTo(s * 0.5f, s * 0.2f)
        path.cubicTo(s * 0.5f, s * 0.06f, s * 0.66f, s * 0.04f, s * 0.68f, s * 0.12f)
        canvas.drawPath(path, stroke)

        // Body: a soft, slightly squat blob.
        fill.color = body
        rect.set(s * 0.08f, s * 0.16f, s * 0.92f, s * 0.94f)
        canvas.drawRoundRect(rect, s * 0.38f, s * 0.36f, fill)
        // Shine.
        fill.color = Color.argb(70, 255, 255, 255)
        rect.set(s * 0.2f, s * 0.24f, s * 0.42f, s * 0.36f)
        canvas.drawOval(rect, fill)

        stroke.color = INK
        stroke.strokeWidth = s * 0.055f
        fill.color = INK
        val eyeY = s * 0.52f
        val eyes = floatArrayOf(s * 0.35f, s * 0.65f)

        if (mood == Mood.CHILL || mood == Mood.CURIOUS) {
            fill.color = Color.argb(90, 255, 110, 140)
            rect.set(s * 0.15f, s * 0.6f, s * 0.29f, s * 0.69f); canvas.drawOval(rect, fill)
            rect.set(s * 0.71f, s * 0.6f, s * 0.85f, s * 0.69f); canvas.drawOval(rect, fill)
            fill.color = INK
        }

        when (mood) {
            Mood.CHILL -> eyes.forEach { x -> // happy closed eyes: ∩
                rect.set(x - s * 0.07f, eyeY - s * 0.05f, x + s * 0.07f, eyeY + s * 0.07f)
                canvas.drawArc(rect, 200f, 140f, false, stroke)
            }
            Mood.CURIOUS -> eyes.forEach { x -> dotEye(canvas, x, eyeY, s * 0.065f) }
            Mood.WOBBLY -> eyes.forEachIndexed { i, x ->
                dotEye(canvas, x, eyeY + s * 0.01f, s * 0.055f)
                // Worried brows, tilted up toward the middle.
                val inner = if (i == 0) 1 else -1
                canvas.drawLine(x - inner * s * 0.08f, eyeY - s * 0.1f, x + inner * s * 0.06f, eyeY - s * 0.15f, stroke)
            }
            Mood.DIZZY -> eyes.forEach { x -> spiral(canvas, x, eyeY, s * 0.085f) }
            Mood.SLEEPY -> eyes.forEach { x -> // heavy lids: ‿ with a flat top
                canvas.drawLine(x - s * 0.08f, eyeY, x + s * 0.08f, eyeY, stroke)
                rect.set(x - s * 0.06f, eyeY - s * 0.04f, x + s * 0.06f, eyeY + s * 0.06f)
                canvas.drawArc(rect, 20f, 140f, false, stroke)
            }
        }

        val mouthY = s * 0.72f
        when (mood) {
            Mood.CHILL -> {
                rect.set(s * 0.38f, mouthY - s * 0.08f, s * 0.62f, mouthY + s * 0.06f)
                canvas.drawArc(rect, 20f, 140f, false, stroke)
            }
            Mood.CURIOUS -> {
                rect.set(s * 0.42f, mouthY - s * 0.04f, s * 0.58f, mouthY + s * 0.07f)
                canvas.drawArc(rect, 0f, 180f, true, fill)
            }
            Mood.WOBBLY, Mood.DIZZY -> wave(canvas, s * 0.36f, s * 0.64f, mouthY, s * 0.03f)
            Mood.SLEEPY -> {
                rect.set(s * 0.45f, mouthY - s * 0.04f, s * 0.55f, mouthY + s * 0.06f)
                canvas.drawOval(rect, fill)
                // A tiny "z".
                stroke.strokeWidth = s * 0.04f
                path.reset()
                path.moveTo(s * 0.8f, s * 0.08f); path.lineTo(s * 0.92f, s * 0.08f)
                path.lineTo(s * 0.8f, s * 0.2f); path.lineTo(s * 0.92f, s * 0.2f)
                stroke.color = body
                canvas.drawPath(path, stroke)
            }
        }
        canvas.restore()
    }

    fun bitmap(sizePx: Int, mood: Mood): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        draw(Canvas(bitmap), sizePx.toFloat(), mood)
        return bitmap
    }

    private fun dotEye(canvas: Canvas, x: Float, y: Float, r: Float) {
        canvas.drawCircle(x, y, r, fill)
        fill.color = Color.WHITE
        canvas.drawCircle(x - r * 0.35f, y - r * 0.35f, r * 0.32f, fill)
        fill.color = INK
    }

    private fun spiral(canvas: Canvas, x: Float, y: Float, r: Float) {
        path.reset()
        val turns = 2.2
        val steps = 40
        for (i in 0..steps) {
            val t = i / steps.toDouble()
            val angle = t * turns * 2 * PI
            val radius = r * t
            val px = x + (radius * cos(angle)).toFloat()
            val py = y + (radius * sin(angle)).toFloat()
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        val width = stroke.strokeWidth
        stroke.strokeWidth = width * 0.6f
        canvas.drawPath(path, stroke)
        stroke.strokeWidth = width
    }

    private fun wave(canvas: Canvas, from: Float, to: Float, y: Float, amplitude: Float) {
        path.reset()
        path.moveTo(from, y)
        val step = (to - from) / 4
        for (i in 0 until 4) {
            val x = from + step * i
            path.quadTo(x + step / 2, y + if (i % 2 == 0) -amplitude * 2 else amplitude * 2, x + step, y)
        }
        canvas.drawPath(path, stroke)
    }
}

/** Mo as a plain View, for overlay windows. */
class MoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    var mood: Mood = Mood.CHILL
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    override fun onDraw(canvas: Canvas) {
        val size = minOf(width, height).toFloat()
        MoPainter.draw(canvas, size, mood, (width - size) / 2, (height - size) / 2)
    }
}
