package io.github.amandhakar.mindscroll.service

import android.content.Context
import io.github.amandhakar.mindscroll.tracker.DetectionLog
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * A short history of when the accessibility service started and stopped, kept on the phone so it
 * survives the app being killed (the detection log does not). When the service stops for good
 * ("Not working" next to the switch in Accessibility settings), this says how it ended:
 * stopped by the system (`onUnbind` / `onDestroy` were called), or the process just disappeared
 * (killed or crashed, so nothing was called and the state is still "running" at the next start).
 */
class ServiceLife(context: Context, private val clock: () -> Long = System::currentTimeMillis) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** The service connected. Notes how the previous run ended. */
    fun connected() {
        val now = clock()
        val note = verdict(prefs.getString(KEY_STATE, null), prefs.getLong(KEY_ALIVE, 0L))
        if (note != null) add(now, note)
        add(now, "service connected")
        prefs.edit().putString(KEY_STATE, RUNNING).putLong(KEY_ALIVE, now).putLong(KEY_STARTED, now).apply()
    }

    /** Called often; writes at most once a minute. */
    fun alive() {
        val now = clock()
        if (now - prefs.getLong(KEY_ALIVE, 0L) < HEARTBEAT_MS) return
        prefs.edit().putLong(KEY_ALIVE, now).apply()
    }

    fun interrupted() = add(clock(), "interrupted by the system")

    /** [how] is `unbound` or `destroyed`: the system stopped the service. */
    fun stopped(how: String) {
        val now = clock()
        val ranFor = now - prefs.getLong(KEY_STARTED, now)
        add(now, "service $how after ${ranFor / 60_000} min")
        prefs.edit().putString(KEY_STATE, STOPPED).putLong(KEY_ALIVE, now).apply()
    }

    /** The saved history, oldest first. */
    fun lines(): List<String> = prefs.getString(KEY_LINES, "")!!.split('\n').filter { it.isNotEmpty() }

    /** Puts the saved history at the top of the detection log (once per app start). */
    fun replayInto(log: DetectionLog) {
        val saved = lines()
        if (saved.isNotEmpty()) log.add("service history (kept on the phone):\n" + saved.joinToString("\n") { "  $it" })
    }

    private fun add(at: Long, text: String) {
        val kept = (lines() + "${stamp(at)}  $text").takeLast(MAX_LINES)
        prefs.edit().putString(KEY_LINES, kept.joinToString("\n")).apply()
        DetectionLog.add("service: $text")
    }

    companion object {
        private const val PREFS = "service_life"
        private const val KEY_LINES = "lines"
        private const val KEY_STATE = "state"
        private const val KEY_ALIVE = "alive_at"
        private const val KEY_STARTED = "started_at"
        private const val RUNNING = "running"
        private const val STOPPED = "stopped"
        private const val MAX_LINES = 40
        private const val HEARTBEAT_MS = 60_000L
        private val format = DateTimeFormatter.ofPattern("MMM d HH:mm:ss").withZone(ZoneId.systemDefault())

        private fun stamp(at: Long) = format.format(Instant.ofEpochMilli(at))

        /**
         * What to say about the previous run when the service connects: nothing on the very first run
         * or after a clean stop, and a warning when it was still "running" (it never got to stop).
         */
        fun verdict(previousState: String?, lastAliveAt: Long): String? = when (previousState) {
            null, STOPPED -> null
            else -> "previous run ended without a stop (killed or crashed); last seen alive ${stamp(lastAliveAt)}"
        }
    }
}
