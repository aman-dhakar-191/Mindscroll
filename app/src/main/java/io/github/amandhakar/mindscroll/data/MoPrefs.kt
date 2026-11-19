package io.github.amandhakar.mindscroll.data

import android.content.Context
import android.content.SharedPreferences
import io.github.amandhakar.mindscroll.mascot.MoInteractionState
import io.github.amandhakar.mindscroll.mascot.MoProfile
import io.github.amandhakar.mindscroll.mascot.MoStore
import io.github.amandhakar.mindscroll.mascot.QuietHours

/**
 * Mo's preferences and speaking history, kept apart from the reel measurements in Room. A
 * handful of values read on every remark, so preferences: reads come from memory.
 */
class MoPrefs(context: Context) : MoStore {
    private val prefs: SharedPreferences = context.getSharedPreferences("mo", Context.MODE_PRIVATE)

    override var profile: MoProfile
        get() = MoProfile(
            quietHours = if (prefs.contains(KEY_QUIET_START)) {
                QuietHours(prefs.getInt(KEY_QUIET_START, 0), prefs.getInt(KEY_QUIET_END, 0))
            } else {
                null
            },
            initiatives = prefs.getBoolean(KEY_INITIATIVES, true),
        )
        set(value) = prefs.edit().apply {
            val hours = value.quietHours
            if (hours == null) {
                remove(KEY_QUIET_START)
                remove(KEY_QUIET_END)
            } else {
                putInt(KEY_QUIET_START, hours.startMinute)
                putInt(KEY_QUIET_END, hours.endMinute)
            }
            putBoolean(KEY_INITIATIVES, value.initiatives)
        }.apply()

    override var interaction: MoInteractionState
        get() = MoInteractionState(
            spokenAt = prefs.getString(KEY_SPOKEN_AT, "")!!.split(',').mapNotNull(String::toLongOrNull),
            quietUntil = long(KEY_QUIET_UNTIL),
            lastGreetingDay = long(KEY_LAST_GREETING_DAY),
            lastInitiativeAt = long(KEY_LAST_INITIATIVE_AT),
            initiativeDay = long(KEY_INITIATIVE_DAY),
            dailyInitiativeCount = prefs.getInt(KEY_DAILY_INITIATIVES, 0),
            dismissalStreak = prefs.getInt(KEY_DISMISSAL_STREAK, 0),
            initiativesPausedDay = long(KEY_INITIATIVES_PAUSED_DAY),
            lastCallbackDay = long(KEY_LAST_CALLBACK_DAY),
            lastCharacterDay = long(KEY_LAST_CHARACTER_DAY),
            characterBeat = prefs.getInt(KEY_CHARACTER_BEAT, 0),
        )
        set(value) = prefs.edit().apply {
            putString(KEY_SPOKEN_AT, value.spokenAt.joinToString(","))
            putOrRemove(KEY_QUIET_UNTIL, value.quietUntil)
            putOrRemove(KEY_LAST_GREETING_DAY, value.lastGreetingDay)
            putOrRemove(KEY_LAST_INITIATIVE_AT, value.lastInitiativeAt)
            putOrRemove(KEY_INITIATIVE_DAY, value.initiativeDay)
            putInt(KEY_DAILY_INITIATIVES, value.dailyInitiativeCount)
            putInt(KEY_DISMISSAL_STREAK, value.dismissalStreak)
            putOrRemove(KEY_INITIATIVES_PAUSED_DAY, value.initiativesPausedDay)
            putOrRemove(KEY_LAST_CALLBACK_DAY, value.lastCallbackDay)
            putOrRemove(KEY_LAST_CHARACTER_DAY, value.lastCharacterDay)
            putInt(KEY_CHARACTER_BEAT, value.characterBeat)
        }.apply()

    private fun long(key: String): Long? = if (prefs.contains(key)) prefs.getLong(key, 0) else null

    private fun SharedPreferences.Editor.putOrRemove(key: String, value: Long?) {
        if (value == null) remove(key) else putLong(key, value)
    }

    private companion object {
        const val KEY_QUIET_START = "quiet_start_minute"
        const val KEY_QUIET_END = "quiet_end_minute"
        const val KEY_INITIATIVES = "initiatives"
        const val KEY_SPOKEN_AT = "spoken_at"
        const val KEY_QUIET_UNTIL = "quiet_until"
        const val KEY_LAST_GREETING_DAY = "last_greeting_day"
        const val KEY_LAST_INITIATIVE_AT = "last_initiative_at"
        const val KEY_INITIATIVE_DAY = "initiative_day"
        const val KEY_DAILY_INITIATIVES = "daily_initiatives"
        const val KEY_DISMISSAL_STREAK = "dismissal_streak"
        const val KEY_INITIATIVES_PAUSED_DAY = "initiatives_paused_day"
        const val KEY_LAST_CALLBACK_DAY = "last_callback_day"
        const val KEY_LAST_CHARACTER_DAY = "last_character_day"
        const val KEY_CHARACTER_BEAT = "character_beat"
    }
}
