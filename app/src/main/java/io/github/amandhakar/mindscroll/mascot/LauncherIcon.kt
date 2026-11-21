package io.github.amandhakar.mindscroll.mascot

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * The app icon on the home screen is whoever the user picked. Android only lets a launcher icon
 * change by enabling one `activity-alias` and disabling the others, so each [MoCharacter] has
 * one in the manifest (`.LauncherMo`, `.LauncherKit`, ...).
 *
 * The launcher may take a few seconds to notice, and a home-screen shortcut made from the old
 * icon can disappear (it points at the old alias); the app drawer entry is always there.
 */
object LauncherIcon {
    private const val PACKAGE = "io.github.amandhakar.mindscroll"

    private fun alias(context: Context, character: MoCharacter) =
        ComponentName(context.packageName, "$PACKAGE.Launcher${character.name.lowercase().replaceFirstChar { it.uppercase() }}")

    /** Makes [character] the launcher icon. Enables the new one first so the app never has none. */
    fun apply(context: Context, character: MoCharacter) {
        val pm = context.packageManager
        val wanted = alias(context, character)
        if (pm.getComponentEnabledSetting(wanted) != PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
            pm.setComponentEnabledSetting(wanted, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
        }
        for (other in MoCharacter.entries) {
            if (other == character) continue
            val component = alias(context, other)
            if (pm.getComponentEnabledSetting(component) != PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
                pm.setComponentEnabledSetting(component, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
            }
        }
    }
}
