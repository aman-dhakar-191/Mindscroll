package io.github.amandhakar.mindscroll.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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

@Composable
fun HomeScreen(
    permissions: PermissionState,
    onOpenSetup: () -> Unit,
    onOpenLog: () -> Unit,
    onOpenSettings: () -> Unit,
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
        Column(Modifier.safeDrawingPadding()) {
            HomeTopBar(onOpenSetup = onOpenSetup, onOpenLog = onOpenLog, onOpenSettings = onOpenSettings)
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (!permissions.accessibility) {
                    Card(
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    ) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Counting is off", fontWeight = FontWeight.SemiBold)
                            Text("Mindscroll's accessibility service is turned off, so new reels aren't counted.")
                            FilledTonalButton(onClick = onOpenSetup) { Text("Turn it back on") }
                        }
                    }
                }

                Hero(total, mood, tracker.title.lowercase())

                if (apps.isNotEmpty()) {
                    val maxReels = apps.maxOf { it.reels }.toFloat().coerceAtLeast(1f)
                    Panel {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("Today", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${Durations.format(apps.sumOf { it.timeMs })} in apps",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            apps.forEach { AppRow(it, it.reels.toFloat() / maxReels) }
                        }
                    }
                }

                memory?.let { MemoryCard(MoMemory.insights(it)) }
                WeekCard(week)
            }
        }
    }
}

/**
 * The app name on the left; the rarely needed things (setup, "count looks wrong?", settings) in
 * an overflow menu on the right, where people look for them.
 */
@Composable
private fun HomeTopBar(onOpenSetup: () -> Unit, onOpenLog: () -> Unit, onOpenSettings: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    val dots = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Mindscroll",
            Modifier.weight(1f),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Box {
            IconButton(
                onClick = { open = true },
                modifier = Modifier.semantics { contentDescription = "More options" },
            ) {
                // Three dots, drawn: no icon library needed, and it follows the theme colour.
                Canvas(Modifier.size(20.dp)) {
                    repeat(3) { i ->
                        drawCircle(dots, radius = 2.2.dp.toPx(), center = Offset(size.width / 2, size.height * (0.15f + 0.35f * i)))
                    }
                }
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                DropdownMenuItem(text = { Text("Settings") }, onClick = { open = false; onOpenSettings() })
                DropdownMenuItem(text = { Text("Permissions and setup") }, onClick = { open = false; onOpenSetup() })
                DropdownMenuItem(text = { Text("Count looks wrong?") }, onClick = { open = false; onOpenLog() })
            }
        }
    }
}

@Composable
private fun Hero(total: Long, mood: Mood, unit: String) {
    val rested = Mo.isRested(total, mood)
    val accent = Color(mood.color)
    val style = LocalContext.current.app.settings.motionStyle
    Box(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.30f), accent.copy(alpha = 0.06f))))
            .padding(20.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text(
                Mo.greeting(LocalTime.now().hour, Days.today()),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MoFace(mood, Modifier.size(88.dp), reactTo = total, profile = style.profile)
                Surface(
                    shape = RoundedCornerShape(topStart = 4.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 20.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    modifier = Modifier.weight(1f),
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(accent))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "${Mo.NAME} · ${mood.title}".uppercase(),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(Mo.line(total, LocalTime.now().hour, mood, rested), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    total.toString(),
                    fontSize = 72.sp,
                    lineHeight = 72.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-1).sp,
                )
                Text(
                    "$unit today",
                    Modifier.padding(start = 10.dp, bottom = 12.dp),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** One app: its name and numbers, over a bar showing its share of the busiest app's reels. */
@Composable
private fun AppRow(stat: AppStat, share: Float) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stat.label,
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text("${stat.reels} reels", fontWeight = FontWeight.SemiBold)
            Text(
                "  ·  ${Durations.format(stat.timeMs)}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(share.coerceIn(0.03f, 1f))
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}

@Composable
private fun MemoryCard(insights: List<String>) {
    if (insights.isEmpty()) return
    Panel {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
    Panel {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                        Text(
                            day.total.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (day.day == today) FontWeight.Bold else FontWeight.Normal,
                            color = if (day.day == today) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(4.dp))
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(0.75f * day.total / max + 0.02f)
                                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 3.dp, bottomEnd = 3.dp))
                                .background(
                                    if (day.day == today) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.32f)
                                    },
                                ),
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            LocalDate.ofEpochDay(day.day).dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (day.day == today) FontWeight.Bold else FontWeight.Normal,
                            color = if (day.day == today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}
