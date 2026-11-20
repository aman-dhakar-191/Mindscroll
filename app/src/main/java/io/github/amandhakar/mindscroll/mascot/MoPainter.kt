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
import io.github.amandhakar.mindscroll.tracker.DetectionLog
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Draws Mo into a square, in any [MoPose]. One drawing shared by Compose (via the native
 * canvas), the overlay views, and still bitmaps for the widget and notification.
 */
object MoPainter {
    /**
     * Animated Mo is drawn this much smaller than the square, anchored at the bottom. At full
     * size the curl on top sits ~2% from the edge, so a stretch, a hop, a yawn or a lean would
     * be clipped by the view. Static bitmaps (widget, notification) draw at full size.
     */
    const val ANIMATED_STAGE = 0.9f

    /** Highest point of the curl at rest, as a fraction of the square (stroke included; measured). */
    private const val CURL_TOP = 0.0375f
    private val INK = 0xFF1B1B2F.toInt()
    private val colorEvaluator = android.animation.ArgbEvaluator()

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val path = Path()
    private val eyeX = FloatArray(2)
    private val rect = RectF()

    /** Draws Mo filling a [size] × [size] square at ([left], [top]). Main thread only. */
    fun draw(
        canvas: Canvas,
        size: Float,
        mood: Mood,
        left: Float = 0f,
        top: Float = 0f,
        pose: MoPose = MoPose(),
        previousMoodColor: Int = mood.color.toInt(),
        stage: Float = 1f,
    ) {
        val s = size
        val body = if (pose.moodTransition < 1f) {
            colorEvaluator.evaluate(pose.moodTransition, previousMoodColor, mood.color.toInt()) as Int
        } else mood.color.toInt()
        val wave = sin(pose.phase * 2 * PI).toFloat()
        val asleep = pose.sleep > 0.5f
        val yawning = !asleep && pose.yawn > 0.25f
        val excited = !asleep && !yawning && pose.excite > 0.35f && pose.whirl <= 0.35f
        val surprised = !asleep && !yawning && !excited && pose.whirl <= 0.35f && pose.react > 0.35f
        // Spiral eyes: scrolling too fast, or mesmerised by one reel. Takes over the face.
        val whirling = !asleep && !yawning && pose.whirl > 0.35f
        // Saying goodbye: eyes close in a smile as Mo turns away.
        val farewell = !asleep && !yawning && !excited && !surprised && !whirling && pose.exit > 0.35f
        val amp = pose.idleAmplitude
        // Eyelids still heavy while waking up.
        val blink = maxOf(pose.blink, pose.sleep)

        canvas.save()
        canvas.translate(left, top)
        if (stage != 1f) canvas.scale(stage, stage, s * 0.5f, s * 0.94f)

        // A small lean-out makes Mo look around the edge before settling back in.
        if (pose.peek > 0f) {
            canvas.translate(s * 0.12f * pose.peek, 0f)
        }
        if (pose.exit > 0f) {
            // Turning away to leave, with a small wave of the head as it goes.
            canvas.translate(-s * 0.10f * pose.exit, 0f)
            canvas.rotate(-8f * pose.exit + 2.5f * pose.exit * sin(pose.exit * 14f), s * 0.5f, s * 0.94f)
        }
        // Whole-body motion, pivoting on the bottom middle.
        val pivotX = s * 0.5f
        val pivotY = s * 0.94f
        // A tiny vertical drift makes breathing feel less like a scale transform.
        canvas.translate(0f, -s * 0.008f * wave * amp)
        when {
            // Asleep: slow, deep breaths whatever the mood.
            asleep -> canvas.scale(1 + 0.04f * wave, 1 - 0.04f * wave, pivotX, pivotY)
            // Waking: a big stretch, tall and thin.
            pose.yawn > 0f -> canvas.scale(1 - 0.06f * pose.yawn, 1 + 0.1f * pose.yawn, pivotX, pivotY)
            else -> when (mood) {
                Mood.CHILL -> canvas.scale(1 + 0.018f * wave * amp, 1 - 0.022f * wave * amp, pivotX, pivotY) // soft breathing
                Mood.WOBBLY -> canvas.rotate(4f * wave * amp, pivotX, pivotY) // gentle, slower sway
                Mood.SLEEPY -> canvas.rotate(2.2f * wave * amp, pivotX, pivotY) // sleepy nod
                Mood.CURIOUS -> canvas.rotate(1.2f * wave * amp, pivotX, pivotY) // attentive little tilt
                Mood.DIZZY -> canvas.rotate(2.8f * wave * amp, pivotX, pivotY) // gently unsteady
            }
        }
        // Dizzy: the whole body wobbles as the eyes spin.
        if (pose.whirl > 0f) {
            canvas.rotate(6f * pose.whirl * sin(pose.whirlTurn * 2f * PI.toFloat() * 0.8f), pivotX, pivotY)
        }
        // App switch: the body turns the way Mo's eyes are going.
        if (pose.glance != 0f) {
            canvas.translate(s * 0.03f * pose.glance, 0f)
            canvas.rotate(5f * pose.glance, pivotX, pivotY)
        }
        if (pose.peek > 0f) {
            canvas.rotate(-7f * pose.peek, pivotX, pivotY)
        }
        // Anticipation: a quick crouch that releases into the pop.
        if (pose.anticipation > 0f) {
            canvas.scale(1 + 0.05f * pose.anticipation, 1 - 0.07f * pose.anticipation, pivotX, pivotY)
        }
        // The startle is one spring value, so the body and face stay in step. Positive: stretch
        // up and lift off. The spring then overshoots below zero, which squashes wide as Mo
        // lands, and settles. Applied continuously (no threshold), so nothing pops in.
        if (pose.react != 0f) {
            // Capped so no style or intensity can stretch Mo past the headroom the stage leaves.
            val r = pose.react.coerceIn(-0.6f, 1.25f)
            val sy = 1 + 0.08f * r
            val curlTop = CURL_TOP - 0.02f * r.coerceIn(0f, 1f)
            // The hop is whatever headroom the stretch leaves, so the curl can never leave the view.
            val headroom = ((pivotY - s * 0.012f) / stage - (pivotY - s * curlTop) * sy)
            val hop = minOf(s * 0.03f * pose.hop * r.coerceAtLeast(0f), headroom.coerceAtLeast(0f))
            canvas.translate(0f, -hop)
            canvas.scale(1 - 0.05f * r, sy, pivotX, pivotY)
        }
        // Mood changes settle with a small, soft bounce rather than snapping instantly.
        if (pose.moodTransition < 1f) {
            val settle = kotlin.math.sin((1f - pose.moodTransition) * Math.PI).toFloat()
            canvas.scale(1f + 0.018f * settle, 1f - 0.018f * settle, pivotX, pivotY)
        }

        // The curl on top: a little scroll, as in "scroll". It perks up on surprise.
        stroke.color = body
        stroke.strokeWidth = s * 0.07f
        path.rewind()
        path.moveTo(s * 0.5f, s * 0.2f)
        val curlLift = s * 0.02f * pose.react.coerceIn(0f, 1f)
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
        eyeX[0] = s * 0.35f
        eyeX[1] = s * 0.65f
        // Eyes open a little wider while Mo is paying attention.
        val attention = 1f + 0.3f * pose.peek + 0.2f * abs(pose.glance)

        if (!asleep && (mood == Mood.CHILL || mood == Mood.CURIOUS || surprised || excited || farewell)) {
            fill.color = Color.argb(if (excited) 130 else 90, 255, 110, 140)
            rect.set(s * 0.15f, s * 0.6f, s * 0.29f, s * 0.69f); canvas.drawOval(rect, fill)
            rect.set(s * 0.71f, s * 0.6f, s * 0.85f, s * 0.69f); canvas.drawOval(rect, fill)
            fill.color = INK
        }

        if (asleep) {
            for (x in eyeX) { // peacefully shut: ‿
                rect.set(x - s * 0.07f, eyeY - s * 0.06f, x + s * 0.07f, eyeY + s * 0.04f)
                canvas.drawArc(rect, 20f, 140f, false, stroke)
            }
        } else if (yawning) {
            for (x in eyeX) { // squeezed shut mid-yawn: ∩
                rect.set(x - s * 0.07f, eyeY - s * 0.04f, x + s * 0.07f, eyeY + s * 0.08f)
                canvas.drawArc(rect, 200f, 140f, false, stroke)
            }
        } else if (excited || farewell) {
            // Beaming: ^ ^, a touch bigger when it's real joy.
            val grow = if (excited) 1.15f else 1f
            for (x in eyeX) {
                rect.set(x - s * 0.07f * grow, eyeY - s * 0.05f, x + s * 0.07f * grow, eyeY + s * 0.07f * grow)
                canvas.drawArc(rect, 200f, 140f, false, stroke)
            }
        } else if (whirling) {
            eyeX.forEachIndexed { i, x ->
                canvas.save()
                canvas.rotate(pose.whirlTurn * 360f * if (i == 0) 1 else -1, x, eyeY)
                spiral(canvas, x, eyeY, s * 0.085f)
                canvas.restore()
            }
        } else if (surprised) {
            for (x in eyeX) wideEye(canvas, x, eyeY, s * (0.09f + 0.012f * pose.react.coerceIn(0f, 1f)), blink)
        } else when (mood) {
            Mood.CHILL -> for (x0 in eyeX) { // happy closed eyes: ∩, sliding with a glance
                val x = x0 + s * pose.gazeX
                rect.set(x - s * 0.07f, eyeY - s * 0.05f, x + s * 0.07f, eyeY + s * 0.07f)
                canvas.drawArc(rect, 200f, 140f, false, stroke)
            }
            // Glancing left and right.
            Mood.CURIOUS -> for (x in eyeX) dotEye(canvas, x + s * (0.025f * wave * amp + pose.gazeX), eyeY + s * pose.gazeY, s * 0.065f * attention, blink)
            Mood.WOBBLY -> for (x in eyeX) dotEye(canvas, x + s * pose.gazeX, eyeY + s * (0.01f + pose.gazeY), s * 0.055f * attention, blink)
            // Spinning spirals.
            Mood.DIZZY -> eyeX.forEachIndexed { i, x ->
                val turn = pose.phase * 360f * if (i == 0) 1 else -1
                canvas.save()
                canvas.rotate(turn, x, eyeY)
                spiral(canvas, x + s * pose.gazeX, eyeY + s * pose.gazeY, s * 0.085f)
                canvas.restore()
            }
            Mood.SLEEPY -> for (x in eyeX) { // heavy lids that droop further as Mo nods
                val droop = s * 0.015f * (wave + 1)
                canvas.drawLine(x - s * 0.08f, eyeY + droop, x + s * 0.08f, eyeY + droop, stroke)
                rect.set(x - s * 0.06f, eyeY - s * 0.04f + droop, x + s * 0.06f, eyeY + s * 0.06f + droop)
                canvas.drawArc(rect, 20f, 140f, false, stroke)
            }
        }

        if (!asleep && !yawning) brows(canvas, s, eyeY, pose, mood, wave)

        val mouthY = s * 0.72f
        if (asleep) {
            // A small, slightly open sleeping mouth.
            rect.set(s * 0.46f, mouthY - s * 0.02f, s * 0.54f, mouthY + s * 0.04f)
            canvas.drawOval(rect, fill)
        } else if (yawning) {
            val open = s * (0.04f + 0.08f * pose.yawn)
            rect.set(s * 0.5f - open * 0.7f, mouthY - open * 0.8f, s * 0.5f + open * 0.7f, mouthY + open)
            canvas.drawOval(rect, fill)
        } else if (whirling) {
            wave(canvas, s * 0.36f, s * 0.64f, mouthY, s * 0.03f)
        } else if (excited) {
            // An open grin that widens with the joy.
            val open = pose.excite.coerceIn(0f, 1f)
            rect.set(s * 0.37f, mouthY - s * 0.05f, s * 0.63f, mouthY - s * 0.05f + s * 0.17f * open)
            canvas.drawArc(rect, 0f, 180f, true, fill)
        } else if (surprised) {
            val open = s * (0.045f + 0.045f * pose.react.coerceIn(0f, 1f))
            rect.set(s * 0.5f - open * 0.8f, mouthY - open, s * 0.5f + open * 0.8f, mouthY + open)
            canvas.drawOval(rect, fill)
        } else when (mood) {
            // The smile deepens with [MoPose.smile]: a hello, a farewell or a happy moment.
            Mood.CHILL -> {
                val k = (pose.smile / MoMotion.baseSmile(Mood.CHILL)).coerceIn(0.3f, 1.7f)
                rect.set(s * 0.5f - s * 0.12f * (0.8f + 0.2f * k), mouthY - s * 0.08f * k, s * 0.5f + s * 0.12f * (0.8f + 0.2f * k), mouthY + s * 0.06f * k)
                canvas.drawArc(rect, 20f, 140f, false, stroke)
            }
            Mood.CURIOUS -> {
                val k = (pose.smile / MoMotion.baseSmile(Mood.CURIOUS)).coerceIn(0.5f, 2.2f)
                rect.set(s * 0.5f - s * 0.08f * k.coerceAtMost(1.6f), mouthY - s * 0.04f, s * 0.5f + s * 0.08f * k.coerceAtMost(1.6f), mouthY + s * 0.07f * k)
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

        // A "z" floats up and fades, outside the body's sway: running on fumes, or asleep.
        if (asleep || (mood == Mood.SLEEPY && !surprised && !excited && !farewell && !yawning && !whirling)) {
            canvas.save()
            canvas.translate(left, top - s * 0.08f * pose.phase)
            stroke.color = body
            stroke.alpha = (255 * (1 - pose.phase * 0.8f)).toInt()
            stroke.strokeWidth = s * 0.04f
            path.rewind()
            path.moveTo(s * 0.8f, s * 0.08f); path.lineTo(s * 0.92f, s * 0.08f)
            path.lineTo(s * 0.8f, s * 0.2f); path.lineTo(s * 0.92f, s * 0.2f)
            canvas.drawPath(path, stroke)
            stroke.alpha = 255
            canvas.restore()
        }
    }

    /** A still Mo, for the widget and notification (they can't animate). */
    fun bitmap(sizePx: Int, mood: Mood, asleep: Boolean = false): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        draw(Canvas(bitmap), sizePx.toFloat(), mood, pose = MoPose(sleep = if (asleep) 1f else 0f))
        return bitmap
    }

    /**
     * Eyebrows for every mood, driven by [MoPose.brow]: raised for surprise and curiosity,
     * lowered for sleepiness, tilted worried for the unsteady moods. Nothing is drawn near the
     * resting brow height of a calm face, so the quiet moods keep their clean look.
     */
    private fun brows(canvas: Canvas, s: Float, eyeY: Float, pose: MoPose, mood: Mood, wave: Float) {
        val b = pose.brow
        if (abs(b) < 0.12f) return
        val worried = (mood == Mood.WOBBLY || mood == Mood.DIZZY) && b < 0f
        val width = stroke.strokeWidth
        stroke.strokeWidth = width * 0.85f
        for (i in 0..1) {
            val x = eyeX[i]
            val inward = if (i == 0) 1 else -1 // toward the middle of the face
            // One brow (the right) rides higher during a curious glance.
            val skew = if (i == 1) pose.browSkew else 0f
            val cy = eyeY - s * (0.15f + 0.05f * b + 0.04f * skew)
            val tilt = if (worried) s * 0.05f * (-b / 0.55f).coerceAtMost(1.3f) else 0f
            val twitch = if (worried) s * 0.01f * wave * pose.idleAmplitude else 0f
            canvas.drawLine(
                x - inward * s * 0.08f, cy + tilt / 2 - twitch,
                x + inward * s * 0.06f, cy - tilt / 2 - twitch, stroke,
            )
        }
        stroke.strokeWidth = width
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
        path.rewind()
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
        path.rewind()
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
 * [idleMotion] it also plays the mood's idle loop, redrawn at the [profile]'s slow idle rate
 * (the sway is a 3.6 s loop; it never needs display rate). Event animations (a reaction, a
 * gesture, a blink) draw at display rate only while they run, then the view goes quiet.
 *
 * Every animation that can be interrupted remembers the pose it was interrupted in
 * (`reactFrom`, `*Carry`) and the new one starts from there, so a second reel or a quick app
 * switch continues the motion instead of snapping to rest and starting over.
 */
class MoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    var mood: Mood = Mood.CHILL
        set(value) {
            if (field != value) {
                previousMoodColor = field.color.toInt()
                field = value
                moodChangedAt = SystemClock.uptimeMillis()
                invalidate()
            }
        }

    var idleMotion: Boolean = false
        set(value) {
            field = value
            // The director runs on a timer only while there is idle motion to direct.
            if (value) scheduleIdle() else removeCallbacks(idleDirector)
            invalidate()
        }

    /** How lively Mo is; set from the user's motion style. */
    var profile: MotionProfile = MotionProfile.BALANCED
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    /** Mo is asleep: eyes shut, a "z". Use [wakeUp] to wake Mo with a stretch and a yawn. */
    var asleep: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                if (value) wakeStart = null
                invalidate()
            }
        }

