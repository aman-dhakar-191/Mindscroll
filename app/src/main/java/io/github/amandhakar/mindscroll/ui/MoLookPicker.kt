package io.github.amandhakar.mindscroll.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.amandhakar.mindscroll.data.Settings
import io.github.amandhakar.mindscroll.mascot.MoCharacter
import io.github.amandhakar.mindscroll.mascot.MoPainter
import io.github.amandhakar.mindscroll.mascot.MoPalette
import io.github.amandhakar.mindscroll.mascot.Mood

/**
 * Saves the choice. The launcher icon follows later, when the app is out of sight
 * (`MainActivity.onStop`): switching it under the user's hands can close the app.
 */
fun Settings.chooseCharacter(character: MoCharacter) {
    mascotCharacter = character
}

/** One still [character] in [palette], at [mood]. Not animated: used for the choices. */
@Composable
fun MoSticker(character: MoCharacter, palette: MoPalette, modifier: Modifier = Modifier, mood: Mood = Mood.CHILL) {
    Canvas(modifier) {
        drawIntoCanvas { canvas ->
            val side = minOf(size.width, size.height)
            MoPainter.draw(
                canvas.nativeCanvas, side, mood,
                left = (size.width - side) / 2, top = (size.height - side) / 2,
                previousMoodColor = palette.color(mood),
                stage = 0.94f,
                character = character,
                palette = palette,
            )
        }
    }
}

/**
 * Pick who lives in the camera notch, and the colours they wear. Used in Settings and in setup.
 * The character is shown in the chosen palette, so a colour choice previews on every tile.
 */
@Composable
fun MoLookPicker(
    character: MoCharacter,
    palette: MoPalette,
    onCharacter: (MoCharacter) -> Unit,
    onPalette: (MoPalette) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Character", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        for (row in MoCharacter.entries.chunked(3)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (option in row) {
                    CharacterTile(option, palette, option == character, Modifier.weight(1f)) { onCharacter(option) }
                }
            }
        }
        Text(character.blurb, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Text("Colour", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        for (row in MoPalette.entries.chunked(3)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (option in row) {
                    PaletteChip(option, option == palette, Modifier.weight(1f)) { onPalette(option) }
                }
                // Keep the last row's chips the same width as the others.
                repeat(3 - row.size) { Box(Modifier.weight(1f)) }
            }
        }
        Text(
            "Each colour set gives every mood its own shade, so you can still tell how Mo feels.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CharacterTile(
    character: MoCharacter,
    palette: MoPalette,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    Surface(
        modifier = modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = if (selected) BorderStroke(2.dp, primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            MoSticker(character, palette, Modifier.fillMaxWidth().aspectRatio(1f))
            Text(
                character.title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) primary else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun PaletteChip(palette: MoPalette, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    Surface(
        modifier = modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = if (selected) BorderStroke(2.dp, primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(horizontalArrangement = Arrangement.spacedBy((-4).dp)) {
                for (mood in Mood.entries) {
                    Box(
                        Modifier
                            .size(16.dp)
                            .background(Color(palette.color(mood)), CircleShape)
                            .border(1.5.dp, MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
                    )
                }
            }
            Text(
                palette.title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}
