package com.pypath.app.cloud

import com.pypath.app.data.activity.ActivityData
import com.pypath.app.data.programs.ProgramLibrary
import com.pypath.app.data.progress.UserProgress
import kotlinx.serialization.Serializable

/** Format version of the JSON stored in each `user_cloud_state` column. */
const val CLOUD_FORMAT = 1

/**
 * `progress` column. [resetAt] is when the learner last pressed "Reset progress" (0 = never); the
 * side with the newer reset wins wholesale, so a reset on one phone is not undone by another.
 */
@Serializable
data class ProgressEnvelope(val v: Int = CLOUD_FORMAT, val resetAt: Long = 0, val data: UserProgress = UserProgress())

/** `activity` column (question history, XP per day, streaks). Shares the progress [ProgressEnvelope.resetAt]. */
@Serializable
data class ActivityEnvelope(val v: Int = CLOUD_FORMAT, val resetAt: Long = 0, val data: ActivityData = ActivityData())

/** `programs` column: saved Advanced IDE programs, including deletion markers. */
@Serializable
data class ProgramsEnvelope(val v: Int = CLOUD_FORMAT, val data: ProgramLibrary = ProgramLibrary())

/**
 * Preferences that follow the learner between phones. Device-specific settings (study reminder
 * alarm and its notification permission, onboarding hints) and the Premium developer switch are
 * deliberately NOT synced. [stamps] holds, per field, when it was last changed (0 = never).
 */
@Serializable
data class CloudSettings(
    val soundEffects: Boolean = true,
    val certificateName: String = "",
    val dailyGoal: String? = null,
    val learningMinutes: Int? = null,
    val introOfferShown: Boolean = false,
    val stamps: Map<String, Long> = emptyMap(),
) {
    companion object {
        const val SOUND = "soundEffects"
        const val NAME = "certificateName"
        const val GOAL = "dailyGoal"
        const val MINUTES = "learningMinutes"
        const val OFFER = "introOfferShown"
    }
}

@Serializable
data class SettingsEnvelope(val v: Int = CLOUD_FORMAT, val data: CloudSettings = CloudSettings())

/** Everything that is synced, in one value. */
data class CloudSnapshot(
    val progress: ProgressEnvelope,
    val activity: ActivityEnvelope,
    val programs: ProgramsEnvelope,
    val settings: SettingsEnvelope,
)
