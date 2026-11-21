package io.github.amandhakar.mindscroll.display

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.TextView
import io.github.amandhakar.mindscroll.mascot.MoLook
import io.github.amandhakar.mindscroll.mascot.MoView
import io.github.amandhakar.mindscroll.mascot.MotionProfile
import io.github.amandhakar.mindscroll.mascot.Mood
import io.github.amandhakar.mindscroll.tracker.DetectionLog
import kotlin.random.Random

/**
 * Experimental. Mo is pushed out of the island inside a drop of liquid; the drop pops and Mo is out
 * completely, roams the whole screen along a random route, shakes its body and says how it feels;
 * then a new drop forms around Mo and the island pulls it back in. See [LiquidTimeline] for the
 * sequence and [RoamPath] for the route.
 *
 * It is a second, see-through, full-screen window drawn over the island's own window, which stays
 * as it is. Touches pass straight through it. It draws a thin black strip along the island's bottom
 * edge (clear of the numbers) and a black drop around Mo; blurring the two and then sharpening the
 * edge (alpha threshold) is the usual "gooey" trick, and it joins them with a stretching neck when
 * they are close. Mo is drawn on top of the drop. Inside the island the strip and drop are black on
 * black, so they only show where they leave it. The island's own Mo is hidden while this one is out.
 *
 * The blur needs Android 12 (API 31). Older phones get the same movement without the stretch.
 */
