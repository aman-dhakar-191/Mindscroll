package io.github.amandhakar.mindscroll.mascot

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** One frame of Mo's expression. All values are 0..1. */
data class MoPose(
    /** Position in the mood's idle loop (breathing, glancing, swaying, spinning, nodding). */
    val phase: Float = 0f,
    /** 0 = eyes open, 1 = eyes shut. */
    val blink: Float = 0f,
    /**
     * The startle envelope when a new reel counts. 0 = rest, 1 = full pop (stretch up, wide
     * eyes, "o" mouth). It is a spring, so it overshoots below 0 on the way back: that
     * rebound is the squash that settles the body. Roughly -0.4..1.8.
     */
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
    val reactionIntensity: Float = 1f,
    val exit: Float = 0f,
    /** Eyes dart one way and back (app switch). -1..1, signed by direction. */
    val glance: Float = 0f,
    /** Joy: the reaction in its happy form (^ ^ eyes, open grin) instead of surprise. 0..1+. */
    val excite: Float = 0f,
    /** Brows, shared by every expression. -1 lowered/worried .. 1 raised. */
    val brow: Float = 0f,
    /** 0..1: how much higher one brow sits than the other (curiosity). */
    val browSkew: Float = 0f,
    /** Mouth corners, shared by every expression. -1 frown .. 1 broad smile. */
    val smile: Float = 0f,
    /** Dizzy or mesmerised: spiral eyes and a wobble. 0..1 envelope. */
    val whirl: Float = 0f,
    /** How many turns the spiral eyes have made since the whirl began (for the eyes and the wobble). */
    val whirlTurn: Float = 0f,
    /** Scales the idle sway and breathing for the motion style. */
    val idleAmplitude: Float = 1f,
    /** Scales the hop of a reaction for the motion style. */
    val hop: Float = 1f,
)

/**
 * How lively Mo is. One profile per motion style, so the styles differ in *timing and physics*
 * (how fast Mo reacts, how much he overshoots, how long he takes to settle) and not just in
 * how big the reaction is.
 */
data class MotionProfile(
    /** Multiplies the size of every reaction. */
    val reactScale: Float,
    /** The crouch before a pop. */
    val anticipationMs: Long,
    /** How fast the pop rises. */
    val riseMs: Long,
    /** How long the spring takes to come to rest after the pop. */
    val settleMs: Long,
    /** Spring damping ratio: 1 = no overshoot at all, lower = bouncier. */
    val damping: Float,
    /** How far a reaction lifts Mo off the floor. */
    val hop: Float,
    val idleAmplitude: Float,
    /** Redraw interval of the idle loop. Slow sway needs no more than this. */
    val idleFrameMs: Long,
    /** Speed of gestures (peek, glance, exit); above 1 = snappier. */
    val gestureSpeed: Float,
    /** Scales the gap between blinks; below 1 = blinks more often. */
    val blinkGapScale: Float,
) {
    /**
     * This profile with the reaction's timing (crouch, rise, settle) stretched by [factor]; above
     * 1 is slower. Used for one reaction at a time: a tired Mo reacts slower, a Mo used to the
     * scrolling reacts briskly. Callers cache the result; it is not made per frame.
     */
    fun paced(factor: Float): MotionProfile =
        if (factor == 1f) this
        else copy(
            anticipationMs = (anticipationMs * factor).toLong(),
            riseMs = (riseMs * factor).toLong().coerceAtLeast(40L),
            settleMs = (settleMs * factor).toLong().coerceAtLeast(120L),
        )

    /** Real time a gesture takes at this style's speed. */
    val peekMs: Long get() = (MoMotion.PEEK_MS / gestureSpeed).toLong()
    val glanceMs: Long get() = (MoMotion.GLANCE_MS / gestureSpeed).toLong()
    val exitMs: Long get() = (MoMotion.EXIT_MS / gestureSpeed).toLong()

    /** From the start of the pop to the spring being at rest. */
    val reactMs: Long get() = riseMs + settleMs
    /** Everything a reaction can draw, crouch included. */
    val reactTotalMs: Long get() = anticipationMs + reactMs

    companion object {
        /** Soft and unhurried: small, critically damped reactions with no bounce. */
        val CALM = MotionProfile(
            reactScale = 0.8f, anticipationMs = 80, riseMs = 320, settleMs = 850, damping = 1f,
            hop = 0.4f, idleAmplitude = 0.6f, idleFrameMs = 66, gestureSpeed = 0.55f, blinkGapScale = 1.3f,
        )
        val BALANCED = MotionProfile(
            reactScale = 1.1f, anticipationMs = 150, riseMs = 200, settleMs = 1000, damping = 0.55f,
            hop = 1f, idleAmplitude = 1f, idleFrameMs = 50, gestureSpeed = 0.65f, blinkGapScale = 1f,
        )
        /** Snappy and springy: a longer crouch, a fast pop, a couple of rebounds. */
        val PLAYFUL = MotionProfile(
            reactScale = 1.45f, anticipationMs = 260, riseMs = 170, settleMs = 1500, damping = 0.28f,
            hop = 1.7f, idleAmplitude = 1.35f, idleFrameMs = 40, gestureSpeed = 0.75f, blinkGapScale = 0.7f,
        )
    }
}

