package io.github.amandhakar.mindscroll.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.amandhakar.mindscroll.app
import io.github.amandhakar.mindscroll.data.Settings
import io.github.amandhakar.mindscroll.mascot.Mood

@Composable
fun SettingsScreen(onBack: () -> Unit, onOpenApps: () -> Unit, onOpenMemories: () -> Unit) {
    val context = LocalContext.current
    val settings = context.app.settings
    var character by remember { mutableStateOf(settings.mascotCharacter) }
    var palette by remember { mutableStateOf(settings.mascotPalette) }
    var cameraPatch by remember { mutableStateOf(settings.cameraPatch) }
    var bubble by remember { mutableStateOf(settings.bubble) }
    var mascotSize by remember { mutableStateOf(settings.mascotSize) }
    var motionStyle by remember { mutableStateOf(settings.motionStyle) }
    var milestones by remember { mutableStateOf(settings.milestoneReactions) }
    var liquidIsland by remember { mutableStateOf(settings.liquidIsland) }
    var liquidEvery by remember { mutableStateOf(settings.liquidEvery) }

    Scaffold(
        topBar = {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onBack) { Text("Back") }
                Text("Settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionHeader("Character and colour")
            Panel {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        MoFace(Mood.CHILL, Modifier.size(104.dp))
                    }
                    MoLookPicker(
                        character = character,
                        palette = palette,
                        onCharacter = {
                            character = it
                            settings.chooseCharacter(it)
                        },
                        onPalette = {
                            palette = it
                            settings.mascotPalette = it
                        },
                    )
                    Text(
                        "The app icon changes to match your character and colour when you leave the app. Your launcher may take a few seconds to show it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            SectionHeader("Camera island")
            IslandSettings(settings, onOpenApps)

            SectionHeader("Where Mo appears")
            Panel {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    PreferenceSwitch(
                        "Around the camera",
                        "Show Mo and the selected information while a tracked app is open.",
                        cameraPatch,
                    ) {
                        cameraPatch = it
                        settings.cameraPatch = it
                    }
                    PreferenceSwitch(
                        "Floating bubble",
                        "Keep a draggable Mo bubble visible over other apps.",
                        bubble,
                    ) {
                        bubble = it
                        settings.bubble = it
                    }
                }
            }
            SectionHeader("Mo's personality")
            Panel {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Mascot size", style = MaterialTheme.typography.bodyLarge)
                    InfoChoice("Compact", mascotSize == Settings.MascotSize.COMPACT) {
                        mascotSize = Settings.MascotSize.COMPACT
                        settings.mascotSize = mascotSize
                    }
                    InfoChoice("Standard", mascotSize == Settings.MascotSize.STANDARD) {
                        mascotSize = Settings.MascotSize.STANDARD
                        settings.mascotSize = mascotSize
                    }
                    InfoChoice("Large", mascotSize == Settings.MascotSize.LARGE) {
                        mascotSize = Settings.MascotSize.LARGE
                        settings.mascotSize = mascotSize
                    }
                    Text("Animation style", style = MaterialTheme.typography.bodyLarge)
                    InfoChoice("Calm · soft reactions, no bounce, no idle sway", motionStyle == Settings.MotionStyle.CALM) {
                        motionStyle = Settings.MotionStyle.CALM
                        settings.motionStyle = motionStyle
                    }
                    InfoChoice("Balanced · a quick pop with one gentle bounce", motionStyle == Settings.MotionStyle.BALANCED) {
                        motionStyle = Settings.MotionStyle.BALANCED
                        settings.motionStyle = motionStyle
                    }
                    InfoChoice("Playful · springy, bigger reactions and quicker gestures", motionStyle == Settings.MotionStyle.PLAYFUL) {
                        motionStyle = Settings.MotionStyle.PLAYFUL
                        settings.motionStyle = motionStyle
                    }
                    PreferenceSwitch(
                        title = "Milestone reactions",
                        subtitle = "Let Mo celebrate reel-count milestones with occasional speech bubbles.",
                        checked = milestones,
                    ) {
                        milestones = it
                        settings.milestoneReactions = it
                    }
                }
            }
            SectionHeader("Tracking")
            TrackedAppsCard(onOpenApps)
            SectionHeader("Mo")
            MoCard(onOpenMemories)
            SectionHeader("Experiments")
            Panel {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    PreferenceSwitch(
                        title = "Liquid island",
                        subtitle = "Mo comes out of the island in a drop of liquid, roams the whole screen along a random " +
                            "route, shakes its body and says how it feels, then goes back in. Also when you tap Mo. " +
                            "Experimental: it may look rough. The liquid stretch needs Android 12 or newer.",
                        checked = liquidIsland,
                    ) {
                        liquidIsland = it
                        settings.liquidIsland = it
                    }
                    Text("How often", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 8.dp))
                    for ((option, label) in listOf(
                        Settings.LiquidEvery.QUARTER_HOUR to "Every 15 minutes",
                        Settings.LiquidEvery.HALF_HOUR to "Every 30 minutes",
                        Settings.LiquidEvery.HOUR to "Every hour",
                        Settings.LiquidEvery.TWO_HOURS to "Every 2 hours",
                        Settings.LiquidEvery.FOUR_HOURS to "Every 4 hours",
                    )) {
                        ChoiceRow(
                            label = label,
                            selected = liquidEvery == option,
                            subtitle = if (option == Settings.LiquidEvery.HOUR) "Default. At most this often, while a tracked app is open." else null,
                            enabled = liquidIsland,
                        ) {
                            liquidEvery = option
                            settings.liquidEvery = option
                        }
                    }
                }
            }
            SectionHeader("Privacy")
            DiagnosticsCard()
            Text(
                "The notification and home-screen widget continue showing today's count.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun InfoChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun PreferenceSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}
