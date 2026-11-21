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

    enum class InfoMode { TIMER, COUNTER, BOTH, HIDDEN }
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
        val infoMode: InfoMode,
        val autoScroll: Boolean,
        val mascotSize: MascotSize,
        val motionStyle: MotionStyle,
        val milestoneReactions: Boolean,
        val timerReveal: TimerReveal = TimerReveal.ALWAYS,
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

    var infoMode: InfoMode
        get() = runCatching { InfoMode.valueOf(prefs.getString(KEY_INFO_MODE, InfoMode.BOTH.name)!!) }
            .getOrDefault(InfoMode.BOTH)
        set(value) = prefs.edit().putString(KEY_INFO_MODE, value.name).apply()

    var autoScroll: Boolean
        get() = prefs.getBoolean(KEY_AUTO_SCROLL, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_SCROLL, value).apply()

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
        cameraPatch, bubble, infoMode, autoScroll, mascotSize, motionStyle, milestoneReactions,
        timerReveal, mascotCharacter, mascotPalette,
    )

    fun observeDisplay(): Flow<Display> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_CAMERA_PATCH || key == KEY_BUBBLE || key == KEY_INFO_MODE || key == KEY_AUTO_SCROLL || key == KEY_MASCOT_SIZE || key == KEY_MOTION_STYLE || key == KEY_MILESTONE_REACTIONS ||
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