/** The visual meaning of a tracked-app session transition. */
enum class SessionReaction {
    NONE,
    FIRST_ENTRY,
    APP_SWITCH,
    RETURN_AFTER_BREAK,
    APP_EXIT,
}

/** Timing for Mo's animations, shared by the app (Compose) and the overlays (views). */
object MoMotion {
    /** Length of one idle loop. */
    const val LOOP_MS = 3600L
    const val BLINK_MS = 180L
    /** Balanced style's length of a reaction without the crouch (kept for callers; see [MotionProfile.reactMs]). */
    const val REACT_MS = 1200L
    /** Waking: eyes open over the first part, the yawn rises and falls over the whole. */
    const val WAKE_MS = 1600L
    private const val EYES_OPEN_MS = 1100L
    private const val REACT_RISE_MS = 120L
    const val ANTICIPATION_MS = 90L
    const val MOOD_TRANSITION_MS = 650L
    const val PEEK_MS = 680L
    const val EXIT_MS = 720L
    const val GLANCE_MS = 700L
    /** Spiral eyes for scrolling too fast: quick spin, short. */
    const val WHIRL_FAST_MS = 1_800L
    /** Spiral eyes for staring at one reel: slow and long, mesmerised. */
    const val WHIRL_SLOW_MS = 3_200L
    private const val EXIT_LEAN_IN = 0.3f
    /** A gesture that interrupts another one blends out the old pose over this long. */
    const val CARRY_FADE_MS = 160L
    /** A new reaction landing while Mo is already moving this much skips the crouch. */
    const val CHAIN_THRESHOLD = 0.25f
    /** Sleepy blinks are slow and heavy. */
    private const val SLEEPY_BLINK_MS = 320L
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
        if (currentPackage == null) {
            return if (hasSeenSession && previousPackage != null) SessionReaction.APP_EXIT else SessionReaction.NONE
        }
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
    fun blink(sinceBlinkMs: Long, durationMs: Long = BLINK_MS): Float {
        if (sinceBlinkMs < 0 || sinceBlinkMs >= durationMs) return 0f
        val t = sinceBlinkMs.toFloat() / durationMs
        val halfCycle = if (t < 0.5f) t * 2f else (1f - t) * 2f
        return smoothStep(halfCycle)
    }

    fun blinkDurationMs(mood: Mood): Long = if (mood == Mood.SLEEPY) SLEEPY_BLINK_MS else BLINK_MS

