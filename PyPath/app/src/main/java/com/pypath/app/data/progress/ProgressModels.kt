package com.pypath.app.data.progress

import com.pypath.app.data.model.StepType
import kotlinx.serialization.Serializable

/**
 * Everything the app remembers about the learner. Stored locally as JSON.
 * Add new fields with default values so older saved data keeps decoding.
 *
 * Schema history:
 *  1 - onboarding, current sub-level, per-sub-level steps and quiz results
 *  2 - adds [earnedBadges] and [SubLevelProgress.project]. Migration: badges for levels that were
 *      already complete are granted silently (see [ProgressUpdates.migrate]).
 *  (still 2) - adds [completedTasks]. No migration needed: older saved data decodes with an
 *      empty set, so the schema number is unchanged.
 *  3 - Batch 3: adds [xpAwards]. Migration: one-time XP for everything already completed (quizzes
 *      passed, levels, projects, practical tasks) is granted once, without counting towards the
 *      daily goal (see [ProgressUpdates.migrate]). Per-question history lives in the separate
 *      activity store (data/activity) and starts empty.
 */
@Serializable
data class UserProgress(
    val schemaVersion: Int = CURRENT_SCHEMA,
    val onboardingCompleted: Boolean = false,
    /** Sub-level the learner is currently working on (used by "Continue Learning"). */
    val currentSubLevelId: String? = null,
    val subLevels: Map<String, SubLevelProgress> = emptyMap(),
    /** Level ids whose completion badge has been earned (and its celebration shown). */
    val earnedBadges: Set<String> = emptySet(),
    /** Ids of Practical tasks whose automatic tests all passed (via the Check button). */
    val completedTasks: Set<String> = emptySet(),
    /**
     * One-time XP already granted, keyed by event (e.g. "quiz:l1s2", "level:l1", see XpRules).
     * A key is added once and never removed, so no event can pay XP twice.
     */
    val xpAwards: Map<String, Int> = emptyMap(),
) {
    fun of(subLevelId: String): SubLevelProgress = subLevels[subLevelId] ?: SubLevelProgress()

    companion object {
        const val CURRENT_SCHEMA = 3
    }
}

@Serializable
data class SubLevelProgress(
    val completedSteps: Set<StepType> = emptySet(),
    val quiz: QuizResult? = null,
    val completed: Boolean = false,
    val completedAt: Long? = null,
    val lastOpenedAt: Long? = null,
    /** Only for project sub-levels. */
    val project: ProjectProgress? = null,
)

@Serializable
data class QuizResult(
    val lastScore: Int,
    val bestScore: Int,
    /** Questions in the last attempt (the denominator of [lastScore]). */
    val total: Int,
    val attempts: Int,
    /** Sticky: once a quiz is passed it stays passed, even if questions are added to it later. */
    val passed: Boolean,
    /**
     * Batch 3 Part 2: questions in the attempt that set [bestScore]. 0 (older saves) means the same
     * as [total]. Needed because appended questions can make a later attempt longer.
     */
    val bestTotal: Int = 0,
) {
    /** Denominator of [bestScore]. */
    val bestOutOf: Int get() = if (bestTotal > 0) bestTotal else total
    val bestPercent: Int get() = if (bestOutOf <= 0) 0 else bestScore * 100 / bestOutOf

    companion object {
        /**
         * The stored result after one more attempt. [passed] stays true once earned, and the best
         * attempt is the one with the best percentage, kept with its own question count.
         */
        fun record(prev: QuizResult?, score: Int, total: Int, passed: Boolean): QuizResult {
            val newPercent = if (total <= 0) 0 else score * 100 / total
            val best = prev == null || newPercent > prev.bestPercent ||
                (newPercent == prev.bestPercent && total >= prev.bestOutOf)
            return QuizResult(
                lastScore = score,
                bestScore = if (best || prev == null) score else prev.bestScore,
                total = total,
                attempts = (prev?.attempts ?: 0) + 1,
                passed = passed || (prev?.passed ?: false),
                bestTotal = if (best || prev == null) total else prev.bestOutOf,
            )
        }
    }
}

/** Resume state for a guided project. */
@Serializable
data class ProjectProgress(
    val briefingSeen: Boolean = false,
    /** Ids of stages answered correctly, in the order they were completed. */
    val completedStages: List<String> = emptyList(),
    /** Wrong answers across all stages ("attempts used"). Never blocks progress. */
    val wrongAttempts: Int = 0,
)
