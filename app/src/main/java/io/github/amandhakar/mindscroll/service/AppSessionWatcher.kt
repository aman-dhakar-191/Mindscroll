package io.github.amandhakar.mindscroll.service

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tracks which watched app is in use right now, for the live timer in the camera patch. The
 * service only gets events from watched apps, so leaving one is noticed by checking the app in
 * front once a second, and only while a session is running.
 */
class AppSessionWatcher(
    private val service: AccessibilityService,
    packages: Set<String>,
) {
    private val sessions = AppSessions(packages)
    private val handler = Handler(Looper.getMainLooper())
    private val power = service.getSystemService(PowerManager::class.java)
    private val _session = MutableStateFlow<AppSession?>(null)
    val session: StateFlow<AppSession?> = _session.asStateFlow()

    private var checkScheduled = false

    private val check = object : Runnable {
        override fun run() {
            checkScheduled = false
            val front = runCatching { service.rootInActiveWindow?.packageName?.toString() }.getOrNull()
            sessions.onCheck(front, power.isInteractive, SystemClock.elapsedRealtime())
            publish()
        }
    }

    fun onAppEvent(packageName: String) {
        sessions.onAppEvent(packageName, SystemClock.elapsedRealtime())
        publish()
    }

    fun stop() {
        handler.removeCallbacks(check)
        checkScheduled = false
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
    }
}