    /**
     * The startle: a fast ease-out rise (from [from], so a reaction that lands mid-motion
     * carries on from where Mo is instead of snapping to rest first), then a damped spring
     * that overshoots below zero and settles. The overshoot is what turns the pop into a
     * squash-and-stretch instead of a fade.
     */
    fun react(sinceReactMs: Long, profile: MotionProfile = MotionProfile.BALANCED, from: Float = 0f): Float {
        if (sinceReactMs < 0 || sinceReactMs >= profile.reactMs) return 0f
        if (sinceReactMs < profile.riseMs) {
            val t = sinceReactMs.toFloat() / profile.riseMs
            val easedOut = 1f - (1f - t) * (1f - t) * (1f - t)
            return from + (1f - from) * easedOut
        }
        return springDecay(sinceReactMs - profile.riseMs, profile.settleMs, profile.damping)
    }

    /**
     * A spring released from 1 with no velocity, relaxing to 0 over [durationMs] (envelope down
     * to ~2%). [damping] is the damping ratio; 1 or more is critically damped (no overshoot).
     * Closed form, so it can be sampled at any time with no state or per-frame integration.
     */
    fun springDecay(tMs: Long, durationMs: Long, damping: Float): Float {
        if (tMs < 0 || tMs >= durationMs) return 0f
        val t = tMs / 1000f
        val d = durationMs / 1000f
        val z = damping.coerceIn(0.05f, 1f)
        val value = if (z >= 1f) {
            val w = 7f / d
            exp(-w * t) * (1f + w * t)
        } else {
            val w = 4f / (z * d)
            val wd = w * sqrt(1f - z * z)
            exp(-z * w * t) * (kotlin.math.cos(wd * t) + z / sqrt(1f - z * z) * sin(wd * t))
        }
        // Make sure it lands exactly on rest.
        val tail = (t / d - 0.9f) / 0.1f
        return if (tail > 0f) value * (1f - smoothStep(tail)) else value
    }

    /**
     * The crouch: eases in over [MotionProfile.anticipationMs], then releases over the pop's
     * rise so the crouch and the stretch blend instead of the body snapping from crouched to
     * neutral at the instant the pop starts.
     */
    fun anticipation(sinceReactMs: Long, profile: MotionProfile, from: Float = 0f): Float {
        val len = anticipationLength(profile, from)
        return when {
            len <= 0 || sinceReactMs < 0 -> 0f
            sinceReactMs < len -> smoothStep(sinceReactMs.toFloat() / len)
            sinceReactMs < len + profile.riseMs -> 1f - smoothStep((sinceReactMs - len).toFloat() / profile.riseMs)
            else -> 0f
        }
    }

    /**
     * How big a reaction is: the event's own [intensity] (habituation and fatigue make it small)
     * times the style's scale. Never so small that Mo seems frozen, never past the headroom.
     */
    fun reactAmount(intensity: Float, profile: MotionProfile): Float =
        (intensity * profile.reactScale).coerceIn(0.25f, 1.8f)

    /** No crouch when already in motion: a wind-up mid-bounce reads as a glitch. */
    fun anticipationLength(profile: MotionProfile, from: Float): Long =
        if (abs(from) > CHAIN_THRESHOLD) 0L else profile.anticipationMs

    /** A whole reaction, crouch included, is over this long after it starts. */
    fun reactionLength(profile: MotionProfile, from: Float = 0f): Long =
        anticipationLength(profile, from) + profile.reactMs

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

    /**
     * A small lean and turn as Mo notices the tracked app closing: eases into the lean over the
     * first third (it used to start at full lean, which snapped), then eases back out.
     */
    fun exit(sinceExitMs: Long): Float {
        if (sinceExitMs < 0 || sinceExitMs >= EXIT_MS) return 0f
        val t = sinceExitMs.toFloat() / EXIT_MS
        return if (t < EXIT_LEAN_IN) smoothStep(t / EXIT_LEAN_IN) else smoothStep(1f - (t - EXIT_LEAN_IN) / (1f - EXIT_LEAN_IN))
    }

