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
        previousMoodColor: Int = MoLook.color(mood),
        stage: Float = 1f,
        character: MoCharacter = MoLook.character,
        palette: MoPalette = MoLook.palette,
    ) {
        val s = size
        val body = if (pose.moodTransition < 1f) {
            colorEvaluator.evaluate(pose.moodTransition, previousMoodColor, palette.color(mood)) as Int
        } else palette.color(mood)
        val wave = sin(pose.phase * 2 * PI).toFloat()
        val asleep = pose.sleep > 0.5f
        val yawning = !asleep && pose.yawn > 0.25f
        // Crying with laughter: the laughing face stays as long as the tears do, or the tears would
        // read as sadness once the reaction's own grin has faded.
        val tearing = !asleep && !yawning && pose.whirl <= 0.35f && pose.propMask and MoProp.TEARS.bit != 0 &&
            MoMotion.propProgress(pose.propSinceMs, MoProp.TEARS).let { it >= 0f && it < 0.9f }
        val excited = !asleep && !yawning && pose.whirl <= 0.35f && (pose.excite > 0.35f || tearing)
        val surprised = !asleep && !yawning && !excited && pose.whirl <= 0.35f && pose.react > 0.35f
        // Spiral eyes: scrolling too fast, or mesmerised by one reel. Takes over the face.
        val whirling = !asleep && !yawning && pose.whirl > 0.35f
        // The side-eye: unamused half-lids, pupils slid sideways. A new reel (surprise) beats it.
        val sideEyeing = !asleep && !yawning && !whirling && pose.sideEye > 0.35f &&
            !(pose.react > 0.35f) && pose.excite <= 0.35f
        // Saying goodbye: eyes close in a smile as Mo turns away.
        val farewell = !asleep && !yawning && !excited && !surprised && !whirling && !sideEyeing && pose.exit > 0.35f
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
            val curlTop = character.top - 0.02f * r.coerceIn(0f, 1f)
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

        // What is behind the body: Mo's curl (a little scroll, as in "scroll"), or a cat's ears, a
        // bunny's, a sprout's leaves. It perks up on surprise.
        val lift = s * 0.02f * pose.react.coerceIn(0f, 1f)
        if (character == MoCharacter.MO) {
            stroke.color = body
            stroke.strokeWidth = s * 0.07f
            path.rewind()
            path.moveTo(s * 0.5f, s * 0.2f)
            path.cubicTo(s * 0.5f, s * 0.06f - lift, s * 0.66f, s * 0.04f - lift, s * 0.68f, s * 0.12f - lift)
            canvas.drawPath(path, stroke)
        } else {
            MoBodies.drawBack(canvas, s, body, character, lift)
        }

        // Body: a soft, slightly squat blob (or a cat, a cloud, a ghost...), with a shine.
        MoBodies.drawBody(canvas, s, body, character)
        MoBodies.drawFront(canvas, s, character)

        stroke.color = INK
        stroke.strokeWidth = s * 0.055f
        fill.color = INK
        val faceDrop = s * character.faceDrop
        val eyeY = s * 0.52f + faceDrop
        eyeX[0] = s * 0.35f
        eyeX[1] = s * 0.65f
        // Eyes open a little wider while Mo is paying attention.
        val attention = 1f + 0.3f * pose.peek + 0.2f * abs(pose.glance)

        if (!asleep && !sideEyeing && (mood == Mood.CHILL || mood == Mood.CURIOUS || surprised || excited || farewell)) {
            fill.color = Color.argb(if (excited) 130 else 90, 255, 110, 140)
            rect.set(s * 0.15f, s * 0.6f + faceDrop, s * 0.29f, s * 0.69f + faceDrop); canvas.drawOval(rect, fill)
            rect.set(s * 0.71f, s * 0.6f + faceDrop, s * 0.85f, s * 0.69f + faceDrop); canvas.drawOval(rect, fill)
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
        } else if (sideEyeing) {
            for (x in eyeX) sideEyeAt(canvas, x, eyeY, s * 0.075f, pose.sideEye, body)
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

        val mouthY = s * 0.72f + faceDrop
        if (asleep) {
            // A small, slightly open sleeping mouth.
            rect.set(s * 0.46f, mouthY - s * 0.02f, s * 0.54f, mouthY + s * 0.04f)
            canvas.drawOval(rect, fill)
        } else if (yawning) {
            val open = s * (0.04f + 0.08f * pose.yawn)
            rect.set(s * 0.5f - open * 0.7f, mouthY - open * 0.8f, s * 0.5f + open * 0.7f, mouthY + open)
            canvas.drawOval(rect, fill)
        } else if (sideEyeing) {
            // Flat and a little crooked: "...".
            canvas.drawLine(s * 0.41f, mouthY + s * 0.005f, s * 0.59f, mouthY - s * 0.012f, stroke)
        } else if (whirling) {
            wave(canvas, s * 0.36f, s * 0.64f, mouthY, s * 0.03f)
        } else if (excited) {
            // An open grin that widens with the joy.
            val open = (if (tearing) 1f else pose.excite).coerceIn(0f, 1f)
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
        if (pose.propMask != 0) props(canvas, s, pose, eyeY)
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
    fun bitmap(
        sizePx: Int,
        mood: Mood,
        asleep: Boolean = false,
        character: MoCharacter = MoLook.character,
        palette: MoPalette = MoLook.palette,
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        // The others have ears or leaves right up to the top edge; Mo's curl leaves room at full size.
        val stage = if (character == MoCharacter.MO) 1f else 0.94f
        draw(
            Canvas(bitmap), sizePx.toFloat(), mood,
            pose = MoPose(sleep = if (asleep) 1f else 0f),
            previousMoodColor = palette.color(mood), stage = stage, character = character, palette = palette,
        )
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

    /** Sweat and tears: deep enough to show on every mood colour, light enough to read as water. */
    private val WATER = intArrayOf(0xFF, 0x2E, 0x9B, 0xF2)
    private val GRASS = 0xFF5CD65C.toInt()
    private val blades = floatArrayOf(0.7f, 1f, 0.55f, 0.9f, 0.62f)

    /**
     * Comic props around Mo, the way a reaction meme adds a sweat drop or a question mark. White
     * ones show on the dark patch; each pops in with a little overshoot and shrinks away.
     */
    private fun props(canvas: Canvas, s: Float, pose: MoPose, eyeY: Float) {
        val width = stroke.strokeWidth
        for (prop in MoProp.entries) {
            if (pose.propMask and prop.bit == 0) continue
            val t = MoMotion.propProgress(pose.propSinceMs, prop)
            if (t < 0f) continue
            val pop = MoMotion.propPop(t)
            when (prop) {
                MoProp.QUESTION -> {
                    val cx = s * 0.84f
                    val cy = s * 0.15f
                    val r = s * 0.055f * pop
                    canvas.save()
                    canvas.rotate(10f * sin(t * 14f), cx, cy + r * 2f)
                    stroke.color = Color.WHITE
                    stroke.strokeWidth = s * 0.04f * pop.coerceAtMost(1.1f)
                    rect.set(cx - r, cy - r, cx + r, cy + r)
                    canvas.drawArc(rect, 180f, 220f, false, stroke)
                    canvas.drawLine(cx + r * 0.77f, cy + r * 0.64f, cx, cy + r * 1.55f, stroke)
                    fill.color = Color.WHITE
                    canvas.drawCircle(cx, cy + r * 2.25f, s * 0.026f * pop, fill)
                    canvas.restore()
                }
                MoProp.EXCLAIM -> {
                    // Shakes as it lands, then holds still.
                    val cx = s * 0.17f + s * 0.012f * sin(t * 60f) * (1f - t).coerceIn(0f, 1f)
                    val top = s * 0.07f
                    val len = s * 0.1f * pop
                    stroke.color = Color.WHITE
                    stroke.strokeWidth = s * 0.05f * pop.coerceAtMost(1.1f)
                    canvas.drawLine(cx, top, cx, top + len, stroke)
                    fill.color = Color.WHITE
                    canvas.drawCircle(cx, top + len + s * 0.05f * pop, s * 0.03f * pop, fill)
                }
                MoProp.DOTS -> {
                    fill.color = Color.WHITE
                    for (k in 0..2) {
                        // One dot at a time: "…"
                        val appears = 0.1f + 0.15f * k
                        if (t < appears) continue
                        val bob = s * 0.012f * sin((t * 7f - k * 0.7f) * PI.toFloat())
                        val grow = MoMotion.propPop(((t - appears) / 0.1f * 0.16f).coerceIn(0f, 0.16f)).coerceAtLeast(0f) *
                            if (t > 0.8f) pop else 1f
                        canvas.drawCircle(s * (0.7f + 0.09f * k), s * 0.14f + bob, s * 0.028f * grow, fill)
                    }
                }
                MoProp.SWEAT -> {
                    // Two drops slide down the temple, the second a beat behind, fading as they go.
                    for (k in 0..1) {
                        val local = ((t - 0.2f * k) / 0.7f).coerceIn(0f, 1f)
                        if (local <= 0f || local >= 1f) continue
                        val x = s * (if (k == 0) 0.2f else 0.8f)
                        val y = s * (0.27f + 0.3f * local)
                        drop(canvas, x, y, s * 0.042f * pop.coerceAtMost(1.1f), (255 * (1f - local * local)).toInt())
                    }
                }
                MoProp.TEARS -> {
                    // Streams of tears down both cheeks, laughing so hard Mo is crying.
                    val fade = pop
                    for (i in 0..1) {
                        val out = if (i == 0) -1f else 1f
                        stroke.color = Color.argb((190 * fade).toInt(), WATER[1], WATER[2], WATER[3])
                        stroke.strokeWidth = s * 0.042f
                        canvas.drawLine(eyeX[i] + out * s * 0.045f, eyeY + s * 0.05f, eyeX[i] + out * s * 0.09f, eyeY + s * 0.2f, stroke)
                        fill.color = Color.argb((255 * fade).toInt(), WATER[1], WATER[2], WATER[3])
                        for (k in 0..2) {
                            val phase = (t * 2.2f + k / 3f) % 1f
                            canvas.drawCircle(
                                eyeX[i] + out * s * (0.045f + 0.045f * phase),
                                eyeY + s * (0.05f + 0.2f * phase),
                                s * 0.03f * (1f - phase * 0.4f),
                                fill,
                            )
                        }
                    }
                }
                MoProp.GRASS -> {
                    // A tuft grows at each foot and stays a while: "touch grass".
                    val grow = (t / 0.3f).coerceIn(0f, 1f) * pop.coerceAtMost(1f).coerceAtLeast(if (t < 0.8f) 1f else 0f)
                    stroke.color = GRASS
                    stroke.strokeWidth = s * 0.034f
                    for (side in 0..1) {
                        val baseX = s * (if (side == 0) 0.2f else 0.7f)
                        for (b in blades.indices) {
                            val x = baseX + s * 0.025f * b
                            val h = s * 0.13f * blades[b] * grow
                            val lean = (b - 2) * s * 0.012f
                            canvas.drawLine(x, s * 0.945f, x + lean, s * 0.945f - h, stroke)
                        }
                    }
                }
                MoProp.SPEED_LINES -> {
                    // Short streaks off both sides, streaming outward as they fade.
                    stroke.color = Color.argb((210 * pop).toInt().coerceIn(0, 255), 255, 255, 255)
                    stroke.strokeWidth = s * 0.022f
                    for (k in 0..2) {
                        val y = s * (0.34f + 0.15f * k)
                        val shift = s * 0.03f * ((t * 4f + k * 0.33f) % 1f)
                        canvas.drawLine(s * 0.055f - shift, y, s * 0.012f - shift * 0.4f, y, stroke)
                        canvas.drawLine(s * 0.945f + shift, y, s * 0.988f + shift * 0.4f, y, stroke)
                    }
                }
            }
        }
        stroke.strokeWidth = width
        stroke.color = INK
        fill.color = INK
    }

    /** A teardrop: sweat or a tear, point up. */
    private fun drop(canvas: Canvas, x: Float, y: Float, r: Float, alpha: Int) {
        fill.color = Color.argb(alpha.coerceIn(0, 255), WATER[1], WATER[2], WATER[3])
        canvas.drawCircle(x, y, r, fill)
        path.rewind()
        path.moveTo(x - r * 0.85f, y - r * 0.5f)
        path.lineTo(x, y - r * 2.1f)
        path.lineTo(x + r * 0.85f, y - r * 0.5f)
        path.close()
        canvas.drawPath(path, fill)
    }

    /**
     * An unamused eye: white, with the pupil slid to the side ([look] 0..1) and a flat lid cut
     * across the top. The lid is the body's colour, so it reads as a heavy eyelid.
     */
    private fun sideEyeAt(canvas: Canvas, x: Float, y: Float, r: Float, look: Float, lid: Int) {
        val ry = r * 0.85f
        fill.color = Color.WHITE
        rect.set(x - r, y - ry, x + r, y + ry)
        canvas.drawOval(rect, fill)
        fill.color = INK
        canvas.drawCircle(x + look * r * 0.52f, y + ry * 0.12f, r * 0.46f, fill)
        // The lid: a flat cut across the upper part of the eye, with its edge drawn in.
        fill.color = lid
        rect.set(x - r - 2f, y - ry - 2f, x + r + 2f, y - ry * 0.2f)
        canvas.drawRect(rect, fill)
        stroke.color = INK
        canvas.drawLine(x - r, y - ry * 0.2f, x + r, y - ry * 0.2f, stroke)
        fill.color = INK
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
                previousMoodColor = MoLook.color(field)
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
    private var sideEyeStart: Long? = null
    private var propStart: Long? = null
    private var propMask = 0
    private var propLengthMs = 0L
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
    private var previousMoodColor: Int = MoLook.color(mood)
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
                running(whirlStart, MoMotion.whirlDurationMs(whirlSlow), now) ||
                running(sideEyeStart, MoMotion.SIDE_EYE_MS, now) || running(propStart, propLengthMs, now)
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
        reactProfile, yawnStart, whirlStart, whirlSlow, sideEyeStart, propStart, propMask,
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

    /** The side-eye: flat half-lids, pupils slid to the side. "You good?" */
    fun sideEye() {
        if (asleep) return
        sideEyeStart = SystemClock.uptimeMillis()
        invalidate()
    }

    /** Pops comic props around Mo (a sweat drop, a "?!", tears), all starting together. */
    fun props(vararg which: MoProp) {
        if (asleep || which.isEmpty()) return
        propMask = which.fold(0) { mask, prop -> mask or prop.bit }
        propLengthMs = which.maxOf { it.durationMs }
        propStart = SystemClock.uptimeMillis()
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
        // The "?!" lands with the second look.
        postDelayed({
            if (!asleep) {
                peek()
                props(MoProp.QUESTION, MoProp.EXCLAIM)
            }
        }, DOUBLE_TAKE_GAP_MS)
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
            running(sideEyeStart, MoMotion.SIDE_EYE_MS) ||
            running(propStart, propLengthMs) ||
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
