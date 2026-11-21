package io.github.amandhakar.mindscroll.mascot

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF

/**
 * The part of each [MoCharacter] that isn't the shared face: what sits behind the body (ears,
 * leaves), the body itself, and what sits on the body (whiskers, a nose). All coordinates are on
 * a 100 × 100 grid, scaled by the square's size, so they line up with [MoPainter]'s face.
 * Main thread only, like [MoPainter].
 */
internal object MoBodies {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val path = Path()
    private val rect = RectF()

    private val EAR_PINK = 0xFFFF8FB0.toInt()
    private val LEAF_GREEN = 0xFF2F9E44.toInt()
    private val INK = 0xFF1B1B2F.toInt()

    /** Mixes [a] toward [b] by [t] (0 = a, 1 = b). */
    private fun mix(a: Int, b: Int, t: Float): Int {
        fun ch(shift: Int) = (((a shr shift) and 0xFF) + (((b shr shift) and 0xFF) - ((a shr shift) and 0xFF)) * t).toInt()
        return Color.rgb(ch(16), ch(8), ch(0))
    }

    /** Ears and leaves, drawn first so the body overlaps their roots. [lift] perks them up on a startle. */
    fun drawBack(canvas: Canvas, s: Float, color: Int, character: MoCharacter, lift: Float) {
        val u = s / 100f
        canvas.save()
        canvas.translate(0f, -lift)
        when (character) {
            MoCharacter.KIT -> {
                // Left ear in grid units; the right one is its mirror image.
                fun x(side: Int, v: Float) = (if (side == 0) v else 100f - v) * u
                fill.color = color
                stroke.color = color
                stroke.strokeWidth = 7f * u
                for (side in 0..1) {
                    path.rewind()
                    path.moveTo(x(side, 18f), 32f * u)
                    path.lineTo(x(side, 20f), 8f * u)
                    path.lineTo(x(side, 44f), 20f * u)
                    path.close()
                    canvas.drawPath(path, fill)
                    canvas.drawPath(path, stroke)
                }
                fill.color = EAR_PINK
                fill.alpha = 190
                for (side in 0..1) {
                    path.rewind()
                    path.moveTo(x(side, 23f), 24f * u)
                    path.lineTo(x(side, 24f), 15f * u)
                    path.lineTo(x(side, 34f), 20f * u)
                    path.close()
                    canvas.drawPath(path, fill)
                }
                fill.alpha = 255
            }
            MoCharacter.BUN -> {
                for (side in 0..1) {
                    val m = if (side == 0) 1f else -1f
                    val cx = if (side == 0) 33f else 67f
                    canvas.save()
                    canvas.rotate(m * -9f, cx * u, 28f * u)
                    fill.color = color
                    rect.set((cx - 8.5f) * u, (17f - 16f) * u, (cx + 8.5f) * u, (17f + 16f) * u)
                    canvas.drawOval(rect, fill)
                    fill.color = EAR_PINK
                    fill.alpha = 180
                    rect.set((cx - 3.6f) * u, (18f - 10.5f) * u, (cx + 3.6f) * u, (18f + 10.5f) * u)
                    canvas.drawOval(rect, fill)
                    fill.alpha = 255
                    canvas.restore()
                }
            }
            MoCharacter.SPROUT -> {
                stroke.color = mix(color, INK, 0.3f)
                stroke.strokeWidth = 5f * u
                path.rewind()
                path.moveTo(50f * u, 22f * u)
                path.cubicTo(50f * u, 14f * u, 50f * u, 10f * u, 50f * u, 6f * u)
                canvas.drawPath(path, stroke)
                fill.color = mix(color, LEAF_GREEN, 0.55f)
                for (side in 0..1) {
                    val m = if (side == 0) -1f else 1f // left leaf first
                    path.rewind()
                    path.moveTo(50f * u, 8f * u)
                    path.cubicTo((50f + m * 6f) * u, -1f * u, (50f + m * 20f) * u, -1f * u, (50f + m * 26f) * u, 6f * u)
                    path.cubicTo((50f + m * 18f) * u, 14f * u, (50f + m * 6f) * u, 14f * u, 50f * u, 8f * u)
                    path.close()
                    canvas.drawPath(path, fill)
                }
            }
            else -> Unit
        }
        canvas.restore()
    }

