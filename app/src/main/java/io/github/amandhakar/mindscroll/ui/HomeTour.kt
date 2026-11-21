package io.github.amandhakar.mindscroll.ui

import androidx.compose.animation.core.animateRectAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.amandhakar.mindscroll.mascot.Mood

/** The places Mo shows the user around the home screen, in order. */
enum class TourStop(val title: String, val line: String) {
    HERO(
        "Me and the count",
        "This is where I live. Today's reels are the big number, and I react to every new one.",
    ),
    WEEK(
        "Your week",
        "Your last seven days. Today is the bright bar. No targets, no scores. Just the shape of your week.",
    ),
    MENU(
        "Everything else",
        "Up here you'll find Settings (my look, the camera island, which apps I watch) and \"Count looks wrong?\", which shows what I saw.",
    ),
    GO(
        "Your turn",
        "Now go scroll. Open one of your apps and I'll be up by the camera. *waves*",
    ),
}

/**
 * Mo walks the user around the real home screen: dims everything except one area at a time and
 * says what it is. [targets] are the areas' bounds in the root; a stop with no area (not on
 * screen yet, or nothing to show for a new user) just talks. [onStop] runs when a stop is
 * reached, so the screen can scroll the area into view.
 */
@Composable
fun HomeTour(
    targets: Map<TourStop, Rect?>,
    onStop: suspend (TourStop) -> Unit,
    onFinish: () -> Unit,
) {
    var index by remember { mutableIntStateOf(0) }
    val stop = TourStop.entries[index]
    val last = index == TourStop.entries.lastIndex
    LaunchedEffect(stop) { onStop(stop) }

    var root by remember { mutableStateOf(IntSize.Zero) }
    val target = targets[stop]
    val hole by animateRectAsState(target ?: Rect.Zero, label = "tourHole")
    val density = LocalDensity.current
    val pad = with(density) { 8.dp.toPx() }
    val radius = with(density) { 28.dp.toPx() }
    val inUpperHalf = target != null && target.center.y < root.height / 2f

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { root = it }
            // The tour owns the screen while it runs: taps don't reach the home screen under it.
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        Canvas(Modifier.fillMaxSize().graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)) {
            drawRect(Color.Black.copy(alpha = 0.66f))
            if (target != null) {
                drawRoundRect(
                    color = Color.Black,
                    topLeft = Offset(hole.left - pad, hole.top - pad),
                    size = Size(hole.width + 2 * pad, hole.height + 2 * pad),
                    cornerRadius = CornerRadius(radius),
                    blendMode = BlendMode.Clear,
                )
            }
        }

        Surface(
            modifier = Modifier
                .align(if (inUpperHalf) Alignment.BottomCenter else Alignment.TopCenter)
                .safeDrawingPadding()
                .padding(16.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                    MoFace(if (last) Mood.CHILL else Mood.CURIOUS, Modifier.size(64.dp), reactTo = index)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stop.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(stop.line, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${index + 1} of ${TourStop.entries.size}",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (!last) TextButton(onClick = onFinish) { Text("Skip") }
                    Button(onClick = { if (last) onFinish() else index++ }) { Text(if (last) "Done" else "Next") }
                }
            }
        }
    }
}
