package com.pypath.app.domain

import com.pypath.app.data.model.Course
import com.pypath.app.data.model.Level
import com.pypath.app.data.model.SubLevel
import com.pypath.app.data.progress.SubLevelProgress
import com.pypath.app.data.progress.UserProgress

enum class NodeStatus { LOCKED, AVAILABLE, IN_PROGRESS, COMPLETED, COMING_SOON }

data class SubLevelState(
    val level: Level,
    val subLevel: SubLevel,
    val indexInLevel: Int,
    val status: NodeStatus,
    val progress: SubLevelProgress,
    /** The sub-level the learner must finish before this one unlocks (for messaging). */
    val unlockedBy: SubLevel?,
    /** An optional level project (Level.projects), not part of the main path. */
    val isBonus: Boolean = false,
) {
    val isPlayable get() = status == NodeStatus.AVAILABLE || status == NodeStatus.IN_PROGRESS || status == NodeStatus.COMPLETED
}

data class LevelState(
    val level: Level,
    val status: NodeStatus,
    val subLevels: List<SubLevelState>,
    /** The level's completion badge has been earned (its celebration was shown once). */
    val badgeEarned: Boolean = false,
    /** Optional level projects. They unlock when the level is completed and don't count towards it. */
    val projects: List<SubLevelState> = emptyList(),
) {
    /** The cheat sheet opens once every sub-level of the level is done. */
    val cheatSheetUnlocked get() = status == NodeStatus.COMPLETED && level.cheatSheet != null

    /** Level finished but the badge celebration has not been shown yet. */
    val badgePending get() = status == NodeStatus.COMPLETED && !badgeEarned

    val completedCount get() = subLevels.count { it.status == NodeStatus.COMPLETED }
    val total get() = subLevels.size
    val fraction get() = if (total == 0) 0f else completedCount.toFloat() / total
}

/**
 * Pure, testable progression rules. The single place that decides what is unlocked.
 *
 * Current rule: sub-levels unlock linearly across the whole course — a sub-level is
 * available once the previous sub-level (in course order) is completed. A level is
 * unlocked when its first sub-level is. Future rules (e.g. level-final assessment gates)
 * plug in here without touching the UI.
 */
class CourseSnapshot(val course: Course, val progress: UserProgress) {

    val levels: List<LevelState>
    private val byId: Map<String, SubLevelState>
    val orderedSubLevels: List<SubLevelState>

    init {
        val result = mutableListOf<LevelState>()
        var previousCompleted = true
        var previous: SubLevel? = null
        for (level in course.levels) {
            val subs = level.subLevels.mapIndexed { idx, sub ->
                val p = progress.of(sub.id)
                val status = when {
                    !level.available -> NodeStatus.COMING_SOON
                    p.completed -> NodeStatus.COMPLETED
                    !previousCompleted -> NodeStatus.LOCKED
                    p.completedSteps.isNotEmpty() || p.quiz != null || p.project != null -> NodeStatus.IN_PROGRESS
                    else -> NodeStatus.AVAILABLE
                }
                val state = SubLevelState(level, sub, idx, status, p, previous)
                previousCompleted = level.available && p.completed
                previous = sub
                state
            }
            val levelStatus = when {
                !level.available -> NodeStatus.COMING_SOON
                subs.isNotEmpty() && subs.all { it.status == NodeStatus.COMPLETED } -> NodeStatus.COMPLETED
                subs.any { it.status == NodeStatus.COMPLETED || it.status == NodeStatus.IN_PROGRESS } -> NodeStatus.IN_PROGRESS
                subs.firstOrNull()?.status == NodeStatus.AVAILABLE -> NodeStatus.AVAILABLE
                else -> NodeStatus.LOCKED
            }
            val projects = level.projects.map { pr ->
                val p = progress.of(pr.id)
                val status = when {
                    !level.available -> NodeStatus.COMING_SOON
                    p.completed -> NodeStatus.COMPLETED
                    levelStatus != NodeStatus.COMPLETED -> NodeStatus.LOCKED
                    p.project != null -> NodeStatus.IN_PROGRESS
                    else -> NodeStatus.AVAILABLE
                }
                SubLevelState(level, pr, -1, status, p, unlockedBy = level.subLevels.lastOrNull(), isBonus = true)
            }
            result += LevelState(level, levelStatus, subs, badgeEarned = level.id in progress.earnedBadges, projects = projects)
        }
        levels = result
        orderedSubLevels = result.flatMap { it.subLevels }
        byId = (orderedSubLevels + result.flatMap { it.projects }).associateBy { it.subLevel.id }
    }

    /** Optional level projects, in level order. */
    val bonusProjects: List<SubLevelState> get() = levels.flatMap { it.projects }

    /** Every guided project: each level's project, then the final project (in course order). */
    val allProjects: List<SubLevelState>
        get() = levels.flatMap { l -> l.projects + l.subLevels.filter { it.subLevel.isProject } }

    /**
     * Sub-levels of the core course: every level except the optional advanced extensions (Level.advanced).
     * Batch 1: the certificate keeps meaning "finished Levels 1-7", so adding advanced levels never re-locks it.
     */
    val coreSubLevels: List<SubLevelState> get() = levels.filterNot { it.level.advanced }.flatMap { it.subLevels }

    /** The certificate unlocks when every sub-level of the core course is complete. */
    val certificateUnlocked: Boolean
        get() {
            val playable = coreSubLevels.filter { it.status != NodeStatus.COMING_SOON }
            return playable.isNotEmpty() && playable.all { it.status == NodeStatus.COMPLETED }
        }

    fun subLevel(id: String): SubLevelState? = byId[id]
    fun level(id: String): LevelState? = levels.firstOrNull { it.level.id == id }

    /** Sub-level that "Continue Learning" should open, or null when everything available is done. */
    val continueTarget: SubLevelState?
        get() {
            // Level projects are optional, so "Continue Learning" always follows the main path.
            val saved = progress.currentSubLevelId?.let { byId[it] }?.takeUnless { it.isBonus }
            if (saved != null && saved.isPlayable && saved.status != NodeStatus.COMPLETED) return saved
            return orderedSubLevels.firstOrNull {
                it.status == NodeStatus.AVAILABLE || it.status == NodeStatus.IN_PROGRESS
            }
        }

    /** The level/sub-level shown as "current" on the dashboard. */
    val current: SubLevelState?
        get() = continueTarget ?: orderedSubLevels.lastOrNull { it.status == NodeStatus.COMPLETED }

    fun next(after: String): SubLevelState? {
        val idx = orderedSubLevels.indexOfFirst { it.subLevel.id == after }
        return orderedSubLevels.getOrNull(idx + 1)
    }

    val playableTotal get() = orderedSubLevels.count { it.status != NodeStatus.COMING_SOON }
    val completedTotal get() = orderedSubLevels.count { it.status == NodeStatus.COMPLETED }
    val overallFraction get() = if (playableTotal == 0) 0f else completedTotal.toFloat() / playableTotal
    val allAvailableComplete get() = playableTotal > 0 && completedTotal == playableTotal

    /** First completed level whose badge celebration hasn't been shown (e.g. the app closed right after finishing). */
    val pendingBadgeLevel: LevelState?
        get() = if (progress.schemaVersion < UserProgress.CURRENT_SCHEMA) null // migration grants these silently
        else levels.firstOrNull { it.badgePending }
}