    /** The body, with its shine. */
    fun drawBody(canvas: Canvas, s: Float, color: Int, character: MoCharacter) {
        val u = s / 100f
        fill.color = color
        var shine = floatArrayOf(31f, 30f, 11f, 6f) // centre x, centre y, radius x, radius y
        when (character) {
            MoCharacter.MO, MoCharacter.KIT -> {
                rect.set(8f * u, 16f * u, 92f * u, 94f * u)
                canvas.drawRoundRect(rect, 38f * u, 36f * u, fill)
            }
            MoCharacter.BUN -> {
                rect.set(8f * u, 22f * u, 92f * u, 94f * u)
                canvas.drawRoundRect(rect, 36f * u, 34f * u, fill)
                shine = floatArrayOf(31f, 36f, 11f, 6f)
            }
            MoCharacter.SPROUT -> {
                rect.set(8f * u, 18f * u, 92f * u, 94f * u)
                canvas.drawRoundRect(rect, 38f * u, 35f * u, fill)
            }
            MoCharacter.NIMBUS -> {
                rect.set(8f * u, 42f * u, 92f * u, 92f * u)
                canvas.drawRoundRect(rect, 25f * u, 25f * u, fill)
                canvas.drawCircle(31f * u, 42f * u, 21f * u, fill)
                canvas.drawCircle(58f * u, 35f * u, 25f * u, fill)
                canvas.drawCircle(79f * u, 52f * u, 15f * u, fill)
                canvas.drawCircle(21f * u, 58f * u, 15f * u, fill)
                shine = floatArrayOf(22f, 34f, 8f, 5f)
            }
            MoCharacter.BOO -> {
                path.rewind()
                path.moveTo(8f * u, 54f * u)
                path.cubicTo(8f * u, 28f * u, 26f * u, 14f * u, 50f * u, 14f * u)
                path.cubicTo(74f * u, 14f * u, 92f * u, 28f * u, 92f * u, 54f * u)
                path.lineTo(92f * u, 90f * u)
                path.quadTo(92f * u, 96f * u, 86f * u, 92f * u)
                for ((x, y) in BOO_HEM) path.lineTo(x * u, y * u)
                path.quadTo(8f * u, 96f * u, 8f * u, 90f * u)
                path.close()
                canvas.drawPath(path, fill)
                shine = floatArrayOf(20f, 24f, 11f, 6f)
            }
        }
        fill.color = Color.argb(70, 255, 255, 255)
        rect.set((shine[0] - shine[2]) * u, (shine[1] - shine[3]) * u, (shine[0] + shine[2]) * u, (shine[1] + shine[3]) * u)
        canvas.drawOval(rect, fill)
    }

    /** Whiskers and a nose, on top of the body and under the face. */
    fun drawFront(canvas: Canvas, s: Float, character: MoCharacter) {
        val u = s / 100f
        when (character) {
            MoCharacter.KIT -> {
                stroke.color = INK
                stroke.alpha = 115
                stroke.strokeWidth = 2.2f * u
                for (side in 0..1) {
                    val out = if (side == 0) 10f else 90f
                    val inn = if (side == 0) 22f else 78f
                    canvas.drawLine(out * u, 62f * u, inn * u, 64f * u, stroke)
                    canvas.drawLine(out * u, 70f * u, inn * u, 69f * u, stroke)
                }
                stroke.alpha = 255
            }
            MoCharacter.BUN -> {
                fill.color = EAR_PINK
                rect.set(46.6f * u, 63.4f * u, 53.4f * u, 68.6f * u)
                canvas.drawOval(rect, fill)
            }
            else -> Unit
        }
    }


    private val BOO_HEM = listOf(
        78f to 86f, 68f to 94f, 58f to 86f, 50f to 94f, 42f to 86f, 32f to 94f, 22f to 86f, 14f to 92f,
    )
}
