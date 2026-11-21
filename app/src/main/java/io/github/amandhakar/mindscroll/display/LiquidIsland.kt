package io.github.amandhakar.mindscroll.display

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.drawable.GradientDrawable
import android.graphics.Shader
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.util.TypedValue
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.TextView
import io.github.amandhakar.mindscroll.mascot.MoLook
import io.github.amandhakar.mindscroll.mascot.MoView
import io.github.amandhakar.mindscroll.mascot.MotionProfile
import io.github.amandhakar.mindscroll.mascot.Mood
import io.github.amandhakar.mindscroll.tracker.DetectionLog

/**
 * Experimental. Mo is pushed out of the island inside a drop of liquid; the drop pops and Mo is out
 * completely, roams around outside, shakes its body and asks how you are doing; then a new drop
 * forms around Mo and the island pulls it back in. See [LiquidTimeline] for the sequence.
 *
 * It is a second, see-through window drawn over the island's own window, which stays as it is.
 * The window draws a thin black strip along the island's bottom edge (clear of the numbers) and a
 * black blob where Mo is; blurring the two and then sharpening the edge (alpha threshold) is the
 * usual "gooey" trick, and it joins them with a stretching neck when they are close. Mo is drawn
 * on top of the blob. Inside the island the strip and blob are black on black, so they only show
 * where they leave it. The island's own Mo is hidden while this one is out.
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
     * Plays it once. [pill] is the island on screen, [moCenterX] where Mo's slot is, and
     * [hideRealMo] hides (true) or shows (false) the island's own Mo while this one is out. [message]
     * is what Mo asks while it roams, or null to stay quiet.
     * [onEnd] runs when it is over, or cancelled. Returns false if it could not start.
     */
    fun play(
        pill: Rect,
        moCenterX: Float,
        moSize: Int,
        mood: Mood,
        profile: MotionProfile,
        message: String?,
        hideRealMo: (Boolean) -> Unit,
        onEnd: () -> Unit,
    ): Boolean {
        if (running) return false
        val density = context.resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val margin = dp(24)
        // The drop is a little bigger than Mo, so it reads as a bubble around it.
        val radius = moSize / 2f + dp(5)
        val pillH = pill.height().toFloat()
        val cyIn = pillH / 2f
        // Far enough out that the blob hangs just clear of the island, joined by a thin neck.
        val cyOut = pillH + radius + dp(3)
        val winW = pill.width() + 2 * margin
        val bobPx = dp(10)
        // Mo's question goes in a bubble under it, drawn the way Mo's other bubbles are.
        val bubble = message?.let { line ->
            TextView(context).apply {
                text = line
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                maxWidth = dp(190)
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
        val winH = (cyOut + bobPx + radius * 1.25f + dp(24) + bubbleH + dp(8)).toInt()
        val originX = pill.left - margin
        val cx = moCenterX - originX

        val strip = RectF(
            margin + pillH / 2f, pillH - dp(12), margin + pill.width() - pillH / 2f, pillH,
        )
        // Where Mo may wander: along under the island, clear of its ends.
        val xMin = margin + radius
        val xMax = margin + pill.width() - radius
        val xMid = (xMin + xMax) / 2f
        val reach = (xMax - xMin) / 2f * 0.85f
        val stripMidY = pillH - dp(6)
        val goo = GooView(context).also { it.setGeometry(strip, cx, cyIn, radius, radius) }
        val mo = MoView(context).apply {
            this.mood = mood
            this.profile = profile
            idleMotion = true
        }
        val layer = FrameLayout(context).apply {
            clipChildren = false
            addView(goo, FrameLayout.LayoutParams(winW, winH))
            addView(mo, FrameLayout.LayoutParams(moSize, moSize))
            bubble?.let { addView(it, FrameLayout.LayoutParams(bubbleW, bubbleH)) }
        }
        mo.translationX = cx - moSize / 2f
        mo.translationY = cyIn - moSize / 2f

        val params = WindowManager.LayoutParams(
            winW, winH,
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
            x = originX
            y = pill.top
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
        var lastX = cx
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
                val roam = LiquidTimeline.roam(t)
                // Out from the island, then wandering: from Mo's own spot toward the middle, swinging
                // left and right, sinking and rising, and back to its own spot before it is pulled in.
                val x = (cx + (xMid - cx) * roam.envelope + reach * roam.wander).coerceIn(xMin, xMax)
                val cy = cyIn + (cyOut - cyIn) * p + bobPx * roam.bob
                // The drop: around Mo while it comes out, then it pops back into the island's edge so Mo
                // is free, and a new one forms around Mo as it comes back.
                val drop = LiquidTimeline.drop(t)
                val dropY = stripMidY + (cy - stripMidY) * drop.attach
                goo.setGeometry(strip, x, dropY, radius * drop.scale, radius * drop.scale)
                mo.translationX = x - moSize / 2f
                mo.translationY = cy - moSize / 2f
                // Leans into the way it is going, and straightens up as it slows.
                lean += ((x - lastX) * 2.2f - lean) * 0.2f
                lastX = x
                // A shake of the whole body, from side to side, in the middle of the roam.
                val shake = LiquidTimeline.shake(t)
                mo.rotation = lean.coerceIn(-10f, 10f) + 14f * shake
                mo.translationX += dp(2) * shake
                // The question under Mo, following it, fading in and out.
                bubble?.let {
                    it.alpha = LiquidTimeline.bubbleAlpha(t)
                    it.translationX = (x - bubbleW / 2f).coerceIn(0f, (winW - bubbleW).toFloat())
                    it.translationY = cy + moSize / 2f + dp(8)
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
                val blur = RenderEffect.createBlurEffect(BLUR_PX * resources.displayMetrics.density, BLUR_PX * resources.displayMetrics.density, Shader.TileMode.DECAL)
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
            const val BLUR_PX = 7.5f
        }
    }
}
