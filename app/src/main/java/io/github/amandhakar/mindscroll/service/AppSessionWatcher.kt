package io.github.amandhakar.mindscroll.service

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import io.github.amandhakar.mindscroll.data.Days
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tracks which watched app is in use right now, for the live timer in the camera patch, and
 * hands time used to [onUsage] for screen time: once a minute during a long session, and when
 * a session ends. The service only gets events from watched apps, so leaving one is noticed by
 * checking the app in front once a second, and only while a session is running.
 */
class AppSessionWatcher(
    private val service: AccessibilityService,
    packages: Set<String>,
    private val onUsage: (AppUsage) -> Unit,
) {
    private val sessions = AppSessions(packages) { Days.today() }
    private val handler = Handler(Looper.getMainLooper())
    private val power = service.getSystemService(PowerManager::class.java)
    private val _session = MutableStateFlow<AppSession?>(null)
    val session: StateFlow<AppSession?> = _session.asStateFlow()

    private var checkScheduled = false
    private var lastFlushAt = SystemClock.elapsedRealtime()

    private val check = object : Runnable {
        override fun run() {
            checkScheduled = false
            val front = runCatching { service.rootInActiveWindow?.packageName?.toString() }.getOrNull()
            val now = SystemClock.elapsedRealtime()
            sessions.onCheck(front, power.isInteractive, now)
            if (sessions.current == null || now - lastFlushAt >= FLUSH_MS) flush(now)
            publish()
        }
    }

    /** Which apps are tracked changed. */
    fun setPackages(packages: Set<String>) {
        sessions.setPackages(packages)
        publish()
    }

    /** Today's saved screen time per app, so a reopened app's timer resumes from it. */
    fun addSavedToday(saved: Map<String, Long>) {
        sessions.addSavedToday(saved)
        publish()
    }

    fun onAppEvent(packageName: String) {
        sessions.onAppEvent(packageName, SystemClock.elapsedRealtime())
        publish()
    }

    /** Stops checking and saves whatever time hasn't been saved yet. */
    fun stop() {
        handler.removeCallbacks(check)
        checkScheduled = false
        flush(SystemClock.elapsedRealtime())
    }

    private fun flush(now: Long) {
        lastFlushAt = now
        sessions.drainUsage().forEach(onUsage)
    }

    private fun publish() {
        _session.value = sessions.current
        // A steady once-a-second check while a session runs. Not rescheduled per event: apps
        // send events many times a second, which would keep pushing the check back.
        if (sessions.current != null && !checkScheduled) {
            checkScheduled = true
            handler.postDelayed(check, CHECK_MS)
        }
    }

    private companion object {
        const val CHECK_MS = 1_000L
        /** Long sessions are saved in chunks, so a killed service loses at most this much. */
        const val FLUSH_MS = 60_000L
    }
}