    private var blinkStart: Long? = null
    private var reactStart: Long? = null
    private var reactFrom: Float = 0f
    /** Timing of the reaction in progress (tired Mo: slower; Mo used to the scrolling: brisker). */
    private var reactProfile: MotionProfile = MotionProfile.BALANCED
    private var reactLengthMs = 0L
    private var yawnStart: Long? = null
    private var whirlStart: Long? = null
    private var whirlSlow = false
    private val idle = MoIdle()
    private var lastIdleBehavior: IdleBehavior? = null
    private var reactExcited = false
    private var wakeStart: Long? = null
    private var moodChangedAt: Long? = null
    private var peekStart: Long? = null
    private var peekCarry: Float = 0f
    private var exitStart: Long? = null
    private var exitCarry: Float = 0f
    private var glanceStart: Long? = null
    private var glanceCarry: Float = 0f
    private var reactionIntensity: Float = 1f
    private var previousMoodColor: Int = mood.color.toInt()
    /** The system's animation scale is 0 ("remove animations"): keep Mo still apart from short, soft reactions. */
    private var reducedMotion = false

    private val effectiveProfile: MotionProfile get() = if (reducedMotion) MotionProfile.CALM else profile

    /** Every so often Mo does a small unprompted thing; see [MoIdle]. Never over an event animation. */
    private val idleDirector = object : Runnable {
        override fun run() {
            val now = SystemClock.uptimeMillis()
            val busy = asleep || reducedMotion || !isShown ||
                running(reactStart, reactLengthMs, now) || running(peekStart, effectiveProfile.peekMs, now) ||
                running(glanceStart, effectiveProfile.glanceMs, now) || running(exitStart, effectiveProfile.exitMs, now) ||
                running(wakeStart, MoMotion.WAKE_MS, now) || running(yawnStart, MoMotion.WAKE_MS, now) ||
                running(whirlStart, MoMotion.whirlDurationMs(whirlSlow), now)
            if (!busy && idleMotion) {
                val behavior = idle.pick(mood, java.time.LocalTime.now().hour, lastIdleBehavior)
                lastIdleBehavior = behavior
                DetectionLog.add("mo: idle → ${behavior.label} (${mood.name.lowercase()})")
                when (behavior) {
                    IdleBehavior.LOOK_AROUND -> glance()
                    IdleBehavior.PEEK -> peek()
                    IdleBehavior.YAWN -> yawnStart = now.also { invalidate() }
                    // Small enough to stay a wiggle: a nod of a reaction, not a startle.
                    IdleBehavior.WIGGLE -> react(intensity = 0.3f, tempo = 0.9f)
                }
            }
            if (idleMotion) scheduleIdle()
        }
    }

