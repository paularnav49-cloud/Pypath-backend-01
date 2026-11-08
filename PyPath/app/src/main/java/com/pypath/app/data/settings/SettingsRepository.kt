package com.pypath.app.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pypath.app.domain.DailyGoal
import com.pypath.app.domain.Reminders
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "pypath_settings")

/** App preferences, kept apart from learning progress so "Reset progress" doesn't touch them. */
interface SettingsRepository {
    val soundEffects: Flow<Boolean>
    suspend fun setSoundEffects(enabled: Boolean)
    /** Name printed on the course certificate. Kept on the phone only. */
    val certificateName: Flow<String>
    suspend fun setCertificateName(name: String)
    /** Batch 3: daily XP goal (default Regular, 40 XP). A preference, so Reset progress keeps it. */
    val dailyGoal: Flow<DailyGoal>
    suspend fun setDailyGoal(goal: DailyGoal)

    /** Batch 3 Part 3: daily study reminder (off by default) and its time in minutes after midnight. */
    val reminder: Flow<ReminderPrefs>
    suspend fun setReminderEnabled(enabled: Boolean)
    suspend fun setReminderMinutes(minutes: Int)
    /** Remembers the day a reminder was shown, so there is at most one per day. */
    suspend fun markReminderSent(day: Long)
}

/** Daily reminder settings. [lastSentDay] is an epoch day, null when none was ever shown. */
data class ReminderPrefs(
    val enabled: Boolean = false,
    val minutes: Int = Reminders.DEFAULT_MINUTES,
    val lastSentDay: Long? = null,
)

class LocalSettingsRepository(private val context: Context) : SettingsRepository {
    private val soundKey = booleanPreferencesKey("sound_effects")

    override val soundEffects: Flow<Boolean> = context.settingsStore.data.map { it[soundKey] ?: true }

    override suspend fun setSoundEffects(enabled: Boolean) {
        context.settingsStore.edit { it[soundKey] = enabled }
    }

    private val nameKey = stringPreferencesKey("certificate_name")

    override val certificateName: Flow<String> = context.settingsStore.data.map { it[nameKey] ?: "" }

    override suspend fun setCertificateName(name: String) {
        context.settingsStore.edit { it[nameKey] = name }
    }

    private val goalKey = stringPreferencesKey("daily_goal")

    override val dailyGoal: Flow<DailyGoal> = context.settingsStore.data.map { DailyGoal.fromName(it[goalKey]) }

    override suspend fun setDailyGoal(goal: DailyGoal) {
        context.settingsStore.edit { it[goalKey] = goal.name }
    }

    private val reminderOnKey = booleanPreferencesKey("reminder_enabled")
    private val reminderMinutesKey = intPreferencesKey("reminder_minutes")
    private val reminderSentKey = longPreferencesKey("reminder_last_sent_day")

    override val reminder: Flow<ReminderPrefs> = context.settingsStore.data.map {
        ReminderPrefs(
            enabled = it[reminderOnKey] ?: false,
            minutes = Reminders.clampMinutes(it[reminderMinutesKey] ?: Reminders.DEFAULT_MINUTES),
            lastSentDay = it[reminderSentKey],
        )
    }

    override suspend fun setReminderEnabled(enabled: Boolean) {
        context.settingsStore.edit { it[reminderOnKey] = enabled }
    }

    override suspend fun setReminderMinutes(minutes: Int) {
        context.settingsStore.edit { it[reminderMinutesKey] = Reminders.clampMinutes(minutes) }
    }

    override suspend fun markReminderSent(day: Long) {
        context.settingsStore.edit { it[reminderSentKey] = day }
    }
}
