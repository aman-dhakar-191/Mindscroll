package io.github.amandhakar.mindscroll.mascot

import kotlin.math.abs
import kotlin.random.Random

/** One frame of Mo's expression. All values are 0..1. */
data class MoPose(
    /** Position in the mood's idle loop (breathing, glancing, swaying, spinning, nodding). */
    val phase: Float = 0f,
    /** 0 = eyes open, 1 = eyes shut. */
    val blink: Float = 0f,
    /** Surprise when a new reel counts: squash, wide eyes, "o" mouth. */
    val react: Float = 0f,
    /** 1 = fast asleep (eyes shut, a "z" floating), 0 = awake. */
    val sleep: Float = 0f,
    /** Waking up: a big yawn and a stretch. */
    val yawn: Float = 0f,
    val anticipation: Float = 0f,
    val gazeX: Float = 0f,
    val gazeY: Float = 0f,
    val moodTransition: Float = 1f,
    /** 0 = still, 1 = leaning out for a quick curious peek. */
    val peek: Float = 0f,
)

/** The visual meaning of a tracked-app session transition. */
enum class SessionReaction {
    NONE,
    FIRST_ENTRY,
    APP_SWITCH,
    RETURN_AFTER_BREAK,
}

/** Timing for Mo's animations, shared by the app (Compose) and the overlays (views). */
object MoMotion {
    /** Length of one idle loop. */
    const val LOOP_MS = 3600L
    const val BLINK_MS = 180L
    const val REACT_MS = 750L
    /** Waking: eyes open over the first part, the yawn rises and falls over the whole. */
    const val WAKE_MS = 1600L
    private const val EYES_OPEN_MS = 1100L
    private const val REACT_RISE_MS = 120L
    const val ANTICIPATION_MS = 90L
    const val MOOD_TRANSITION_MS = 420L
    const val PEEK_MS = 680L
    const val RETURN_BREAK_THRESHOLD_MS = 5 * 60_000L

    /**
     * Classifies only meaningful session edges; callers invoke this when the session changes.
     * A short inactive gap is treated as a fresh arrival, while a longer one gets a welcome-back.
     */
    fun sessionReaction(
        previousPackage: String?,
        currentPackage: String?,
        endedAtMs: Long?,
        nowMs: Long,
        hasSeenSession: Boolean,
    ): SessionReaction {
        if (currentPackage == null) return SessionReaction.NONE
        if (!hasSeenSession) return SessionReaction.FIRST_ENTRY
        if (endedAtMs != null && nowMs - endedAtMs >= RETURN_BREAK_THRESHOLD_MS) {
            return SessionReaction.RETURN_AFTER_BREAK
        }
        if (previousPackage != null && previousPackage != currentPackage) {
            return SessionReaction.APP_SWITCH
        }
        return SessionReaction.FIRST_ENTRY
    }

    fun phase(timeMs: Long): Float = (timeMs.mod(LOOP_MS)).toFloat() / LOOP_MS

    /** Eyelids close and reopen over [BLINK_MS]. */
    fun blink(sinceBlinkMs: Long): Float {
        if (sinceBlinkMs < 0 || sinceBlinkMs >= BLINK_MS) return 0f
        val t = sinceBlinkMs.toFloat() / BLINK_MS
        val halfCycle = if (t < 0.5f) t * 2f else (1f - t) * 2f
        return smoothStep(halfCycle)
    }

    /** A quick rise, then an eased settle over the rest of [REACT_MS]. */
    fun react(sinceReactMs: Long): Float {
        if (sinceReactMs < 0 || sinceReactMs >= REACT_MS) return 0f
        if (sinceReactMs < REACT_RISE_MS) {
            val t = sinceReactMs.toFloat() / REACT_RISE_MS
            return 1f - (1f - t) * (1f - t) * (1f - t)
        }
        val t = (sinceReactMs - REACT_RISE_MS).toFloat() / (REACT_MS - REACT_RISE_MS)
        val eased = t * t * (3 - 2 * t)
        return 1f - eased
    }

    private fun smoothStep(t: Float): Float = t * t * (3f - 2f * t)

    /** How asleep Mo still is, [sinceWakeMs] after starting to wake up. */
    fun wakingSleep(sinceWakeMs: Long): Float = when {
        sinceWakeMs < 0 -> 1f
        sinceWakeMs >= EYES_OPEN_MS -> 0f
        else -> 1f - sinceWakeMs.toFloat() / EYES_OPEN_MS
    }

    /** The yawn while waking: up, a moment wide open, and down again. */
    fun yawn(sinceWakeMs: Long): Float {
        if (sinceWakeMs < 0 || sinceWakeMs >= WAKE_MS) return 0f
        val t = sinceWakeMs.toFloat() / WAKE_MS
        return kotlin.math.sin(t * Math.PI).toFloat()
    }

    /** A quick lean-out, hold, and retreat when Mo notices a context change. */
    fun peek(sincePeekMs: Long): Float {
        if (sincePeekMs < 0 || sincePeekMs >= PEEK_MS) return 0f
        val t = sincePeekMs.toFloat() / PEEK_MS
        return when {
            t < 0.28f -> smoothStep(t / 0.28f)
            t < 0.62f -> 1f
            else -> 1f - smoothStep((t - 0.62f) / 0.38f)
        }
    }

    /** Irregular gaps between blinks look alive; a fixed rhythm looks mechanical. */
    fun nextBlinkDelayMs(random: Random = Random): Long = 2200L + random.nextLong(3200L)

    fun pose(
        timeMs: Long,
        blinkStartMs: Long?,
        reactStartMs: Long?,
        idle: Boolean,
        asleep: Boolean = false,
        wakeStartMs: Long? = null,
        mood: Mood = Mood.CHILL,
        moodChangedAtMs: Long? = null,
        peekStartMs: Long? = null,
    ): MoPose {
        val sinceWake = wakeStartMs?.let { timeMs - it }
        val sinceReact = reactStartMs?.let { timeMs - it }
        val loop = if (idle || asleep) phase(timeMs) else 0f
        val glance = when (mood) {
            Mood.CHILL -> 0.008f
            Mood.CURIOUS -> 0.045f
            Mood.WOBBLY -> 0.025f
            Mood.DIZZY -> 0.06f
            Mood.SLEEPY -> 0.012f
        }
        val anticipation = sinceReact?.let {
            if (it in 0 until ANTICIPATION_MS) smoothStep(it.toFloat() / ANTICIPATION_MS) else 0f
        } ?: 0f
        return MoPose(
            phase = loop,
            blink = if (asleep) 0f else blinkStartMs?.let { blink(timeMs - it) } ?: 0f,
            react = sinceReact?.let { react(it - ANTICIPATION_MS) } ?: 0f,
            sleep = when {
                asleep -> 1f
                sinceWake != null -> wakingSleep(sinceWake)
                else -> 0f
            },
            yawn = sinceWake?.let { yawn(it) } ?: 0f,
            anticipation = anticipation,
            gazeX = if (idle && !asleep) kotlin.math.sin(loop * 2 * Math.PI).toFloat() * glance else 0f,
            gazeY = if (idle && !asleep) kotlin.math.cos(loop * 4 * Math.PI).toFloat() * glance * 0.35f else 0f,
            moodTransition = moodChangedAtMs?.let {
                smoothStep(((timeMs - it).toFloat() / MOOD_TRANSITION_MS).coerceIn(0f, 1f))
            } ?: 1f,
            peek = peekStartMs?.let { peek(timeMs - it) } ?: 0f,
        )
    }
}
