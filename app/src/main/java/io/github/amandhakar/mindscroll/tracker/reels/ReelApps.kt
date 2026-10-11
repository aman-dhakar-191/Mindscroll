package io.github.amandhakar.mindscroll.tracker.reels

/**
 * How to recognize and show one tracked app.
 *
 * [containerIds] are view ids of the app's reel feed where known. They are a shortcut, not a
 * requirement: in any tracked app, a full-screen list whose visible item fills it counts as a
 * reel feed, which covers apps without known ids and survives id renames.
 */
data class ReelApp(
    val packageName: String,
    val label: String,
    /** Short tag for the detection log. */
    val short: String,
    /** A color for the app, ARGB, used for its live timer in the camera patch. */
    val accent: Long,
    val containerIds: Set<String> = emptySet(),
)

/**
 * What Mindscroll knows about specific apps. Which apps are tracked is up to the user (see
 * TrackedApps); this only adds hints for apps it knows, and makes up sensible defaults for the
 * rest.
 */
object ReelApps {
    /** Apps tracked before the user picks any, with known feed ids and colors. */
    val all = listOf(
        ReelApp(
            packageName = "com.instagram.android",
            label = "Instagram",
            short = "IG",
            accent = 0xFFFF5C8A,
            containerIds = setOf("com.instagram.android:id/clips_viewer_view_pager"),
        ),
        ReelApp(
            packageName = "com.google.android.youtube",
            label = "YouTube",
            short = "YT",
            accent = 0xFFFF4E45,
            containerIds = setOf("com.google.android.youtube:id/reel_recycler"),
        ),
        ReelApp(
            packageName = "com.facebook.katana",
            label = "Facebook",
            short = "FB",
            accent = 0xFF4F9BFF,
        ),
    )

    val known: Map<String, ReelApp> = all.associateBy { it.packageName }

    /** Colors for apps without one of their own, picked by package name so each stays the same. */
    private val palette = listOf(0xFFFF8A5C, 0xFFFFC94D, 0xFF6EE7B7, 0xFF5EC8FF, 0xFFB38BFF, 0xFFFF7EB6)

    /** [packageName] as a tracked app called [label]: known hints if any, defaults otherwise. */
    fun forPackage(packageName: String, label: String): ReelApp {
        known[packageName]?.let { return it.copy(label = label) }
        return ReelApp(
            packageName = packageName,
            label = label,
            short = initials(label),
            accent = palette[Math.floorMod(packageName.hashCode(), palette.size)],
        )
    }

    /** "Snapchat" → "SN", "Josh Video" → "JV". */
    internal fun initials(label: String): String {
        val words = label.split(' ', '-', '_').filter { it.isNotBlank() }
        val letters = if (words.size >= 2) "${words[0][0]}${words[1][0]}" else label.take(2)
        return letters.uppercase().ifEmpty { "??" }
    }
}