class LiquidIsland(
    private val context: Context,
    private val windowManager: WindowManager,
) {
    private var root: FrameLayout? = null
    private var animator: ValueAnimator? = null

    /** True from the moment Mo starts coming out until it is back and the window is gone. */
    var running = false
        private set

    /**
     * Plays it once. [pill] is the island on screen, [moCenterX] where Mo's slot is, [screen] the
     * whole screen, and [seed] picks the random route. [hideRealMo] hides (true) or shows (false)
     * the island's own Mo while this one is out. [message] is what Mo says while it roams, or null
     * to stay quiet. [onEnd] runs when it is over, or cancelled. Returns false if it could not start.
     */
    fun play(
        pill: Rect,
        moCenterX: Float,
        moSize: Int,
        mood: Mood,
        profile: MotionProfile,
        message: String?,
        screen: Rect,
        seed: Long,
        hideRealMo: (Boolean) -> Unit,
        onEnd: () -> Unit,
    ): Boolean {
        if (running) return false
        val density = context.resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val screenW = screen.width()
        val screenH = screen.height()
        // The drop is a little bigger than Mo, so it reads as a bubble around it.
        val radius = moSize / 2f + dp(5)
        val pillH = pill.height().toFloat()
        val cyIn = pill.exactCenterY()
        // Where Mo hangs just below the island when it first comes out, and where its route begins and ends.
        val outX = moCenterX
        val outY = pill.bottom + radius + dp(3)
        val stripMidY = pill.bottom - dp(6).toFloat()
        val strip = RectF(pill.left + pillH / 2f, pill.bottom - dp(12).toFloat(), pill.right - pillH / 2f, pill.bottom.toFloat())

        // Anywhere on the screen, clear of the edges, the island, and the gesture bar.
        val edge = moSize / 2f + dp(8)
        val path = RoamPath.random(
            Random(seed),
            start = Pt(outX, outY),
            minX = edge,
            maxX = screenW - edge,
            minY = pill.bottom + moSize / 2f + dp(16),
            maxY = screenH - moSize / 2f - dp(96),
        )

        // What Mo says goes in a bubble beside it, drawn the way Mo's other bubbles are.
        val bubble = message?.let { line ->
            TextView(context).apply {
                text = line
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                maxWidth = dp(200)
                gravity = Gravity.CENTER
                setPadding(dp(14), dp(9), dp(14), dp(10))
                background = GradientDrawable().apply {
                    setColor(Color.BLACK)
                    cornerRadius = dp(18).toFloat()
                    setStroke(dp(1), MoLook.color(mood))
                }
                alpha = 0f
                measure(
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                )
            }
        }
        val bubbleW = bubble?.measuredWidth ?: 0
        val bubbleH = bubble?.measuredHeight ?: 0

        val goo = GooView(context).also { it.setGeometry(strip, outX, cyIn, radius, radius) }
        val mo = MoView(context).apply {
            this.mood = mood
            this.profile = profile
            idleMotion = true
        }
        val layer = FrameLayout(context).apply {
            clipChildren = false
            addView(goo, FrameLayout.LayoutParams(screenW, screenH))
            addView(mo, FrameLayout.LayoutParams(moSize, moSize))
            bubble?.let { addView(it, FrameLayout.LayoutParams(bubbleW, bubbleH)) }
        }
        mo.translationX = outX - moSize / 2f
        mo.translationY = cyIn - moSize / 2f

        val params = WindowManager.LayoutParams(
            screenW, screenH,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                // The blur is a RenderEffect: it only draws on a hardware-accelerated window.
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        if (runCatching { windowManager.addView(layer, params) }.isFailure) {
            DetectionLog.add("liquid: could not add the window")
            return false
        }

        root = layer
        running = true
        var realMoHidden = false
        var waved = false
        var glancedAt = 0
        var shook = false
        var lastX = outX
        var lean = 0f

        fun finish() {
            if (!running) return
            running = false
            animator = null
            hideRealMo(false)
            runCatching { windowManager.removeViewImmediate(layer) }
            root = null
            onEnd()
        }

        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = LiquidTimeline.TOTAL_MS
            interpolator = LinearInterpolator()
            addUpdateListener { va ->
                val t = va.animatedFraction
                val p = LiquidTimeline.position(t)
                // Out from the island, then along the random route, then home and back in.
                val at = if (LiquidTimeline.isFree(t)) path.at(LiquidTimeline.roamProgress(t)) else Pt(outX, outY)
                val x = at.x
                val y = cyIn + (outY - cyIn) * p + (at.y - outY)
                // The drop: around Mo while it comes out, then it pops back into the island's edge so Mo
                // is free, and a new one forms around Mo as it comes back.
                val drop = LiquidTimeline.drop(t)
                val dropY = stripMidY + (y - stripMidY) * drop.attach
                goo.visibility = if (drop.scale > 0.01f) View.VISIBLE else View.INVISIBLE
                goo.setGeometry(strip, x, dropY, radius * drop.scale, radius * drop.scale)
                mo.translationX = x - moSize / 2f
                mo.translationY = y - moSize / 2f
                // Leans into the way it is going, and straightens up as it slows.
                lean += ((x - lastX) * 0.6f - lean) * 0.2f
                lastX = x
                // A shake of the whole body, from side to side, in the middle of the roam.
                val shake = LiquidTimeline.shake(t)
                mo.rotation = lean.coerceIn(-12f, 12f) + 14f * shake
                mo.translationX += dp(2) * shake
                // What Mo says, beside it, following it, fading in and out; above it near the bottom of the screen.
                bubble?.let {
                    it.alpha = LiquidTimeline.bubbleAlpha(t)
                    it.translationX = (x - bubbleW / 2f).coerceIn(dp(8).toFloat(), (screenW - bubbleW - dp(8)).toFloat())
                    val below = y + moSize / 2f + dp(8) + bubbleH + dp(24) <= screenH
                    it.translationY = if (below) y + moSize / 2f + dp(8) else y - moSize / 2f - dp(8) - bubbleH
                }
                // The island's own Mo steps out just after this one is drawn over it, so nothing flickers.
                if (!realMoHidden && t > 0.01f) {
                    realMoHidden = true
                    hideRealMo(true)
                }
                val u = LiquidTimeline.roamProgress(t)
                // The drop pops: Mo is out completely, and pleased about it.
                if (!waved && LiquidTimeline.isFree(t)) {
                    waved = true
                    mo.react(intensity = 0.9f, excited = true)
                }
                // A look around, a happy shake in the middle, another look around.
                if (glancedAt == 0 && u >= 0.22f) {
                    glancedAt = 1
                    mo.glance()
                }
                if (!shook && u >= 0.40f) {
                    shook = true
                    mo.react(intensity = 0.6f, excited = true)
                }
                if (glancedAt == 1 && u >= 0.72f) {
                    glancedAt = 2
                    mo.glance()
                }
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) = finish()
                override fun onAnimationCancel(animation: Animator) = finish()
            })
            start()
        }
        return true
    }

    /** Stops now (the island went away, say), putting the real Mo back. */
    fun cancel() {
        animator?.cancel()
    }

    /** A thin strip and a circle, drawn black and made gooey by a blur and an alpha threshold. */
    private class GooView(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
        private val strip = RectF()
        private var cx = 0f
        private var cy = 0f
        private var rx = 0f
        private var ry = 0f

        init {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val sigma = BLUR_DP * resources.displayMetrics.density
                val blur = RenderEffect.createBlurEffect(sigma, sigma, Shader.TileMode.DECAL)
                // Alpha x19 - 9: what is mostly solid becomes solid, the soft edge snaps to a clean one,
                // and where the two shapes blur into each other a smooth neck is left between them.
                val sharpen = ColorMatrixColorFilter(
                    ColorMatrix(
                        floatArrayOf(
                            1f, 0f, 0f, 0f, 0f,
                            0f, 1f, 0f, 0f, 0f,
                            0f, 0f, 1f, 0f, 0f,
                            0f, 0f, 0f, 19f, -9f * 255f,
                        ),
                    ),
                )
                setRenderEffect(RenderEffect.createColorFilterEffect(sharpen, blur))
            }
        }

        fun setGeometry(strip: RectF, cx: Float, cy: Float, rx: Float, ry: Float) {
            this.strip.set(strip)
            this.cx = cx
            this.cy = cy
            this.rx = rx
            this.ry = ry
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            canvas.drawRoundRect(strip, strip.height() / 2f, strip.height() / 2f, paint)
            canvas.drawOval(cx - rx, cy - ry, cx + rx, cy + ry, paint)
        }

        private companion object {
            const val BLUR_DP = 7.5f
        }
    }
}
