package com.pypath.app.domain

import com.pypath.app.data.model.Course
import com.pypath.app.data.progress.UserProgress

/**
 * Batch 3: every XP number in one place, so the rules are easy to tune.
 *
 * Two kinds of XP:
 *  - one-time events (quiz passed, level completed, final project, level project, practical task):
 *    stored in [UserProgress.xpAwards] under a stable key, so each can be granted only once;
 *  - per-question XP: stored in the activity store (QuestionRecord.xp), at most once per question.
 */
object XpRules {
    /** Correct on the learner's first ever answer to that question (in a quiz). */
    const val ANSWER_FIRST_TRY = 10
    /** Correct on a later attempt (the question was answered wrong before). */
    const val ANSWER_LATER = 4
    const val ANSWER_WRONG = 0
    const val QUIZ_PASS = 20
    const val LEVEL_COMPLETE = 50
    const val FINAL_PROJECT = 100
    /** Optional level projects (not in the brief; treated like passing a sub-level). */
    const val LEVEL_PROJECT = 20
    const val PRACTICAL_TASK = 30
    /** A finished spaced-review session. */
    const val REVIEW_SESSION = 10
    /** Review sessions that pay [REVIEW_SESSION] XP per day (more can be done, without XP). */
    const val REVIEW_SESSIONS_WITH_XP_PER_DAY = 3
    /** Questions in one review session at most. */
    const val REVIEW_SESSION_SIZE = 10

    /** A question answered correctly after opening its hint earns half (rounded down). */
    fun withHint(xp: Int): Int = xp / 2

    /** Keys of one-time events in [UserProgress.xpAwards]. Never rename: saved progress uses them. */
    fun quizKey(subLevelId: String) = "quiz:$subLevelId"
    fun levelKey(levelId: String) = "level:$levelId"
    fun finalProjectKey(subLevelId: String) = "final:$subLevelId"
    fun levelProjectKey(projectId: String) = "project:$projectId"
    fun taskKey(taskId: String) = "task:$taskId"
}

/** A title the learner holds from [minXp] total XP. */
data class XpTitle(val name: String, val minXp: Int)

object XpTitles {
    val all: List<XpTitle> = listOf(
        XpTitle("Beginner", 0),
        XpTitle("Learner", 150),
        XpTitle("Coder", 500),
        XpTitle("Problem Solver", 1200),
        XpTitle("Python Pro", 2500),
    )

    fun forXp(xp: Int): XpTitle = all.last { xp >= it.minXp }

    fun next(xp: Int): XpTitle? = all.firstOrNull { it.minXp > xp }

    /** 0..1 progress from the current title to the next one (1 at the top title). */
    fun progressToNext(xp: Int): Float {
        val cur = forXp(xp)
        val nxt = next(xp) ?: return 1f
        return ((xp - cur.minXp).toFloat() / (nxt.minXp - cur.minXp)).coerceIn(0f, 1f)
    }
}

object Xp {

    /**
     * Every one-time XP event the learner has earned according to the saved progress, keyed by
     * [XpRules] keys. Computed only from state that already exists (pass flags, completed levels,
     * completed projects, Batch 2 completed tasks), so it also works retroactively for learners
     * who finished content before XP existed.
     */
    fun oneTimeEvents(course: Course, progress: UserProgress): Map<String, Int> {
        val snap = CourseSnapshot(course, progress)
        val out = LinkedHashMap<String, Int>()
        for (lvl in snap.levels) {
            for (s in lvl.subLevels) {
                val sub = s.subLevel
                if (sub.isProject) {
                    if (s.status == NodeStatus.COMPLETED) out[XpRules.finalProjectKey(sub.id)] = XpRules.FINAL_PROJECT
                } else if (s.progress.quiz?.passed == true) {
                    out[XpRules.quizKey(sub.id)] = XpRules.QUIZ_PASS
                }
            }
            if (lvl.status == NodeStatus.COMPLETED) out[XpRules.levelKey(lvl.level.id)] = XpRules.LEVEL_COMPLETE
            for (pr in lvl.projects) {
                if (pr.status == NodeStatus.COMPLETED) out[XpRules.levelProjectKey(pr.subLevel.id)] = XpRules.LEVEL_PROJECT
            }
        }
        for (task in progress.completedTasks.sorted()) out[XpRules.taskKey(task)] = XpRules.PRACTICAL_TASK
        return out
    }

    /** One-time events earned but not yet recorded in [UserProgress.xpAwards]. */
    fun newEvents(course: Course, progress: UserProgress): Map<String, Int> =
        oneTimeEvents(course, progress).filterKeys { it !in progress.xpAwards }

    /** XP for one quiz answer. [eligible] is false when the sub-level quiz was already passed. */
    fun answerXp(attemptsBefore: Int, xpAlreadyEarned: Int, correct: Boolean, hintUsed: Boolean, eligible: Boolean): Int {
        if (!eligible || !correct || xpAlreadyEarned > 0) return 0
        val base = if (attemptsBefore == 0) XpRules.ANSWER_FIRST_TRY else XpRules.ANSWER_LATER
        return if (hintUsed) XpRules.withHint(base) else base
    }
}
