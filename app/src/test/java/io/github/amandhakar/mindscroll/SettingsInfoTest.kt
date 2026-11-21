package io.github.amandhakar.mindscroll

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.amandhakar.mindscroll.data.Settings
import io.github.amandhakar.mindscroll.data.Settings.AppInfo
import io.github.amandhakar.mindscroll.data.Settings.LeaveDelay
import io.github.amandhakar.mindscroll.data.Settings.TimerReveal
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Nobody's island changes on update, and the new choices are saved. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class SettingsInfoTest {
    private lateinit var context: Context
    private val ig = "com.instagram.android"
    private val claude = "com.anthropic.claude"

    @Before
    fun clean() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun legacy(mode: String?, autoScroll: Boolean? = null) {
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().apply {
            if (mode != null) putString("info_mode", mode)
            if (autoScroll != null) putBoolean("auto_scroll", autoScroll)
        }.commit()
    }

    @Test
    fun withNothingSavedReelAppsTakeBothAndOthersTheTimer() {
        val choices = Settings(context).infoChoices()
        assertEquals(AppInfo.BOTH, choices.choiceFor(ig, countsReels = true))
        assertEquals(AppInfo.TIMER, choices.choiceFor(claude, countsReels = false))
    }

    @Test
    fun theOldSettingBecomesTheDefaultForReelApps() {
        legacy("COUNTER")
        assertEquals(AppInfo.COUNTER, Settings(context).infoChoices().choiceFor(ig, true))
        legacy("TIMER")
        assertEquals(AppInfo.TIMER, Settings(context).infoChoices().choiceFor(ig, true))
    }

    @Test
    fun counterAndTimerWithAutoScrollOffUsedToBeTheCounterAlone() {
        legacy("BOTH", autoScroll = false)
        assertEquals(AppInfo.COUNTER, Settings(context).infoChoices().choiceFor(ig, true))
    }

    @Test
    fun hideBothStaysHiddenEverywhere() {
        legacy("HIDDEN")
        val choices = Settings(context).infoChoices()
        assertEquals(AppInfo.NOTHING, choices.choiceFor(ig, true))
        assertEquals(AppInfo.NOTHING, choices.choiceFor(claude, false))
    }

    @Test
    fun anAppsOwnChoiceIsSavedAndReplacedNotDuplicated() {
        val settings = Settings(context)
        settings.setAppInfo(claude, AppInfo.NOTHING)
        settings.setAppInfo(ig, AppInfo.COUNTER)
        settings.setAppInfo(claude, AppInfo.TIMER)
        val choices = Settings(context).infoChoices()
        assertEquals(AppInfo.TIMER, choices.choiceFor(claude, false))
        assertEquals(AppInfo.COUNTER, choices.choiceFor(ig, true))
        assertEquals(2, choices.perApp.size)
    }

    @Test
    fun defaultsForTheNewOptionsAreTheOldBehaviour() {
        val settings = Settings(context)
        assertEquals(TimerReveal.ALWAYS, settings.timerReveal)
        assertEquals(TimerReveal.EVERY_5_MIN, settings.counterTimerEvery)
        assertEquals(LeaveDelay.THREE_SECONDS, settings.leaveDelay)
    }

    @Test
    fun theOptionsAreSaved() {
        val settings = Settings(context)
        settings.counterTimerEvery = TimerReveal.EVERY_10_MIN
        settings.leaveDelay = LeaveDelay.FIVE_SECONDS
        val again = Settings(context)
        assertEquals(TimerReveal.EVERY_10_MIN, again.counterTimerEvery)
        assertEquals(LeaveDelay.FIVE_SECONDS, again.leaveDelay)
    }
}
