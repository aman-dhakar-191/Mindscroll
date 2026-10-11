package io.github.amandhakar.mindscroll.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import io.github.amandhakar.mindscroll.data.Days
import io.github.amandhakar.mindscroll.data.MetricsRepository
import io.github.amandhakar.mindscroll.display.CounterOverlay
import io.github.amandhakar.mindscroll.mascot.MoMemory
import io.github.amandhakar.mindscroll.mascot.MoMoments
import io.github.amandhakar.mindscroll.mascot.MoSleep
import io.github.amandhakar.mindscroll.mascot.Mood
import io.github.amandhakar.mindscroll.tracker.DetectionLog
import io.github.amandhakar.mindscroll.tracker.TrackerRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Mo's live behaviour on the phone: when Mo speaks up ([MoMoments]), sleeps and wakes
 * ([MoSleep]), and remembers the day before ([MoMemory]). Feeds those from the screen turning
 * on and off, app sessions and the counter, and acts through the [overlay].
 */
class MoBrain(
    private val context: Context,
    private val overlay: CounterOverlay,
    private val metrics: MetricsRepository,
    private val scope: CoroutineScope,
) {
    private val moments = MoMoments()
    private val sleep = MoSleep(SystemClock.elapsedRealtime())
    private val handler = Handler(Looper.getMainLooper())
    private val _asleep = MutableStateFlow(false)
    /** Whether Mo is asleep, for the widget and notification. */
    val asleep: StateFlow<Boolean> = _asleep.asStateFlow()

    private var lastTotal: Long? = null
    private var dayStartSpokenFor: Long? = null

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val now = SystemClock.elapsedRealtime()
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    sleep.onScreenOff(now)
                    publishSleep()
                }
                Intent.ACTION_SCREEN_ON -> sleep.onScreenOn(now)?.let { slept -> wake(now, slept) }
            }
        }
    }

    private val tick = object : Runnable {
        override fun run() {
            if (sleep.onTick(SystemClock.elapsedRealtime())) publishSleep()
            handler.postDelayed(this, TICK_MS)
        }
    }

    fun start() {
        ContextCompat.registerReceiver(
            context,
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        handler.postDelayed(tick, TICK_MS)
    }

    fun stop() {
        handler.removeCallbacks(tick)
        runCatching { context.unregisterReceiver(screenReceiver) }
    }

    /** The counter changed: today's [total] reels, Mo feeling [mood]. */
    fun onCounter(total: Long, mood: Mood) {
        val now = SystemClock.elapsedRealtime()
        val previous = lastTotal
        lastTotal = total
        if (previous != null && total > previous) {
            sleep.onActivity(now)?.let { slept -> wake(now, slept) }
            speak(moments.onReel(now, total))
        }
        speak(moments.onMood(now, mood))
    }

    /** A watched app was opened. */
    fun onSessionStarted(session: AppSession, label: String) {
        val now = SystemClock.elapsedRealtime()
        sleep.onActivity(now)?.let { slept -> wake(now, slept) }
        val today = Days.today()
        if (dayStartSpokenFor != today) {
            // First reel app of the day: Mo remembers yesterday.
            dayStartSpokenFor = today
            scope.launch {
                val facts = runCatching {
                    metrics.memoryFacts(TrackerRegistry.primary.id, TrackerRegistry.primary::itemLabel)
                }.getOrNull()
                val line = facts?.let(MoMemory::dayStart)
                if (line != null) {
                    speak(moments.onRemark(SystemClock.elapsedRealtime(), line))
                } else {
                    speak(moments.onAppOpened(now, label, session.openNumber))
                }
            }
            return
        }
        speak(moments.onAppOpened(now, label, session.openNumber))
    }

    private fun wake(now: Long, sleptMs: Long) {
        overlay.wakeUp()
        publishSleep()
        speak(moments.onWake(now, sleptMs))
    }

    private fun publishSleep() {
        _asleep.value = sleep.asleep
        if (sleep.asleep) overlay.setAsleep(true)
    }

    private fun speak(line: String?) {
        if (line == null) return
        DetectionLog.add("Mo says: $line")
        overlay.say(line)
    }

    private companion object {
        /** How often to check whether Mo has dozed off. */
        const val TICK_MS = 30_000L
    }
}
