package io.github.amandhakar.mindscroll.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.amandhakar.mindscroll.tracker.DetectionLog

/**
 * What the reel detector saw and decided, newest first. For when the count looks wrong:
 * reproduce it, then copy this and share it.
 */
@Composable
fun DetectionLogScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val entries by DetectionLog.entries.collectAsStateWithLifecycle()

    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.safeDrawingPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Detection log", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "Open Instagram, YouTube or Facebook, do the thing that counted wrong, then come " +
                    "back and copy this. It shows view ids, list positions and sizes, never what's " +
                    "on screen. Kept in memory only.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = {
                    val clipboard = context.getSystemService(ClipboardManager::class.java)
                    clipboard.setPrimaryClip(ClipData.newPlainText("Mindscroll detection log", entries.joinToString("\n")))
                    Toast.makeText(context, "Copied ${entries.size} lines", Toast.LENGTH_SHORT).show()
                }) { Text("Copy") }
                OutlinedButton(onClick = { DetectionLog.clear() }) { Text("Clear") }
                TextButton(onClick = onBack) { Text("Back") }
            }
            if (entries.isEmpty()) {
                Text(
                    "Nothing yet. If this stays empty after using a reel app, the accessibility " +
                        "service isn't running: turn it off and on again in Accessibility settings.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            LazyColumn(Modifier.fillMaxWidth()) {
                items(entries.asReversed()) { line ->
                    Text(
                        line,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = if ("COUNTED" in line) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(vertical = 3.dp),
                    )
                }
            }
        }
    }
}