    private companion object {
        const val DOUBLE_TAKE_GAP_MS = 380L
    }

    private fun scheduleIdle() {
        removeCallbacks(idleDirector)
        if (isAttachedToWindow) postDelayed(idleDirector, idle.nextDelayMs(mood, effectiveProfile))
    }

    private fun running(start: Long?, lengthMs: Long, now: Long = SystemClock.uptimeMillis()) =
        start?.let { now - it < lengthMs } ?: false

    private val blinker = object : Runnable {
        override fun run() {
            blinkStart = SystemClock.uptimeMillis()
            invalidate()
            postDelayed(this, MoMotion.nextBlinkDelayMs(profile = effectiveProfile))
        }
    }

    private fun pose(now: Long, idle: Boolean = idleMotion && !reducedMotion): MoPose = MoMotion.pose(
        now, blinkStart, reactStart, idle, asleep, wakeStart, mood, moodChangedAt, peekStart, reactionIntensity,
        exitStart, effectiveProfile, reactFrom, reactExcited, glanceStart, peekCarry, exitCarry, glanceCarry,
        reactProfile, yawnStart, whirlStart, whirlSlow,
    )

    /**
     * A new reel counted: Mo is startled (or, if [excited], delighted). If a reaction is
     * already playing, the new one carries on from the current pose with no crouch.
     */
    fun react(intensity: Float = 1f, excited: Boolean = false, tempo: Float = 1f) {
        val now = SystemClock.uptimeMillis()
        val amount = MoMotion.reactAmount(intensity, effectiveProfile)
        reactFrom = if (reactStart == null) 0f else pose(now).react / amount
        // Cached, so the per-frame pose does no allocation.
        reactProfile = effectiveProfile.paced(tempo)
        reactLengthMs = MoMotion.reactionLength(reactProfile, reactFrom)
        DetectionLog.add(
            "mo: pop from ${"%.2f".format(java.util.Locale.ROOT, reactFrom)} " +
                "(${if (kotlin.math.abs(reactFrom) > MoMotion.CHAIN_THRESHOLD) "already moving, no crouch" else "at rest, crouch first"}), " +
                "size ${"%.2f".format(java.util.Locale.ROOT, amount)}, " +
                "${reactProfile.riseMs}ms rise, ${reactProfile.settleMs}ms settle",
        )
        reactionIntensity = intensity
        reactExcited = excited
        reactStart = now
        invalidate()
    }

