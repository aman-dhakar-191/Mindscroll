package io.github.amandhakar.mindscroll.display

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.TextUtils
import android.util.TypedValue
import android.view.Display
import android.view.Gravity
import android.view.MotionEvent
import android.view.Surface
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import io.github.amandhakar.mindscroll.data.Settings
import io.github.amandhakar.mindscroll.mascot.Mo
import io.github.amandhakar.mindscroll.mascot.MoView
import io.github.amandhakar.mindscroll.ui.MainActivity
import kotlin.math.abs

/**
 * The on-screen counters, all drawn as accessibility overlays. These belong to the
 * accessibility service, so they need no "display over other apps" permission and, unlike
 * normal overlays, can sit on top of the status bar.
 *
 * - Camera patch: a black island around the front camera, Mo on one side and the count on the
 *   other. Tap it for the panel.
 * - Bubble: Mo with a count badge, draggable anywhere. Tap it for the panel.
 * - Panel: Mo's take on today, per-app counts and the last 7 days. Closes on a tap outside,
 *   after a few seconds, or with its buttons.
 */
class CounterOverlay(
    private val service: AccessibilityService,
    private val settings: Settings,
) {
    private val windowManager = service.getSystemService(WindowManager::class.java)
    private val handler = Handler(Looper.getMainLooper())

    private var patch: PatchViews? = null
    private var bubble: BubbleViews? = null
    private var panel: PanelViews? = null
    private var panelClosedAt = 0L

    private var state: CounterState? = null
    private var display = Settings.Display(cameraPatch = false, bubble = false)

    private class PatchViews(val root: View, val mo: MoView, val count: TextView, val bottomY: Int)
    private class BubbleViews(val root: View, val mo: MoView, val count: TextView)

    fun show(newState: CounterState, newDisplay: Settings.Display) {
        val grew = state != null && newState.total > (state?.total ?: 0)
        state = newState
        display = newDisplay

        if (display.cameraPatch && isPortrait()) {
            val views = patch ?: addPatch()?.also { patch = it }
            views?.let {
                it.mo.mood = newState.mood
                it.count.text = compact(newState.total)
                if (grew) bump(it.count, it.mo)
            }
        } else {
            removePatch()
        }

        if (display.bubble) {
            val views = bubble ?: addBubble()?.also { bubble = it }
            views?.let {
                it.mo.mood = newState.mood
                it.count.text = compact(newState.total)
                if (grew) bump(it.count, it.mo)
            }
        } else {
            bubble?.let { remove(it.root) }
            bubble = null
        }

        panel?.bind(newState)
    }

    /** The camera position moves with rotation; rebuild the patch for the new layout. */
    fun onConfigurationChanged() {
        removePatch()
        closePanel()
        state?.let { show(it, display) }
    }

    fun removeAll() {
        removePatch()
        bubble?.let { remove(it.root) }
        bubble = null
        closePanel()
    }

    // --- Camera patch ---

    private fun addPatch(): PatchViews? {
        val camera = topCutout()
        val height = maxOf((camera?.height() ?: 0) + dp(8), dp(30))
        val slot = dp(30)

        val mo = MoView(service)
        val count = TextView(service).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            gravity = Gravity.CENTER
            maxLines = 1
        }
        val pill = LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.BLACK)
                cornerRadius = height / 2f
            }
            setPadding(dp(8), 0, dp(8), 0)
            addView(mo, LinearLayout.LayoutParams(slot, dp(22)))
            // With a camera hole, Mo and the count sit either side of it, island style.
            val gap = if (camera != null) camera.width() + dp(10) else dp(6)
            addView(View(service), LinearLayout.LayoutParams(gap, 1))
            addView(count, LinearLayout.LayoutParams(slot + dp(6), LinearLayout.LayoutParams.MATCH_PARENT))
            contentDescription = "Mindscroll: today's reels. Tap for details."
            setOnClickListener { togglePanel() }
        }

        val width = dp(16) + slot + (if (camera != null) camera.width() + dp(10) else dp(6)) + slot + dp(6)
        val params = overlayParams(touchable = true).apply {
            this.width = width
            this.height = height
            gravity = Gravity.TOP or Gravity.START
            if (camera != null) {
                x = maxOf(0, camera.centerX() - width / 2)
                y = maxOf(0, camera.centerY() - height / 2)
            } else {
                x = (screenWidth() - width) / 2
                y = dp(4)
            }
        }
        if (!add(pill, params)) return null
        pill.alpha = 0f
        pill.scaleX = 0.6f
        pill.animate().alpha(1f).scaleX(1f).setDuration(250).setInterpolator(DecelerateInterpolator()).start()
        return PatchViews(pill, mo, count, bottomY = params.y + height)
    }

    private fun removePatch() {
        patch?.let { remove(it.root) }
        patch = null
    }

    // --- Bubble ---

    @SuppressLint("ClickableViewAccessibility")
    private fun addBubble(): BubbleViews? {
        val size = dp(60)
        val mo = MoView(service)
        val count = TextView(service).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(5), 0, dp(5), 0)
            minWidth = dp(20)
            background = GradientDrawable().apply {
                setColor(Color.BLACK)
                cornerRadius = dp(10).toFloat()
                setStroke(dp(1), Color.argb(90, 255, 255, 255))
            }
        }
        val root = FrameLayout(service).apply {
            addView(mo, FrameLayout.LayoutParams(dp(50), dp(50), Gravity.CENTER))
            addView(count, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, dp(20), Gravity.BOTTOM or Gravity.END))
            contentDescription = "Mindscroll bubble. Tap for details, drag to move."
        }
        val (startX, startY) = settings.bubblePosition
        val params = overlayParams(touchable = true).apply {
            width = size
            height = size
            gravity = Gravity.TOP or Gravity.START
            x = startX
            y = startY
        }
        var downX = 0f
        var downY = 0f
        var startParamX = 0
        var startParamY = 0
        var dragging = false
        root.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX; downY = event.rawY
                    startParamX = params.x; startParamY = params.y
                    dragging = false
                }
                MotionEvent.ACTION_MOVE -> {
                    if (abs(event.rawX - downX) > dp(8) || abs(event.rawY - downY) > dp(8)) dragging = true
                    if (dragging) {
                        params.x = startParamX + (event.rawX - downX).toInt()
                        params.y = startParamY + (event.rawY - downY).toInt()
                        runCatching { windowManager.updateViewLayout(v, params) }
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (dragging) settings.bubblePosition = params.x to params.y else togglePanel()
                }
            }
            true
        }
        return if (add(root, params)) BubbleViews(root, mo, count) else null
    }

    // --- Panel ---

    private fun togglePanel() {
        if (panel != null) {
            closePanel()
            return
        }
        // The tap that closed the panel from outside was on the patch itself: stay closed.
        if (SystemClock.uptimeMillis() - panelClosedAt < 300) return
        openPanel()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun openPanel() {
        val current = state ?: return
        val views = PanelViews(
            context = service,
            dp = ::dp,
            onOpenApp = {
                closePanel()
                runCatching {
                    service.startActivity(
                        Intent(service, MainActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                    )
                }
            },
            onClose = ::closePanel,
        )
        views.bind(current)
        views.root.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) closePanel()
            scheduleAutoClose()
            false
        }
        val params = overlayParams(touchable = true).apply {
            flags = flags or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
            width = minOf(screenWidth() - dp(24), dp(400))
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = (patch?.bottomY ?: dp(28)) + dp(8)
        }
        if (!add(views.root, params)) return
        panel = views
        views.root.apply {
            alpha = 0f
            pivotY = 0f
            scaleY = 0.85f
            animate().alpha(1f).scaleY(1f).setDuration(220).setInterpolator(DecelerateInterpolator()).start()
        }
        scheduleAutoClose()
    }

    private fun closePanel() {
        val views = panel ?: return
        panel = null
        panelClosedAt = SystemClock.uptimeMillis()
        handler.removeCallbacks(autoClose)
        views.root.animate().alpha(0f).scaleY(0.9f).setDuration(150)
            .withEndAction { remove(views.root) }
            .start()
    }

    private val autoClose = Runnable { closePanel() }

    private fun scheduleAutoClose() {
        handler.removeCallbacks(autoClose)
        handler.postDelayed(autoClose, PANEL_TIMEOUT_MS)
    }

    // --- Helpers ---

    /** When the count goes up: the number pops, and Mo hops and looks surprised. */
    private fun bump(count: View, mo: MoView) {
        mo.react()
        count.animate().cancel()
        count.scaleX = 1.35f
        count.scaleY = 1.35f
        count.animate().scaleX(1f).scaleY(1f).setDuration(350).setInterpolator(OvershootInterpolator(3f)).start()
        mo.animate().cancel()
        mo.translationY = -dp(4).toFloat()
        mo.animate().translationY(0f).setDuration(300).setInterpolator(OvershootInterpolator(4f)).start()
    }

    private fun compact(total: Long): String = if (total >= 1000) "${total / 1000}k" else total.toString()

    private fun overlayParams(touchable: Boolean) = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            (if (touchable) 0 else WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE),
        PixelFormat.TRANSLUCENT,
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    private fun add(view: View, params: WindowManager.LayoutParams): Boolean =
        runCatching { windowManager.addView(view, params) }.isSuccess

    private fun remove(view: View) {
        runCatching { windowManager.removeViewImmediate(view) }
    }

    private fun defaultDisplay(): Display? =
        service.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)

    private fun screenWidth(): Int = service.resources.displayMetrics.widthPixels

    private fun isPortrait(): Boolean = when (defaultDisplay()?.rotation) {
        Surface.ROTATION_90, Surface.ROTATION_270 -> false
        else -> true
    }

    /** The front camera cutout at the top of the screen, if the phone has one and says where. */
    private fun topCutout(): Rect? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val cutout = defaultDisplay()?.cutout ?: return null
        return cutout.boundingRects.firstOrNull { it.top < dp(48) && !it.isEmpty }
    }

    private fun dp(value: Int): Int =
        (value * service.resources.displayMetrics.density).toInt()

    private companion object {
        const val PANEL_TIMEOUT_MS = 10_000L
    }
}

