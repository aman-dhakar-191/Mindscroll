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
import io.github.amandhakar.mindscroll.tracker.TrackedApps
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
    private lateinit var brain: MoBrain
    private val hosts = mutableMapOf<String, TrackerHost>()

    override fun onServiceConnected() {
        super.onServiceConnected()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        overlay = CounterOverlay(this, app.settings)
        brain = MoBrain(this, overlay, app.metrics, app.moPrefs, scope).also { it.start() }
        val metrics = app.metrics
        val appScope = app.appScope
        appSessions = AppSessionWatcher(this, TrackedApps.packages.value) { usage ->
            appScope.launch { metrics.record(TrackerRegistry.screenTime.id, usage.packageName, usage.ms) }
        }

        // Listen only to what the trackers need, and follow changes to the tracked apps.
        scope.launch {
            TrackedApps.packages.collect { tracked ->
                val packages = TrackerRegistry.watchedPackages()
                val self = this@TrackerAccessibilityService.packageName
                serviceInfo = serviceInfo.apply {
                    packageNames = when {
                        packages == null -> null
                        // No apps tracked: listen only to ourselves (an empty list means every app).
                        packages.isEmpty() -> arrayOf(self)
                        else -> packages
                    }
                    eventTypes = TrackerRegistry.eventTypes()
                }
                appSessions.setPackages(tracked)
                DetectionLog.add("watching ${tracked.joinToString { TrackedApps.label(it) }.ifEmpty { "no apps" }}")
            }
        }

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
            app.metrics.observeMood(primary.id),
        ) { total, byItem, week, time, mood ->
            CounterState(total, AppStat.merge(byItem, time, primary::itemLabel), week, mood)
        }
            // Each new reel updates all three queries; show the settled result once.
            .debounce(150)
            .distinctUntilChanged()
        scope.launch {
            combine(state, brain.asleep) { s, asleep -> s.copy(asleep = asleep) }
                .combine(app.settings.observeDisplay()) { s, d -> s to d }
                .collect { (state, display) ->
                    CounterNotification.show(this@TrackerAccessibilityService, state)
                    overlay.show(state, display)
                    CounterWidget.update(this@TrackerAccessibilityService, state)
                    brain.onCounter(state.total, state.mood)
                }
        }
        // Live timer in the camera patch while a watched app is in use. It resumes from the
        // time already saved today, so reopening an app continues its timer.
        scope.launch {
            val saved = app.metrics.observeTodayByItem(TrackerRegistry.screenTime.id).first()
            appSessions.addSavedToday(saved.associate { it.itemKey to it.total })
        }
        scope.launch {
            var previous: AppSession? = null
            appSessions.session.collect { session ->
                overlay.setSession(session)
                // A new open (not the same session updating its timer): Mo may have something to say.
                if (session != null && previous?.startedAt != session.startedAt) {
                    brain.onSessionStarted(session, primary.itemLabel(session.packageName))
                }
                previous = session
            }
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
        if (::brain.isInitialized) brain.stop()
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
