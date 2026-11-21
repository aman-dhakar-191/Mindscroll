package io.github.amandhakar.mindscroll.mascot

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import io.github.amandhakar.mindscroll.tracker.DetectionLog

/**
 * The app icon on the home screen is the user's character in the user's colours. Android only
 * lets a launcher icon change by enabling one `activity-alias` and disabling the others, so
 * there is one per character and colour set in the manifest (`.LauncherKitBerry`, ...).
 *
 * Switching the alias that the running app was started from makes some phones close the app
 * under the user's hands, so this is only ever called once the app is out of sight (see
 * `MainActivity.onStop`), never from a tap. It does nothing when the right icon is already on.
 *
 * The launcher may take a few seconds to notice, and a home-screen shortcut made from the old
 * icon can disappear (it points at the old alias); the app drawer entry is always there.
 */
object LauncherIcon {
    private const val PACKAGE = "io.github.amandhakar.mindscroll"

    /** The manifest name of the alias for a character in a palette, e.g. `LauncherKitBerry`. */
    fun aliasName(character: MoCharacter, palette: MoPalette): String =
        "Launcher${character.name.lowercase().replaceFirstChar { it.uppercase() }}" +
            palette.name.lowercase().replaceFirstChar { it.uppercase() }

    private fun component(context: Context, character: MoCharacter, palette: MoPalette) =
        ComponentName(context.packageName, "$PACKAGE.${aliasName(character, palette)}")

    /** Puts [character] in [palette] on the home screen, if it isn't already there. */
    fun apply(context: Context, character: MoCharacter, palette: MoPalette) {
        runCatching {
            val pm = context.packageManager
            val wanted = component(context, character, palette)
            val already = pm.getComponentEnabledSetting(wanted)
            // A default-enabled alias (Mo, Classic) reports DEFAULT until it has been toggled.
            val isDefault = character == MoCharacter.MO && palette == MoPalette.CLASSIC
            val wantedOn = already == PackageManager.COMPONENT_ENABLED_STATE_ENABLED ||
                (isDefault && already == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)
            var othersOn = false
            for (c in MoCharacter.entries) for (p in MoPalette.entries) {
                if (c == character && p == palette) continue
                val setting = pm.getComponentEnabledSetting(component(context, c, p))
                val defaultOn = c == MoCharacter.MO && p == MoPalette.CLASSIC
                if (setting == PackageManager.COMPONENT_ENABLED_STATE_ENABLED ||
                    (defaultOn && setting == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)
                ) othersOn = true
            }
            if (wantedOn && !othersOn) return@runCatching

            DetectionLog.add("icon: switching the launcher icon to ${aliasName(character, palette)}")
            // New one first, so the app never has no icon.
            pm.setComponentEnabledSetting(wanted, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
            for (c in MoCharacter.entries) for (p in MoPalette.entries) {
                if (c == character && p == palette) continue
                val other = component(context, c, p)
                if (pm.getComponentEnabledSetting(other) != PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
                    pm.setComponentEnabledSetting(other, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
                }
            }
        }.onFailure { DetectionLog.add("icon: could not switch the launcher icon (${it.javaClass.simpleName})") }
    }
}
