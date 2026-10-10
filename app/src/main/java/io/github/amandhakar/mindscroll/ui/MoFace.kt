package io.github.amandhakar.mindscroll.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import io.github.amandhakar.mindscroll.mascot.Mood
import io.github.amandhakar.mindscroll.mascot.MoPainter

/** Mo, gently bobbing. */
@Composable
fun MoFace(mood: Mood, modifier: Modifier = Modifier) {
    val bob by rememberInfiniteTransition(label = "mo").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse),
        label = "bob",
    )
    Canvas(modifier.graphicsLayer { translationY = -bob * size.height * 0.04f }) {
        drawIntoCanvas { canvas ->
            val side = minOf(size.width, size.height)
            MoPainter.draw(
                canvas.nativeCanvas, side, mood,
                left = (size.width - side) / 2, top = (size.height - side) / 2,
            )
        }
    }
}
