package io.github.amandhakar.mindscroll.service

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.res.Configuration
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import io.github.amandhakar.mindscroll.app
import io.github.amandhakar.mindscroll.display.CounterNotification
import io.github.amandhakar.mindscroll.display.CounterOverlay
import io.github.amandhakar.mindscroll.display.CounterWidget
import io.github.amandhakar.mindscroll.tracker.Recorder
import io.github.amandhakar.mindscroll.tracker.TrackerRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Feeds accessibility events to the trackers and keeps the counter on screen. The system
 * keeps an enabled accessibility service running (and restarts it after boot), so this is
 * also the home of the persistent notification and the overlays.
 */
class TrackerAccessibilityService : AccessibilityService() {

    private lateinit var scope: CoroutineScope
    private lateinit var overlay: CounterOverlay
    private val recorders = mutableMapOf<String, Recorder>()

    override fun onServiceConnected() {
        super.onServiceConnected()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        overlay = CounterOverlay(this, app.settings)

        // Listen only to what the registered trackers need.
        serviceInfo = serviceInfo.apply {
            packageNames = TrackerRegistry.watchedPackages()
            eventTypes = TrackerRegistry.eventTypes()
        }

        TrackerRegistry.all.forEach { tracker ->
            recorders[tracker.id] = Recorder { itemKey, value ->
                scope.launch(Dispatchers.IO) { app.metrics.record(tracker.id, itemKey, value) }
            }
        }

        val primary = TrackerRegistry.primary
        scope.launch {
            combine(
                app.metrics.observeTodayTotal(primary.id),
                app.metrics.observeTodayByItem(primary.id),
                app.settings.observeDisplay(),
            ) { total, byItem, display -> Triple(total, byItem, display) }
                .collect { (total, byItem, display) ->
                    CounterNotification.show(this@TrackerAccessibilityService, total, byItem)
                    overlay.show(total, display)
                    CounterWidget.update(this@TrackerAccessibilityService, total)
                }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        for (tracker in TrackerRegistry.all) {
            val recorder = recorders[tracker.id] ?: continue
            if (event.eventType and tracker.eventTypes == 0) continue
            tracker.onAccessibilityEvent(event, recorder)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (::overlay.isInitialized) overlay.onConfigurationChanged()
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        if (::scope.isInitialized) scope.cancel()
        if (::overlay.isInitialized) overlay.removeAll()
        CounterNotification.cancel(this)
        super.onDestroy()
    }

    companion object {
        fun isEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false
            val me = ComponentName(context, TrackerAccessibilityService::class.java)
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
        }
    }
}
