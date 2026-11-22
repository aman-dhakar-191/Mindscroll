package io.github.amandhakar.islandlab

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Logs every notification posted or removed on the phone, to find out what the notifications behind
 * an island (Android Live Updates, or a phone maker's own kind) look like. It records the app, the flags,
 * the category and the NAMES of the extras that are not Android's standard ones. It never reads or
 * stores a title or any text. Needs "Notification access" for Island Lab, which only the user can give.
 */
class ProbeService : NotificationListenerService() {
    override fun onListenerConnected() {
        val active = runCatching { activeNotifications.toList() }.getOrDefault(emptyList())
        ProbeLog.add("listener connected; ${active.size} notifications active now")
        active.forEach { ProbeLog.add("  now: ${describe(it)}") }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        ProbeLog.add("+ ${describe(sbn)}")
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        ProbeLog.add("- ${describe(sbn)}")
    }

    private fun describe(sbn: StatusBarNotification): String {
        val n = sbn.notification
        val extras = n.extras
        val keys = extras.keySet()
        // Names only, and only the ones that are not Android's own standard extras.
        val other = keys.filter { !it.startsWith("android.") }.sorted()
        val ongoing = n.flags and Notification.FLAG_ONGOING_EVENT != 0
        return "${sbn.packageName} id=${sbn.id} flags=0x${Integer.toHexString(n.flags)} ongoing=$ongoing " +
            "category=${n.category} " +
            "promoteRequested=${keys.contains("android.requestPromotedOngoing")} " +
            "shortText=${keys.contains("android.shortCriticalText")} " +
            "otherExtras=$other"
    }
}
