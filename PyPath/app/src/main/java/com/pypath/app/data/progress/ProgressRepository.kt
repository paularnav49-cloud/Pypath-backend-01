package com.pypath.app.data.progress

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pypath.app.data.model.Course
import com.pypath.app.data.model.StepType
import com.pypath.app.data.progress.ProgressUpdates.updateSub
import com.pypath.app.data.progress.ProgressUpdates.withCompletionCheck
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.progressStore: DataStore<Preferences> by preferencesDataStore(name = "pypath_progress")

/** Abstraction so a cloud-synced implementation can be swapped in later. */
interface ProgressRepository {
    val progress: Flow<UserProgress>
    suspend fun completeOnboarding()
    suspend fun setCurrentSubLevel(subLevelId: String)
    suspend fun markStepCompleted(subLevelId: String, step: StepType, requiredSteps: List<StepType>)
    suspend fun recordQuizResult(subLevelId: String, score: Int, total: Int, passed: Boolean, requiredSteps: List<StepType>)
    suspend fun markBriefingSeen(subLevelId: String)
    suspend fun recordProjectMiss(subLevelId: String)
    suspend fun completeProjectStage(subLevelId: String, stageId: String, allStageIds: List<String>, requiredSteps: List<StepType>)
    suspend fun earnBadge(levelId: String)
    suspend fun markTaskCompleted(taskId: String)
    /** Grants one-time XP for newly finished content; returns the XP granted now. */
    suspend fun syncXp(course: Course): Int
    suspend fun migrate(course: Course)
    suspend fun reset()
}

class LocalProgressRepository(private val context: Context) : ProgressRepository {

    private val key = stringPreferencesKey("user_progress_json")

    override val progress: Flow<UserProgress> = context.progressStore.data.map { prefs -> ProgressUpdates.decode(prefs[key]) }

    private suspend fun update(transform: (UserProgress) -> UserProgress) {
        context.progressStore.edit { prefs ->
            val current = ProgressUpdates.decode(prefs[key])
            val next = transform(current)
            if (next != current || prefs[key] == null) prefs[key] = ProgressUpdates.encode(next)
        }
    }

    private fun now() = System.currentTimeMillis()

    override suspend fun completeOnboarding() = update { it.copy(onboardingCompleted = true) }

    override suspend fun setCurrentSubLevel(subLevelId: String) = update {
        it.copy(currentSubLevelId = subLevelId)
            .updateSub(subLevelId) { s -> s.copy(lastOpenedAt = now()) }
    }

    override suspend fun markStepCompleted(subLevelId: String, step: StepType, requiredSteps: List<StepType>) =
        update {
            it.updateSub(subLevelId) { s ->
                s.copy(completedSteps = s.completedSteps + step).withCompletionCheck(requiredSteps, now())
            }
        }

    override suspend fun recordQuizResult(
        subLevelId: String, score: Int, total: Int, passed: Boolean, requiredSteps: List<StepType>,
    ) = update {
        it.updateSub(subLevelId) { s ->
            // Batch 3 Part 2: passed stays sticky; the best attempt keeps its own question count
            // because quizzes can grow when new questions are appended.
            val result = QuizResult.record(s.quiz, score, total, passed)
            val steps = if (result.passed) s.completedSteps + StepType.QUIZ else s.completedSteps
            s.copy(quiz = result, completedSteps = steps).withCompletionCheck(requiredSteps, now())
        }
    }

    override suspend fun markBriefingSeen(subLevelId: String) = update { ProgressUpdates.markBriefingSeen(it, subLevelId) }

    override suspend fun recordProjectMiss(subLevelId: String) = update { ProgressUpdates.recordProjectMiss(it, subLevelId) }

    override suspend fun completeProjectStage(
        subLevelId: String, stageId: String, allStageIds: List<String>, requiredSteps: List<StepType>,
    ) = update { ProgressUpdates.completeProjectStage(it, subLevelId, stageId, allStageIds, requiredSteps, now()) }

    override suspend fun earnBadge(levelId: String) = update { ProgressUpdates.earnBadge(it, levelId) }

    override suspend fun markTaskCompleted(taskId: String) = update { ProgressUpdates.markTaskCompleted(it, taskId) }

    override suspend fun syncXp(course: Course): Int {
        var gained = 0
        update { current ->
            val (next, xp) = ProgressUpdates.awardXp(current, course)
            gained = xp
            next
        }
        return gained
    }

    override suspend fun migrate(course: Course) = update { ProgressUpdates.migrate(it, course) }

    /** Clears course progress and badges but keeps onboarding as seen. */
    override suspend fun reset() = update { ProgressUpdates.reset(it) }
}
