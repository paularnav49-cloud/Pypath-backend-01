package com.pypath.app.domain

import com.pypath.app.data.activity.ActivityData
import com.pypath.app.data.model.Level

/** Tracked answers for one level (only questions answered since the Batch 3 update). */
data class LevelAccuracy(val level: Level, val answered: Int, val correct: Int) {
    val percent: Int get() = if (answered == 0) 0 else correct * 100 / answered
}

/** A sub-level with low tracked accuracy. */
data class WeakTopic(val subLevel: SubLevelState, val answered: Int, val correct: Int) {
    val percent: Int get() = if (answered == 0) 0 else correct * 100 / answered
}

data class BadgeStatus(val level: Level, val earned: Boolean)

/** Everything the stats screen and the dashboard header show, computed from local data only. */
data class LearnerStats(
    val totalXp: Int,
    val title: XpTitle,
    val nextTitle: XpTitle?,
    val progressToNext: Float,
    val streak: StreakInfo,
    val goal: DailyGoal,
    val todayXp: Int,
    val todayActions: Int,
    val goalMetToday: Boolean,
    val questionsAnswered: Int,
    val questionsCorrect: Int,
    val perLevel: List<LevelAccuracy>,
    /** True when some quizzes were passed before answers were tracked: show "since this update". */
    val historyPartial: Boolean,
    val subLevelsPassed: Int,
    val subLevelsTotal: Int,
    val levelsCompleted: Int,
    val levelsTotal: Int,
    val badges: List<BadgeStatus>,
    val tasksCompleted: Int,
    /** Number of practical tasks in the app, or null when unknown. */
    val tasksTotal: Int?,
    val weakest: List<WeakTopic>,
) {
    val accuracyPercent: Int? get() = if (questionsAnswered == 0) null else questionsCorrect * 100 / questionsAnswered
    val xpToNext: Int get() = nextTitle?.let { (it.minXp - totalXp).coerceAtLeast(0) } ?: 0
    val badgesEarned: Int get() = badges.count { it.earned }
    /** 0..1 progress towards today's goal (1 when the goal is off and the day is active). */
    val goalFraction: Float
        get() = when {
            goal.xp == 0 -> if (goalMetToday) 1f else 0f
            else -> (todayXp.toFloat() / goal.xp).coerceIn(0f, 1f)
        }

    companion object {
        /** Weak topics need at least this many tracked answers, so one slip isn't "weak". */
        const val WEAK_MIN_ANSWERS = 3
        const val WEAK_COUNT = 3

        fun build(snapshot: CourseSnapshot, activity: ActivityData, goal: DailyGoal, today: Long, tasksTotal: Int? = null): LearnerStats {
            val progress = snapshot.progress
            // Question id -> sub-level, from the content in the app today. Records whose id no longer
            // exists (content was updated) are ignored.
            val owner = HashMap<String, SubLevelState>()
            for (s in snapshot.orderedSubLevels) s.subLevel.quiz?.questions?.forEach { owner[it.id] = s }
            val tracked = activity.questions.filterKeys { it in owner }

            val questionXp = activity.questions.values.sumOf { it.xp }
            val total = progress.xpAwards.values.sum() + questionXp + activity.reviewXp

            val perSub = tracked.entries.groupBy { owner.getValue(it.key) }
            val weakest = perSub.map { (s, list) -> WeakTopic(s, list.sumOf { it.value.attempts }, list.sumOf { it.value.correct }) }
                .filter { it.answered >= WEAK_MIN_ANSWERS && it.correct < it.answered }
                .sortedWith(compareBy<WeakTopic> { it.correct.toDouble() / it.answered }.thenByDescending { it.answered })
                .take(WEAK_COUNT)

            val perLevel = snapshot.levels.mapNotNull { lvl ->
                val recs = tracked.filter { owner.getValue(it.key).level.id == lvl.level.id }.values
                val n = recs.sumOf { it.attempts }
                if (n == 0) null else LevelAccuracy(lvl.level, n, recs.sumOf { it.correct })
            }

            val passedQuizzes = snapshot.orderedSubLevels.filter { it.progress.quiz?.passed == true }
            val trackedSubs = perSub.keys.map { it.subLevel.id }.toSet()
            val partial = passedQuizzes.any { it.subLevel.id !in trackedSubs }

            val tally = activity.days[today]
            val playable = snapshot.orderedSubLevels.filter { it.status != NodeStatus.COMING_SOON }
            val levels = snapshot.levels.filter { it.level.available }
            val streak = Streaks.info(activity, today)
            return LearnerStats(
                totalXp = total,
                title = XpTitles.forXp(total),
                nextTitle = XpTitles.next(total),
                progressToNext = XpTitles.progressToNext(total),
                streak = streak,
                goal = goal,
                todayXp = tally?.xp ?: 0,
                todayActions = tally?.actions ?: 0,
                goalMetToday = streak.activeToday,
                questionsAnswered = tracked.values.sumOf { it.attempts },
                questionsCorrect = tracked.values.sumOf { it.correct },
                perLevel = perLevel,
                historyPartial = partial,
                subLevelsPassed = playable.count { it.status == NodeStatus.COMPLETED },
                subLevelsTotal = playable.size,
                levelsCompleted = levels.count { it.status == NodeStatus.COMPLETED },
                levelsTotal = levels.size,
                badges = levels.map { BadgeStatus(it.level, it.badgeEarned) },
                tasksCompleted = progress.completedTasks.size,
                tasksTotal = tasksTotal,
                weakest = weakest,
            )
        }
    }
}