    /**
     * Eyes dart to one side and swing back past centre before settling: "what was that?" on an
     * app switch. Signed, so the body and eyes can lean the way Mo is looking.
     */
    fun glance(sinceMs: Long): Float {
        if (sinceMs < 0 || sinceMs >= GLANCE_MS) return 0f
        val t = sinceMs.toFloat() / GLANCE_MS
        return sin(t * 2f * PI.toFloat()) * (1f - smoothStep(t))
    }

    fun whirlDurationMs(slow: Boolean): Long = if (slow) WHIRL_SLOW_MS else WHIRL_FAST_MS

    /** Spiral eyes: they come in quickly, hold, then ease out over the last third. */
    fun whirl(sinceMs: Long, slow: Boolean): Float {
        val length = whirlDurationMs(slow)
        if (sinceMs < 0 || sinceMs >= length) return 0f
        val t = sinceMs.toFloat() / length
        return when {
            t < 0.12f -> smoothStep(t / 0.12f)
            t < 0.65f -> 1f
            else -> 1f - smoothStep((t - 0.65f) / 0.35f)
        }
    }

    /** Turns made by the spiral eyes [sinceMs] into a whirl: fast spins about 1.6 times a second, slow 0.5. */
    fun whirlTurn(sinceMs: Long, slow: Boolean): Float =
        if (sinceMs < 0) 0f else sinceMs / 1000f * (if (slow) 0.5f else 1.6f)

    /**
     * Blends the pose a gesture was interrupted in back to the new gesture over
     * [CARRY_FADE_MS], so restarting a gesture mid-flight never snaps.
     */
    fun carried(value: Float, carry: Float, sinceMs: Long): Float {
        if (carry == 0f || sinceMs < 0 || sinceMs >= CARRY_FADE_MS) return value
        return value + carry * (1f - smoothStep(sinceMs.toFloat() / CARRY_FADE_MS))
    }

    /** Irregular gaps between blinks look alive; a fixed rhythm looks mechanical. */
    fun nextBlinkDelayMs(random: Random = Random, profile: MotionProfile = MotionProfile.BALANCED): Long =
        ((2200L + random.nextLong(3200L)) * profile.blinkGapScale).toLong()

    /** Resting brow height of a mood. Negative = lowered (worried, drooping). */
    fun baseBrow(mood: Mood): Float = when (mood) {
        Mood.CHILL -> 0.1f
        Mood.CURIOUS -> 0.35f
        Mood.WOBBLY -> -0.55f
        Mood.DIZZY -> -0.25f
        Mood.SLEEPY -> -0.35f
    }

    /** Resting mouth corners of a mood. */
    fun baseSmile(mood: Mood): Float = when (mood) {
        Mood.CHILL -> 0.65f
        Mood.CURIOUS -> 0.25f
        Mood.WOBBLY -> -0.25f
        Mood.DIZZY -> -0.1f
        Mood.SLEEPY -> 0.05f
    }

