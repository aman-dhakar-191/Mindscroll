package io.github.amandhakar.mindscroll.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
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

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val settings = LocalContext.current.app.settings
    var infoMode by remember { mutableStateOf(settings.infoMode) }
    var autoScroll by remember { mutableStateOf(settings.autoScroll) }
    var cameraPatch by remember { mutableStateOf(settings.cameraPatch) }
    var bubble by remember { mutableStateOf(settings.bubble) }

    BackHandler(onBack = onBack)
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
            Text("Overlay display", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Information shown beside Mo", style = MaterialTheme.typography.bodyLarge)
                    InfoChoice("Session timer only", infoMode == Settings.InfoMode.TIMER) {
                        infoMode = Settings.InfoMode.TIMER
                        settings.infoMode = infoMode
                    }
                    InfoChoice("Reel counter only", infoMode == Settings.InfoMode.COUNTER) {
                        infoMode = Settings.InfoMode.COUNTER
                        settings.infoMode = infoMode
                    }
                    InfoChoice("Timer and counter", infoMode == Settings.InfoMode.BOTH) {
                        infoMode = Settings.InfoMode.BOTH
                        settings.infoMode = infoMode
                    }
                    InfoChoice("Hide both", infoMode == Settings.InfoMode.HIDDEN) {
                        infoMode = Settings.InfoMode.HIDDEN
                        settings.infoMode = infoMode
                    }
                    PreferenceSwitch(
                        title = "Auto-scroll between timer and counter",
                        subtitle = "When enabled, the island alternates between both values.",
                        checked = autoScroll,
                        enabled = infoMode == Settings.InfoMode.BOTH,
                    ) {
                        autoScroll = it
                        settings.autoScroll = it
                    }
                }
            }

            Text("Where Mo appears", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Card {
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
