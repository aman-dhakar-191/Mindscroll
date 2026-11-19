package io.github.amandhakar.mindscroll.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.amandhakar.mindscroll.app
import io.github.amandhakar.mindscroll.mascot.Confidence
import io.github.amandhakar.mindscroll.mascot.Memory
import io.github.amandhakar.mindscroll.mascot.MemorySource
import io.github.amandhakar.mindscroll.mascot.MemoryType
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.launch

/**
 * Everything Mo remembers, and how Mo knows it, with a way to keep or forget each one. It
 * all stays on the phone.
 */
@Composable
fun MoMemoryScreen(onBack: () -> Unit) {
    val app = LocalContext.current.app
    val store = app.moMemories
    val remembered by remember { store.observe() }.collectAsStateWithLifecycle(emptyList())
    val now = System.currentTimeMillis()
    val memories = remembered.filter { it.isLive(now) }

    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.safeDrawingPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "What Mo remembers",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onBack) { Text("Done") }
            }
            Text(
                "Mo notices patterns in your counts, never what's in the reels, and forgets them " +
                    "after a week unless you keep them. It all stays on this phone.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (memories.isEmpty()) {
                Text("Nothing yet. Mo is still getting to know you.", Modifier.padding(vertical = 16.dp))
            }
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(memories, key = { it.key }) { memory ->
                    MemoryRow(
                        memory,
                        onKeep = { app.appScope.launch { store.keep(memory.key) } },
                        onForget = { app.appScope.launch { store.forget(memory.key, System.currentTimeMillis()) } },
                    )
                }
            }
            if (memories.isNotEmpty()) {
                OutlinedButton(
                    onClick = { app.appScope.launch { store.forgetAll(System.currentTimeMillis()) } },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Forget everything") }
            }
        }
    }
}

@Composable
private fun MemoryRow(memory: Memory, onKeep: () -> Unit, onForget: () -> Unit) {
    Card {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(memory.content, fontWeight = FontWeight.SemiBold)
            Text(
                howMoKnows(memory),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                val canKeep = memory.source == MemorySource.NOTICED && !memory.userApproved
                if (canKeep) TextButton(onClick = onKeep) { Text("Keep") }
                TextButton(onClick = onForget) { Text(if (memory.type == MemoryType.CHECK_IN) "Cancel" else "Forget") }
            }
        }
    }
}

private fun howMoKnows(memory: Memory): String = when {
    memory.type == MemoryType.CHECK_IN -> "You asked. Due at ${clockTime(memory.dueAt ?: 0)}"
    memory.userApproved -> "I noticed this, and you said to keep it"
    memory.source == MemorySource.TOLD_ME -> "You told me"
    else -> "I noticed this, " + when (memory.confidence) {
        Confidence.HIGH -> "pretty sure"
        Confidence.MEDIUM -> "I think"
        Confidence.LOW -> "just a hunch so far"
    } + ". I'll forget it in a week unless you keep it."
}

/** [ms] as a local time of day, e.g. "3:45 PM". */
internal fun clockTime(ms: Long): String =
    Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalTime()
        .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
