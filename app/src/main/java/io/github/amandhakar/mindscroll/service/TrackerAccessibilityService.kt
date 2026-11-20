package io.github.amandhakar.mindscroll.service

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import io.github.amandhakar.mindscroll.app
import io.github.amandhakar.mindscroll.data.Settings as AppSettings
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
    private lateinit var calls: CallWatcher
    private val hosts = mutableMapOf<String, TrackerHost>()
    private val diagnosticEventCounts = mutableMapOf<String, Int>()

    override fun onServiceConnected() {
        super.onServiceConnected()
        FirebaseCrashlytics.getInstance().log("Accessibility service connected")
        FirebaseAnalytics.getInstance(this).logEvent("accessibility_service_connected", null)
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        overlay = CounterOverlay(this, app.settings)
        // Clear a promoted notification left behind by an earlier service instance.
        CounterNotification.cancelLive(this)
        brain = MoBrain(this, overlay, app.metrics, app.moPrefs, app.moMemories, scope).also { it.start() }
        calls = CallWatcher(this) { inCall -> overlay.setInCall(inCall) }.also { it.start() }
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
                FirebaseCrashlytics.getInstance().setCustomKey("tracked_app_count", tracked.size)
                FirebaseCrashlytics.getInstance().log("Tracked app selection updated; count=${tracked.size}")
                FirebaseAnalytics.getInstance(this@TrackerAccessibilityService).logEvent(
                    "tracked_apps_configured", Bundle().apply { putInt("app_count", tracked.size) },
                )
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
        // Which tracked apps count reels (the rest have the timer only), whenever that changes.
        scope.launch {
            combine(TrackedApps.packages, TrackedApps.reelPackages) { tracked, _ ->
                tracked.filter(TrackedApps::countsReels) to tracked.filterNot(TrackedApps::countsReels)
            }.collect { (reels, timerOnly) ->
                DetectionLog.add(
                    "reels counted in: ${reels.joinToString { TrackedApps.label(it) }.ifEmpty { "no apps" }}; " +
                        "timer only: ${timerOnly.joinToString { TrackedApps.label(it) }.ifEmpty { "none" }}",
                )
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
                brain.onSession(session)
                // A new open (not the same session updating its timer): Mo may have something to say.
                if (session != null && previous?.startedAt != session.startedAt) {
                    brain.onSessionStarted(session, primary.itemLabel(session.packageName))
                }
                previous = session
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString()
        if (::appSessions.isInitialized) packageName?.let { appSessions.onAppEvent(it) }
        if (packageName != null && TrackedApps.packages.value.contains(packageName)) {
            val count = (diagnosticEventCounts[packageName] ?: 0) + 1
            diagnosticEventCounts[packageName] = count
            if (count % 50 == 0) {
                FirebaseCrashlytics.getInstance().setCustomKey("last_tracked_package", packageName)
                FirebaseCrashlytics.getInstance().setCustomKey("last_accessibility_event_type", event.eventType)
                FirebaseCrashlytics.getInstance().log("Received $count accessibility events from a tracked app; last type=${event.eventType}")
                FirebaseAnalytics.getInstance(this).logEvent(
                    "accessibility_events_batch", Bundle().apply {
                        putString("tracked_package", packageName)
                        putInt("event_count", 50)
                        putInt("event_type", event.eventType)
                    },
                )
            }
        }
        for (tracker in TrackerRegistry.all) {
            val host = hosts[tracker.id] ?: continue
            if (event.eventType and tracker.eventTypes == 0) continue
            try {
                tracker.onAccessibilityEvent(event, host)
            } catch (error: Exception) {
                FirebaseCrashlytics.getInstance().log("Tracker ${tracker.id} failed on accessibility event type=${event.eventType}")
                FirebaseCrashlytics.getInstance().recordException(error)
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (::overlay.isInitialized) overlay.onConfigurationChanged()
    }

    override fun onInterrupt() {
        FirebaseCrashlytics.getInstance().log("Accessibility service interrupted by system")
        FirebaseAnalytics.getInstance(this).logEvent("accessibility_service_interrupted", null)
    }

    override fun onDestroy() {
        FirebaseCrashlytics.getInstance().log("Accessibility service destroyed")
        FirebaseAnalytics.getInstance(this).logEvent("accessibility_service_destroyed", null)
        // Saves unsaved screen time (on the app scope, which outlives this service).
        if (::appSessions.isInitialized) appSessions.stop()
        if (::brain.isInitialized) brain.stop()
        if (::calls.isInitialized) calls.stop()
        if (::scope.isInitialized) scope.cancel()
        if (::overlay.isInitialized) overlay.removeAll()
        CounterNotification.cancel(this)
        CounterNotification.cancelLive(this)
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
