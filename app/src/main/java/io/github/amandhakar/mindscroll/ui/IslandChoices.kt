package io.github.amandhakar.mindscroll.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.amandhakar.mindscroll.data.Settings
import io.github.amandhakar.mindscroll.data.Settings.AppInfo
import io.github.amandhakar.mindscroll.data.Settings.TimerReveal
import io.github.amandhakar.mindscroll.mascot.MoLook
import io.github.amandhakar.mindscroll.mascot.Mood
import io.github.amandhakar.mindscroll.tracker.TrackedApps

private val InfoLabels = listOf(
    AppInfo.TIMER to "Timer",
    AppInfo.COUNTER to "Counter",
    AppInfo.BOTH to "Both",
    AppInfo.NOTHING to "Nothing",
)

/**
 * Timer, Counter, Both or Nothing for one app. Counter and Both need reel counting in that app,
 * so without it they are greyed out. [selected] is what the app really shows.
 */
@Composable
fun InfoSegments(
    selected: AppInfo,
    countsReels: Boolean,
    onChoose: (AppInfo) -> Unit,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val line = MaterialTheme.colorScheme.outlineVariant
    Row(
        modifier
            .fillMaxWidth()
            .height(40.dp)
            .border(1.dp, line, RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp)),
    ) {
        for ((info, label) in InfoLabels) {
            val needsReels = (info == AppInfo.COUNTER || info == AppInfo.BOTH) && !countsReels
            val on = info == selected
            Box(
                Modifier
                    .weight(1f)
                    .height(40.dp)
                    .background(if (on) primary else Color.Transparent, RoundedCornerShape(12.dp))
                    .alpha(if (needsReels) 0.38f else 1f)
                    .selectable(selected = on, enabled = !needsReels, role = Role.RadioButton) { onChoose(info) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                    color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/** A radio row with an optional line under it, greyed out and inert when not [enabled]. */
@Composable
fun ChoiceRow(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.5f)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled, modifier = Modifier.padding(12.dp))
        Column(Modifier.padding(vertical = 6.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** The island as it looks next to the camera: Mo, the camera hole, and [text] (or nothing). */
@Composable
fun IslandPill(text: String?, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Row(
            Modifier
                .background(Color.Black, RoundedCornerShape(50))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            MoSticker(MoLook.character, MoLook.palette, Modifier.size(28.dp), Mood.CHILL)
            Box(Modifier.size(14.dp).background(Color(0xFF0A0B10), CircleShape).border(1.5.dp, Color(0xFF2A2A33), CircleShape))
            if (text != null) {
                Text(text, color = Color.White, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 6.dp))
            } else {
                Box(Modifier.size(28.dp))
            }
        }
    }
}

/**
 * Settings → Camera island: what is chosen per app (in Apps to track), how often the timer
 * shows, and how soon the island hides after leaving an app.
 */
@Composable
fun IslandSettings(settings: Settings, onOpenApps: () -> Unit) {
    val tracked by TrackedApps.packages.collectAsStateWithLifecycle()
    // Read so the summary follows the reel choice; countsReels answers per app.
    val reelChoice by TrackedApps.reelPackages.collectAsStateWithLifecycle()
    val choices = remember { settings.infoChoices() }
    var timerOnly by remember { mutableStateOf(settings.timerReveal) }
    var counterTimer by remember { mutableStateOf(settings.counterTimerEvery) }
    var leave by remember { mutableStateOf(settings.leaveDelay) }

    val shown = remember(tracked, reelChoice) {
        tracked.map { choices.choiceFor(it, TrackedApps.countsReels(it)) }
    }
    val bothApps = shown.count { it == AppInfo.BOTH }
    val timerApps = shown.count { it == AppInfo.TIMER }
    val counterApps = shown.count { it == AppInfo.COUNTER }
    val nothingApps = shown.count { it == AppInfo.NOTHING }

    Panel {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("What it shows", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            fun line(count: Int, one: String, many: String) =
                if (count == 0) null else "$count ${if (count == 1) "app $one" else "apps $many"}"
            val lines = listOfNotNull(
                line(bothApps, "shows the counter, with the timer now and then", "show the counter, with the timer now and then"),
                line(counterApps, "shows the reel counter", "show the reel counter"),
                line(timerApps, "shows the session timer", "show the session timer"),
                line(nothingApps, "shows nothing but Mo", "show nothing but Mo"),
            )
            if (lines.isEmpty()) Text("No tracked apps yet.", style = MaterialTheme.typography.bodyMedium)
            lines.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
            Text(
                "Chosen for each app in Apps to track.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onOpenApps) { Text("Choose per app") }
        }
    }

    Panel {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Apps with counter and timer", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                if (bothApps > 0) "The island keeps showing the reel counter. The session timer shows for 5 seconds every:"
                else "Turned off: no app shows counter and timer. Pick Both for an app to use this.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            IslandPill("42", Modifier.padding(vertical = 8.dp))
            ChoiceRow("5 minutes", counterTimer == TimerReveal.EVERY_5_MIN, enabled = bothApps > 0) {
                counterTimer = TimerReveal.EVERY_5_MIN
                settings.counterTimerEvery = counterTimer
            }
            ChoiceRow("10 minutes", counterTimer == TimerReveal.EVERY_10_MIN, enabled = bothApps > 0) {
                counterTimer = TimerReveal.EVERY_10_MIN
                settings.counterTimerEvery = counterTimer
            }
        }
    }

    Panel {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Apps with the timer only", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                if (timerApps > 0) "How often the session timer is shown:"
                else "Turned off: no app shows the timer only. Pick Timer for an app to use this.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            IslandPill("12:30", Modifier.padding(vertical = 8.dp))
            ChoiceRow("Always on", timerOnly == TimerReveal.ALWAYS, enabled = timerApps > 0) {
                timerOnly = TimerReveal.ALWAYS
                settings.timerReveal = timerOnly
            }
            ChoiceRow("Every 5 minutes", timerOnly == TimerReveal.EVERY_5_MIN, subtitle = "Hidden in between. Up for 5 seconds each time.", enabled = timerApps > 0) {
                timerOnly = TimerReveal.EVERY_5_MIN
                settings.timerReveal = timerOnly
            }
            ChoiceRow("Every 10 minutes", timerOnly == TimerReveal.EVERY_10_MIN, subtitle = "Hidden in between. Up for 5 seconds each time.", enabled = timerApps > 0) {
                timerOnly = TimerReveal.EVERY_10_MIN
                settings.timerReveal = timerOnly
            }
        }
    }

    Panel {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("When you leave an app", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "After you switch away from a tracked app, Mo says goodbye and the island hides after:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ChoiceRow("3 seconds", leave == Settings.LeaveDelay.THREE_SECONDS, subtitle = "About as quick as it was before.") {
                leave = Settings.LeaveDelay.THREE_SECONDS
                settings.leaveDelay = leave
            }
            ChoiceRow("5 seconds", leave == Settings.LeaveDelay.FIVE_SECONDS, subtitle = "Waits a little longer, so a quick peek at a notification doesn't end your session.") {
                leave = Settings.LeaveDelay.FIVE_SECONDS
                settings.leaveDelay = leave
            }
        }
    }
}
