package io.github.amandhakar.mindscroll.mascot

enum class MemoryType {
    /** A pattern in the counts, like the hour we scroll most. */
    PATTERN,
    /** The user asked Mo to check in at [Memory.dueAt]. */
    CHECK_IN,
}

/** How Mo knows: told by the user, or noticed in the counts. */
enum class MemorySource { TOLD_ME, NOTICED }

/** How sure Mo is. Mo says so when it's less than [HIGH]. */
enum class Confidence { LOW, MEDIUM, HIGH }

/**
 * Something Mo remembers. Kept on the phone only, and the user can see, keep or forget each
 * one ("What Mo remembers"). Times are wall-clock ms.
 */
data class Memory(
    /** One memory per key: a new observation of the same thing replaces the old one. */
    val key: String,
    val type: MemoryType,
    /** The remembered thing for Mo's logic, e.g. "22" for a peak hour or "30" minutes. */
    val value: String,
    /** The same, for people to read. */
    val content: String,
    val source: MemorySource,
    val confidence: Confidence,
    val createdAt: Long,
    /** When Mo forgets this on its own, or null to keep it until the user says otherwise. */
    val expiresAt: Long?,
    /** When a check-in is due. */
    val dueAt: Long? = null,
    /** The user chose to keep this. Kept memories don't expire and aren't overwritten. */
    val userApproved: Boolean = false,
    /** The user asked Mo to forget this, so Mo doesn't simply notice it again tomorrow. */
    val forgottenAt: Long? = null,
) {
    fun isLive(now: Long): Boolean = forgottenAt == null && (expiresAt == null || expiresAt > now)

    /** Whether Mo should talk about this as a fact rather than a hunch. */
    val sure: Boolean get() = userApproved || source == MemorySource.TOLD_ME || confidence == Confidence.HIGH
}

/** Rules for what Mo keeps, replaces and forgets. Pure logic. */
object MoMemories {
    const val PEAK_HOUR = "peak_hour"
    const val FAVOURITE_APP = "favourite_app"
    private const val CHECK_IN_PREFIX = "check_in:"

    /** Noticed patterns fade after this unless the user keeps them. */
    const val NOTICED_FOR_MS = 8 * DAY_MS
    /** After being told to forget something, Mo doesn't notice it again for this long. */
    const val FORGOTTEN_FOR_MS = 30 * DAY_MS

    /**
     * What to store for a [noticed] pattern given what's [existing] under its key, or null to
     * leave things as they are: kept and recently forgotten memories are the user's call.
     */
    fun merge(existing: Memory?, noticed: Memory, now: Long): Memory? {
        if (existing == null) return noticed
        existing.forgottenAt?.let { forgotten ->
            return if (now - forgotten < FORGOTTEN_FOR_MS) null else noticed
        }
        if (existing.userApproved) return null
        // Same thing noticed again: Mo has known it since it was first noticed.
        return if (existing.value == noticed.value) noticed.copy(createdAt = existing.createdAt) else noticed
    }

    /** The user keeps [memory]: it no longer fades. */
    fun keep(memory: Memory): Memory = memory.copy(userApproved = true, expiresAt = null)

    /** Forgets [memory], keeping only the fact that it was forgotten. */
    fun forget(memory: Memory, now: Long): Memory = memory.copy(value = "", content = "", forgottenAt = now)

    /** Whether the stored [memory] can be deleted: expired, or forgotten long enough ago. */
    fun isDone(memory: Memory, now: Long): Boolean {
        val forgotten = memory.forgottenAt
        if (forgotten != null) return now - forgotten >= FORGOTTEN_FOR_MS
        val expires = memory.expiresAt
        return expires != null && expires <= now
    }

    /** The user asked Mo to check in [minutes] from [now]. */
    fun checkIn(now: Long, minutes: Long): Memory = Memory(
        key = "$CHECK_IN_PREFIX$now",
        type = MemoryType.CHECK_IN,
        value = minutes.toString(),
        content = "Check in after $minutes minutes",
        source = MemorySource.TOLD_ME,
        confidence = Confidence.HIGH,
        createdAt = now,
        // Gone if Mo couldn't say it within a while of being due (phone off, quiet hours).
        expiresAt = now + minutes * 60_000L + MoInitiative.LATE_LIMIT_MS,
        dueAt = now + minutes * 60_000L,
    )
}

internal const val DAY_MS = 24 * 60 * 60_000L
