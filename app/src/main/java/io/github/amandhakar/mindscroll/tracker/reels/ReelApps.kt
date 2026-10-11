package io.github.amandhakar.mindscroll.tracker.reels

/**
 * An app with a vertical, full-screen short-video feed.
 *
 * [containerIds] are view ids of the feed's scrolling container where known. They are a
 * shortcut, not a requirement: any full-screen list whose visible item fills it also counts,
 * which covers apps (like Facebook) that don't expose ids, and survives id renames.
 */
data class ReelApp(
    val packageName: String,
    val label: String,
    /** Short tag for the detection log. */
    val short: String,
    /** The app's brand-ish color, ARGB, for its live timer in the camera patch. */
    val accent: Long,
    val containerIds: Set<String> = emptySet(),
)

object ReelApps {
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
            label = "YouTube Shorts",
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

    val byPackage: Map<String, ReelApp> = all.associateBy { it.packageName }
}
