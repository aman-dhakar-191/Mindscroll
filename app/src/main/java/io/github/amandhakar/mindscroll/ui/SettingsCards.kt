package io.github.amandhakar.mindscroll.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import io.github.amandhakar.mindscroll.app
import io.github.amandhakar.mindscroll.data.DayTotal
import io.github.amandhakar.mindscroll.data.Days
import io.github.amandhakar.mindscroll.mascot.MemoryFacts
import io.github.amandhakar.mindscroll.mascot.MoMemory
import io.github.amandhakar.mindscroll.data.Durations
import io.github.amandhakar.mindscroll.display.AppStat
import io.github.amandhakar.mindscroll.mascot.Mo
import io.github.amandhakar.mindscroll.mascot.MoMemories
import io.github.amandhakar.mindscroll.mascot.MoPolicy
import io.github.amandhakar.mindscroll.mascot.Mood
import io.github.amandhakar.mindscroll.mascot.QuietHours
import io.github.amandhakar.mindscroll.tracker.TrackedApps
import io.github.amandhakar.mindscroll.tracker.TrackerRegistry
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.launch

/*
 * The cards that set Mo and tracking up. They live on the Settings screen, not Home, so Home
 * keeps only today's numbers.
 */

@Composable
internal fun TrackedAppsCard(onOpenApps: () -> Unit) {
    val tracked by TrackedApps.packages.collectAsStateWithLifecycle()
    val names = tracked.map(TrackedApps::label).sorted()
    Panel {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Apps to track", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                if (names.isEmpty()) "None yet. Pick the apps Mo should keep an eye on." else names.joinToString(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FilledTonalButton(onClick = onOpenApps) { Text("Choose apps") }
        }
    }
}

@Composable
internal fun DiagnosticsCard() {
    val context = LocalContext.current
    val settings = context.app.settings
    var enabled by remember { mutableStateOf(settings.diagnosticsEnabled) }
    Panel {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Diagnostics", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            ToggleRow(
                "Share diagnostic data",
                "Send crash reports and technical accessibility-service events to help diagnose counting issues. Screen contents are not collected.",
                enabled,
            ) {
                enabled = it
                settings.diagnosticsEnabled = it
                FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(it)
                FirebaseAnalytics.getInstance(context).setAnalyticsCollectionEnabled(it)
            }
        }
    }
}

/** How Mo behaves: when to be quiet, bringing things up, check-ins, and what Mo remembers. */
@Composable
internal fun MoCard(onOpenMemories: () -> Unit) {
    val app = LocalContext.current.app
    val store = app.moPrefs
    val policy = remember { MoPolicy(store) }
    var quietAtNight by remember { mutableStateOf(store.profile.quietHours != null) }
    var initiatives by remember { mutableStateOf(store.profile.initiatives) }
    var snoozedUntil by remember { mutableStateOf(policy.snoozedUntil(System.currentTimeMillis())) }
    var checkInAt by remember { mutableStateOf<Long?>(null) }
    Panel {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Mo", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            ToggleRow("Quiet at night", "No speech bubbles from 10 pm to 8 am. Mo still counts.", quietAtNight) {
                quietAtNight = it
                store.profile = store.profile.copy(quietHours = if (it) QuietHours.NIGHT else null)
            }
            ToggleRow(
                "Mo brings things up",
                "Now and then, at most a few times a day: something Mo remembers, or a bit of Mo's own life.",
                initiatives,
            ) {
                initiatives = it
                store.profile = store.profile.copy(initiatives = it)
            }
            val until = snoozedUntil
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (until == null) {
                    Text("Need Mo to hush for a bit?", Modifier.weight(1f))
                    FilledTonalButton(onClick = {
                        val now = System.currentTimeMillis()
                        policy.snooze(now, SNOOZE_MS)
                        snoozedUntil = policy.snoozedUntil(now)
                    }) { Text("Quiet for an hour") }
                } else {
                    Text("Quiet until ${clockTime(until)}", Modifier.weight(1f))
                    TextButton(onClick = {
                        policy.unsnooze()
                        snoozedUntil = null
                    }) { Text("Let Mo talk") }
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                val due = checkInAt
                Text(if (due == null) "Check in with me in" else "Mo will check in at ${clockTime(due)}", Modifier.weight(1f))
                CHECK_IN_MINUTES.forEach { minutes ->
                    TextButton(onClick = {
                        val checkIn = MoMemories.checkIn(System.currentTimeMillis(), minutes)
                        checkInAt = checkIn.dueAt
                        app.appScope.launch { app.moMemories.add(checkIn) }
                    }) { Text(if (minutes < 60) "$minutes min" else "${minutes / 60} h") }
                }
            }
            TextButton(onClick = onOpenMemories) { Text("What Mo remembers") }
        }
    }
}

private val CHECK_IN_MINUTES = listOf(30L, 60L)

private const val SNOOZE_MS = 60 * 60_000L

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
