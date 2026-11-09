package com.pypath.app.data.activity

import com.pypath.app.domain.Streaks
import com.pypath.app.domain.Xp
import com.pypath.app.domain.XpRules
import kotlinx.serialization.json.Json

/**
 * Pure transformations of [ActivityData]. [LocalActivityRepository] runs them inside a DataStore
 * transaction; unit tests call them directly with fixed days. [today] is a local epoch day and
 * [goalXp] the daily goal in XP (0 = goal off).
 */
object ActivityUpdates {

    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun decode(text: String?): ActivityData =
        text?.let { runCatching { json.decodeFromString(ActivityData.serializer(), it) }.getOrNull() } ?: ActivityData()

    fun encode(a: ActivityData): String = json.encodeToString(ActivityData.serializer(), a)

    /** Leitner intervals in days for boxes 1..5 (Part 2 moves questions up on correct reviews). */
    val BOX_INTERVALS = listOf(1, 3, 7, 14, 30)
    const val MAX_BOX = 5

    fun intervalFor(box: Int): Int = BOX_INTERVALS[(box.coerceIn(1, MAX_BOX)) - 1]

    /** XP a quiz answer would earn now (same rule as [recordAnswer]). */
    fun answerXp(a: ActivityData, questionId: String, correct: Boolean, hintUsed: Boolean, eligible: Boolean): Int {
        val r = a.questions[questionId]
        return Xp.answerXp(r?.attempts ?: 0, r?.xp ?: 0, correct, hintUsed, eligible)
    }

    /**
     * Records one answer to a quiz question.
     *  - Wrong: the question enters (or returns to) Leitner box 1, due the next day.
     *  - Correct in a review ([fromReview]) while the question is due: box + 1 (max 5), due after
     *    that box's interval. A question reviewed early (a "weakest topics" review) keeps its box.
     *  - Correct in a normal quiz: the box is left as it is.
     * Quiz answers earn XP once per question ([Xp.answerXp]); review answers earn none here (a
     * finished review session earns [XpRules.REVIEW_SESSION], see [finishReview]).
     */
    fun recordAnswer(
        a: ActivityData, questionId: String, subLevelId: String, correct: Boolean, hintUsed: Boolean,
        eligible: Boolean, today: Long, goalXp: Int, fromReview: Boolean = false,
    ): ActivityData {
        val old = a.questions[questionId] ?: QuestionRecord(subLevelId = subLevelId)
        val xp = if (fromReview) 0 else Xp.answerXp(old.attempts, old.xp, correct, hintUsed, eligible)
        val (box, due) = when {
            !correct -> 1 to today + intervalFor(1)
            fromReview && old.box > 0 && (old.dueDay ?: today) <= today -> {
                val b = (old.box + 1).coerceAtMost(MAX_BOX)
                b to today + intervalFor(b)
            }
            else -> old.box to old.dueDay
        }
        val rec = old.copy(
            subLevelId = subLevelId.ifEmpty { old.subLevelId },
            attempts = old.attempts + 1,
            correct = old.correct + if (correct) 1 else 0,
            lastCorrect = correct,
            lastDay = today,
            box = box,
            dueDay = due,
            xp = old.xp + xp,
        )
        return addActivity(a.copy(questions = a.questions + (questionId to rec)), today, goalXp, xp = xp, actions = 1)
    }

    /** A lesson page finished or a project stage answered: counts towards an active day when the goal is off. */
    fun recordAction(a: ActivityData, today: Long, goalXp: Int): ActivityData = addActivity(a, today, goalXp, xp = 0, actions = 1)

    /** One-time XP (quiz passed, level, project, task, review session) earned today. */
    fun addXp(a: ActivityData, today: Long, goalXp: Int, xp: Int): ActivityData =
        if (xp <= 0) a else addActivity(a, today, goalXp, xp = xp, actions = 0)

    /** Part 2: XP for a finished review session, kept apart from question XP. */
    fun addReviewXp(a: ActivityData, today: Long, goalXp: Int, xp: Int): ActivityData =
        addXp(a.copy(reviewXp = a.reviewXp + xp), today, goalXp, xp)

    private fun addActivity(a: ActivityData, today: Long, goalXp: Int, xp: Int, actions: Int): ActivityData {
        val t = a.days[today] ?: DayTally()
        val tally = t.copy(xp = t.xp + xp, actions = t.actions + actions)
        val days = (a.days + (today to tally)).filterKeys { it > today - ActivityLimits.DAY_TALLIES }
        return evaluateDay(a.copy(days = days), today, goalXp)
    }

