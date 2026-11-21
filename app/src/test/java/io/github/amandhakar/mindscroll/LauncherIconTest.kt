package io.github.amandhakar.mindscroll

import io.github.amandhakar.mindscroll.mascot.LauncherIcon
import io.github.amandhakar.mindscroll.mascot.MoCharacter
import io.github.amandhakar.mindscroll.mascot.MoPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The launcher icon is switched by enabling one `activity-alias` by name, so a typo or a missing
 * alias fails silently on a phone. This checks the manifest against the code and the drawables.
 */
class LauncherIconTest {
    private val main = File("src/main")
    private val manifest = File(main, "AndroidManifest.xml").readText()

    private val aliasBlocks = Regex("<activity-alias(.*?)</activity-alias>", RegexOption.DOT_MATCHES_ALL)
        .findAll(manifest).map { it.groupValues[1] }.toList()

    private fun attr(block: String, name: String) =
        Regex("""android:$name="([^"]*)"""").find(block)?.groupValues?.get(1)

    @Test
    fun everyCharacterAndPaletteHasExactlyOneAlias() {
        val expected = MoCharacter.entries.flatMap { c -> MoPalette.entries.map { p -> "." + LauncherIcon.aliasName(c, p) } }
        val actual = aliasBlocks.map { attr(it, "name") }
        assertEquals(expected.sorted(), actual.filterNotNull().sorted())
    }

    @Test
    fun onlyMoInClassicIsOnByDefault() {
        val on = aliasBlocks.filter { attr(it, "enabled") == "true" }.map { attr(it, "name") }
        assertEquals(listOf(".LauncherMoClassic"), on)
    }

    @Test
    fun theActivityItselfIsNotALauncherEntry() {
        val activity = Regex("<activity(?!-alias)(.*?)</activity>|<activity(?!-alias)([^>]*?)/>", RegexOption.DOT_MATCHES_ALL)
            .find(manifest)!!.value
        assertTrue("MainActivity must not be a launcher entry itself", "LAUNCHER" !in activity)
    }

    @Test
    fun everyAliasHasItsIconAndDrawable() {
        for (block in aliasBlocks) {
            val icon = attr(block, "icon")!!.removePrefix("@mipmap/")
            val mipmap = File(main, "res/mipmap-anydpi-v26/$icon.xml")
            assertTrue("missing $mipmap", mipmap.exists())
            val drawable = Regex("@drawable/(\\w+)").find(mipmap.readText())!!.groupValues[1]
            assertTrue("missing drawable $drawable", File(main, "res/drawable/$drawable.xml").exists())
        }
    }

    @Test
    fun theAppIconIsTheDefaultOne() {
        val xml = File(main, "res/mipmap-anydpi-v26/ic_launcher.xml").readText()
        assertTrue("@drawable/ic_launcher_fg_mo_classic" in xml)
    }
}