/** The expanded panel's views. Plain views: Compose needs more plumbing in overlay windows. */
private class PanelViews(
    context: android.content.Context,
    private val dp: (Int) -> Int,
    onOpenApp: () -> Unit,
    onClose: () -> Unit,
) {
    private val mo = MoView(context).apply { idleMotion = true }
    private val moodText = text(context, 12f, bold = true)
    private val lineText = text(context, 15f, color = Color.WHITE).apply {
        maxLines = 3
        ellipsize = TextUtils.TruncateAt.END
    }
    private val countText = text(context, 40f, color = Color.WHITE, bold = true)
    private val apps = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val week = WeekBarsView(context)
    private val openButton = button(context, "Open Mindscroll", filled = true, onOpenApp)

    val root: LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(16), dp(18), dp(14))
        background = GradientDrawable().apply {
            setColor(Color.BLACK)
            cornerRadius = dp(28).toFloat()
        }
        elevation = dp(8).toFloat()

        addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(mo, LinearLayout.LayoutParams(dp(60), dp(60)))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), 0, 0, 0)
                addView(moodText)
                addView(lineText)
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        })

        addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
            setPadding(0, dp(10), 0, dp(4))
            addView(countText)
            addView(text(context, 14f).apply {
                text = "  reels today"
                setPadding(0, 0, 0, dp(8))
            })
        })
        addView(apps)
        addView(week, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(72)).apply {
            topMargin = dp(10)
        })
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            setPadding(0, dp(12), 0, 0)
            addView(button(context, "Close", filled = false, onClose))
            addView(View(context), LinearLayout.LayoutParams(dp(8), 1))
            addView(openButton)
        })
    }

    fun bind(state: CounterState) {
        val mood = state.mood
        val accent = mood.color.toInt()
        mo.mood = mood
        moodText.text = "${Mo.NAME} · ${mood.title}".uppercase()
        moodText.setTextColor(accent)
        lineText.text = state.line()
        countText.text = state.total.toString()
        (openButton.background as GradientDrawable).setColor(accent)

        apps.removeAllViews()
        val max = state.byItem.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
        state.byItem.forEach { (label, count) ->
            apps.addView(appRow(apps.context, label, count, count.toFloat() / max, accent))
        }
        week.set(state.week, accent)
    }

    private fun appRow(context: android.content.Context, label: String, count: Long, fraction: Float, accent: Int) =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(6), 0, 0)
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(text(context, 13f).apply { text = label }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                addView(text(context, 13f, color = Color.WHITE, bold = true).apply { text = count.toString() })
            })
            // Track and bar.
            addView(FrameLayout(context).apply {
                background = GradientDrawable().apply {
                    setColor(Color.argb(40, 255, 255, 255))
                    cornerRadius = dp(3).toFloat()
                }
                addView(View(context).apply {
                    background = GradientDrawable().apply {
                        setColor(accent)
                        cornerRadius = dp(3).toFloat()
                    }
                    pivotX = 0f
                    scaleX = fraction.coerceIn(0.03f, 1f)
                }, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dp(6)))
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(6)).apply { topMargin = dp(4) })
        }

    private fun text(
        context: android.content.Context,
        sp: Float,
        color: Int = Color.argb(170, 255, 255, 255),
        bold: Boolean = false,
    ) = TextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        setTextColor(color)
        if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
    }

    private fun button(context: android.content.Context, label: String, filled: Boolean, onClick: () -> Unit) =
        TextView(context).apply {
            text = label
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setTextColor(if (filled) 0xFF1B1B2F.toInt() else Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(9), dp(16), dp(9))
            background = GradientDrawable().apply {
                cornerRadius = dp(20).toFloat()
                if (filled) setColor(Color.WHITE) else setStroke(dp(1), Color.argb(90, 255, 255, 255))
            }
            setOnClickListener { onClick() }
        }
}
