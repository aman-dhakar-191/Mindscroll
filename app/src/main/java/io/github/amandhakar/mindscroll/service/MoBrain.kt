package io.github.amandhakar.mindscroll.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import io.github.amandhakar.mindscroll.data.MetricsRepository
import io.github.amandhakar.mindscroll.data.MoMemoryStore
import io.github.amandhakar.mindscroll.display.CounterOverlay
import io.github.amandhakar.mindscroll.mascot.Initiative
import io.github.amandhakar.mindscroll.mascot.MoInitiative
import io.github.amandhakar.mindscroll.mascot.MoMemory
import io.github.amandhakar.mindscroll.mascot.MoMoments
import io.github.amandhakar.mindscroll.mascot.MoPolicy
import io.github.amandhakar.mindscroll.mascot.MoSleep
import io.github.amandhakar.mindscroll.mascot.MoStore
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
 * on and off, app sessions and the counter, and acts through the [overlay]. Every so often
 * it asks [MoInitiative] whether Mo has something to bring up unprompted, from what Mo
 * remembers ([memories]). Whether Mo may speak at all is up to [MoPolicy], which keeps its
 * state in [store] so it survives restarts.
 */
class MoBrain(
    private val context: Context,
    private val overlay: CounterOverlay,
    private val metrics: MetricsRepository,
    private val store: MoStore,
    private val memories: MoMemoryStore,
    private val scope: CoroutineScope,
) {
    private val policy = MoPolicy(store)
    private val initiative = MoInitiative()
    // Wall-clock times: the policy compares them with times saved before a restart. Sleep only
    // matters while the service runs, so it keeps elapsed realtime.
    private val moments = MoMoments(policy)
    private val sleep = MoSleep(SystemClock.elapsedRealtime())
    private val handler = Handler(Looper.getMainLooper())
    private val _asleep = MutableStateFlow(false)
    /** Whether Mo is asleep, for the widget and notification. */
    val asleep: StateFlow<Boolean> = _asleep.asStateFlow()

    private var lastTotal: Long? = null
    /** The tracked-app session in progress, if any. */
    private var session: AppSession? = null
    /** Whether Mo is already working out whether to bring something up. */
    private var considering = false

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val now = SystemClock.elapsedRealtime()
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    sleep.onScreenOff(now)
                    publishSleep()
                }
                Intent.ACTION_SCREEN_ON -> sleep.onScreenOn(now)?.let { slept -> wake(slept) }
            }
        }
    }

    private val tick = object : Runnable {
        override fun run() {
            if (sleep.onTick(SystemClock.elapsedRealtime())) publishSleep()
            if (!sleep.asleep) considerInitiative()
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
        overlay.onSpeechEnded = { dismissed -> policy.speechEnded(System.currentTimeMillis(), dismissed) }
    }

    fun stop() {
        handler.removeCallbacks(tick)
        overlay.onSpeechEnded = null
        runCatching { context.unregisterReceiver(screenReceiver) }
    }

    /** The counter changed: today's [total] reels, Mo feeling [mood]. */
    fun onCounter(total: Long, mood: Mood) {
        val previous = lastTotal
        lastTotal = total
        if (previous != null && total > previous) {
            sleep.onActivity(SystemClock.elapsedRealtime())?.let { slept -> wake(slept) }
            speak(moments.onReel(System.currentTimeMillis(), total))
        }
        speak(moments.onMood(System.currentTimeMillis(), mood))
    }

    /** The tracked-app session changed: [current] is in progress, or null outside tracked apps. */
    fun onSession(current: AppSession?) {
        session = current
    }

    /** A watched app was opened. */
    fun onSessionStarted(session: AppSession, label: String) {
        sleep.onActivity(SystemClock.elapsedRealtime())?.let { slept -> wake(slept) }
        val now = System.currentTimeMillis()
        if (policy.claimGreeting(now)) {
            // First reel app of the day: Mo remembers yesterday.
            scope.launch {
                val facts = runCatching {
                    metrics.memoryFacts(TrackerRegistry.primary.id, TrackerRegistry.primary::itemLabel)
                }.getOrNull()
                // Once a day is enough to notice weekly patterns.
                facts?.let { runCatching { memories.notice(MoMemory.candidates(it, now), now) } }
                val line = facts?.let(MoMemory::dayStart)
                if (line != null) {
                    speak(moments.onRemark(System.currentTimeMillis(), line))
                } else {
                    speak(moments.onAppOpened(now, label, session.openNumber))
                }
            }
            return
        }
        speak(moments.onAppOpened(now, label, session.openNumber))
    }

    /**
     * Mo brings something up if there's a reason ([MoInitiative]) and the [policy] allows it.
     * A check-in that was said is done; other kinds are marked so they don't repeat today.
     */
    private fun considerInitiative() {
        if (considering) return
        considering = true
        scope.launch {
            try {
                val now = System.currentTimeMillis()
                val remembered = runCatching { memories.live(now) }.getOrNull() ?: return@launch
                val inAppFor = session?.let { SystemClock.elapsedRealtime() - it.startedAt }
                val idea = initiative.next(now, remembered, store.interaction, inAppFor) ?: return@launch
                // Things may have changed while reading memories.
                if (sleep.asleep || !policy.trySpeak(now, idea.remark)) return@launch
                store.interaction = initiative.said(idea, store.interaction, now)
                val shown = speak(idea.text)
                // A check-in nobody saw stays due, until it's too late (it expires).
                if (shown && idea.kind == Initiative.Kind.FOLLOW_THROUGH) {
                    idea.memoryKey?.let { runCatching { memories.done(it) } }
                }
            } finally {
                considering = false
            }
        }
    }

    private fun wake(sleptMs: Long) {
        overlay.wakeUp()
        publishSleep()
        speak(moments.onWake(System.currentTimeMillis(), sleptMs))
    }

    private fun publishSleep() {
        _asleep.value = sleep.asleep
        if (sleep.asleep) overlay.setAsleep(true)
    }

    /** Returns whether Mo was seen saying [line]. */
    private fun speak(line: String?): Boolean {
        if (line == null) return false
        DetectionLog.add("Mo says: $line")
        return overlay.say(line)
    }

    private companion object {
        /** How often to check whether Mo has dozed off. */
        const val TICK_MS = 30_000L
    }
}
