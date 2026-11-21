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
import android.view.animation.AlphaAnimation
import android.view.animation.TranslateAnimation
import android.view.animation.OvershootInterpolator
import android.widget.Chronometer
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ViewFlipper
import android.widget.Toast
import io.github.amandhakar.mindscroll.data.Durations
import io.github.amandhakar.mindscroll.data.Settings
import io.github.amandhakar.mindscroll.app
import io.github.amandhakar.mindscroll.mascot.Mo
import io.github.amandhakar.mindscroll.mascot.MoPolicy
import kotlin.random.Random
import io.github.amandhakar.mindscroll.mascot.MoLook
import io.github.amandhakar.mindscroll.mascot.MoView
import io.github.amandhakar.mindscroll.mascot.MoMotion
import io.github.amandhakar.mindscroll.mascot.MoProp
import io.github.amandhakar.mindscroll.mascot.MoTemperament
import io.github.amandhakar.mindscroll.mascot.ScrollPattern
import io.github.amandhakar.mindscroll.mascot.MotionProfile
import io.github.amandhakar.mindscroll.mascot.SessionReaction
import io.github.amandhakar.mindscroll.mascot.Mood
import io.github.amandhakar.mindscroll.service.AppSession
import io.github.amandhakar.mindscroll.tracker.DetectionLog
import io.github.amandhakar.mindscroll.tracker.TrackedApps
import io.github.amandhakar.mindscroll.tracker.reels.ReelApps
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

    /** With the timer set to appear now and then: whether it is up right now. */
    private var timerRevealed = false
    private val revealTimer = Runnable {
        setTimerRevealed(true)
        handler.postDelayed(hideTimer, TIMER_REVEAL_MS)
    }
    private val hideTimer = Runnable {
        setTimerRevealed(false)
        scheduleTimerReveal()
    }

    private var patch: PatchViews? = null

    /** Experimental: Mo comes out of the island and goes back in (see [LiquidIsland]). Made when first needed. */
    private var liquid: LiquidIsland? = null
    private var lastLiquidAtMs = 0L
    /** Brings Mo out when it is due (see [scheduleLiquid]). */
    private val liquidTick = Runnable { liquidEmerge("it has been a while", minGapMs = 0L) }
    private var bubble: BubbleViews? = null
    private var panel: PanelViews? = null
    private var panelClosedAt = 0L

    private var speech: View? = null
    /** Called when a speech bubble goes away: tapped away (true) or after running its course. */
    var onSpeechEnded: ((dismissed: Boolean) -> Unit)? = null
    private var moAsleep = false
    private var screenOn = true

    private var state: CounterState? = null
    private var display = settings.display()
    private var lastPokeAtMs = 0L
    private var consecutivePokes = 0
    private var lastReelAtMs = 0L
    private var rapidReels = 0
    /** Reels in a steady run (a minute or less apart); Mo gets used to it. See [MoTemperament]. */
    private var steadyStreak = 0

    private var session: AppSession? = null
    private var lastSessionPackage: String? = null
    private var lastSessionEndedAtMs: Long? = null
    private var hasSeenSession = false
    private var exitPending = false
    private var pendingPatchReaction: SessionReaction? = null
    private val finishExit = Runnable { exitPending = false; refresh() }
    private var inCall = false
    /** Long-pressing the patch hides it until this time (uptime ms). */
    private var snoozedUntil = 0L
    private val endSnooze = Runnable { refresh() }

    private class PatchViews(
        val root: LinearLayout,
        val left: LinearLayout,
        val mo: MoView,
        val timer: Chronometer,
        val count: TextView,
        val info: ViewFlipper,
        val params: WindowManager.LayoutParams,
        val camera: Rect?,
        val bottomY: Int,
    )
    private class BubbleViews(val root: View, val mo: MoView, val count: TextView)

    fun show(newState: CounterState, newDisplay: Settings.Display) {
        val grew = state != null && newState.total > (state?.total ?: 0)
        state = newState
        val displayChanged = display != newDisplay
        val revealChanged = display.timerReveal != newDisplay.timerReveal ||
            display.counterTimerEvery != newDisplay.counterTimerEvery || display.info != newDisplay.info
        val liquidChanged = display.liquidIsland != newDisplay.liquidIsland || display.liquidEvery != newDisplay.liquidEvery
        display = newDisplay
        if (liquidChanged) scheduleLiquid()
        if (revealChanged) {
            timerRevealed = false
            scheduleTimerReveal()
        }
        if (displayChanged) {
            DetectionLog.add(
                "display: style=${newDisplay.motionStyle.name.lowercase()}, " +
                    "info=${newDisplay.info.perApp.size} chosen (defaults: reel apps ${newDisplay.info.reelDefault.name.lowercase()}, " +
                    "others ${newDisplay.info.otherDefault.name.lowercase()}), " +
                    "mascot=${newDisplay.mascotSize.name.lowercase()}, patch=${newDisplay.cameraPatch}, bubble=${newDisplay.bubble}",
            )
        }
        if (displayChanged) {
            // A new character or palette: the views read MoLook when they draw, so just redraw.
            patch?.mo?.invalidate()
            bubble?.mo?.invalidate()
        }
        if (newState.mood != lastLoggedMood) {
            DetectionLog.add("mo: mood ${lastLoggedMood?.name?.lowercase() ?: "start"} → ${newState.mood.name.lowercase()} (${newState.total} reels today)")
            lastLoggedMood = newState.mood
        }

        logPatchWanted(patchWanted())
        if (patchWanted()) {
            val views = patch ?: addPatch()?.also {
                patch = it
                it.mo.asleep = moAsleep
                pendingPatchReaction?.let { reaction ->
                    playSessionReaction(it.mo, reaction)
                    pendingPatchReaction = null
                }
            }
            views?.let {
                it.mo.mood = newState.mood
                it.mo.profile = profile()
                it.mo.idleMotion = screenOn && display.motionStyle != Settings.MotionStyle.CALM
                it.count.text = compact(newState.total)
                bindInfoMode(it)
                layoutPatch(it, apply = true)
                if (grew) bump(it.count, it.mo)
            }
        } else {
            removePatch()
        }

        if (display.bubble) {
            val views = bubble ?: addBubble()?.also { bubble = it; it.mo.asleep = moAsleep }
            views?.let {
                it.mo.mood = newState.mood
                it.mo.profile = profile()
                it.mo.idleMotion = screenOn && display.motionStyle != Settings.MotionStyle.CALM
                it.count.text = compact(newState.total)
                bindBubbleCount()
                if (displayChanged) {
                    val params = it.root.layoutParams as WindowManager.LayoutParams
                    params.width = bubbleSize()
                    params.height = bubbleSize()
                    (it.mo.layoutParams as FrameLayout.LayoutParams).apply {
                        width = bubbleMoSize()
                        height = bubbleMoSize()
                    }
                    runCatching { windowManager.updateViewLayout(it.root, params) }
                }
                if (grew) bump(it.count, it.mo)
            }
        } else {
            bubble?.let { remove(it.root) }
            bubble = null
        }

        panel?.bind(newState)
    }

    /**
     * Keep Mo's native patch visible for the full tracked-app session. It steps aside during
     * calls and while snoozed, but does not hand its place to a system Live Update chip: that
     * chip cannot render MoView, its animations, or the in-patch Chronometer.
     */
    private fun patchWanted(): Boolean =
        display.cameraPatch &&
            isPortrait() &&
            !inCall &&
            SystemClock.uptimeMillis() >= snoozedUntil &&
            (session != null || exitPending)

    /** Re-applies what's on screen after something that affects it changed. */
    private fun refresh() {
        state?.let { show(it, display) }
    }

    /** A phone or VoIP call is ringing or under way: the patch makes room for the call chip. */
    fun setInCall(newInCall: Boolean) {
        if (newInCall == inCall) return
        DetectionLog.add("overlay: call ${if (newInCall) "started, patch steps aside" else "ended"}")
        inCall = newInCall
        refresh()
    }

    /** The camera position moves with rotation; rebuild the patch for the new layout. */
    fun onConfigurationChanged() {
        removePatch()
        closePanel()
        state?.let { show(it, display) }
    }

    /** Pause continuous mascot animation while the display is off to avoid needless work. */
    fun setScreenOn(on: Boolean) {
        if (screenOn == on) return
        screenOn = on
        val animate = on && display.motionStyle != Settings.MotionStyle.CALM
        DetectionLog.add("mo: screen ${if (on) "on" else "off"} → idle animation ${if (animate) "on" else "off"} (${display.motionStyle.name.lowercase()})")
        patch?.mo?.idleMotion = animate
        bubble?.mo?.idleMotion = animate
    }

    /** Mo dozes off (true) or is put back to sleep; the patch and bubble show a sleeping Mo. */
    fun setAsleep(asleep: Boolean) {
        if (asleep != moAsleep) DetectionLog.add("mo: ${if (asleep) "fell asleep" else "no longer asleep"}")
        moAsleep = asleep
        patch?.mo?.asleep = asleep
        bubble?.mo?.asleep = asleep
    }

    /**
     * Mo reacts to a habit: dizzy spiral eyes for scrolling too fast, a lean in for lingering on
     * one reel, slow spirals when staring at it for ages, a stretch after a long stretch of
     * scrolling, and a double take for coming straight back.
     */
    fun onPattern(pattern: ScrollPattern) {
        val views = listOfNotNull(patch?.mo, bubble?.mo)
        DetectionLog.add(
            "mo: ${pattern.label} → " + when (pattern) {
                ScrollPattern.TOO_FAST -> "panic: fast spiral eyes, sweat drops, speed lines"
                ScrollPattern.LINGERING -> "side-eye and a slow \"...\""
                ScrollPattern.STARING -> "mesmerised: slow spiral eyes"
                ScrollPattern.MARATHON -> "stretch and yawn, a tuft of grass: touch grass"
                ScrollPattern.QUICK_RETURN -> "double take and a \"?!\""
            } + " (${views.size} view${if (views.size == 1) "" else "s"})",
        )
        views.forEach { mo ->
            when (pattern) {
                ScrollPattern.TOO_FAST -> {
                    mo.whirl(slow = false)
                    mo.props(MoProp.SWEAT, MoProp.SPEED_LINES)
                }
                ScrollPattern.LINGERING -> {
                    mo.sideEye()
                    mo.props(MoProp.DOTS)
                }
                ScrollPattern.STARING -> mo.whirl(slow = true)
                ScrollPattern.MARATHON -> {
                    mo.stretch()
                    mo.props(MoProp.GRASS)
                }
                ScrollPattern.QUICK_RETURN -> mo.doubleTake()
            }
        }
    }

    /** Mo wakes up with a stretch and a yawn. */
    fun wakeUp() {
        DetectionLog.add("mo: wake up (stretch and yawn)")
        moAsleep = false
        patch?.mo?.wakeUp()
        bubble?.mo?.wakeUp()
    }

    /**
     * Mo says something: a speech bubble under the camera patch (or the floating bubble), for
     * a few seconds or until tapped. Skipped while the panel is open or nothing shows Mo.
     * Returns whether it was shown.
     */
    fun say(text: String): Boolean {
        if (panel != null) return false
        val patchViews = patch
        val bubbleViews = bubble
        val params = overlayParams(touchable = true)
        val speaker: MoView
        when {
            patchViews != null -> {
                speaker = patchViews.mo
                params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                params.x = patchViews.params.x + patchViews.params.width / 2 - screenWidth() / 2
                params.y = patchViews.bottomY + dp(6)
            }
            bubbleViews != null -> {
                speaker = bubbleViews.mo
                val at = bubbleViews.root.layoutParams as WindowManager.LayoutParams
                params.gravity = Gravity.TOP or Gravity.START
                params.x = at.x
                params.y = at.y + dp(64)
            }
            else -> return false
        }
        removeSpeech()
        val accent = MoLook.color(state?.mood ?: Mood.CHILL)
        val view = TextView(service).apply {
            this.text = text
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            maxWidth = dp(260)
            setPadding(dp(14), dp(9), dp(14), dp(10))
            background = GradientDrawable().apply {
                setColor(Color.BLACK)
                cornerRadius = dp(18).toFloat()
                setStroke(dp(1), accent)
            }
            contentDescription = "${Mo.NAME} says: $text"
            setOnClickListener { removeSpeech(dismissed = true) }
        }
        if (!add(view, params)) return false
        speech = view
        if (speaker.asleep) speaker.wakeUp() else speaker.react()
        view.alpha = 0f
        view.translationY = -dp(6).toFloat()
        view.animate().alpha(1f).translationY(0f).setDuration(200).setInterpolator(DecelerateInterpolator()).start()
        handler.removeCallbacks(hideSpeech)
        handler.postDelayed(hideSpeech, SPEECH_MS + text.length * SPEECH_MS_PER_CHAR)
        return true
    }

    private val hideSpeech = Runnable { removeSpeech(dismissed = false) }

    /** [dismissed] says how the bubble went away, or is null when it was cleared for other reasons. */
    private fun removeSpeech(dismissed: Boolean? = null) {
        handler.removeCallbacks(hideSpeech)
        val view = speech ?: return
        speech = null
        dismissed?.let { onSpeechEnded?.invoke(it) }
        view.animate().alpha(0f).setDuration(150).withEndAction { remove(view) }.start()
    }

    fun removeAll() {
        handler.removeCallbacks(endSnooze)
        handler.removeCallbacks(liquidTick)
        liquid?.cancel()
        handler.removeCallbacks(revealTimer)
        handler.removeCallbacks(hideTimer)
        removeSpeech()
        removePatch(animate = false)
        bubble?.let { remove(it.root) }
        bubble = null
        closePanel()
    }

    // --- Camera patch ---

    private fun addPatch(): PatchViews? {
        val camera = topCutout()
        val patchMoSize = patchMoSize()
        // A thin rim around the camera, not a tall pill: it used to be at least 44dp and centred on
        // the camera, which ran it to the very top of the screen. Now a little screen shows above it.
        val height = patchHeight(camera)
        val mo = MoView(service).apply {
            profile = this@CounterOverlay.profile()
            idleMotion = this@CounterOverlay.display.motionStyle != Settings.MotionStyle.CALM
            contentDescription = "Mo, tap to interact"
            setOnClickListener { poke(this) }
        }
        val timer = Chronometer(service).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            gravity = Gravity.CENTER
            maxLines = 1
            visibility = View.VISIBLE
        }
        val count = TextView(service).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            gravity = Gravity.CENTER
            maxLines = 1
        }
        val left = LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            addView(mo, LinearLayout.LayoutParams(patchMoSize, patchMoSize))
        }
        // The counter and the timer share one slot. A change fades; nothing flips by itself or scrolls.
        val info = ViewFlipper(service).apply {
            isAutoStart = false
            inAnimation = AlphaAnimation(0f, 1f).apply { duration = 220 }
            outAnimation = AlphaAnimation(1f, 0f).apply { duration = 160 }
            addView(count, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            addView(timer, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            displayedChild = 0
        }
        val pill = LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.BLACK)
                cornerRadius = height / 2f
            }
            setPadding(dp(PATCH_PADDING_DP), 0, dp(PATCH_PADDING_DP), 0)
            // Mo occupies one side; the reel count and session timer share the other.
            addView(left, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT))
            addView(View(service), LinearLayout.LayoutParams(cameraGap(camera), 1))
            addView(info, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT))
            contentDescription = "Mindscroll: today's reels and current session time. Tap for details, long-press to hide for an hour."
            setOnClickListener { togglePanel() }
            setOnLongClickListener {
                snooze()
                true
            }
        }

        val params = overlayParams(touchable = true).apply {
            this.height = height
            gravity = Gravity.TOP or Gravity.START
            y = patchTop(camera, height)
        }
        val views = PatchViews(pill, left, mo, timer, count, info, params, camera, bottomY = params.y + height)
        bindSession(views)
        // A longer time (59:59 → 1:00:00) needs a wider island.
        var lastLength = 0
        timer.setOnChronometerTickListener {
            if (it.text.length != lastLength) {
                lastLength = it.text.length
                layoutPatch(views, apply = true)
            }
        }
        layoutPatch(views, apply = false)
        if (!add(pill, params)) return null
        pill.alpha = 0f
        pill.scaleX = 0.88f
        pill.scaleY = 0.82f
        pill.animate().alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(280).setInterpolator(OvershootInterpolator(0.8f)).start()
        return views
    }

    /** The app in use right now, or null. Shows or hides the live timer in the patch. */
    fun setSession(newSession: AppSession?) {
        if (newSession == session) return

        val now = SystemClock.uptimeMillis()
        val previousPackage = session?.packageName ?: lastSessionPackage
        val reaction = MoMotion.sessionReaction(
            previousPackage = previousPackage,
            currentPackage = newSession?.packageName,
            endedAtMs = lastSessionEndedAtMs,
            nowMs = now,
            hasSeenSession = hasSeenSession,
        )

        // So "Count looks wrong?" can explain what the patch shows, and why.
        if (newSession == null) {
            if (hasSeenSession) DetectionLog.add("overlay: no app session now (was ${previousPackage ?: "none"})")
        } else if (newSession.packageName != session?.packageName) {
            val pkg = newSession.packageName
            val source = if (TrackedApps.reelPackages.value == null) "default" else "your choice"
            DetectionLog.add(
                "overlay: session in ${TrackedApps.label(pkg)} ($pkg), reels counted there: " +
                    "${TrackedApps.countsReels(pkg)} ($source)",
            )
        }

        session = newSession
        // A new session (or a new timer base) starts its own 5- or 10-minute rhythm.
        timerRevealed = false
        scheduleTimerReveal()
        // Experimental: Mo comes out when it is due, counted from the last time (at most once an hour by default).
        if (newSession == null || newSession.packageName != previousPackage) scheduleLiquid()
        if (newSession == null) {
            if (hasSeenSession) lastSessionEndedAtMs = now
            if (reaction == SessionReaction.APP_EXIT) {
                DetectionLog.add("mo: app exit → turns away smiling for ${profile().exitMs}ms, then the patch goes")
                exitPending = true
                patch?.mo?.exitSession()
                bubble?.mo?.exitSession()
                handler.removeCallbacks(finishExit)
                handler.postDelayed(finishExit, profile().exitMs)
            }
        } else {
            handler.removeCallbacks(finishExit)
            exitPending = false
            lastSessionPackage = newSession.packageName
            lastSessionEndedAtMs = null
            hasSeenSession = true
            // Each edge gets its own small gesture; no reaction on ordinary state refreshes.
            when (reaction) {
                SessionReaction.FIRST_ENTRY, SessionReaction.APP_SWITCH, SessionReaction.RETURN_AFTER_BREAK -> {
                    val patchMo = patch?.mo
                    if (patchMo != null) playSessionReaction(patchMo, reaction)
                    else pendingPatchReaction = reaction
                    bubble?.mo?.let { playSessionReaction(it, reaction) }
                }
                SessionReaction.APP_EXIT, SessionReaction.NONE -> Unit
            }
        }

        patch?.let {
            bindSession(it)
            layoutPatch(it, apply = true)
        }
        bindBubbleCount()
        // Keep the camera island alive for Mo's farewell, then remove it after the animation.
        if (newSession != null || !exitPending) refresh()
    }

    /**
     * One gesture per session edge, each with its own face and body so they read differently:
     * entry leans out with raised brows and a smile, a switch glances sideways, coming back
     * after a break wakes (or, if awake, beams with a joyful hop), and exit turns away smiling.
     */
    private fun playSessionReaction(mo: MoView, reaction: SessionReaction) {
        DetectionLog.add(
            "mo: ${reaction.name.lowercase()} → " + when (reaction) {
                SessionReaction.FIRST_ENTRY -> "lean out and smile"
                SessionReaction.APP_SWITCH -> "sideways glance"
                SessionReaction.RETURN_AFTER_BREAK -> if (mo.asleep) "wake with a stretch" else "joyful hop"
                else -> "nothing"
            } + " (${display.motionStyle.name.lowercase()})",
        )
        when (reaction) {
            SessionReaction.FIRST_ENTRY -> mo.peek()
            SessionReaction.APP_SWITCH -> mo.glance()
            SessionReaction.RETURN_AFTER_BREAK -> {
                if (mo.asleep) mo.wakeUp() else mo.react(1.15f, excited = true)
            }
            SessionReaction.APP_EXIT, SessionReaction.NONE -> Unit
        }
    }

    private fun bindSession(views: PatchViews) {
        val current = session
        if (current == null) {
            views.timer.stop()
            bindInfoMode(views)
            views.root.contentDescription = "Mindscroll: today's reels. Tap for details."
            return
        }
        val app = ReelApps.forPackage(current.packageName, TrackedApps.label(current.packageName))
        // Running whenever the timer is part of what this app shows; the flipper decides when it is seen.
        val timerNeeded = IslandInfo.usesTimer(choice(), countsReelsHere())
        views.timer.base = current.timerBase
        views.timer.setTextColor(app.accent.toInt())
        if (timerNeeded) views.timer.start() else views.timer.stop()
        bindInfoMode(views)
        views.root.contentDescription = if (reelContext()) {
            "Mindscroll: time in ${app.label} and today's reels. Tap for details."
        } else {
            "Mindscroll: time in ${app.label}. Tap for details."
        }
    }

    /**
     * Whether the app in front counts reels. Every tracked app has a timer; the reel counter
     * belongs to the apps set to count reels. With no session (home screen, other apps) the
     * counter is shown as usual.
     */
    private fun reelContext(): Boolean = session?.packageName?.let(TrackedApps::countsReels) ?: true

    /** The app the island is for: the one in front, or the one just left while Mo says goodbye. */
    private fun infoPackage(): String? = session?.packageName ?: lastSessionPackage

    private fun countsReelsHere(): Boolean = infoPackage()?.let(TrackedApps::countsReels) ?: true

    /** What the user chose for this app, after the rule that an app without reels has no counter. */
    private fun choice(): Settings.AppInfo = display.info.choiceFor(infoPackage(), countsReelsHere())

    /** What fills the information slot right now (the rules are in [IslandInfo]). */
    private fun content(): IslandContent =
        IslandInfo.content(choice(), countsReelsHere(), display.timerReveal, timerRevealed)

    /** Brings the timer up or puts it away, resizing the island around it. */
    private fun setTimerRevealed(revealed: Boolean) {
        timerRevealed = revealed
        patch?.let {
            bindSession(it)
            layoutPatch(it, apply = true)
        }
    }

    /** Plans the next time the timer shows, from how long the session has run. Nothing when it doesn't come and go. */
    private fun scheduleTimerReveal() {
        handler.removeCallbacks(revealTimer)
        handler.removeCallbacks(hideTimer)
        val current = session ?: return
        val every = IslandInfo.rhythmMs(choice(), countsReelsHere(), display.timerReveal, display.counterTimerEvery) ?: return
        handler.postDelayed(revealTimer, IslandInfo.nextRevealInMs(every, SystemClock.elapsedRealtime() - current.timerBase))
    }

    /** The bubble's badge is a reel count, so it goes with the counter. */
    private fun bindBubbleCount() {
        bubble?.count?.visibility = if (reelContext()) View.VISIBLE else View.GONE
    }

    private var lastLoggedInfo = ""
    private var lastLoggedMood: Mood? = null
    private var lastPatchWanted: Boolean? = null

    /** Logs the patch appearing or going away, with what was holding it back. */
    private fun logPatchWanted(wanted: Boolean) {
        if (lastPatchWanted == wanted) return
        lastPatchWanted = wanted
        DetectionLog.add(
            if (wanted) "overlay: patch shown"
            else "overlay: patch hidden (cameraPatch=${display.cameraPatch}, portrait=${isPortrait()}, inCall=$inCall, " +
                "snoozed=${SystemClock.uptimeMillis() < snoozedUntil}, session=${session != null}, exiting=$exitPending)",
        )
    }

    /** Logs what the patch shows and why, when that changes, so a surprise can be explained afterwards. */
    private fun logInfoDecision(content: IslandContent) {
        val line = "overlay shows ${content.name.lowercase()}: app=${infoPackage() ?: "none"}, chosen=${choice().name.lowercase()}, " +
            "countsReels=${countsReelsHere()}, timerUp=$timerRevealed, session=${session != null}, exiting=$exitPending"
        if (line == lastLoggedInfo) return
        lastLoggedInfo = line
        DetectionLog.add(line)
    }

    private fun bindInfoMode(views: PatchViews) {
        val content = content()
        logInfoDecision(content)
        views.info.stopFlipping()
        when (content) {
            IslandContent.COUNTER -> {
                views.info.visibility = View.VISIBLE
                views.info.displayedChild = 0
            }
            IslandContent.TIMER -> {
                views.info.visibility = if (session == null) View.GONE else View.VISIBLE
                views.info.displayedChild = 1
            }
            IslandContent.NOTHING -> views.info.visibility = View.GONE
        }
    }

    /**
     * Sizes the patch around the larger Mo and the single information slot.
     */
    private fun layoutPatch(views: PatchViews, apply: Boolean) {
        val moSize = patchMoSize()
        (views.left.getChildAt(0).layoutParams as LinearLayout.LayoutParams).apply {
            width = moSize
            height = moSize
        }
        val timerWidth = views.timer.paint.measureText("00:00").toInt() + dp(12)
        val countWidth = views.count.paint.measureText(views.count.text.toString()).toInt() + dp(12)
        val moWidth = moSize + dp(4)
        val content = content()
        val infoWidth = when {
            content == IslandContent.NOTHING -> 0
            // Counter with the timer now and then: one width for both, so the island doesn't jump.
            choice() == Settings.AppInfo.BOTH -> maxOf(timerWidth, countWidth)
            content == IslandContent.TIMER -> if (session == null) 0 else timerWidth
            else -> countWidth
        }
        // Both sides of the camera get the same width, so the hole sits in the middle of the island
        // whatever is on either side. With Mo on one side and a narrower (or no) timer on the other,
        // the island used to be centred on the camera but the hole was off-centre inside it.
        val side = maxOf(moWidth, infoWidth)
        (views.left.layoutParams as LinearLayout.LayoutParams).width = side
        (views.info.layoutParams as LinearLayout.LayoutParams).width = if (infoWidth == 0) 0 else side
        val width = 2 * dp(PATCH_PADDING_DP) + 2 * side + cameraGap(views.camera)
        views.params.width = width
        // Mo's size can change while the island is up, and the island hugs whatever is tallest.
        val height = patchHeight(views.camera)
        views.params.height = height
        views.params.y = patchTop(views.camera, height)
        (views.root.background as? GradientDrawable)?.cornerRadius = height / 2f
        views.params.x = maxOf(0, (views.camera?.centerX() ?: (screenWidth() / 2)) - width / 2)
        logCameraPlacement(views, width)
        if (apply) {
            views.left.requestLayout()
            views.info.requestLayout()
            runCatching { windowManager.updateViewLayout(views.root, views.params) }
        }
    }

    private var lastLoggedPlacement: String? = null

    /** Where the camera is and where the island went, once per change, to explain an island that looks off-centre. */
    private fun logCameraPlacement(views: PatchViews, width: Int) {
        val camera = views.camera
        val line = "overlay: camera=${camera?.let { "x ${it.left}..${it.right}, y ${it.top}..${it.bottom}" } ?: "none"}, " +
            "screen=${screenWidth()}, island x=${views.params.x} width=$width" +
            (camera?.let { ", camera centre ${it.centerX()} vs island centre ${views.params.x + width / 2}" } ?: "")
        if (line == lastLoggedPlacement) return
        lastLoggedPlacement = line
        DetectionLog.add(line)
    }

    private fun patchHeight(camera: Rect?): Int =
        maxOf((camera?.height() ?: 0) + dp(PATCH_RIM_DP * 2), patchMoSize() + dp(4))

    private fun patchTop(camera: Rect?, height: Int): Int =
        if (camera != null) maxOf(dp(PATCH_TOP_GAP_DP), camera.centerY() - height / 2) else dp(4)

    private fun patchMoSize(): Int = dp(when (display.mascotSize) {
        Settings.MascotSize.COMPACT -> 28
        Settings.MascotSize.STANDARD -> 34
        Settings.MascotSize.LARGE -> 40
    })

    private fun bubbleMoSize(): Int = dp(when (display.mascotSize) {
        Settings.MascotSize.COMPACT -> 42
        Settings.MascotSize.STANDARD -> 50
        Settings.MascotSize.LARGE -> 58
    })

    private fun bubbleSize(): Int = dp(when (display.mascotSize) {
        Settings.MascotSize.COMPACT -> 52
        Settings.MascotSize.STANDARD -> 60
        Settings.MascotSize.LARGE -> 70
    })

    private fun cameraGap(camera: Rect?): Int = if (camera != null) camera.width() + dp(8) else dp(8)

    /** Takes the patch off screen, fading it out unless [animate] is false (shutting down). */
    private fun removePatch(animate: Boolean = true) {
        val views = patch ?: return
        handler.removeCallbacks(liquidTick)
        liquid?.cancel()
        patch = null
        views.timer.stop()
        views.info.stopFlipping()
        if (speech != null) removeSpeech()
        if (!animate) {
            remove(views.root)
            return
        }
        views.root.animate().alpha(0f).scaleX(0.6f).setDuration(200)
            .withEndAction { remove(views.root) }
            .start()
        // Mo's patch may have only been staying for the panel.
        refresh()
    }

    /** Hides the patch for [SNOOZE_MS]; it comes back by itself. */
    private fun snooze() {
        DetectionLog.add("overlay: long-press → hidden for an hour")
        snoozedUntil = SystemClock.uptimeMillis() + SNOOZE_MS
        handler.removeCallbacks(endSnooze)
        handler.postDelayed(endSnooze, SNOOZE_MS)
        closePanel()
        refresh()
        Toast.makeText(service, "${Mo.NAME} will be back in an hour.", Toast.LENGTH_SHORT).show()
    }

    // --- Bubble ---

    @SuppressLint("ClickableViewAccessibility")
    private fun addBubble(): BubbleViews? {
        val size = bubbleSize()
        val bubbleMoSize = bubbleMoSize()
        val mo = MoView(service).apply {
            profile = this@CounterOverlay.profile()
            idleMotion = this@CounterOverlay.display.motionStyle != Settings.MotionStyle.CALM
            contentDescription = "Mo, tap to interact"
            setOnClickListener { poke(this) }
        }
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
            addView(mo, FrameLayout.LayoutParams(bubbleMoSize, bubbleMoSize, Gravity.CENTER))
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
                    if (dragging) {
                        settings.bubblePosition = params.x to params.y
                    } else {
                        val hitMo = event.x >= mo.x && event.x <= mo.x + mo.width &&
                            event.y >= mo.y && event.y <= mo.y + mo.height
                        if (hitMo) poke(mo) else togglePanel()
                    }
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
        removeSpeech()
        if (moAsleep) wakeUp()
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

    /** The user's motion style as animation timing and physics. */
    private fun profile(): MotionProfile = display.motionStyle.profile

    /**
     * Squashes [view] to the given scale and lifts it [hopPx], from wherever it is right now, then
     * springs everything back to rest with the style's overshoot. Starting from the current
     * values (not fixed ones) means a second kick mid-animation continues smoothly instead of
     * snapping, and always animating all three properties back to rest means cancelling a kick
     * can never strand the view part-way. [delayMs] holds the launch until the body's crouch
     * releases, so the hop leaves the ground with the pop.
     */
    private fun kick(view: View, peakX: Float, peakY: Float, hopPx: Float, p: MotionProfile, delayMs: Long = 0L) {
        view.animate().cancel()
        view.animate().scaleX(peakX).scaleY(peakY).translationY(-hopPx)
            .setStartDelay(delayMs)
            .setDuration(p.riseMs)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                view.animate().scaleX(1f).scaleY(1f).translationY(0f)
                    .setStartDelay(0L)
                    .setDuration((p.settleMs * 0.6f).toLong())
                    .setInterpolator(OvershootInterpolator((1f - p.damping) * 5f))
                    .start()
            }
            .start()
    }

    /** A direct tap on Mo is a small interaction; the surrounding island still opens details. */
    private fun poke(mo: MoView) {
        val now = SystemClock.uptimeMillis()
        consecutivePokes = if (now - lastPokeAtMs <= 650L) consecutivePokes + 1 else 1
        lastPokeAtMs = now
        if (mo.asleep) {
            DetectionLog.add("mo: tapped while asleep")
            mo.wakeUp()
        } else {
            val p = profile()
            val repeated = consecutivePokes >= 3
            DetectionLog.add("mo: tapped (#$consecutivePokes in a row${if (repeated) ", giggles" else ""}, ${display.motionStyle.name.lowercase()})")
            // Tickled: the third quick tap in a row turns the startle into a giggle.
            mo.react(excited = repeated)
            // Tickled past a point, Mo is crying with laughter.
            if (repeated) mo.props(MoProp.TEARS)
            val playful = display.motionStyle == Settings.MotionStyle.PLAYFUL
            val squash = when {
                repeated && playful -> 0.78f
                playful -> 0.88f
                display.motionStyle == Settings.MotionStyle.CALM -> 0.97f
                else -> 0.94f
            }
            kick(mo, squash, if (repeated && playful) 1.12f else squash, hopPx = 0f, p = p)
            // Experimental: tapping Mo in the island makes it come out for a moment.
            if (mo === patch?.mo) liquidEmerge("tapped", minGapMs = 4_000L)
        }
    }

    /**
     * What Mo says while it is out: how Mo feels, in Mo's voice. Null when Mo keeps quiet: unprompted
     * remarks switched off, quiet hours, or snoozed.
     */
    private fun liquidFeeling(): String? = runCatching {
        val current = state ?: return@runCatching null
        val store = service.app.moPrefs
        if (!store.profile.initiatives || MoPolicy(store).quiet(System.currentTimeMillis())) return@runCatching null
        Mo.feeling(current.mood, Mo.isRested(current.total, current.mood), Random.nextLong())
    }.getOrNull()

    /**
     * Plans the next time Mo comes out: the user's frequency (an hour by default) after the last
     * time, and not sooner than a moment after the app opened. Nothing when the experiment is off or
     * no tracked app is open.
     */
    private fun scheduleLiquid() {
        handler.removeCallbacks(liquidTick)
        if (!display.liquidIsland || session == null) return
        val due = settings.liquidLastAt + display.liquidEvery.ms - System.currentTimeMillis()
        handler.postDelayed(liquidTick, maxOf(LIQUID_FIRST_DELAY_MS, due))
    }

    /** The whole screen, including the bars, in pixels. */
    private fun screenBounds(): Rect =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Rect(windowManager.maximumWindowMetrics.bounds)
        else service.resources.displayMetrics.let { Rect(0, 0, it.widthPixels, it.heightPixels) }

    /**
     * Experimental, behind its own setting: Mo comes out of the island in a drop, roams the whole
     * screen along a random route, shakes its body and says how it feels, then goes back in. Skipped
     * while asleep, in a call, while Mo is speaking or the panel is open (it tries again a little
     * later), or if it played a moment ago.
     */
    private fun liquidEmerge(reason: String, minGapMs: Long) {
        if (!display.liquidIsland) return
        val views = patch ?: run {
            // The island isn't up (snoozed, say): try again soon, if this was the scheduled one.
            if (minGapMs == 0L && session != null) handler.postDelayed(liquidTick, LIQUID_RETRY_MS)
            return
        }
        val now = SystemClock.uptimeMillis()
        val island = liquid ?: LiquidIsland(service, windowManager).also { liquid = it }
        if (now - lastLiquidAtMs < minGapMs) return
        if (island.running || moAsleep || inCall || speech != null || panel != null) {
            // Not a good moment: try again soon, if this was the scheduled one.
            if (minGapMs == 0L && session != null) handler.postDelayed(liquidTick, LIQUID_RETRY_MS)
            return
        }
        lastLiquidAtMs = now
        val pill = Rect(views.params.x, views.params.y, views.params.x + views.params.width, views.params.y + views.params.height)
        val moCenterX = views.params.x + views.left.left + views.left.width / 2f
        val message = liquidFeeling()
        val seed = Random.nextLong()
        DetectionLog.add(
            "liquid: Mo comes out ($reason), route seed $seed, liquid stretch ${Build.VERSION.SDK_INT >= Build.VERSION_CODES.S}, " +
                "says: ${message ?: "nothing (quiet)"}",
        )
        val started = island.play(
            pill = pill,
            moCenterX = moCenterX,
            moSize = patchMoSize(),
            mood = state?.mood ?: Mood.CHILL,
            profile = profile(),
            message = message,
            screen = screenBounds(),
            seed = seed,
            hideRealMo = { hide -> views.mo.alpha = if (hide) 0f else 1f },
            onEnd = {},
        )
        if (started) {
            settings.liquidLastAt = System.currentTimeMillis()
            // Counted from now: the next time Mo is due is a whole interval away.
            scheduleLiquid()
        } else {
            lastLiquidAtMs = 0L
        }
    }

    /**
     * When the count goes up: the number pops and Mo reacts. The pop and Mo are one gesture
     * with one set of timings, and a reel arriving mid-reaction carries on from the current
     * pose. A run of quick reels (a fast scroll) turns the startle into excitement.
     */
    private fun bump(count: View, mo: MoView) {
        val now = SystemClock.uptimeMillis()
        val p = profile()
        val sinceLastReel = now - lastReelAtMs
        rapidReels = if (sinceLastReel <= RAPID_REEL_MS) rapidReels + 1 else 1
        steadyStreak = if (sinceLastReel <= MoTemperament.STEADY_GAP_MS) steadyStreak + 1 else 1
        lastReelAtMs = now
        // Mid-reaction, the body skips its crouch (see MoMotion.anticipationLength), so the hop must too.
        val chained = sinceLastReel < p.reactMs / 2
        val excited = rapidReels >= RAPID_REELS_FOR_EXCITEMENT && mo.mood != Mood.DIZZY && mo.mood != Mood.SLEEPY
        // Tired is smaller and slower; a long run of reels is something Mo gets used to.
        val temper = MoTemperament.reaction(mo.mood, steadyStreak)
        DetectionLog.add(
            "mo: reel #${state?.total} → ${if (excited) "excited" else "startle"}, " +
                "${if (chained) "chained (no crouch)" else "fresh crouch"}, gap ${if (sinceLastReel > 60_000) ">1min" else "${sinceLastReel}ms"}, " +
                "streak $rapidReels, run $steadyStreak (novelty ${"%.2f".format(java.util.Locale.ROOT, MoTemperament.novelty(steadyStreak))}, " +
                "energy ${"%.2f".format(java.util.Locale.ROOT, MoTemperament.energy(mo.mood))}) → size ${"%.2f".format(java.util.Locale.ROOT, temper.size)}, " +
                "tempo ${"%.2f".format(java.util.Locale.ROOT, temper.tempo)}, ${display.motionStyle.name.lowercase()}",
        )
        mo.react(intensity = temper.size, excited = excited, tempo = temper.tempo)
        val paced = p.paced(temper.tempo)
        // The number pops with Mo: same size and pace.
        val peak = 1f + (0.25f * p.hop + 0.05f) * temper.size
        kick(count, peak, peak, hopPx = 0f, p = paced)
        // Mo hops as the crouch releases; the body's own squash and stretch are drawn by MoView.
        kick(mo, 1f, 1f, hopPx = dp(4) * p.hop.coerceAtMost(1.25f) * temper.size, p = paced, delayMs = if (chained) 0L else MoMotion.anticipationLength(paced, 0f))
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
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
        val cutout = defaultDisplay()?.cutout ?: return null
        val topLimit = cutout.safeInsetTop + dp(8)
        val screenCenter = screenWidth() / 2
        return cutout.boundingRects
            .asSequence()
            .filter { !it.isEmpty && it.top <= topLimit }
            .minByOrNull { abs(it.centerX() - screenCenter) }
    }

    private fun dp(value: Int): Int =
        (value * service.resources.displayMetrics.density).toInt()

    private companion object {
        const val PANEL_TIMEOUT_MS = 10_000L
        /** Reels closer together than this count as a fast scroll. */
        const val RAPID_REEL_MS = 1_500L
        const val RAPID_REELS_FOR_EXCITEMENT = 3
        const val PATCH_PADDING_DP = 6
        /** Island height beyond the camera hole, per side. */
        const val PATCH_RIM_DP = 4
        /** The island never starts closer than this to the top edge of the screen. */
        const val PATCH_TOP_GAP_DP = 3
        const val INFO_FLIP_MS = 2_800L
        /** Mo comes out no sooner than this after a tracked app opens. */
        const val LIQUID_FIRST_DELAY_MS = 1_400L
        /** When it wasn't a good moment, try again after this. */
        const val LIQUID_RETRY_MS = 60_000L
        /** How long the timer stays up each time it shows, when it isn't always on. */
        const val TIMER_REVEAL_MS = 5_000L
        const val SNOOZE_MS = 60 * 60_000L
        /** How long a speech bubble stays up: a base plus reading time. */
        const val SPEECH_MS = 3_000L
        const val SPEECH_MS_PER_CHAR = 40L
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
    private val countLabel = text(context, 14f).apply { setPadding(0, 0, 0, dp(8)) }
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
            addView(countLabel)
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
        val accent = MoLook.color(mood)
        mo.mood = mood
        moodText.text = "${Mo.NAME} · ${mood.title}".uppercase()
        moodText.setTextColor(accent)
        lineText.text = state.line()
        countText.text = state.total.toString()
        countLabel.text = if (state.screenTimeMs > 0) {
            "  reels today · ${Durations.format(state.screenTimeMs)} in apps"
        } else {
            "  reels today"
        }
        (openButton.background as GradientDrawable).setColor(accent)

        apps.removeAllViews()
        val max = state.apps.maxOfOrNull { it.reels }?.coerceAtLeast(1) ?: 1
        state.apps.forEach { stat ->
            apps.addView(appRow(apps.context, stat, stat.reels.toFloat() / max, accent))
        }
        week.set(state.week, accent)
    }

    private fun appRow(context: android.content.Context, stat: AppStat, fraction: Float, accent: Int) =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(6), 0, 0)
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(text(context, 13f).apply { text = stat.label }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                addView(text(context, 13f, color = Color.WHITE, bold = true).apply { text = stat.reels.toString() })
                addView(text(context, 13f).apply { text = "  ·  ${Durations.format(stat.timeMs)}" })
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
