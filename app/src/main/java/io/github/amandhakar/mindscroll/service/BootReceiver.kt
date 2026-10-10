package io.github.amandhakar.mindscroll.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.amandhakar.mindscroll.app
import io.github.amandhakar.mindscroll.display.CounterNotification
import io.github.amandhakar.mindscroll.display.CounterWidget

/**
 * Android restarts an enabled accessibility service by itself after boot (and after an app
 * update), which brings back the notification and overlays. This receiver covers the rest:
 * it refreshes the widget, and if the service has been turned off it says so instead of
 * silently not counting.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> Unit
            else -> return
        }
        CounterWidget.refresh(context, goAsync())
        if (context.app.settings.onboardingDone && !TrackerAccessibilityService.isEnabled(context)) {
            CounterNotification.showTrackingOff(context)
        }
    }
}
