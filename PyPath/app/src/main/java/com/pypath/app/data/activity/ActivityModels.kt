package com.pypath.app.data.activity

import kotlinx.serialization.Serializable

/**
 * Batch 3: learning activity, kept in its own DataStore file ("pypath_activity") so the main
 * progress JSON stays small. Days are local calendar days stored as epoch days
 * (LocalDate.toEpochDay()), so daylight-saving shifts never change which day an answer belongs to.
 *
 * Nothing here existed before Batch 3: per-question history starts empty on the first launch after
 * the update and fills as the learner answers from then on.
 *
 * Schema history:
 *  1 - questions, day tallies, active/frozen days, streak bookkeeping, review XP.
 *      Batch 3 Part 2 adds [reviewSession] and [DayTally.reviews] with defaults (still schema 1:
 *      older activity JSON decodes unchanged, so no migration step is needed).
 */
@Serializable
data class ActivityData(
    val schemaVersion: Int = CURRENT_SCHEMA,
    /** Per-question history, keyed by question id. */
    val questions: Map<String, QuestionRecord> = emptyMap(),
    /** XP and actions per day for the last [ActivityLimits.DAY_TALLIES] days. */
    val days: Map<Long, DayTally> = emptyMap(),
    /** Days that met the goal (sorted, last [ActivityLimits.ACTIVE_DAYS] days). Each day appears once. */
    val activeDays: List<Long> = emptyList(),
    /** Missed days covered by a streak freeze. They keep the streak going but are not "days practised". */
    val frozenDays: List<Long> = emptyList(),
    /** Longest streak ever reached. Never decreases (even if the clock moves backwards). */
    val longestStreak: Int = 0,
    /** Streak freezes ready to use (0 or 1). */
    val freezes: Int = 0,
    /** The last day a freeze covered, for the "your streak was saved" message. */
    val lastFreezeUsedDay: Long? = null,
    /** Streak milestone (3, 7, 14, 30, 100) waiting to be celebrated, or null. */
    val pendingMilestone: Int? = null,
    /** Part 2: XP from finished review sessions. */
    val reviewXp: Int = 0,
    /** Last day of the latest streak run and its length, so streaks longer than 400 days keep counting. */
    val runEnd: Long? = null,
    val runLength: Int = 0,
    /** Spaced review in progress (or just finished, until its summary is closed). Survives the app being killed. */
    val reviewSession: ReviewSessionState? = null,
) {
    companion object {
        const val CURRENT_SCHEMA = 1
    }
}

/** What the app knows about one question. Leitner fields are used by spaced review (Part 2). */
@Serializable
data class QuestionRecord(
    /** Sub-level the question belonged to when answered (stats fall back to it if content moves). */
    val subLevelId: String = "",
    val attempts: Int = 0,
    val correct: Int = 0,
    val lastCorrect: Boolean = false,
    val lastDay: Long = 0,
    /** Leitner box 1..5; 0 = not in the review pool (never answered wrong). */
    val box: Int = 0,
    /** Day the question is due for review (only when [box] > 0). */
    val dueDay: Long? = null,
    /** XP this question has earned in quizzes (at most once). */
    val xp: Int = 0,
)

@Serializable
data class DayTally(
    val xp: Int = 0,
    /** Lesson pages finished, quiz questions answered and project stages answered. */
    val actions: Int = 0,
    /** Review sessions finished that day (review XP is limited per day). */
    val reviews: Int = 0,
)

/**
 * A review session: up to 10 question ids picked when it started, and how far the learner got.
 * Answers are saved one by one, so after the app is killed the session continues at [position].
 */
@Serializable
data class ReviewSessionState(
    val ids: List<String> = emptyList(),
    /** Index in [ids] of the next question to answer. */
    val position: Int = 0,
    val answered: Int = 0,
    val correct: Int = 0,
    /** Questions that moved up a Leitner box in this session. */
    val movedUp: Int = 0,
    /** Sub-levels the session is limited to ("weakest topics"), empty for a normal due review. */
    val scope: List<String> = emptyList(),
    val startedDay: Long = 0,
    val finished: Boolean = false,
    val xpEarned: Int = 0,
)

object ActivityLimits {
    const val ACTIVE_DAYS = 400
    const val DAY_TALLIES = 30
}
