package io.github.amandhakar.mindscroll.tracker

import io.github.amandhakar.mindscroll.tracker.reels.ReelApps
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The apps the user chose to track (Settings → "Apps to track"), shared by every tracker and
 * display. Kept in sync with the saved choice by MindscrollApp, so changing it takes effect
 * right away, accessibility service included.
 */
object TrackedApps {
    private val _packages = MutableStateFlow<Set<String>>(emptySet())
    val packages: StateFlow<Set<String>> = _packages.asStateFlow()

    /** Names a package the way the phone does ("Instagram"); set up by MindscrollApp. */
    var labeler: (String) -> String = { it }

    fun set(packages: Set<String>) {
        _packages.value = packages
    }

    /**
     * Where reels are counted, as the user chose it (Apps to track → "Count reels"). Null until
     * they choose, in which case [countsReels] falls back to apps that look like reel apps.
     */
    private val _reelPackages = MutableStateFlow<Set<String>?>(null)
    val reelPackages: StateFlow<Set<String>?> = _reelPackages.asStateFlow()

    fun setReelPackages(packages: Set<String>?) {
        _reelPackages.value = packages
    }

    fun isTracked(packageName: String): Boolean = packageName in _packages.value

    /**
     * Whether reels are counted (and the reel counter shown) in this app. Every tracked app has
     * a timer and screen time; only these have a reel counter. Until the user chooses, that is
     * Instagram and YouTube, including patched builds such as ReVanced/RVX YouTube.
     */
    fun countsReels(packageName: String): Boolean =
        isTracked(packageName) && (_reelPackages.value?.contains(packageName) ?: looksLikeReelApp(packageName))

    /** The default for an app the user hasn't decided about. */
    fun looksLikeReelApp(packageName: String): Boolean {
        if (packageName in ReelApps.known) return true
        val name = packageName.lowercase()
        return "instagram" in name || "youtube" in name
    }

    fun label(packageName: String): String = labeler(packageName)
}
