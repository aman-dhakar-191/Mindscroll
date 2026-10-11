package io.github.amandhakar.mindscroll.tracker

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

    fun isTracked(packageName: String): Boolean = packageName in _packages.value

    fun label(packageName: String): String = labeler(packageName)
}
