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
import io.github.amandhakar.mindscroll.mascot.Mo
import io.github.amandhakar.mindscroll.mascot.Mood
import io.github.amandhakar.mindscroll.tracker.TrackerRegistry
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun HomeScreen(permissions: PermissionState, onOpenSetup: () -> Unit, onOpenLog: () -> Unit) {
    val context = LocalContext.current
    val app = context.app
    val tracker = TrackerRegistry.primary

    val total by remember { app.metrics.observeTodayTotal(tracker.id) }.collectAsStateWithLifecycle(0L)
    val byItem by remember { app.metrics.observeTodayByItem(tracker.id) }.collectAsStateWithLifecycle(emptyList())
    val week by remember { app.metrics.observeDailyTotals(tracker.id, 7) }.collectAsStateWithLifecycle(emptyList())

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

            MoHeader(total)

            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(total.toString(), fontSize = 88.sp, fontWeight = FontWeight.Bold)
                Text("${tracker.title.lowercase()} today", style = MaterialTheme.typography.titleMedium)
            }

            if (byItem.isNotEmpty()) {
                Card {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        byItem.forEach {
                            Row(Modifier.fillMaxWidth()) {
                                Text(tracker.itemLabel(it.itemKey), Modifier.weight(1f))
                                Text(it.total.toString(), fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            WeekCard(week)
            DisplayCard()

            Row(Modifier.align(Alignment.CenterHorizontally)) {
                TextButton(onClick = onOpenSetup) { Text("Permissions and setup") }
                TextButton(onClick = onOpenLog) { Text("Count looks wrong?") }
            }
        }
    }
}

@Composable
private fun MoHeader(total: Long) {
    val mood = Mood.forCount(total)
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
                Text(Mo.line(total, LocalTime.now().hour), style = MaterialTheme.typography.bodyLarge)
            }
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
