package io.github.amandhakar.mindscroll.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.amandhakar.mindscroll.tracker.DetectionLog
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * What the reel detector saw and decided, newest first. For when the count looks wrong:
 * reproduce it, then copy this and share it.
 */
@Composable
fun DetectionLogScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val entries by DetectionLog.entries.collectAsStateWithLifecycle()

    // Saving goes through the system file picker: no storage permission, and the user chooses where.
    val saveToFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) {
            val saved = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(DetectionLog.text().toByteArray()) } != null
            }.getOrDefault(false)
            Toast.makeText(context, if (saved) "Saved the log" else "Couldn't save the log", Toast.LENGTH_SHORT).show()
        }
    }
    val kilobytes = entries.sumOf { it.length + 1 } / 1024

    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.safeDrawingPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Detection log",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                TextButton(onClick = onBack) { Text("Back") }
            }
            Text(
                "Open the app, do the thing that looked wrong, then come back and copy or save this. " +
                    "It shows what Mindscroll decided: reel detection, when app sessions start and end " +
                    "and why, what the camera patch shows, and what Mo does and when. View ids, list " +
                    "positions and sizes, never what's on screen. Kept in memory only.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton(onClick = {
                    val clipboard = context.getSystemService(ClipboardManager::class.java)
                    val copied = runCatching {
                        clipboard.setPrimaryClip(ClipData.newPlainText("Mindscroll detection log", DetectionLog.text()))
                    }.isSuccess
                    // A very long log can be too big for the clipboard; saving a file always works.
                    Toast.makeText(
                        context,
                        if (copied) "Copied ${entries.size} lines" else "Too big to copy: use Save to file",
                        Toast.LENGTH_SHORT,
                    ).show()
                }) { Text("Copy") }
                FilledTonalButton(onClick = {
                    saveToFile.launch("mindscroll-log-${LocalDateTime.now().format(FILE_TIME)}.txt")
                }) { Text("Save to file") }
                OutlinedButton(onClick = { DetectionLog.clear() }) { Text("Clear") }
            }
            if (entries.isNotEmpty()) {
                Text(
                    "${entries.size} lines · $kilobytes KB" + if (kilobytes >= 100) " · big: use Save to file" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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

private val FILE_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")
