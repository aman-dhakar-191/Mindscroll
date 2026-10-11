package io.github.amandhakar.mindscroll.service

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.res.Configuration
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import io.github.amandhakar.mindscroll.app
import io.github.amandhakar.mindscroll.display.CounterNotification
import io.github.amandhakar.mindscroll.display.CounterOverlay
import io.github.amandhakar.mindscroll.display.AppStat
import io.github.amandhakar.mindscroll.display.CounterState
import io.github.amandhakar.mindscroll.display.CounterWidget
import io.github.amandhakar.mindscroll.tracker.DetectionLog
import io.github.amandhakar.mindscroll.tracker.TrackerHost
import io.github.amandhakar.mindscroll.tracker.TrackerRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Feeds accessibility events to the trackers and keeps the counter on screen. The system
 * keeps an enabled accessibility service running (and restarts it after boot), so this is
 * also the home of the persistent notification and the overlays.
 */
@OptIn(FlowPreview::class)
class TrackerAccessibilityService : AccessibilityService() {

    private lateinit var scope: CoroutineScope
    private lateinit var overlay: CounterOverlay
    private lateinit var appSessions: AppSessionWatcher
    private val hosts = mutableMapOf<String, TrackerHost>()

    override fun onServiceConnected() {
        super.onServiceConnected()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        overlay = CounterOverlay(this, app.settings)
        val metrics = app.metrics
        val appScope = app.appScope
        appSessions = AppSessionWatcher(this, TrackerRegistry.watchedPackages()?.toSet().orEmpty()) { usage ->
            appScope.launch { metrics.record(TrackerRegistry.screenTime.id, usage.packageName, usage.ms) }
        }

        // Listen only to what the registered trackers need.
        serviceInfo = serviceInfo.apply {
            packageNames = TrackerRegistry.watchedPackages()
            eventTypes = TrackerRegistry.eventTypes()
        }

        DetectionLog.add("service connected, watching ${TrackerRegistry.watchedPackages()?.joinToString() ?: "all apps"}")

        TrackerRegistry.all.forEach { tracker ->
            hosts[tracker.id] = object : TrackerHost {
                override fun record(itemKey: String, value: Long) {
                    scope.launch(Dispatchers.IO) { app.metrics.record(tracker.id, itemKey, value) }
                }

                override fun activeWindowRoot(): AccessibilityNodeInfo? = rootInActiveWindow
            }
        }

        val primary = TrackerRegistry.primary
        val state = combine(
            app.metrics.observeTodayTotal(primary.id),
            app.metrics.observeTodayByItem(primary.id),
            app.metrics.observeDailyTotals(primary.id, 7),
            app.metrics.observeTodayByItem(TrackerRegistry.screenTime.id),
        ) { total, byItem, week, time ->
            CounterState(total, AppStat.merge(byItem, time, primary::itemLabel), week)
        }
            // Each new reel updates all three queries; show the settled result once.
            .debounce(150)
            .distinctUntilChanged()
        scope.launch {
            combine(state, app.settings.observeDisplay()) { s, d -> s to d }.collect { (state, display) ->
                CounterNotification.show(this@TrackerAccessibilityService, state)
                overlay.show(state, display)
                CounterWidget.update(this@TrackerAccessibilityService, state.total)
            }
        }
        // Live timer in the camera patch while a watched app is in use. It resumes from the
        // time already saved today, so reopening an app continues its timer.
        scope.launch {
            val saved = app.metrics.observeTodayByItem(TrackerRegistry.screenTime.id).first()
            appSessions.addSavedToday(saved.associate { it.itemKey to it.total })
        }
        scope.launch {
            appSessions.session.collect { overlay.setSession(it) }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (::appSessions.isInitialized) event.packageName?.let { appSessions.onAppEvent(it.toString()) }
        for (tracker in TrackerRegistry.all) {
            val host = hosts[tracker.id] ?: continue
            if (event.eventType and tracker.eventTypes == 0) continue
            tracker.onAccessibilityEvent(event, host)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (::overlay.isInitialized) overlay.onConfigurationChanged()
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        // Saves unsaved screen time (on the app scope, which outlives this service).
        if (::appSessions.isInitialized) appSessions.stop()
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
