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
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import com.pypath.app.cloud.CloudSettings
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.coroutines.flow.map

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "pypath_settings")

/** App preferences, kept apart from learning progress so "Reset progress" doesn't touch them. */
interface SettingsRepository {
    val soundEffects: Flow<Boolean>
    suspend fun setSoundEffects(enabled: Boolean)
    /** Name printed on the course certificate (backed up to the learner's account when signed in). */
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

    // ───────────── Final frontend polish (onboarding + Premium offer) ─────────────
    // Default bodies keep any other implementation (previews, tests) compiling unchanged.

    /** Daily learning-time goal in minutes (5, 10, 15 or 30), or null if never chosen. */
    val learningMinutes: Flow<Int?> get() = flowOf(null)
    suspend fun setLearningMinutes(minutes: Int) {}
    /** True from the end of the new onboarding until the first lesson is completed. */
    val firstLessonPending: Flow<Boolean> get() = flowOf(false)
    suspend fun setFirstLessonPending(pending: Boolean) {}
    /** True once the introductory Premium offer (after the first lesson) was shown. */
    val introOfferShown: Flow<Boolean> get() = flowOf(false)
    suspend fun setIntroOfferShown(shown: Boolean) {}

    // ───────────── Cloud sync ─────────────

    /** Preferences that follow a signed-in learner to other phones, with per-field change times. */
    val cloudSettings: Flow<CloudSettings> get() = emptyFlow()
    /** Stores settings received from the cloud (merged by [com.pypath.app.cloud.CloudMerge]). */
    suspend fun applyCloudSettings(settings: CloudSettings) {}
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
        context.settingsStore.edit { it[soundKey] = enabled; stamp(it, CloudSettings.SOUND) }
    }

    private val nameKey = stringPreferencesKey("certificate_name")

    override val certificateName: Flow<String> = context.settingsStore.data.map { it[nameKey] ?: "" }

    override suspend fun setCertificateName(name: String) {
        context.settingsStore.edit { it[nameKey] = name; stamp(it, CloudSettings.NAME) }
    }

    private val goalKey = stringPreferencesKey("daily_goal")

    override val dailyGoal: Flow<DailyGoal> = context.settingsStore.data.map { DailyGoal.fromName(it[goalKey]) }

    override suspend fun setDailyGoal(goal: DailyGoal) {
        context.settingsStore.edit { it[goalKey] = goal.name; stamp(it, CloudSettings.GOAL) }
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

    private val learningMinutesKey = intPreferencesKey("learning_minutes_per_day")
    private val firstLessonPendingKey = booleanPreferencesKey("first_lesson_pending")
    private val introOfferShownKey = booleanPreferencesKey("intro_premium_offer_shown")

    override val learningMinutes: Flow<Int?> = context.settingsStore.data.map { it[learningMinutesKey] }

    override suspend fun setLearningMinutes(minutes: Int) {
        context.settingsStore.edit { it[learningMinutesKey] = minutes; stamp(it, CloudSettings.MINUTES) }
    }

    override val firstLessonPending: Flow<Boolean> = context.settingsStore.data.map { it[firstLessonPendingKey] ?: false }

    override suspend fun setFirstLessonPending(pending: Boolean) {
        context.settingsStore.edit { it[firstLessonPendingKey] = pending }
    }

    override val introOfferShown: Flow<Boolean> = context.settingsStore.data.map { it[introOfferShownKey] ?: false }

    override suspend fun setIntroOfferShown(shown: Boolean) {
        context.settingsStore.edit { it[introOfferShownKey] = shown; stamp(it, CloudSettings.OFFER) }
    }

    // ───────────── Cloud sync ─────────────
    // Each synced setting remembers when it was last changed on this phone, so the newer choice wins
    // per setting when two phones disagree. The study reminder (needs this phone's notification
    // permission) and onboarding hints stay on this phone only.

    private val stampsKey = stringPreferencesKey("cloud_setting_stamps")
    private val stampsSerializer = MapSerializer(String.serializer(), Long.serializer())
    private val json = Json { ignoreUnknownKeys = true }

    private fun stamps(p: Preferences): Map<String, Long> =
        p[stampsKey]?.let { runCatching { json.decodeFromString(stampsSerializer, it) }.getOrNull() }.orEmpty()

    private fun stamp(p: androidx.datastore.preferences.core.MutablePreferences, field: String) {
        p[stampsKey] = json.encodeToString(stampsSerializer, stamps(p) + (field to System.currentTimeMillis()))
    }

    override val cloudSettings: Flow<CloudSettings> = context.settingsStore.data.map {
        CloudSettings(
            soundEffects = it[soundKey] ?: true,
            certificateName = it[nameKey] ?: "",
            dailyGoal = it[goalKey],
            learningMinutes = it[learningMinutesKey],
            introOfferShown = it[introOfferShownKey] ?: false,
            stamps = stamps(it),
        )
    }

    override suspend fun applyCloudSettings(settings: CloudSettings) {
        context.settingsStore.edit {
            it[soundKey] = settings.soundEffects
            it[nameKey] = settings.certificateName
            if (settings.dailyGoal != null) it[goalKey] = DailyGoal.fromName(settings.dailyGoal).name
            if (settings.learningMinutes != null) it[learningMinutesKey] = settings.learningMinutes else it.remove(learningMinutesKey)
            it[introOfferShownKey] = settings.introOfferShown
            it[stampsKey] = json.encodeToString(stampsSerializer, settings.stamps)
        }
    }
}
