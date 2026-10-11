package io.github.amandhakar.mindscroll.mascot

import java.time.Instant
import java.time.ZoneId

/** Something Mo wants to bring up without being prompted, and why. */
data class Initiative(val kind: Kind, val text: String, val memoryKey: String? = null) {
    enum class Kind {
        /** A check-in the user asked for. Comes first. */
        FOLLOW_THROUGH,
        /** Bringing up something Mo remembers. */
        CALLBACK,
        /** A bit of Mo's own little world. The first thing to drop. */
        CHARACTER,
    }

    val remark: Remark get() = if (kind == Kind.FOLLOW_THROUGH) Remark.FOLLOW_THROUGH else Remark.INITIATIVE
}

/**
 * Whether Mo has a reason to speak up unprompted right now, and what it would be. Mo needs a
 * real reason: a check-in that's due, a remembered pattern that fits this moment, or (once a
 * day, after a while in an app) a bit of Mo's own story. Whether Mo may actually say it is up
 * to [MoPolicy]. Pure logic; times are wall-clock ms.
 */
class MoInitiative(private val zone: ZoneId = ZoneId.systemDefault()) {
    /**
     * The most important thing Mo could bring up [now], or null. [memories] are what Mo
     * remembers; [inAppForMs] is how long the current tracked-app session has lasted, or null
     * outside tracked apps (then only check-ins can come up).
     */
    fun next(now: Long, memories: List<Memory>, state: MoInteractionState, inAppForMs: Long?): Initiative? {
        val live = memories.filter { it.isLive(now) }
        live.filter { it.type == MemoryType.CHECK_IN && it.dueAt != null && it.dueAt <= now }
            .minByOrNull { it.dueAt!! }
            ?.let { due ->
                val late = now - due.dueAt!! > ON_TIME_MS
                val text = Mo.checkIn(due.value.toLongOrNull() ?: 0, late, seed = due.createdAt)
                return Initiative(Initiative.Kind.FOLLOW_THROUGH, text, due.key)
            }
        if (inAppForMs == null) return null

        val time = Instant.ofEpochMilli(now).atZone(zone)
        val today = time.toLocalDate().toEpochDay()
        if (state.lastCallbackDay != today) {
            live.firstOrNull { it.key == MoMemories.PEAK_HOUR && it.value.toIntOrNull() == time.hour }?.let {
                val text = Mo.peakHourCallback(MoMemory.hourName(time.hour), it.sure, seed = today)
                return Initiative(Initiative.Kind.CALLBACK, text, it.key)
            }
        }
        if (state.lastCharacterDay != today && inAppForMs >= CHARACTER_AFTER_MS) {
            return Initiative(Initiative.Kind.CHARACTER, Mo.characterBeat(state.characterBeat))
        }
        return null
    }

    /** [state] after Mo said [initiative] [now], so it isn't brought up again today. */
    fun said(initiative: Initiative, state: MoInteractionState, now: Long): MoInteractionState {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().toEpochDay()
        return when (initiative.kind) {
            Initiative.Kind.FOLLOW_THROUGH -> state
            Initiative.Kind.CALLBACK -> state.copy(lastCallbackDay = today)
            Initiative.Kind.CHARACTER -> state.copy(lastCharacterDay = today, characterBeat = state.characterBeat + 1)
        }
    }

    companion object {
        /** A check-in said within this long of being due is on time. */
        const val ON_TIME_MS = 5 * 60_000L
        /** A check-in Mo couldn't say within this long of being due is dropped. */
        const val LATE_LIMIT_MS = 3 * 60 * 60_000L
        /** Mo tells a bit of Mo's story only after this long in an app, not right as it opens. */
        const val CHARACTER_AFTER_MS = 10 * 60_000L
    }
}
