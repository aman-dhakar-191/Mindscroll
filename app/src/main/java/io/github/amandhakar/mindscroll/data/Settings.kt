package io.github.amandhakar.mindscroll.data

import android.content.Context
import android.content.SharedPreferences
import io.github.amandhakar.mindscroll.tracker.reels.ReelApps
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

/** Small user preferences. Measurements live in Room, not here. */
class Settings(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    data class Display(val cameraPatch: Boolean, val bubble: Boolean)

    var onboardingDone: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_DONE, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDING_DONE, value).apply()

    var cameraPatch: Boolean
        get() = prefs.getBoolean(KEY_CAMERA_PATCH, true)
        set(value) = prefs.edit().putBoolean(KEY_CAMERA_PATCH, value).apply()

    var bubble: Boolean
        get() = prefs.getBoolean(KEY_BUBBLE, false)
        set(value) = prefs.edit().putBoolean(KEY_BUBBLE, value).apply()

    /** Where the floating bubble was last dragged to, in px from the top-left. */
    var bubblePosition: Pair<Int, Int>
        get() = prefs.getInt(KEY_BUBBLE_X, 24) to prefs.getInt(KEY_BUBBLE_Y, 400)
        set(value) = prefs.edit().putInt(KEY_BUBBLE_X, value.first).putInt(KEY_BUBBLE_Y, value.second).apply()

    /** Packages to track. Until the user picks, the apps Mindscroll knows reel feeds for. */
    var trackedApps: Set<String>
        get() = prefs.getStringSet(KEY_TRACKED_APPS, null)?.toSet() ?: ReelApps.known.keys
        set(value) = prefs.edit().putStringSet(KEY_TRACKED_APPS, value).apply()

    fun observeTrackedApps(): Flow<Set<String>> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_TRACKED_APPS) trySend(trackedApps)
        }
        trySend(trackedApps)
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.conflate()

    fun display() = Display(cameraPatch, bubble)

    fun observeDisplay(): Flow<Display> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_CAMERA_PATCH || key == KEY_BUBBLE) trySend(display())
        }
        trySend(display())
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.conflate()

    private companion object {
        const val KEY_ONBOARDING_DONE = "onboarding_done"
        const val KEY_CAMERA_PATCH = "camera_patch"
        const val KEY_BUBBLE = "bubble"
        const val KEY_BUBBLE_X = "bubble_x"
        const val KEY_BUBBLE_Y = "bubble_y"
        const val KEY_TRACKED_APPS = "tracked_apps"
    }
}