    /**
     * Marks [day] active once its tally meets the goal (also called when the goal changes).
     * A day is added at most once, so moving the clock back and forth never grants a day twice.
     * When exactly one day before [day] was missed and a freeze is ready, the freeze covers it.
     */
    fun evaluateDay(a: ActivityData, day: Long, goalXp: Int): ActivityData {
        if (day in a.activeDays) return a
        val tally = a.days[day] ?: return a
        val meets = if (goalXp > 0) tally.xp >= goalXp else tally.actions >= 1
        if (!meets) return a

        var frozen = a.frozenDays
        var freezes = a.freezes
        var freezeDay = a.lastFreezeUsedDay
        val before = (a.activeDays + a.frozenDays).toSet()
        if (freezes > 0 && (day - 1) !in before && (day - 2) in before) {
            frozen = (frozen + (day - 1)).sorted()
            freezes -= 1
            freezeDay = day - 1
        }
        val active = (a.activeDays + day).distinct().sorted()
        val counted = Streaks.runEndingAt(day, (active + frozen).toSet())
        // The stored days only reach back 400 days; continue a longer run from the saved length.
        val runEnd = a.runEnd
        val streak = if (runEnd != null && runEnd < day && runEnd > day - counted) a.runLength + (day - runEnd).toInt() else counted
        val newerRun = runEnd == null || day > runEnd
        if (streak > 0 && streak % Streaks.FREEZE_EVERY == 0 && freezes < Streaks.MAX_FREEZES) freezes += 1
        val keepFrom = day - ActivityLimits.ACTIVE_DAYS + 1
        return a.copy(
            activeDays = active.filter { it >= keepFrom },
            frozenDays = frozen.filter { it >= keepFrom },
            freezes = freezes,
            lastFreezeUsedDay = freezeDay,
            longestStreak = maxOf(a.longestStreak, streak),
            pendingMilestone = if (streak in Streaks.MILESTONES) streak else a.pendingMilestone,
            runEnd = if (newerRun) day else a.runEnd,
            runLength = if (newerRun) streak else a.runLength,
        )
    }

    // ───────────── Spaced review sessions ─────────────

    /** Starts (or replaces) the review session with the picked question ids. */
    fun startReview(a: ActivityData, ids: List<String>, scope: List<String>, today: Long): ActivityData =
        a.copy(reviewSession = ReviewSessionState(ids = ids.distinct(), scope = scope, startedDay = today))

    /**
     * Records a review answer: updates the question's box (see [recordAnswer]) and the session.
     * Ids that disappeared from the course before [questionId] are skipped. The session finishes
     * (and pays its XP) when the last question is answered.
     */
    fun recordReviewAnswer(
        a: ActivityData, questionId: String, subLevelId: String, correct: Boolean, hintUsed: Boolean,
        today: Long, goalXp: Int,
    ): ActivityData {
        val boxBefore = a.questions[questionId]?.box ?: 0
        var next = recordAnswer(a, questionId, subLevelId, correct, hintUsed, eligible = false, today = today, goalXp = goalXp, fromReview = true)
        val session = next.reviewSession ?: return next
        if (session.finished) return next
        val at = session.ids.indexOf(questionId)
        if (at < session.position) return next // not part of this session, or answered already
        val boxAfter = next.questions[questionId]?.box ?: 0
        val updated = session.copy(
            position = at + 1,
            answered = session.answered + 1,
            correct = session.correct + if (correct) 1 else 0,
            movedUp = session.movedUp + if (boxAfter > boxBefore) 1 else 0,
        )
        next = next.copy(reviewSession = updated)
        return if (updated.position >= updated.ids.size) finishReview(next, today, goalXp) else next
    }

    /**
     * Ends the session: +[XpRules.REVIEW_SESSION] XP when at least one question was answered and
     * fewer than [XpRules.REVIEW_SESSIONS_WITH_XP_PER_DAY] sessions already paid XP today.
     * The finished state stays until [clearReview], so the summary survives the app being killed.
     */
    fun finishReview(a: ActivityData, today: Long, goalXp: Int): ActivityData {
        val session = a.reviewSession ?: return a
        if (session.finished) return a
        val done = (a.days[today]?.reviews ?: 0)
        val xp = if (session.answered > 0 && done < XpRules.REVIEW_SESSIONS_WITH_XP_PER_DAY) XpRules.REVIEW_SESSION else 0
        val tally = a.days[today] ?: DayTally()
        val counted = if (session.answered > 0) a.copy(days = a.days + (today to tally.copy(reviews = tally.reviews + 1))) else a
        val finished = counted.copy(reviewSession = session.copy(finished = true, xpEarned = xp))
        return if (xp > 0) addReviewXp(finished, today, goalXp, xp) else finished
    }

    fun clearReview(a: ActivityData): ActivityData = if (a.reviewSession == null) a else a.copy(reviewSession = null)

    fun consumeMilestone(a: ActivityData): ActivityData = if (a.pendingMilestone == null) a else a.copy(pendingMilestone = null)

    /** Upgrades older activity data. Only schema 1 exists so far. */
    fun migrate(a: ActivityData): ActivityData = if (a.schemaVersion >= ActivityData.CURRENT_SCHEMA) a else a.copy(schemaVersion = ActivityData.CURRENT_SCHEMA)
}
