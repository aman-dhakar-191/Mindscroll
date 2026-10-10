package io.github.amandhakar.mindscroll.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.amandhakar.mindscroll.mascot.Mo
import io.github.amandhakar.mindscroll.mascot.Mood
import io.github.amandhakar.mindscroll.tracker.reels.ReelApps

@Composable
fun OnboardingScreen(
    permissions: PermissionState,
    onPermissionsChanged: () -> Unit,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
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

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MoFace(Mood.CHILL, Modifier.size(72.dp))
                Column {
                    Text("Mindscroll", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                    Text("Hi, I'm ${Mo.NAME}.", style = MaterialTheme.typography.titleMedium)
                }
            }
            Text(
                "I count the reels and shorts you swipe through each day, so you see the number " +
                    "instead of guessing it. No judging. Mostly.",
                style = MaterialTheme.typography.bodyLarge,
            )

            StepCard(
                title = "1. Count reels (required)",
                body = "Turn on Mindscroll under Accessibility → Installed apps. It only looks at " +
                    "${ReelApps.all.joinToString { it.label }}, and only at the shape of their screens " +
                    "(is a reel feed showing, did it scroll). It doesn't read text or messages, and it has no internet permission, so nothing leaves your phone.\n\n" +
                    "If Android says \"Restricted setting\", open App info, tap ⋮ → Allow restricted " +
                    "settings, then try again.",
                done = permissions.accessibility,
                action = "Open settings",
                onAction = { open(PermissionIntents.accessibilitySettings()) },
                secondaryAction = "App info",
                onSecondaryAction = { open(PermissionIntents.appInfo(context)) },
            )

            StepCard(
                title = "2. Show the count",
                body = "Lets Mindscroll keep today's count in the status bar and notification shade.",
                done = permissions.notifications,
                action = "Allow",
                onAction = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        open(PermissionIntents.notificationSettings(context))
                    }
                },
            )

            StepCard(
                title = "3. Keep running",
                body = "Stops battery optimization from closing the counter in the background. " +
                    "Some phones (Xiaomi, Oppo, Vivo, Samsung) also have their own \"auto-start\" " +
                    "or \"sleeping apps\" lists; keep Mindscroll off those too.",
                done = permissions.batteryUnrestricted,
                action = "Allow",
                onAction = { open(PermissionIntents.ignoreBatteryOptimizations(context)) },
            )

            Text(
                "The camera patch and floating bubble are drawn by the accessibility service, so " +
                    "they don't need the \"display over other apps\" permission.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(4.dp))
            Button(
                onClick = onDone,
                enabled = permissions.accessibility,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (permissions.accessibility) "Done" else "Turn on step 1 to continue")
            }
        }
    }
}

@Composable
private fun StepCard(
    title: String,
    body: String,
    done: Boolean,
    action: String,
    onAction: () -> Unit,
    secondaryAction: String? = null,
    onSecondaryAction: () -> Unit = {},
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (done) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(body, style = MaterialTheme.typography.bodyMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (done) {
                    Text("✓ Done", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                } else {
                    FilledTonalButton(onClick = onAction) { Text(action) }
                    if (secondaryAction != null) {
                        TextButton(onClick = onSecondaryAction) { Text(secondaryAction) }
                    }
                }
            }
        }
    }
}
