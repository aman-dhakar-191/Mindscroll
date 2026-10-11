package io.github.amandhakar.mindscroll.mascot

import java.time.Instant
import java.time.ZoneId

/** Quiet hours as minutes after local midnight. May wrap past midnight (e.g. 22:00 to 08:00). */
data class QuietHours(val startMinute: Int, val endMinute: Int) {
    fun contains(minuteOfDay: Int): Boolean = when {
        startMinute == endMinute -> false
        startMinute < endMinute -> minuteOfDay in startMinute until endMinute
        else -> minuteOfDay >= startMinute || minuteOfDay < endMinute
    }

    companion object {
        val NIGHT = QuietHours(startMinute = 22 * 60, endMinute = 8 * 60)
    }
}

/** What the user has told Mo about how Mo should behave. */
data class MoProfile(
    /** When Mo keeps quiet every day, or null for never. */
    val quietHours: QuietHours? = null,
)

/**
 * Mo's history of speaking up, kept across restarts so a new service or process doesn't
 * reset Mo's cooldowns. Times are wall-clock ms; days are local epoch days.
 */
data class MoInteractionState(
    /** When Mo spoke in about the last hour, oldest first. */
    val spokenAt: List<Long> = emptyList(),
    /** Mo keeps quiet until then, if set (the user said "shh"). */
    val quietUntil: Long? = null,
    /** The day Mo last greeted the user with a memory of yesterday. */
    val lastGreetingDay: Long? = null,
    val lastInitiativeAt: Long? = null,
    val initiativeDay: Long? = null,
    /** Unprompted remarks on [initiativeDay]. */
    val dailyInitiativeCount: Int = 0,
)

/** Where [MoPolicy] keeps its state. On the phone it's preferences; in tests, memory. */
interface MoStore {
    var profile: MoProfile
    var interaction: MoInteractionState
}

class InMemoryMoStore : MoStore {
    override var profile = MoProfile()
    override var interaction = MoInteractionState()
}

/** How much a remark matters, which decides how soon after another one it may come. */
enum class Remark {
    /** Reacting to something that just happened: fast swipes, a fifth open, a memory. */
    REACTION,
    /** A big moment, like a milestone or waking up. */
    BIG,
    /** Mo bringing something up unprompted. The first thing to go when Mo has said enough. */
    INITIATIVE,
}

/**
 * The one place that decides whether Mo may speak at all, whatever Mo wants to say: never
 * while snoozed or in quiet hours, one remark per [GAP_MS] at most ([BIG_GAP_MS] for big
 * moments), no more than [MAX_PER_HOUR] an hour, and unprompted remarks at most
 * [MAX_INITIATIVES_PER_DAY] a day and [INITIATIVE_GAP_MS] apart. What Mo says is decided
 * elsewhere ([MoMoments]).
 *
 * Times are wall-clock ms, so the limits survive restarts and reboots. If the clock is set
 * back, times "in the future" count as now: Mo waits a gap rather than staying quiet for days.
 */
class MoPolicy(
    private val store: MoStore = InMemoryMoStore(),
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    /** Whether a [kind] remark could be said [now], without counting it as said. */
    fun allows(now: Long, kind: Remark): Boolean {
        if (quiet(now)) return false
        val state = current(now)
        val last = state.spokenAt.lastOrNull()
        if (last != null && now - last < if (kind == Remark.BIG) BIG_GAP_MS else GAP_MS) return false
        if (state.spokenAt.size >= MAX_PER_HOUR) return false
        if (kind == Remark.INITIATIVE) {
            val initiativesToday = if (state.initiativeDay == day(now)) state.dailyInitiativeCount else 0
            if (initiativesToday >= MAX_INITIATIVES_PER_DAY) return false
            val lastInitiative = state.lastInitiativeAt
            if (lastInitiative != null && now - lastInitiative < INITIATIVE_GAP_MS) return false
        }
        return true
    }

    /** If a [kind] remark may be said [now], counts it as said and returns true. */
    fun trySpeak(now: Long, kind: Remark): Boolean {
        if (!allows(now, kind)) return false
        val state = store.interaction
        val today = day(now)
        store.interaction = if (kind == Remark.INITIATIVE) {
            state.copy(
                spokenAt = (state.spokenAt + now).takeLast(MAX_PER_HOUR),
                lastInitiativeAt = now,
                initiativeDay = today,
                dailyInitiativeCount = if (state.initiativeDay == today) state.dailyInitiativeCount + 1 else 1,
            )
        } else {
            state.copy(spokenAt = (state.spokenAt + now).takeLast(MAX_PER_HOUR))
        }
        return true
    }

    /**
     * Claims today's greeting: true the first time it's asked on a given day, even across
     * restarts. Whether the greeting is then actually said is up to [trySpeak].
     */
    fun claimGreeting(now: Long): Boolean {
        val today = day(now)
        val state = store.interaction
        if (state.lastGreetingDay == today) return false
        store.interaction = state.copy(lastGreetingDay = today)
        return true
    }

    /** Mo keeps quiet for [durationMs] from [now]. */
    fun snooze(now: Long, durationMs: Long) {
        store.interaction = store.interaction.copy(quietUntil = now + durationMs.coerceIn(0, MAX_SNOOZE_MS))
    }

    fun unsnooze() {
        store.interaction = store.interaction.copy(quietUntil = null)
    }

    /** When the current snooze ends, or null if Mo isn't snoozed [now]. */
    fun snoozedUntil(now: Long): Long? =
        // A snooze ending further away than any snooze can last means the clock went back.
        store.interaction.quietUntil?.takeIf { it > now && it - now <= MAX_SNOOZE_MS }

    /** Whether Mo keeps quiet [now], snoozed or in quiet hours. */
    fun quiet(now: Long): Boolean {
        if (snoozedUntil(now) != null) return true
        val hours = store.profile.quietHours ?: return false
        val time = Instant.ofEpochMilli(now).atZone(zone).toLocalTime()
        return hours.contains(time.hour * 60 + time.minute)
    }

    private fun day(now: Long): Long = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().toEpochDay()

    /**
     * The stored state with remarks older than an hour dropped. Times after [now] mean the
     * clock went back; they're saved as [now], so Mo waits one gap from here and then carries on.
     */
    private fun current(now: Long): MoInteractionState {
        val stored = store.interaction
        val state = stored.copy(
            spokenAt = stored.spokenAt.map { it.coerceAtMost(now) }.filter { now - it <= HOUR_MS },
            lastInitiativeAt = stored.lastInitiativeAt?.coerceAtMost(now),
        )
        if (state != stored) store.interaction = state
        return state
    }

    companion object {
        const val GAP_MS = 3 * 60_000L
        const val BIG_GAP_MS = 45_000L
        const val MAX_PER_HOUR = 10

        const val INITIATIVE_GAP_MS = 60 * 60_000L
        const val MAX_INITIATIVES_PER_DAY = 3

        const val MAX_SNOOZE_MS = 24 * 60 * 60_000L
        private const val HOUR_MS = 60 * 60_000L
    }
}
