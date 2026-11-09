package com.pypath.app.data.progress

import com.pypath.app.data.model.Course
import com.pypath.app.data.model.StepType
import com.pypath.app.domain.Xp
import kotlinx.serialization.json.Json

/**
 * Pure progress transformations. [LocalProgressRepository] applies them inside a DataStore
 * transaction; unit tests call them directly.
 */
object ProgressUpdates {

    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun decode(text: String?): UserProgress =
        text?.let { runCatching { json.decodeFromString(UserProgress.serializer(), it) }.getOrNull() } ?: UserProgress()

    fun encode(p: UserProgress): String = json.encodeToString(UserProgress.serializer(), p)

    fun UserProgress.updateSub(id: String, block: (SubLevelProgress) -> SubLevelProgress) =
        copy(subLevels = subLevels + (id to block(of(id))))

    fun SubLevelProgress.withCompletionCheck(required: List<StepType>, now: Long): SubLevelProgress {
        val done = !completed && completedSteps.containsAll(required)
        return if (done) copy(completed = true, completedAt = now) else this
    }

    fun markBriefingSeen(p: UserProgress, subLevelId: String): UserProgress = p.updateSub(subLevelId) { s ->
        val pr = s.project ?: ProjectProgress()
        s.copy(project = pr.copy(briefingSeen = true))
    }

    fun recordProjectMiss(p: UserProgress, subLevelId: String): UserProgress = p.updateSub(subLevelId) { s ->
        val pr = s.project ?: ProjectProgress(briefingSeen = true)
        s.copy(project = pr.copy(wrongAttempts = pr.wrongAttempts + 1))
    }

    /** Records a correct stage. When every stage is done, the PROJECT step (and the sub-level) completes. */
    fun completeProjectStage(
        p: UserProgress, subLevelId: String, stageId: String, allStageIds: List<String>,
        requiredSteps: List<StepType>, now: Long,
    ): UserProgress = p.updateSub(subLevelId) { s ->
        val pr = s.project ?: ProjectProgress(briefingSeen = true)
        val stages = if (stageId in pr.completedStages) pr.completedStages else pr.completedStages + stageId
        val finished = stages.containsAll(allStageIds)
        s.copy(
            project = pr.copy(briefingSeen = true, completedStages = stages),
            completedSteps = if (finished) s.completedSteps + StepType.PROJECT else s.completedSteps,
        ).withCompletionCheck(requiredSteps, now)
    }

    fun earnBadge(p: UserProgress, levelId: String): UserProgress =
        if (levelId in p.earnedBadges) p else p.copy(earnedBadges = p.earnedBadges + levelId)

    /** Records a Practical task as completed. Only called when every test of the task passed. */
    fun markTaskCompleted(p: UserProgress, taskId: String): UserProgress =
        if (taskId in p.completedTasks) p else p.copy(completedTasks = p.completedTasks + taskId)

    /** Clears course progress, badges, completed tasks and XP but keeps onboarding as seen. */
    fun reset(p: UserProgress): UserProgress = UserProgress(onboardingCompleted = p.onboardingCompleted)

    /**
     * Upgrades saved progress to [UserProgress.CURRENT_SCHEMA], one step at a time.
     *  v1 -> v2: levels that were already complete get their badge silently, so long-time learners
     *            don't get a burst of celebrations.
     *  v2 -> v3: retroactive XP. Every one-time event the saved progress already shows (quiz passed,
     *            level completed, final or level project finished, practical task completed) is
     *            recorded in [UserProgress.xpAwards]. No per-question XP is invented for answers
     *            given before the update, and this XP does not count towards today's daily goal.
     */
    fun migrate(p: UserProgress, course: Course): UserProgress {
        if (p.schemaVersion >= UserProgress.CURRENT_SCHEMA) return p
        var m = p
        if (m.schemaVersion < 2) {
            val done = course.levels
                .filter { lvl -> lvl.available && lvl.subLevels.isNotEmpty() && lvl.subLevels.all { m.of(it.id).completed } }
                .map { it.id }
            m = m.copy(schemaVersion = 2, earnedBadges = m.earnedBadges + done)
        }
        if (m.schemaVersion < 3) {
            m = m.copy(schemaVersion = 3, xpAwards = m.xpAwards + Xp.newEvents(course, m))
        }
        return m.copy(schemaVersion = UserProgress.CURRENT_SCHEMA)
    }

    /**
     * Grants one-time XP for events that happened since the last call (live, e.g. a quiz just passed).
     * Returns the new progress and the XP granted now (0 when nothing new). Does nothing before
     * [migrate] has run, so retroactive XP is never mistaken for XP earned today.
     */
    fun awardXp(p: UserProgress, course: Course): Pair<UserProgress, Int> {
        if (p.schemaVersion < UserProgress.CURRENT_SCHEMA) return p to 0
        val fresh = Xp.newEvents(course, p)
        if (fresh.isEmpty()) return p to 0
        return p.copy(xpAwards = p.xpAwards + fresh) to fresh.values.sum()
    }
}
