package io.github.amandhakar.mindscroll.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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

@Composable
fun HomeScreen(
    permissions: PermissionState,
    onOpenSetup: () -> Unit,
    onOpenLog: () -> Unit,
    onOpenApps: () -> Unit,
    onOpenMemories: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.app
    val tracker = TrackerRegistry.primary

    val total by remember { app.metrics.observeTodayTotal(tracker.id) }.collectAsStateWithLifecycle(0L)
    val byItem by remember { app.metrics.observeTodayByItem(tracker.id) }.collectAsStateWithLifecycle(emptyList())
    val week by remember { app.metrics.observeDailyTotals(tracker.id, 7) }.collectAsStateWithLifecycle(emptyList())
    val timeByItem by remember { app.metrics.observeTodayByItem(TrackerRegistry.screenTime.id) }
        .collectAsStateWithLifecycle(emptyList())
    val apps = AppStat.merge(byItem, timeByItem, tracker::itemLabel)
    val mood by remember { app.metrics.observeMood(tracker.id) }.collectAsStateWithLifecycle(Mood.CHILL)
    var memory by remember { mutableStateOf<MemoryFacts?>(null) }
    LaunchedEffect(total) {
        memory = runCatching { app.metrics.memoryFacts(tracker.id, tracker::itemLabel) }.getOrNull()
    }

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (!permissions.accessibility) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Counting is off", fontWeight = FontWeight.SemiBold)
                        Text("Mindscroll's accessibility service is turned off, so new reels aren't counted.")
                        FilledTonalButton(onClick = onOpenSetup) { Text("Turn it back on") }
                    }
                }
            }

            Text(
                Mo.greeting(LocalTime.now().hour, Days.today()),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            MoHeader(total, mood)

            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(total.toString(), fontSize = 88.sp, fontWeight = FontWeight.Bold)
                Text("${tracker.title.lowercase()} today", style = MaterialTheme.typography.titleMedium)
            }

            if (apps.isNotEmpty()) {
                Card {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth()) {
                            Text("Today", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${Durations.format(apps.sumOf { it.timeMs })} in apps",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        apps.forEach {
                            Row(Modifier.fillMaxWidth()) {
                                Text(it.label, Modifier.weight(1f))
                                Text("${it.reels} reels", fontWeight = FontWeight.SemiBold)
                                Text(
                                    "  ·  ${Durations.format(it.timeMs)}",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            memory?.let { MemoryCard(MoMemory.insights(it)) }
            WeekCard(week)
            TrackedAppsCard(onOpenApps)
            DisplayCard()
            MoCard(onOpenMemories)

            Row(Modifier.align(Alignment.CenterHorizontally)) {
                TextButton(onClick = onOpenSetup) { Text("Permissions and setup") }
                TextButton(onClick = onOpenLog) { Text("Count looks wrong?") }
            }
        }
    }
}

@Composable
private fun MoHeader(total: Long, mood: Mood) {
    val rested = Mo.isRested(total, mood)
    val accent = Color(mood.color)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MoFace(mood, Modifier.size(88.dp), reactTo = total)
        Card(
            shape = RoundedCornerShape(topStart = 4.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 20.dp),
            colors = CardDefaults.cardColors(containerColor = accent.copy(alpha = 0.18f)),
            modifier = Modifier.weight(1f),
        ) {
            Column(Modifier.padding(14.dp)) {
                Text(
                    "${Mo.NAME} · ${mood.title}".uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(Mo.line(total, LocalTime.now().hour, mood, rested), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun MemoryCard(insights: List<String>) {
    if (insights.isEmpty()) return
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${Mo.NAME} remembers", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            insights.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

@Composable
private fun WeekCard(week: List<DayTotal>) {
    if (week.isEmpty()) return
    val max = week.maxOf { it.total }.coerceAtLeast(1)
    val today = LocalDate.now().toEpochDay()
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Last 7 days", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(
                Modifier.fillMaxWidth().height(120.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                week.forEach { day ->
                    Column(
                        Modifier.weight(1f).fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                    ) {
                        Text(day.total.toString(), style = MaterialTheme.typography.labelSmall)
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(0.75f * day.total / max + 0.02f)
                                .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                .background(
                                    if (day.day == today) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                                    },
                                ),
                        )
                        Text(
                            LocalDate.ofEpochDay(day.day).dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackedAppsCard(onOpenApps: () -> Unit) {
    val tracked by TrackedApps.packages.collectAsStateWithLifecycle()
    val names = tracked.map(TrackedApps::label).sorted()
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
private fun DisplayCard() {
    val settings = LocalContext.current.app.settings
    var cameraPatch by remember { mutableStateOf(settings.cameraPatch) }
    var bubble by remember { mutableStateOf(settings.bubble) }
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("On screen", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            ToggleRow("Camera patch", "Mo and today's count around the front camera. Tap it for details.", cameraPatch) {
                cameraPatch = it
                settings.cameraPatch = it
            }
            ToggleRow("Floating bubble", "Mo with a count badge. Drag it anywhere, tap for details.", bubble) {
                bubble = it
                settings.bubble = it
            }
            Text(
                "The notification and home screen widget always show the count.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** How Mo behaves: when to be quiet, bringing things up, check-ins, and what Mo remembers. */
@Composable
private fun MoCard(onOpenMemories: () -> Unit) {
    val app = LocalContext.current.app
    val store = app.moPrefs
    val policy = remember { MoPolicy(store) }
    var quietAtNight by remember { mutableStateOf(store.profile.quietHours != null) }
    var initiatives by remember { mutableStateOf(store.profile.initiatives) }
    var snoozedUntil by remember { mutableStateOf(policy.snoozedUntil(System.currentTimeMillis())) }
    var checkInAt by remember { mutableStateOf<Long?>(null) }
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
