package io.github.amandhakar.mindscroll.display

import android.app.Notification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Why a notification may be putting the phone's own island on screen, by the mark it carries.
 */
enum class IslandKind(val label: String) {
    /** Xiaomi HyperOS Super Island: the notification carries Xiaomi's island data (extras named `miui.focus.…`). */
    XIAOMI_FOCUS("Xiaomi island"),

    /** Android 16 Live Update: the system promoted it to the status bar chip (FLAG_PROMOTED_ONGOING). */
    LIVE_UPDATE("Live Update"),

    /** Any other ongoing notification. Only counts when "any ongoing notification" is switched on. */
    ONGOING("ongoing notification"),
}

/** One notification that may show the phone's island. */
data class IslandNotification(val packageName: String, val kind: IslandKind)

/**
 * The rules for which notifications put the phone's own island on screen, in one place. Pure, so
 * they are tested and the listener only does what this says.
 *
 * The island only shows while a notification with an island mark is up (Xiaomi's island data, or
 * Android 16 promoting it to a Live Update). A plain ongoing notification (music, a VPN, a
 * download) usually has no island, so it only counts when the user opts in.
 */
object IslandRules {
    /** Extras Xiaomi's Super Island reads; `miui.focus.param` holds the island's content. */
    const val XIAOMI_FOCUS_PREFIX = "miui.focus."

    /** Android 16's mark for a notification promoted to a Live Update (the status bar chip). */
    @Suppress("InlinedApi") // A plain int: older Androids never set it, so the check is just false there.
    const val FLAG_PROMOTED = Notification.FLAG_PROMOTED_ONGOING

    /**
     * What kind of island notification this is, or null when it shows no island. [ownPackage]'s
     * notifications are never counted: Mindscroll's own counter would otherwise hide Mo all day.
     */
    fun kind(ownPackage: String, packageName: String, flags: Int, extraKeys: Collection<String>): IslandKind? = when {
        packageName == ownPackage -> null
        extraKeys.any { it.startsWith(XIAOMI_FOCUS_PREFIX) } -> IslandKind.XIAOMI_FOCUS
        flags and FLAG_PROMOTED != 0 -> IslandKind.LIVE_UPDATE
        flags and Notification.FLAG_ONGOING_EVENT != 0 -> IslandKind.ONGOING
        else -> null
    }

    /** Whether this kind hides Mo, given whether plain ongoing notifications count. */
    fun hides(kind: IslandKind, anyOngoing: Boolean): Boolean = kind != IslandKind.ONGOING || anyOngoing

    /** The notification that makes Mo step aside right now, or null when none does. Island marks come first. */
    fun trigger(active: Collection<IslandNotification>, anyOngoing: Boolean): IslandNotification? =
        active.filter { hides(it.kind, anyOngoing) }.minByOrNull { it.kind.ordinal }

    /** The extras worth naming in the detection log: names only, not Android's standard ones. Never values. */
    fun loggableKeys(extraKeys: Collection<String>): List<String> =
        extraKeys.filterNot { it.startsWith("android.") }.sorted()
}

/**
 * The notifications that may be showing the phone's island right now, by notification key, as
 * the notification listener sees them. Empty while notification access isn't given, so Mo works
 * as before.
 */
object SystemIsland {
    private val _active = MutableStateFlow<Map<String, IslandNotification>>(emptyMap())
    val active: StateFlow<Map<String, IslandNotification>> = _active.asStateFlow()

    /** Whether the listener is connected (notification access given and the service bound). */
    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    @Synchronized
    fun put(key: String, notification: IslandNotification?) {
        val now = _active.value
        _active.value = if (notification == null) now - key else now + (key to notification)
    }

    @Synchronized
    fun replaceAll(all: Map<String, IslandNotification>) {
        _active.value = all
    }

    fun setConnected(connected: Boolean) {
        _connected.value = connected
        if (!connected) replaceAll(emptyMap())
    }
}
