package io.github.amandhakar.mindscroll.service

import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import io.github.amandhakar.mindscroll.display.IslandNotification
import io.github.amandhakar.mindscroll.display.IslandRules
import io.github.amandhakar.mindscroll.display.SystemIsland
import io.github.amandhakar.mindscroll.tracker.DetectionLog

/**
 * Watches for notifications that put the phone's own island on screen (Xiaomi's Super Island,
 * Android 16 Live Updates), so the camera patch can step aside while one is up and come back the
 * moment it goes. See [IslandRules] for which ones count.
 *
 * Needs "Notification access", which only the user can give; without it nothing changes. It reads
 * a notification's app, flags and the names of its extras, never its title or text, and keeps
 * nothing once the notification is gone.
 */
class IslandListenerService : NotificationListenerService() {
    override fun onListenerConnected() {
        val active = runCatching { activeNotifications?.toList() }.getOrNull().orEmpty()
        val found = active.mapNotNull { sbn -> classify(sbn)?.let { sbn.key to it } }.toMap()
        SystemIsland.setConnected(true)
        SystemIsland.replaceAll(found)
        DetectionLog.add(
            "island: notification access on; ${active.size} notifications up, " +
                "${found.size} that may show the phone's island" +
                found.values.joinToString(prefix = if (found.isEmpty()) "" else ": ") { "${it.packageName} (${it.kind.label})" },
        )
    }

    override fun onListenerDisconnected() {
        SystemIsland.setConnected(false)
        DetectionLog.add("island: notification access disconnected, Mo no longer steps aside for the phone's island")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val found = classify(sbn)
        val before = SystemIsland.active.value[sbn.key]
        SystemIsland.put(sbn.key, found)
        when {
            found != null && before == null ->
                DetectionLog.add("island: + ${sbn.packageName} id=${sbn.id} (${found.kind.label}) ${describe(sbn)}")
            found == null && before != null ->
                DetectionLog.add("island: ${sbn.packageName} id=${sbn.id} no longer marked (${before.kind.label})")
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        val before = SystemIsland.active.value[sbn.key] ?: return
        SystemIsland.put(sbn.key, null)
        DetectionLog.add(
            "island: - ${sbn.packageName} id=${sbn.id} gone (${before.kind.label}), ${SystemIsland.active.value.size} left",
        )
    }

    private fun classify(sbn: StatusBarNotification): IslandNotification? {
        val keys = runCatching { sbn.notification.extras.keySet().toList() }.getOrDefault(emptyList())
        val kind = IslandRules.kind(packageName, sbn.packageName, sbn.notification.flags, keys) ?: return null
        return IslandNotification(sbn.packageName, kind)
    }

    /** Flags, category and the names of non-standard extras, for the log. Never a value or text. */
    private fun describe(sbn: StatusBarNotification): String {
        val n = sbn.notification
        val keys = runCatching { n.extras.keySet().toList() }.getOrDefault(emptyList())
        return "flags=0x${Integer.toHexString(n.flags)} category=${n.category} extras=${IslandRules.loggableKeys(keys)}"
    }

    companion object {
        /** Whether the user has given Mindscroll notification access. */
        fun isEnabled(context: Context): Boolean =
            NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

        fun component(context: Context) = ComponentName(context, IslandListenerService::class.java)
    }
}
