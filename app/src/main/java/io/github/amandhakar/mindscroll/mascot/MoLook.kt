package io.github.amandhakar.mindscroll.mascot

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Who lives in the camera notch. Every character wears Mo's face (so every mood, blink and yawn
 * works on all of them); only the silhouette and the piece on top differ.
 */
enum class MoCharacter(
    val title: String,
    val blurb: String,
    /** How far the face sits lower than Mo's, as a fraction of the square (Bun's ears take room above). */
    val faceDrop: Float,
    /** Highest point at rest, as a fraction of the square; the startle hop uses it to keep clear of the edge. */
    val top: Float,
) {
    MO("Mo", "The original. A soft blob with a scroll curl.", 0f, 0.0375f),
    KIT("Kit", "A cat. Ears perk up when something new shows up.", 0f, 0.045f),
    BUN("Bun", "A bunny, with long ears.", 0.04f, 0.01f),
    NIMBUS("Nimbus", "A cloud.", 0f, 0.10f),
    SPROUT("Sprout", "A seedling, with two little leaves.", 0f, 0.01f),
    BOO("Boo", "A ghost. Perfect for living inside a hole.", 0f, 0.14f),
}

/** A full set of body colours, one per [Mood], so a mood reads by colour as well as by face. */
enum class MoPalette(val title: String, private val colors: LongArray) {
    // Same order as [Mood]: chill, curious, wobbly, dizzy, sleepy.
    CLASSIC("Classic", LongArray(0)),
    PEACH("Peach", longArrayOf(0xFFFFC9A8, 0xFFFFB3A0, 0xFFFFDF8F, 0xFFFF8F7A, 0xFFE3A8D8)),
    BERRY("Berry", longArrayOf(0xFFFF9FD0, 0xFFD3A8FF, 0xFFFFC2E3, 0xFFFF6F9F, 0xFF9A86E0)),
    OCEAN("Ocean", longArrayOf(0xFF6FE8DE, 0xFF72BFFF, 0xFFA6ECFF, 0xFF6A92FF, 0xFF9AA4F0)),
    MONO("Mono", longArrayOf(0xFFD9D7E8, 0xFFC6C4DA, 0xFFB4B1CB, 0xFFA09DB9, 0xFF8C89A8));

    /** The body colour for [mood]. Classic is the colour [Mood] itself carries. */
    fun color(mood: Mood): Int = if (colors.isEmpty()) mood.color.toInt() else colors[mood.ordinal].toInt()
}

/**
 * The character and palette in use right now. Read wherever Mo is drawn. They are Compose state,
 * so a Compose canvas that reads them redraws by itself when the user picks another; the overlay
 * views are invalidated by the overlay.
 */
object MoLook {
    var character: MoCharacter by mutableStateOf(MoCharacter.MO)
    var palette: MoPalette by mutableStateOf(MoPalette.CLASSIC)

    /** The body colour [mood] has in the chosen palette. */
    fun color(mood: Mood): Int = palette.color(mood)
}
