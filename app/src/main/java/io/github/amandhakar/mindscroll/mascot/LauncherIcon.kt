package io.github.amandhakar.mindscroll.mascot

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import com.google.firebase.crashlytics.FirebaseCrashlytics
import io.github.amandhakar.mindscroll.tracker.DetectionLog
import java.util.concurrent.Executors

/**
 * The app icon on the home screen is the user's character in the user's colours. Android only
 * lets a launcher icon change by enabling one `activity-alias` and disabling the others, so
 * there is one per character and colour set in the manifest (`.LauncherKitBerry`, ...).
 *
 * Every call into the PackageManager is a call to the system, which can be slow, so this is kept to
 * the least possible and never done on the main thread:
 * - it remembers which alias it last switched on, and does nothing at all when that is already the one wanted;
 * - to change it, it touches two aliases (the new one on, the old one off), not all thirty;
 * - only when it does not know what is on (first run, or after an update) does it check all of them, once;
 * - all of it runs on a background thread.
 *
 * Switching the alias that the running app was started from can make some phones close the app,
 * so this is only ever called once the app is out of sight (see `MainActivity.onStop`), never from a tap.
 *
 * The launcher may take a few seconds to notice, and a home-screen shortcut made from the old
 * icon can disappear (it points at the old alias); the app drawer entry is always there.
 */
object LauncherIcon {
    private const val PACKAGE = "io.github.amandhakar.mindscroll"
    private const val PREFS = "launcher_icon"
    private const val KEY_APPLIED = "applied_alias"
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "mindscroll-launcher-icon").apply { isDaemon = true } }

    /** The manifest name of the alias for a character in a palette, e.g. `LauncherKitBerry`. */
    fun aliasName(character: MoCharacter, palette: MoPalette): String =
        "Launcher${character.name.lowercase().replaceFirstChar { it.uppercase() }}" +
            palette.name.lowercase().replaceFirstChar { it.uppercase() }

    /** Every alias there is. */
    fun allAliases(): List<String> = MoCharacter.entries.flatMap { c -> MoPalette.entries.map { p -> aliasName(c, p) } }

    /** The alias that is on when the app is installed (the manifest's own default). */
    const val DEFAULT_ALIAS = "LauncherMoClassic"

    /**
     * What to change to put [wanted] on the home screen, as (alias, on): nothing when it already is;
     * two aliases (the new one on, the [applied] one off) when it is known what was on; and all of them
     * when it is not known ([applied] null or not an alias), which is only the first time.
     */
    fun plan(applied: String?, wanted: String, all: List<String> = allAliases()): List<Pair<String, Boolean>> = when {
        applied == wanted -> emptyList()
        applied != null && applied in all -> listOf(wanted to true, applied to false)
        else -> listOf(wanted to true) + all.filter { it != wanted }.map { it to false }
    }

    /** Puts [character] in [palette] on the home screen, if it isn't already there. Returns at once. */
    fun apply(context: Context, character: MoCharacter, palette: MoPalette) {
        val app = context.applicationContext
        val wanted = aliasName(character, palette)
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val applied = prefs.getString(KEY_APPLIED, null)
        val changes = plan(applied, wanted)
        if (changes.isEmpty()) return
        runCatching {
            worker.execute {
                val started = SystemClock.elapsedRealtime()
                runCatching { run(app, wanted, changes) }
                    .onSuccess {
                        prefs.edit().putString(KEY_APPLIED, wanted).apply()
                        val took = SystemClock.elapsedRealtime() - started
                        DetectionLog.add("icon: switched the launcher icon to $wanted (${changes.size} change${if (changes.size == 1) "" else "s"}, $took ms)")
                        FirebaseCrashlytics.getInstance().log("launcher icon switched to $wanted in $took ms")
                    }
                    .onFailure { DetectionLog.add("icon: could not switch the launcher icon (${it.javaClass.simpleName})") }
            }
        }
    }

    private fun run(context: Context, wanted: String, changes: List<Pair<String, Boolean>>) {
        val pm = context.packageManager
        // The new one on first, so the app never has no icon.
        for ((alias, on) in changes.sortedByDescending { it.second }) {
            val component = ComponentName(context.packageName, "$PACKAGE.$alias")
            val state = pm.getComponentEnabledSetting(component)
            val isOn = state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED ||
                (alias == DEFAULT_ALIAS && state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)
            if (isOn == on) continue
            pm.setComponentEnabledSetting(
                component,
                if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
        }
    }

}
