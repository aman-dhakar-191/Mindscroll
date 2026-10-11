package io.github.amandhakar.mindscroll.mascot

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Draws Mo into a square, in any [MoPose]. One drawing shared by Compose (via the native
 * canvas), the overlay views, and still bitmaps for the widget and notification.
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
    fun draw(canvas: Canvas, size: Float, mood: Mood, left: Float = 0f, top: Float = 0f, pose: MoPose = MoPose()) {
        val s = size
        val body = mood.color.toInt()
        val wave = sin(pose.phase * 2 * PI).toFloat()
        val surprised = pose.react > 0.35f

        canvas.save()
        canvas.translate(left, top)

        // Whole-body motion, pivoting on the bottom middle.
        val pivotX = s * 0.5f
        val pivotY = s * 0.94f
        when (mood) {
            Mood.CHILL -> canvas.scale(1 + 0.025f * wave, 1 - 0.025f * wave, pivotX, pivotY) // breathing
            Mood.WOBBLY -> canvas.rotate(6f * wave, pivotX, pivotY) // swaying
            Mood.SLEEPY -> canvas.rotate(3f * wave, pivotX, pivotY) // nodding off
            else -> Unit
        }
        // Surprise: squash down and spread out, like landing a hop.
        canvas.scale(1 + 0.1f * pose.react, 1 - 0.14f * pose.react, pivotX, pivotY)

        // The curl on top: a little scroll, as in "scroll". It perks up on surprise.
        stroke.color = body
        stroke.strokeWidth = s * 0.07f
        path.reset()
        path.moveTo(s * 0.5f, s * 0.2f)
        val curlLift = s * 0.05f * pose.react
        path.cubicTo(s * 0.5f, s * 0.06f - curlLift, s * 0.66f, s * 0.04f - curlLift, s * 0.68f, s * 0.12f - curlLift)
        canvas.drawPath(path, stroke)

        // Body: a soft, slightly squat blob, with a shine.
        fill.color = body
        rect.set(s * 0.08f, s * 0.16f, s * 0.92f, s * 0.94f)
        canvas.drawRoundRect(rect, s * 0.38f, s * 0.36f, fill)
        fill.color = Color.argb(70, 255, 255, 255)
        rect.set(s * 0.2f, s * 0.24f, s * 0.42f, s * 0.36f)
        canvas.drawOval(rect, fill)

        stroke.color = INK
        stroke.strokeWidth = s * 0.055f
        fill.color = INK
        val eyeY = s * 0.52f
        val eyes = floatArrayOf(s * 0.35f, s * 0.65f)

        if (mood == Mood.CHILL || mood == Mood.CURIOUS || surprised) {
            fill.color = Color.argb(90, 255, 110, 140)
            rect.set(s * 0.15f, s * 0.6f, s * 0.29f, s * 0.69f); canvas.drawOval(rect, fill)
            rect.set(s * 0.71f, s * 0.6f, s * 0.85f, s * 0.69f); canvas.drawOval(rect, fill)
            fill.color = INK
        }

        if (surprised) {
            eyes.forEach { x -> wideEye(canvas, x, eyeY, s * 0.09f, pose.blink) }
        } else when (mood) {
            Mood.CHILL -> eyes.forEach { x -> // happy closed eyes: ∩
                rect.set(x - s * 0.07f, eyeY - s * 0.05f, x + s * 0.07f, eyeY + s * 0.07f)
                canvas.drawArc(rect, 200f, 140f, false, stroke)
            }
            // Glancing left and right.
            Mood.CURIOUS -> eyes.forEach { x -> dotEye(canvas, x + s * 0.025f * wave, eyeY, s * 0.065f, pose.blink) }
            Mood.WOBBLY -> eyes.forEachIndexed { i, x ->
                dotEye(canvas, x, eyeY + s * 0.01f, s * 0.055f, pose.blink)
                // Worried brows, tilted up toward the middle, twitching with the sway.
                val inner = if (i == 0) 1 else -1
                val lift = s * 0.01f * wave
                canvas.drawLine(
                    x - inner * s * 0.08f, eyeY - s * 0.1f - lift,
                    x + inner * s * 0.06f, eyeY - s * 0.15f - lift, stroke,
                )
            }
            // Spinning spirals.
            Mood.DIZZY -> eyes.forEachIndexed { i, x ->
                val turn = pose.phase * 360f * if (i == 0) 1 else -1
                canvas.save()
                canvas.rotate(turn, x, eyeY)
                spiral(canvas, x, eyeY, s * 0.085f)
                canvas.restore()
            }
            Mood.SLEEPY -> eyes.forEach { x -> // heavy lids that droop further as Mo nods
                val droop = s * 0.015f * (wave + 1)
                canvas.drawLine(x - s * 0.08f, eyeY + droop, x + s * 0.08f, eyeY + droop, stroke)
                rect.set(x - s * 0.06f, eyeY - s * 0.04f + droop, x + s * 0.06f, eyeY + s * 0.06f + droop)
                canvas.drawArc(rect, 20f, 140f, false, stroke)
            }
        }

        val mouthY = s * 0.72f
        if (surprised) {
            val open = s * (0.04f + 0.03f * pose.react)
            rect.set(s * 0.5f - open * 0.8f, mouthY - open, s * 0.5f + open * 0.8f, mouthY + open)
            canvas.drawOval(rect, fill)
        } else when (mood) {
            Mood.CHILL -> {
                rect.set(s * 0.38f, mouthY - s * 0.08f, s * 0.62f, mouthY + s * 0.06f)
                canvas.drawArc(rect, 20f, 140f, false, stroke)
            }
            Mood.CURIOUS -> {
                rect.set(s * 0.42f, mouthY - s * 0.04f, s * 0.58f, mouthY + s * 0.07f)
                canvas.drawArc(rect, 0f, 180f, true, fill)
            }
            Mood.WOBBLY -> wave(canvas, s * 0.36f, s * 0.64f, mouthY, s * (0.03f + 0.01f * wave))
            Mood.DIZZY -> wave(canvas, s * 0.36f, s * 0.64f, mouthY, s * 0.03f)
            Mood.SLEEPY -> {
                // A slow yawn.
                val yawn = s * 0.02f * (wave + 1)
                rect.set(s * 0.45f, mouthY - s * 0.04f, s * 0.55f, mouthY + s * 0.06f + yawn)
                canvas.drawOval(rect, fill)
            }
        }
        canvas.restore()

        // Sleepy's "z" floats up and fades, outside the body's sway.
        if (mood == Mood.SLEEPY && !surprised) {
            canvas.save()
            canvas.translate(left, top - s * 0.08f * pose.phase)
            stroke.color = body
            stroke.alpha = (255 * (1 - pose.phase * 0.8f)).toInt()
            stroke.strokeWidth = s * 0.04f
            path.reset()
            path.moveTo(s * 0.8f, s * 0.08f); path.lineTo(s * 0.92f, s * 0.08f)
            path.lineTo(s * 0.8f, s * 0.2f); path.lineTo(s * 0.92f, s * 0.2f)
            canvas.drawPath(path, stroke)
            stroke.alpha = 255
            canvas.restore()
        }
    }

    /** A still Mo, for the widget and notification (they can't animate). */
    fun bitmap(sizePx: Int, mood: Mood): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        draw(Canvas(bitmap), sizePx.toFloat(), mood)
        return bitmap
    }

    /** A dark eye with a glint, squashed shut by [blink]. */
    private fun dotEye(canvas: Canvas, x: Float, y: Float, r: Float, blink: Float) {
        if (blink > 0.85f) {
            canvas.drawLine(x - r, y, x + r, y, stroke)
            return
        }
        val ry = r * (1 - blink)
        rect.set(x - r, y - ry, x + r, y + ry)
        canvas.drawOval(rect, fill)
        if (blink < 0.4f) {
            fill.color = Color.WHITE
            canvas.drawCircle(x - r * 0.35f, y - ry * 0.35f, r * 0.32f, fill)
            fill.color = INK
        }
    }

    /** Surprised: a white eye with a big pupil. */
    private fun wideEye(canvas: Canvas, x: Float, y: Float, r: Float, blink: Float) {
        val ry = r * (1 - blink)
        fill.color = Color.WHITE
        rect.set(x - r, y - ry, x + r, y + ry)
        canvas.drawOval(rect, fill)
        fill.color = INK
        rect.set(x - r * 0.55f, y - ry * 0.55f, x + r * 0.55f, y + ry * 0.55f)
        canvas.drawOval(rect, fill)
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

/**
 * Mo as a plain View, for overlay windows. Blinks now and then and reacts to [react]. With
 * [idleMotion] it also plays the mood's idle loop; leave it off for always-on overlays like the
 * camera patch, which then only redraw during a blink or a reaction.
 */
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

    var idleMotion: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    private var blinkStart: Long? = null
    private var reactStart: Long? = null

    private val blinker = object : Runnable {
        override fun run() {
            blinkStart = SystemClock.uptimeMillis()
            invalidate()
            postDelayed(this, MoMotion.nextBlinkDelayMs())
        }
    }

    /** A new reel counted: Mo looks surprised for a moment. */
    fun react() {
        reactStart = SystemClock.uptimeMillis()
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        postDelayed(blinker, MoMotion.nextBlinkDelayMs())
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(blinker)
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val now = SystemClock.uptimeMillis()
        val size = minOf(width, height).toFloat()
        val pose = MoMotion.pose(now, blinkStart, reactStart, idleMotion)
        MoPainter.draw(canvas, size, mood, (width - size) / 2, (height - size) / 2, pose)

        val blinking = blinkStart?.let { now - it < MoMotion.BLINK_MS } ?: false
        val reacting = reactStart?.let { now - it < MoMotion.REACT_MS } ?: false
        when {
            blinking || reacting -> postInvalidateOnAnimation()
            idleMotion -> postInvalidateDelayed(IDLE_FRAME_MS)
        }
    }

    private companion object {
        /** Idle motion is slow; ~20 fps is smooth enough and much cheaper than 60. */
        const val IDLE_FRAME_MS = 50L
    }
}
