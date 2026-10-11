package io.github.amandhakar.mindscroll.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.amandhakar.mindscroll.app
import io.github.amandhakar.mindscroll.tracker.TrackedApps
import io.github.amandhakar.mindscroll.tracker.reels.ReelApps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** An app on the phone that can be tracked. */
private data class InstalledApp(val packageName: String, val label: String)

/**
 * Every app with a launcher icon, with a switch to track it. Tracked apps get reel counting,
 * screen time, the live timer and Mo's remarks; changes apply right away.
 */
@Composable
fun TrackedAppsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = context.app.settings
    val tracked by TrackedApps.packages.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    // Tracked apps first when the screen opens; they don't jump around while toggling.
    val initiallyTracked = remember { tracked }
    val installed by produceState<List<InstalledApp>?>(null) {
        value = withContext(Dispatchers.IO) { launchableApps(context) }
    }

    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.safeDrawingPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Apps to track",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onBack) { Text("Done") }
            }
            Text(
                "Tracked apps get reel counting, screen time, the live timer and Mo's remarks. " +
                    "Reels are counted in any app with a full-screen, one-at-a-time feed.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search apps") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            val apps = installed
            if (apps == null) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                val shown = apps
                    .filter { query.isBlank() || it.label.contains(query.trim(), ignoreCase = true) }
                    .sortedWith(compareBy<InstalledApp> { it.packageName !in initiallyTracked }.thenBy { it.label.lowercase() })
                Text(
                    "${tracked.size} tracked",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                LazyColumn(Modifier.fillMaxWidth()) {
                    items(shown, key = { it.packageName }) { installedApp ->
                        AppRow(
                            app = installedApp,
                            tracked = installedApp.packageName in tracked,
                            hasReelHints = installedApp.packageName in ReelApps.known,
                            onToggle = { on ->
                                // Read the saved set, not the flow: it's current even between quick toggles.
                                val now = settings.trackedApps
                                settings.trackedApps = if (on) now + installedApp.packageName else now - installedApp.packageName
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppRow(app: InstalledApp, tracked: Boolean, hasReelHints: Boolean, onToggle: (Boolean) -> Unit) {
    val context = LocalContext.current
    val icon by produceState<ImageBitmap?>(null, app.packageName) {
        value = withContext(Dispatchers.IO) { appIcon(context, app.packageName) }
    }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp)) {
            icon?.let { Image(it, contentDescription = null, modifier = Modifier.size(40.dp)) }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(app.label, style = MaterialTheme.typography.bodyLarge)
            if (hasReelHints) {
                Text(
                    "Reel feed known",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = tracked, onCheckedChange = onToggle)
    }
}

/** Apps with a launcher icon, except Mindscroll itself. */
private fun launchableApps(context: Context): List<InstalledApp> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        .map { it.activityInfo.packageName }
        .distinct()
        .filter { it != context.packageName }
        .map { InstalledApp(it, context.app.appLabel(it)) }
        .sortedBy { it.label.lowercase() }
}

private fun appIcon(context: Context, packageName: String): ImageBitmap? = runCatching {
    val size = (40 * context.resources.displayMetrics.density).toInt()
    context.packageManager.getApplicationIcon(packageName).toBitmap(size, size).asImageBitmap()
}.getOrNull()
