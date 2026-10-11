package io.github.amandhakar.mindscroll.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import io.github.amandhakar.mindscroll.mascot.MoMotion
import io.github.amandhakar.mindscroll.mascot.MoPainter
import io.github.amandhakar.mindscroll.mascot.Mood
import kotlinx.coroutines.delay

/**
 * Mo, alive: gently bobbing, blinking now and then, playing the mood's idle loop, and looking
 * surprised whenever [reactTo] changes (pass today's count).
 */
@Composable
fun MoFace(mood: Mood, modifier: Modifier = Modifier, reactTo: Any? = null) {
    val bob by rememberInfiniteTransition(label = "mo").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse),
        label = "bob",
    )

    var now by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) withFrameMillis { now = it }
    }

    var blinkStart by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(MoMotion.nextBlinkDelayMs())
            blinkStart = now
        }
    }

    // React to changes only, not to the first value shown.
    var reactStart by remember { mutableStateOf<Long?>(null) }
    var seen by remember { mutableStateOf(reactTo) }
    LaunchedEffect(reactTo) {
        if (reactTo != seen) {
            seen = reactTo
            reactStart = now
        }
    }

    Canvas(modifier.graphicsLayer { translationY = -bob * size.height * 0.04f }) {
        val pose = MoMotion.pose(now, blinkStart, reactStart, idle = true)
        drawIntoCanvas { canvas ->
            val side = minOf(size.width, size.height)
            MoPainter.draw(
                canvas.nativeCanvas, side, mood,
                left = (size.width - side) / 2, top = (size.height - side) / 2,
                pose = pose,
            )
        }
    }
}
