package io.github.amandhakar.mindscroll.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import io.github.amandhakar.mindscroll.mascot.MoMotion
import io.github.amandhakar.mindscroll.mascot.MoLook
import io.github.amandhakar.mindscroll.mascot.MoPainter
import io.github.amandhakar.mindscroll.mascot.MoPose
import io.github.amandhakar.mindscroll.mascot.MotionProfile
import io.github.amandhakar.mindscroll.mascot.Mood
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlin.math.PI
import kotlin.math.cos

/**
 * Mo, alive: gently bobbing, blinking now and then, playing the mood's idle loop, and looking
 * surprised whenever [reactTo] changes (pass today's count). [profile] is the user's motion
 * style, so the home screen moves like the overlay does.
 *
 * Frames are only requested while something is moving. With nothing animating, the loop sleeps
 * for the profile's idle interval (the sway is slow; it never needs display rate), and it stops
 * completely while the screen isn't resumed or the system has animations turned off.
 */
@Composable
fun MoFace(
    mood: Mood,
    modifier: Modifier = Modifier,
    reactTo: Any? = null,
    profile: MotionProfile = MotionProfile.BALANCED,
) {
    val context = LocalContext.current
    // "Remove animations" in developer/accessibility settings: no idle loop, only short soft reactions.
    val reduced = remember {
        runCatching {
            android.provider.Settings.Global.getFloat(
                context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f,
            ) == 0f
        }.getOrDefault(false)
    }
    val p = if (reduced) MotionProfile.CALM else profile
    // The long-running loops below outlive a recomposition, so they read the latest mood through this.
    val currentMood by rememberUpdatedState(mood)
    val idleOn = !reduced

    var resumed by remember { mutableStateOf(true) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumed = true }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { resumed = false }

    // Clock for drawing. Only read from the draw/layer phases, so updating it never recomposes.
    var now by remember { mutableLongStateOf(0L) }
    // Bumped whenever something starts animating, to wake the frame loop from a full stop.
    var wake by remember { mutableIntStateOf(0) }

    var blinkStart by remember { mutableStateOf<Long?>(null) }
    var reactStart by remember { mutableStateOf<Long?>(null) }
    var reactFrom by remember { mutableFloatStateOf(0f) }
    var moodChangedAt by remember { mutableStateOf<Long?>(null) }
    var previousColor by remember { mutableIntStateOf(MoLook.color(mood)) }
    var shownMood by remember { mutableStateOf(mood) }

    fun poseAt(t: Long): MoPose = MoMotion.pose(
        timeMs = t, blinkStartMs = blinkStart, reactStartMs = reactStart, idle = idleOn,
        mood = currentMood, moodChangedAtMs = moodChangedAt, profile = p, reactFrom = reactFrom,
    )

    fun animating(t: Long): Boolean =
        (blinkStart?.let { t - it < MoMotion.blinkDurationMs(mood) } ?: false) ||
            (reactStart?.let { t - it < MoMotion.reactionLength(p, reactFrom) } ?: false) ||
            (moodChangedAt?.let { t - it < MoMotion.MOOD_TRANSITION_MS } ?: false)

    // The frame loop. Display rate while animating, the slow idle rate otherwise, nothing when
    // paused or when there's nothing to draw.
    LaunchedEffect(resumed, idleOn, p) {
        if (!resumed) return@LaunchedEffect
        while (true) {
            val t = withFrameMillis { it }
            now = t
            when {
                animating(t) -> Unit // next frame
                idleOn -> delay(p.idleFrameMs)
                else -> {
                    val seen = wake
                    snapshotFlow { wake }.first { it != seen }
                }
            }
        }
    }

    LaunchedEffect(p, resumed) {
        if (!resumed) return@LaunchedEffect
        while (true) {
            delay(MoMotion.nextBlinkDelayMs(profile = p))
            blinkStart = withFrameMillis { it }
            wake++
        }
    }

    // React to changes only, not to the first value shown. A reaction that lands while the
    // last one is still playing continues from where Mo is, with no crouch, instead of restarting.
    var seen by remember { mutableStateOf(reactTo) }
    LaunchedEffect(reactTo) {
        if (reactTo != seen) {
            seen = reactTo
            val t = withFrameMillis { it }
            val amount = (p.reactScale).coerceIn(0.5f, 1.8f)
            reactFrom = if (reactStart == null) 0f else poseAt(t).react / amount
            reactStart = t
            now = t
            wake++
        }
    }

    // Mood changes cross-fade the body colour and settle with a soft bounce.
    LaunchedEffect(mood) {
        if (mood != shownMood) {
            previousColor = MoLook.color(shownMood)
            shownMood = mood
            val t = withFrameMillis { it }
            moodChangedAt = t
            now = t
            wake++
        }
    }

    Canvas(
        modifier.graphicsLayer {
            // The idle bob comes from the same clock as everything else (it used to be its own
            // infinite transition, a second always-running animation).
            if (idleOn) {
                val bob = (1f - cos(2f * PI.toFloat() * now / 2800f)) / 2f
                translationY = -bob * size.height * 0.04f * p.idleAmplitude
            }
        },
    ) {
        val pose = poseAt(now)
        drawIntoCanvas { canvas ->
            val side = minOf(size.width, size.height)
            MoPainter.draw(
                canvas.nativeCanvas, side, mood,
                left = (size.width - side) / 2, top = (size.height - side) / 2,
                pose = pose,
                previousMoodColor = previousColor,
                stage = MoPainter.ANIMATED_STAGE,
            )
        }
    }
}
