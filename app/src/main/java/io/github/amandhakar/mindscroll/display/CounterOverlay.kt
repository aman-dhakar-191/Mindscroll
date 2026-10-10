package io.github.amandhakar.mindscroll.display

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.os.Build
import android.util.TypedValue
import android.view.Display
import android.view.Gravity
import android.view.MotionEvent
import android.view.Surface
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextView
import io.github.amandhakar.mindscroll.data.Settings
import kotlin.math.abs

/**
 * Two optional on-screen counters, both drawn as accessibility overlays. These belong to the
 * accessibility service, so they need no "display over other apps" permission and, unlike
 * normal overlays, can sit on top of the status bar.
 *
 * - Camera patch: a black pill around the front camera with the count beside it.
 * - Bubble: a small draggable circle.
 */
class CounterOverlay(
    private val service: AccessibilityService,
    private val settings: Settings,
) {
    private val windowManager = service.getSystemService(WindowManager::class.java)
    private var patch: View? = null
    private var patchText: TextView? = null
    private var bubble: TextView? = null
    private var lastTotal = 0L
    private var lastDisplay = Settings.Display(cameraPatch = false, bubble = false)

    fun show(total: Long, display: Settings.Display) {
        lastTotal = total
        lastDisplay = display
        val showPatch = display.cameraPatch && isPortrait()
        if (showPatch) {
            if (patch == null) addPatch()
            patchText?.text = total.toString()
        } else {
            removePatch()
        }
        if (display.bubble) {
            if (bubble == null) addBubble()
            bubble?.text = total.toString()
        } else {
            bubble?.let { remove(it) }
            bubble = null
        }
    }

    /** The camera position moves with rotation; rebuild the patch for the new layout. */
    fun onConfigurationChanged() {
        removePatch()
        show(lastTotal, lastDisplay)
    }

    fun removeAll() {
        removePatch()
        bubble?.let { remove(it) }
        bubble = null
    }

    private fun addPatch() {
        val camera = topCutout()
        val height = maxOf(camera?.height() ?: 0, dp(26))
        val text = TextView(service).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER_VERTICAL
        }
        val pill = LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.BLACK)
                cornerRadius = height / 2f
            }
            setPadding(dp(10), 0, dp(12), 0)
            // Leave room for the camera hole itself, then the count to its right.
            if (camera != null) addView(Space(service), LinearLayout.LayoutParams(camera.width() + dp(6), 1))
            addView(text)
        }
        val params = overlayParams(touchable = false).apply {
            this.height = height
            if (camera != null) {
                gravity = Gravity.TOP or Gravity.START
                x = maxOf(0, camera.left - dp(10))
                y = maxOf(0, camera.centerY() - height / 2)
            } else {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = dp(4)
            }
        }
        if (add(pill, params)) {
            patch = pill
            patchText = text
        }
    }

    private fun removePatch() {
        patch?.let { remove(it) }
        patch = null
        patchText = null
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun addBubble() {
        val size = dp(48)
        val view = TextView(service).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.argb(0xCC, 0x1B, 0x1B, 0x2F))
                setStroke(dp(2), Color.argb(0xFF, 0x8E, 0x7C, 0xFF))
            }
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
        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX; downY = event.rawY
                    startParamX = params.x; startParamY = params.y
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = startParamX + (event.rawX - downX).toInt()
                    params.y = startParamY + (event.rawY - downY).toInt()
                    runCatching { windowManager.updateViewLayout(v, params) }
                }
                MotionEvent.ACTION_UP -> {
                    if (abs(event.rawX - downX) > 4 || abs(event.rawY - downY) > 4) {
                        settings.bubblePosition = params.x to params.y
                    }
                }
            }
            true
        }
        if (add(view, params)) bubble = view
    }

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
}
