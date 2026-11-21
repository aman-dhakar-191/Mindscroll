package io.github.amandhakar.mindscroll.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import io.github.amandhakar.mindscroll.app
import io.github.amandhakar.mindscroll.mascot.Mo
import io.github.amandhakar.mindscroll.mascot.Mood
import io.github.amandhakar.mindscroll.tracker.TrackedApps
import kotlinx.coroutines.delay

/** The conversation, in order. Each step is Mo saying one thing and asking for one thing. */
private enum class Step(val cheer: String? = null) {
    HELLO,
    LOOK,
    SEE("*perks up* There we go. I can see now."),
    NOTIFY("Thank you. I'll keep it quiet."),
    STAY("Wide awake. Thank you."),
    DONE,
    ;

    fun next(): Step = entries.getOrElse(ordinal + 1) { DONE }
}

/**
 * Setup as a chat with Mo. Mo asks for one thing at a time, notices when it's done (the screen
 * is re-read whenever the user comes back from a system screen) and moves on by itself. When
 * everything is in, Mo offers to show the user around; [onDone] says whether they said yes.
 */
@Composable
fun OnboardingScreen(
    permissions: PermissionState,
    onPermissionsChanged: () -> Unit,
    onDone: (startTour: Boolean) -> Unit,
) {
    val context = LocalContext.current
    val settings = context.app.settings
    fun open(intent: Intent) {
        runCatching { context.startActivity(intent) }
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        // After two denials Android stops showing the dialog and denies straight away;
        // the only way left is the app's notification settings.
        val activity = context as? Activity
        val blocked = activity != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !activity.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)
        if (!granted && blocked) open(PermissionIntents.notificationSettings(context))
        onPermissionsChanged()
    }

    var step by rememberSaveable { mutableStateOf(Step.HELLO) }
    var cheer by remember { mutableStateOf<String?>(null) }
    var reactions by remember { mutableIntStateOf(0) }
    var character by remember { mutableStateOf(settings.mascotCharacter) }
    var palette by remember { mutableStateOf(settings.mascotPalette) }

    val granted = when (step) {
        Step.SEE -> permissions.accessibility
        Step.NOTIFY -> permissions.notifications
        Step.STAY -> permissions.batteryUnrestricted
        else -> false
    }
    // Already granted when this step came up (setup opened again later): move on without a fuss.
    val grantedOnArrival = remember(step) { granted }
    LaunchedEffect(step, granted) {
        if (!granted) return@LaunchedEffect
        if (!grantedOnArrival) {
            cheer = step.cheer
            reactions++
            delay(1_700)
            cheer = null
        }
        step = step.next()
    }
    // Mo perks up when a new step comes in.
    LaunchedEffect(step) {
        if (step == Step.DONE || step == Step.LOOK) reactions++
    }

    val line = cheer ?: when (step) {
        Step.HELLO -> "Hi. I'm ${Mo.NAME}. I live in your camera hole and count the reels you swipe through. No judging. Mostly."
        Step.LOOK -> "First things first. Who should I look like? Pick a shape and a colour. It's still me inside."
        Step.SEE -> "I can't count what I can't see. Turn me on under Accessibility, in Installed apps. " +
            "I only see the shape of a screen, never your messages, and nothing leaves your phone."
        Step.NOTIFY -> "Want today's number in the status bar? I'll keep it small."
        Step.STAY -> "Last one. Phones love to put apps to sleep. Let me stay awake in the background so I don't miss a reel."
        Step.DONE -> "That's everything. Want me to show you around? It takes a minute."
    }

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Mindscroll", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MoFace(Mood.CHILL, Modifier.size(96.dp), reactTo = reactions)
                Surface(
                    shape = RoundedCornerShape(topStart = 4.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.weight(1f),
                ) {
                    TypedText(line, Modifier.padding(16.dp))
                }
            }

            AnimatedContent(
                targetState = step,
                transitionSpec = { fadeIn(tween(240)) togetherWith fadeOut(tween(120)) },
                label = "setupStep",
            ) { shown ->
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    when (shown) {
                        Step.HELLO -> Button(onClick = { step = Step.LOOK }, modifier = Modifier.fillMaxWidth()) {
                            Text("Hi ${Mo.NAME}")
                        }
                        Step.LOOK -> {
                            MoLookPicker(
                                character = character,
                                palette = palette,
                                onCharacter = {
                                    character = it
                                    settings.chooseCharacter(it)
                                    reactions++
                                },
                                onPalette = {
                                    palette = it
                                    settings.mascotPalette = it
                                    reactions++
                                },
                            )
                            Button(onClick = { step = Step.SEE }, modifier = Modifier.fillMaxWidth()) {
                                Text("That's me")
                            }
                            Text(
                                "The app icon follows your pick once you leave the app. You can change this any time in Settings.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Step.SEE -> {
                            Text(
                                "I'll watch ${trackedNames()} to start. Change that any time under Apps to track.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            FilledTonalButton(onClick = { open(PermissionIntents.accessibilitySettings()) }) {
                                Text("Open settings")
                            }
                            Text(
                                "If Android says \"Restricted setting\", open App info, tap ⋮ → Allow restricted settings, then try again.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            TextButton(onClick = { open(PermissionIntents.appInfo(context)) }) { Text("Open App info") }
                        }
                        Step.NOTIFY -> {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                FilledTonalButton(onClick = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    } else {
                                        open(PermissionIntents.notificationSettings(context))
                                    }
                                }) { Text("Allow") }
                                TextButton(onClick = { step = Step.STAY }) { Text("Not now") }
                            }
                        }
                        Step.STAY -> {
                            Text(
                                "Some phones (Xiaomi, Oppo, Vivo, Samsung) also have their own \"auto-start\" or " +
                                    "\"sleeping apps\" lists. Keep Mindscroll off those too.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                FilledTonalButton(onClick = { open(PermissionIntents.ignoreBatteryOptimizations(context)) }) {
                                    Text("Allow")
                                }
                                TextButton(onClick = { step = Step.DONE }) { Text("Not now") }
                            }
                        }
                        Step.DONE -> {
                            Button(onClick = { onDone(true) }, modifier = Modifier.fillMaxWidth()) { Text("Show me around") }
                            TextButton(onClick = { onDone(false) }, modifier = Modifier.fillMaxWidth()) { Text("Skip the tour") }
                            Text(
                                "The camera island and floating bubble are drawn by the accessibility service, so " +
                                    "they don't need the \"display over other apps\" permission.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            StepDots(step)
        }
    }
}

/** Mo's words appear a few letters at a time. The space is reserved up front, so the bubble doesn't grow as it types. */
@Composable
private fun TypedText(text: String, modifier: Modifier = Modifier) {
    var shown by remember(text) { mutableIntStateOf(0) }
    LaunchedEffect(text) {
        shown = 0
        while (shown < text.length) {
            delay(14)
            shown++
        }
    }
    val styled = buildAnnotatedString {
        append(text.take(shown))
        withStyle(SpanStyle(color = Color.Transparent)) { append(text.drop(shown)) }
    }
    Text(
        styled,
        modifier.semantics { contentDescription = text },
        style = MaterialTheme.typography.bodyLarge,
    )
}

@Composable
private fun StepDots(current: Step) {
    Row(
        Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        for (step in Step.entries) {
            Box(
                Modifier
                    .size(if (step == current) 10.dp else 8.dp)
                    .background(
                        if (step.ordinal <= current.ordinal) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant,
                        CircleShape,
                    ),
            )
        }
    }
}

private fun trackedNames(): String =
    TrackedApps.packages.value.map(TrackedApps::label).sorted().joinToString().ifEmpty { "no apps yet" }
