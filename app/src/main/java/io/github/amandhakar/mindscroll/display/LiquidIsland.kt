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
import android.graphics.Shader
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import io.github.amandhakar.mindscroll.mascot.MoView
import io.github.amandhakar.mindscroll.mascot.MotionProfile
import io.github.amandhakar.mindscroll.mascot.Mood
import io.github.amandhakar.mindscroll.tracker.DetectionLog

/**
 * Experimental. Mo comes out of the island, hovers, and goes back in, and the island stretches
 * toward Mo like a liquid and pulls it back in.
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
     * [hideRealMo] hides (true) or shows (false) the island's own Mo while this one is out.
     * [onEnd] runs when it is over, or cancelled. Returns false if it could not start.
     */
    fun play(
        pill: Rect,
        moCenterX: Float,
        moSize: Int,
        mood: Mood,
        profile: MotionProfile,
        hideRealMo: (Boolean) -> Unit,
        onEnd: () -> Unit,
    ): Boolean {
        if (running) return false
        val density = context.resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val margin = dp(24)
        val radius = moSize / 2f + dp(2)
        val pillH = pill.height().toFloat()
        val cyIn = pillH / 2f
        val cyOut = pillH + dp(12)
        val winW = pill.width() + 2 * margin
        val winH = (cyOut + radius + dp(24)).toInt()
        val originX = pill.left - margin
        val cx = moCenterX - originX

        val strip = RectF(
            margin + pillH / 2f, pillH - dp(8), margin + pill.width() - pillH / 2f, pillH,
        )
        val goo = GooView(context).also { it.setGeometry(strip, cx, cyIn, radius) }
        val mo = MoView(context).apply {
            this.mood = mood
            this.profile = profile
            idleMotion = true
        }
        val layer = FrameLayout(context).apply {
            clipChildren = false
            addView(goo, FrameLayout.LayoutParams(winW, winH))
            addView(mo, FrameLayout.LayoutParams(moSize, moSize))
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
        var glanced = false

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
                val cy = cyIn + (cyOut - cyIn) * p
                goo.setGeometry(strip, cx, cy, radius)
                mo.translationY = cy - moSize / 2f
                // The island's own Mo steps out just after this one is drawn over it, so nothing flickers.
                if (!realMoHidden && t > 0.02f) {
                    realMoHidden = true
                    hideRealMo(true)
                }
                if (!waved && LiquidTimeline.isOut(t)) {
                    waved = true
                    mo.react(intensity = 0.7f, excited = true)
                }
                if (!glanced && t >= 0.5f) {
                    glanced = true
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
        private var r = 0f

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

        fun setGeometry(strip: RectF, cx: Float, cy: Float, r: Float) {
            this.strip.set(strip)
            this.cx = cx
            this.cy = cy
            this.r = r
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            canvas.drawRoundRect(strip, strip.height() / 2f, strip.height() / 2f, paint)
            canvas.drawCircle(cx, cy, r, paint)
        }

        private companion object {
            const val BLUR_PX = 6f
        }
    }
}
