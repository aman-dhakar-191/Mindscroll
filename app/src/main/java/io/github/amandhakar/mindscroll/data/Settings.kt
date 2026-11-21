package io.github.amandhakar.mindscroll.data

import android.content.Context
import android.content.SharedPreferences
import io.github.amandhakar.mindscroll.mascot.MoCharacter
import io.github.amandhakar.mindscroll.mascot.MoLook
import io.github.amandhakar.mindscroll.mascot.MoPalette
import io.github.amandhakar.mindscroll.mascot.MotionProfile
import io.github.amandhakar.mindscroll.tracker.reels.ReelApps
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

/** Small user preferences. Measurements live in Room, not here. */
class Settings(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /**
     * What the camera island shows in one app. [BOTH] keeps the reel counter up and shows the
     * session timer now and then (see [TimerReveal]); there is no scrolling between them.
     * Counter and Both need reel counting in that app, so without it they fall back to the timer.
     */
    enum class AppInfo { TIMER, COUNTER, BOTH, NOTHING }

    /** How often Mo comes out of the island (the liquid island experiment), at most. */
    enum class LiquidEvery(val ms: Long) {
        QUARTER_HOUR(15 * 60_000L),
        HALF_HOUR(30 * 60_000L),
        HOUR(60 * 60_000L),
        TWO_HOURS(2 * 60 * 60_000L),
        FOUR_HOURS(4 * 60 * 60_000L),
    }

    /** How soon after leaving an app the island hides. */
    enum class LeaveDelay(val ms: Long) { THREE_SECONDS(3_000L), FIVE_SECONDS(5_000L) }

    /**
     * What the island shows in each app: the user's own choice per app, or the default for apps
     * they haven't chosen for. Pure data, so the rules are testable.
     */
    data class InfoChoices(
        val perApp: Map<String, AppInfo> = emptyMap(),
        /** For an app that counts reels and has no choice of its own. */
        val reelDefault: AppInfo = AppInfo.BOTH,
        /** For any other tracked app with no choice of its own. */
        val otherDefault: AppInfo = AppInfo.TIMER,
    ) {
        /** What [packageName] shows, given whether it counts reels. Null package: the reel default. */
        fun choiceFor(packageName: String?, countsReels: Boolean): AppInfo {
            val chosen = packageName?.let { perApp[it] }
                ?: if (countsReels || packageName == null) reelDefault else otherDefault
            // A counter needs reels to count: an app that doesn't count them shows the timer instead.
            return if (!countsReels && (chosen == AppInfo.COUNTER || chosen == AppInfo.BOTH)) AppInfo.TIMER else chosen
        }
    }
    enum class MascotSize { COMPACT, STANDARD, LARGE }

    /**
     * When the session timer is on show. Always is how it has been (and the default); the others
     * keep it hidden and bring it up for a few seconds each time the session has run another
     * 5 or 10 minutes.
     */
    enum class TimerReveal(val intervalMs: Long?) {
        ALWAYS(null),
        EVERY_5_MIN(5 * 60_000L),
        EVERY_10_MIN(10 * 60_000L),
        ;

        /** Milliseconds until the next reveal, for a session that has run [elapsedMs]. Null for [ALWAYS]. */
        fun nextRevealInMs(elapsedMs: Long): Long? {
            val interval = intervalMs ?: return null
            return interval - (elapsedMs.coerceAtLeast(0L) % interval)
        }
    }
    enum class MotionStyle {
        CALM, BALANCED, PLAYFUL;

        /** The timing and physics this style gives Mo's animations. */
        val profile: MotionProfile
            get() = when (this) {
                CALM -> MotionProfile.CALM
                BALANCED -> MotionProfile.BALANCED
                PLAYFUL -> MotionProfile.PLAYFUL
            }
    }

    data class Display(
        val cameraPatch: Boolean,
        val bubble: Boolean,
        val info: InfoChoices,
        val mascotSize: MascotSize,
        val motionStyle: MotionStyle,
        val milestoneReactions: Boolean,
        /** How often the timer shows in apps set to the timer only. */
        val timerReveal: TimerReveal = TimerReveal.ALWAYS,
        /** How often the timer shows over the counter in apps set to Both. Never "always". */
        val counterTimerEvery: TimerReveal = TimerReveal.EVERY_5_MIN,
        val leaveDelay: LeaveDelay = LeaveDelay.THREE_SECONDS,
        /** Experimental: Mo comes out of the island and goes back in, with a liquid stretch. Off by default. */
        val liquidIsland: Boolean = false,
        val liquidEvery: LiquidEvery = LiquidEvery.HOUR,
        val character: MoCharacter = MoCharacter.MO,
        val palette: MoPalette = MoPalette.CLASSIC,
    )

    init {
        // Whatever draws Mo reads the look from MoLook; keep it in step with what was saved.
        MoLook.character = mascotCharacter
        MoLook.palette = mascotPalette
    }

    var onboardingDone: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_DONE, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDING_DONE, value).apply()

    var cameraPatch: Boolean
        get() = prefs.getBoolean(KEY_CAMERA_PATCH, true)
        set(value) = prefs.edit().putBoolean(KEY_CAMERA_PATCH, value).apply()

    var bubble: Boolean
        get() = prefs.getBoolean(KEY_BUBBLE, false)
        set(value) = prefs.edit().putBoolean(KEY_BUBBLE, value).apply()

    /**
     * The old, single "what the island shows" setting. No longer written: it only supplies the
     * defaults for apps the user hasn't chosen for yet, so nobody's island changes on update.
     * Both with auto-scroll off used to mean the counter alone.
     */
    private fun legacyInfo(): AppInfo {
        val stored = prefs.getString(KEY_INFO_MODE, "BOTH")
        return when {
            stored == "TIMER" -> AppInfo.TIMER
            stored == "COUNTER" -> AppInfo.COUNTER
            stored == "HIDDEN" -> AppInfo.NOTHING
            !prefs.getBoolean(KEY_AUTO_SCROLL, true) -> AppInfo.COUNTER
            else -> AppInfo.BOTH
        }
    }

    /** What the island shows in each app (see [InfoChoices]). */
    fun infoChoices(): InfoChoices {
        val legacy = legacyInfo()
        val perApp = (prefs.getStringSet(KEY_APP_INFO, null) ?: emptySet()).mapNotNull { entry ->
            val at = entry.lastIndexOf('=')
            if (at <= 0) return@mapNotNull null
            val info = runCatching { AppInfo.valueOf(entry.substring(at + 1)) }.getOrNull() ?: return@mapNotNull null
            entry.substring(0, at) to info
        }.toMap()
        return InfoChoices(
            perApp = perApp,
            reelDefault = legacy,
            otherDefault = if (legacy == AppInfo.NOTHING) AppInfo.NOTHING else AppInfo.TIMER,
        )
    }

    /** The user's choice of what the island shows in [packageName]. */
    fun setAppInfo(packageName: String, info: AppInfo) {
        val others = (prefs.getStringSet(KEY_APP_INFO, null) ?: emptySet()).filterNot { it.startsWith("$packageName=") }
        prefs.edit().putStringSet(KEY_APP_INFO, (others + "$packageName=${info.name}").toSet()).apply()
    }

    var counterTimerEvery: TimerReveal
        get() = runCatching { TimerReveal.valueOf(prefs.getString(KEY_COUNTER_TIMER_EVERY, TimerReveal.EVERY_5_MIN.name)!!) }
            .getOrDefault(TimerReveal.EVERY_5_MIN).takeIf { it != TimerReveal.ALWAYS } ?: TimerReveal.EVERY_5_MIN
        set(value) = prefs.edit().putString(KEY_COUNTER_TIMER_EVERY, value.name).apply()

    /** Experimental (see [Display.liquidIsland]). */
    var liquidIsland: Boolean
        get() = prefs.getBoolean(KEY_LIQUID_ISLAND, false)
        set(value) = prefs.edit().putBoolean(KEY_LIQUID_ISLAND, value).apply()

    /** How often Mo comes out (see [LiquidEvery]). An hour until the user chooses. */
    var liquidEvery: LiquidEvery
        get() = runCatching { LiquidEvery.valueOf(prefs.getString(KEY_LIQUID_EVERY, LiquidEvery.HOUR.name)!!) }
            .getOrDefault(LiquidEvery.HOUR)
        set(value) = prefs.edit().putString(KEY_LIQUID_EVERY, value.name).apply()

    /** When Mo last came out, in wall-clock ms (0 for never), so the frequency holds across restarts. */
    var liquidLastAt: Long
        get() = prefs.getLong(KEY_LIQUID_LAST_AT, 0L)
        set(value) = prefs.edit().putLong(KEY_LIQUID_LAST_AT, value).apply()

    var leaveDelay: LeaveDelay
        get() = runCatching { LeaveDelay.valueOf(prefs.getString(KEY_LEAVE_DELAY, LeaveDelay.THREE_SECONDS.name)!!) }
            .getOrDefault(LeaveDelay.THREE_SECONDS)
        set(value) = prefs.edit().putString(KEY_LEAVE_DELAY, value.name).apply()

    var mascotSize: MascotSize
        get() = runCatching { MascotSize.valueOf(prefs.getString(KEY_MASCOT_SIZE, MascotSize.STANDARD.name)!!) }
            .getOrDefault(MascotSize.STANDARD)
        set(value) = prefs.edit().putString(KEY_MASCOT_SIZE, value.name).apply()

    var motionStyle: MotionStyle
        get() = runCatching { MotionStyle.valueOf(prefs.getString(KEY_MOTION_STYLE, MotionStyle.BALANCED.name)!!) }
            .getOrDefault(MotionStyle.BALANCED)
        set(value) = prefs.edit().putString(KEY_MOTION_STYLE, value.name).apply()

    /** Who lives in the camera notch (and is the app's logo). Mo until the user picks. */
    var mascotCharacter: MoCharacter
        get() = runCatching { MoCharacter.valueOf(prefs.getString(KEY_MASCOT_CHARACTER, MoCharacter.MO.name)!!) }
            .getOrDefault(MoCharacter.MO)
        set(value) {
            prefs.edit().putString(KEY_MASCOT_CHARACTER, value.name).apply()
            MoLook.character = value
        }

    /** The set of body colours for the moods. Classic until the user picks. */
    var mascotPalette: MoPalette
        get() = runCatching { MoPalette.valueOf(prefs.getString(KEY_MASCOT_PALETTE, MoPalette.CLASSIC.name)!!) }
            .getOrDefault(MoPalette.CLASSIC)
        set(value) {
            prefs.edit().putString(KEY_MASCOT_PALETTE, value.name).apply()
            MoLook.palette = value
        }

    var timerReveal: TimerReveal
        get() = runCatching { TimerReveal.valueOf(prefs.getString(KEY_TIMER_REVEAL, TimerReveal.ALWAYS.name)!!) }
            .getOrDefault(TimerReveal.ALWAYS)
        set(value) = prefs.edit().putString(KEY_TIMER_REVEAL, value.name).apply()

    var milestoneReactions: Boolean
        get() = prefs.getBoolean(KEY_MILESTONE_REACTIONS, true)
        set(value) = prefs.edit().putBoolean(KEY_MILESTONE_REACTIONS, value).apply()

    /** Crash reports and technical accessibility-service diagnostics; enabled by default. */
    var diagnosticsEnabled: Boolean
        get() = prefs.getBoolean(KEY_DIAGNOSTICS_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_DIAGNOSTICS_ENABLED, value).apply()

    /** Where the floating bubble was last dragged to, in px from the top-left. */
    var bubblePosition: Pair<Int, Int>
        get() = prefs.getInt(KEY_BUBBLE_X, 24) to prefs.getInt(KEY_BUBBLE_Y, 400)
        set(value) = prefs.edit().putInt(KEY_BUBBLE_X, value.first).putInt(KEY_BUBBLE_Y, value.second).apply()

    /** Packages to track. Until the user picks, the apps Mindscroll knows reel feeds for. */
    var trackedApps: Set<String>
        get() = prefs.getStringSet(KEY_TRACKED_APPS, null)?.toSet() ?: ReelApps.known.keys
        set(value) = prefs.edit().putStringSet(KEY_TRACKED_APPS, value).apply()

    /** Apps that count reels; null until the user chooses (see TrackedApps.countsReels). */
    var reelApps: Set<String>?
        get() = prefs.getStringSet(KEY_REEL_APPS, null)?.toSet()
        set(value) = prefs.edit().apply {
            if (value == null) remove(KEY_REEL_APPS) else putStringSet(KEY_REEL_APPS, value)
        }.apply()

    fun observeReelApps(): Flow<Set<String>?> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_REEL_APPS) trySend(reelApps)
        }
        trySend(reelApps)
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.conflate()

    fun observeTrackedApps(): Flow<Set<String>> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_TRACKED_APPS) trySend(trackedApps)
        }
        trySend(trackedApps)
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.conflate()

    fun display() = Display(
        cameraPatch, bubble, infoChoices(), mascotSize, motionStyle, milestoneReactions,
        timerReveal, counterTimerEvery, leaveDelay, liquidIsland, liquidEvery, mascotCharacter, mascotPalette,
    )

    fun observeDisplay(): Flow<Display> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_CAMERA_PATCH || key == KEY_BUBBLE || key == KEY_INFO_MODE || key == KEY_AUTO_SCROLL || key == KEY_APP_INFO ||
                key == KEY_COUNTER_TIMER_EVERY || key == KEY_LEAVE_DELAY || key == KEY_LIQUID_ISLAND || key == KEY_LIQUID_EVERY || key == KEY_MASCOT_SIZE || key == KEY_MOTION_STYLE || key == KEY_MILESTONE_REACTIONS ||
                key == KEY_MASCOT_CHARACTER || key == KEY_MASCOT_PALETTE || key == KEY_TIMER_REVEAL
            ) trySend(display())
        }
        trySend(display())
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.conflate()

    private companion object {
        const val KEY_ONBOARDING_DONE = "onboarding_done"
        const val KEY_CAMERA_PATCH = "camera_patch"
        const val KEY_BUBBLE = "bubble"
        const val KEY_DIAGNOSTICS_ENABLED = "diagnostics_enabled"
        const val KEY_INFO_MODE = "info_mode"
        const val KEY_AUTO_SCROLL = "auto_scroll"
        const val KEY_APP_INFO = "app_info"
        const val KEY_COUNTER_TIMER_EVERY = "counter_timer_every"
        const val KEY_LEAVE_DELAY = "leave_delay"
        const val KEY_LIQUID_ISLAND = "liquid_island"
        const val KEY_LIQUID_EVERY = "liquid_every"
        const val KEY_LIQUID_LAST_AT = "liquid_last_at"
        const val KEY_MASCOT_SIZE = "mascot_size"
        const val KEY_MOTION_STYLE = "motion_style"
        const val KEY_MILESTONE_REACTIONS = "milestone_reactions"
        const val KEY_MASCOT_CHARACTER = "mascot_character"
        const val KEY_TIMER_REVEAL = "timer_reveal"
        const val KEY_MASCOT_PALETTE = "mascot_palette"
        const val KEY_BUBBLE_X = "bubble_x"
        const val KEY_BUBBLE_Y = "bubble_y"
        const val KEY_TRACKED_APPS = "tracked_apps"
        const val KEY_REEL_APPS = "reel_apps"
    }
}