    /** Mo gives a brief, gentle farewell as a tracked app closes. */
    fun exitSession() {
        val now = SystemClock.uptimeMillis()
        exitCarry = pose(now).exit
        exitStart = now
        invalidate()
    }

    /** Mo leans out briefly, as if noticing a change nearby. */
    fun peek() {
        val now = SystemClock.uptimeMillis()
        peekCarry = pose(now).peek
        peekStart = now
        invalidate()
    }

    /** Mo's eyes dart aside and back: "what was that?", as the app in front changes. */
    fun glance() {
        val now = SystemClock.uptimeMillis()
        glanceCarry = pose(now).glance
        glanceStart = now
        invalidate()
    }

    /**
     * Spiral eyes and a wobble: Mo is dizzy from scrolling too fast ([slow] false), or mesmerised
     * by one reel that won't end ([slow] true). Ignored while a whirl is already going.
     */
    fun whirl(slow: Boolean = false) {
        val now = SystemClock.uptimeMillis()
        if (asleep || running(whirlStart, MoMotion.whirlDurationMs(whirlSlow), now)) return
        whirlSlow = slow
        whirlStart = now
        invalidate()
    }

    /** A big stretch and yawn without falling asleep: a long stretch of scrolling, or boredom. */
    fun stretch() {
        if (asleep) return
        yawnStart = SystemClock.uptimeMillis()
        invalidate()
    }