    /**
     * Mo's whole pose at [timeMs]. Every channel is a function of the same clock and the same
     * start times, so the face (brows, eyes, mouth) and the body (squash, hop, lean) are always
     * sampled together and cannot drift apart.
     *
     * Interruptions: [reactFrom] is the reaction value at the moment a new reaction replaced the
     * previous one, and the `*Carry` values are the same for the gestures. Callers capture them
     * with the same functions used here, so a restart continues smoothly from the current pose.
     */
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
        reactionIntensity: Float = 1f,
        exitStartMs: Long? = null,
        profile: MotionProfile = MotionProfile.BALANCED,
        reactFrom: Float = 0f,
        reactExcited: Boolean = false,
        glanceStartMs: Long? = null,
        peekCarry: Float = 0f,
        exitCarry: Float = 0f,
        glanceCarry: Float = 0f,
        /** Timing of the reaction in progress; differs from [profile] when Mo is tired or used to it. */
        reactProfile: MotionProfile = profile,
        /** An idle stretch and yawn (Mo stays awake), as opposed to waking up. */
        yawnStartMs: Long? = null,
        whirlStartMs: Long? = null,
        whirlSlow: Boolean = false,
    ): MoPose {
        val sinceWake = wakeStartMs?.let { timeMs - it }
        val sinceReact = reactStartMs?.let { timeMs - it }
        val loop = if (idle || asleep) phase(timeMs) else 0f
        val speed = profile.gestureSpeed
        val glanceRange = when (mood) {
            Mood.CHILL -> 0.008f
            Mood.CURIOUS -> 0.045f
            Mood.WOBBLY -> 0.025f
            Mood.DIZZY -> 0.06f
            Mood.SLEEPY -> 0.012f
        } * profile.idleAmplitude

        val amount = reactAmount(reactionIntensity, profile)
        val crouchMs = anticipationLength(reactProfile, reactFrom)
        val anticipation = sinceReact?.let { anticipation(it, reactProfile, reactFrom) } ?: 0f
        val react = (sinceReact?.let { react(it - crouchMs, reactProfile, reactFrom) } ?: 0f) * amount

        val peek = peekStartMs?.let {
            val since = timeMs - it
            carried(peek((since * speed).toLong()), peekCarry, since)
        } ?: 0f
        val exit = exitStartMs?.let {
            val since = timeMs - it
            carried(exit((since * speed).toLong()), exitCarry, since)
        } ?: 0f
        val glance = glanceStartMs?.let {
            val since = timeMs - it
            carried(glance((since * speed).toLong()), glanceCarry, since)
        } ?: 0f

        val sleep = when {
            asleep -> 1f
            sinceWake != null -> wakingSleep(sinceWake)
            else -> 0f
        }
        val yawn = maxOf(sinceWake?.let { yawn(it) } ?: 0f, yawnStartMs?.let { yawn(timeMs - it) } ?: 0f)
        val whirlSince = whirlStartMs?.let { timeMs - it }
        val whirl = whirlSince?.let { whirl(it, whirlSlow) } ?: 0f
        val startle = react.coerceAtLeast(0f)
        val excite = if (reactExcited) startle else 0f
        val surprise = startle - excite

        // Expression: one model feeds the face for every mood and every event, so a startle, a
        // hello and a farewell all read as the same character.
        val brow = (
            baseBrow(mood) +
                surprise * 0.9f + excite * 0.5f +
                peek * 0.5f + abs(glance) * 0.3f +
                exit * 0.2f - whirl * 0.3f
            ) * (1f - sleep * 0.6f)
        val smile = (
            baseSmile(mood) +
                excite * 0.9f - surprise * 0.5f +
                peek * 0.35f + exit * 0.5f - whirl * 0.25f
            ) * (1f - sleep * 0.5f)

        return MoPose(
            phase = loop,
            blink = if (asleep) 0f else blinkStartMs?.let { blink(timeMs - it, blinkDurationMs(mood)) } ?: 0f,
            react = react,
            sleep = sleep,
            yawn = yawn,
            anticipation = anticipation,
            gazeX = if (idle && !asleep) sin(loop * 2 * PI).toFloat() * glanceRange + glance * 0.07f else glance * 0.07f,
            gazeY = if (idle && !asleep) kotlin.math.cos(loop * 4 * PI).toFloat() * glanceRange * 0.35f else 0f,
            moodTransition = moodChangedAtMs?.let {
                smoothStep(((timeMs - it).toFloat() / MOOD_TRANSITION_MS).coerceIn(0f, 1f))
            } ?: 1f,
            peek = peek,
            reactionIntensity = reactionIntensity,
            exit = exit,
            glance = glance,
            excite = excite,
            brow = brow.coerceIn(-1f, 1f),
            browSkew = (abs(glance) * 0.8f + peek * 0.3f).coerceIn(0f, 1f),
            smile = smile.coerceIn(-1f, 1f),
            whirl = whirl,
            whirlTurn = whirlSince?.let { whirlTurn(it, whirlSlow) } ?: 0f,
            idleAmplitude = profile.idleAmplitude,
            hop = profile.hop,
        )
    }
}