    /** "Wait, again?": a quick sideways look, then a lean in. */
    fun doubleTake() {
        glance()
        postDelayed({ if (!asleep) peek() }, DOUBLE_TAKE_GAP_MS)
    }

    /** Wakes Mo up, if asleep: eyes open slowly through a big yawn and a stretch. */
    fun wakeUp() {
        if (!asleep) return
        asleep = false
        wakeStart = SystemClock.uptimeMillis()
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        reducedMotion = runCatching {
            android.provider.Settings.Global.getFloat(
                context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f,
            ) == 0f
        }.getOrDefault(false)
        if (reducedMotion) DetectionLog.add("mo: system animation scale is 0 → reduced motion (calm profile, no idle sway)")
        postDelayed(blinker, MoMotion.nextBlinkDelayMs(profile = effectiveProfile))
        if (idleMotion) scheduleIdle()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(blinker)
        removeCallbacks(idleDirector)
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val now = SystemClock.uptimeMillis()
        val size = minOf(width, height).toFloat()
        val p = effectiveProfile
        MoPainter.draw(
            canvas, size, mood, (width - size) / 2, (height - size) / 2, pose(now), previousMoodColor,
            stage = MoPainter.ANIMATED_STAGE,
        )

        val animating = running(blinkStart, MoMotion.blinkDurationMs(mood)) ||
            running(reactStart, reactLengthMs) ||
            running(yawnStart, MoMotion.WAKE_MS) ||
            running(whirlStart, MoMotion.whirlDurationMs(whirlSlow)) ||
            running(wakeStart, MoMotion.WAKE_MS) ||
            running(moodChangedAt, MoMotion.MOOD_TRANSITION_MS) ||
            running(peekStart, p.peekMs) ||
            running(glanceStart, p.glanceMs) ||
            running(exitStart, p.exitMs)
        when {
            animating -> postInvalidateOnAnimation()
            idleMotion && !reducedMotion -> postInvalidateDelayed(p.idleFrameMs)
        }
    }
}
