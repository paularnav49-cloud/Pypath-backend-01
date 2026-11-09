package com.pypath.app.domain

import com.pypath.app.data.activity.ActivityData
import com.pypath.app.data.activity.QuestionRecord
import com.pypath.app.data.model.Question

/** A question that can be reviewed, with the sub-level it belongs to in today's content. */
data class ReviewItem(val question: Question, val subLevel: SubLevelState)

/** What the dashboard "Review" card shows. */
data class ReviewInfo(
    /** Questions due today (or overdue) that still exist in the course. */
    val dueCount: Int,
    /** Questions answered wrong at least once (the review pool). */
    val poolSize: Int,
    /** The next day a question becomes due, when none are due today. */
    val nextDueDay: Long?,
    /** A session in progress: position (1-based next question) and size, or null. */
    val resumeAt: Int?,
    val resumeTotal: Int,
)

/**
 * Batch 3 spaced review (Leitner boxes 1-5, see ActivityUpdates). Only questions answered wrong at
 * least once are in the pool. Ids that no longer exist in the course are ignored everywhere.
 */
object Review {

    /** Every quiz question in the course, by id. Project stages are not reviewed. */
    fun index(snapshot: CourseSnapshot): Map<String, ReviewItem> {
        val out = LinkedHashMap<String, ReviewItem>()
        for (s in snapshot.orderedSubLevels) {
            s.subLevel.quiz?.questions?.forEach { q -> out.putIfAbsent(q.id, ReviewItem(q, s)) }
        }
        return out
    }

    private fun inPool(r: QuestionRecord) = r.box > 0 && r.dueDay != null

    /**
     * Picks up to [limit] questions: due ones (oldest due date first), or, with [scope], pool
     * questions of those sub-levels even if not due yet. Within the same due date the questions are
     * mixed across levels (round robin). Never repeats a question.
     */
    fun pick(
        activity: ActivityData, index: Map<String, ReviewItem>, today: Long,
        scope: Set<String>? = null, limit: Int = XpRules.REVIEW_SESSION_SIZE,
    ): List<String> {
        val candidates = activity.questions.entries
            .filter { (id, r) -> id in index && inPool(r) }
            .filter { (id, r) ->
                if (scope == null) r.dueDay!! <= today else index.getValue(id).subLevel.subLevel.id in scope
            }
            .sortedWith(compareBy<Map.Entry<String, QuestionRecord>>({ it.value.dueDay }, { it.value.lastDay }, { it.key }))
        val picked = mutableListOf<String>()
        for ((_, group) in candidates.groupBy { it.value.dueDay }.toSortedMap(compareBy { it })) {
            val byLevel = group.groupBy { index.getValue(it.key).subLevel.level.id }.values.map { it.toMutableList() }
            while (byLevel.any { it.isNotEmpty() }) {
                for (list in byLevel) if (list.isNotEmpty()) picked += list.removeAt(0).key
            }
        }
        return picked.distinct().take(limit)
    }

    fun info(activity: ActivityData, index: Map<String, ReviewItem>, today: Long): ReviewInfo {
        val pool = activity.questions.filter { (id, r) -> id in index && inPool(r) }
        val due = pool.count { it.value.dueDay!! <= today }
        val next = pool.values.mapNotNull { it.dueDay }.filter { it > today }.minOrNull()
        val session = activity.reviewSession?.takeIf { !it.finished }
        val remaining = session?.let { s -> s.ids.drop(s.position).count { it in index } } ?: 0
        val resume = if (session != null && remaining > 0) session.answered + 1 else null
        return ReviewInfo(
            dueCount = due, poolSize = pool.size, nextDueDay = if (due == 0) next else null,
            resumeAt = resume, resumeTotal = if (session != null) session.answered + remaining else 0,
        )
    }

    /** The questions still to answer in the saved session, skipping ids missing from the course. */
    fun remaining(activity: ActivityData, index: Map<String, ReviewItem>): List<ReviewItem> {
        val s = activity.reviewSession ?: return emptyList()
        if (s.finished) return emptyList()
        return s.ids.drop(s.position).mapNotNull { index[it] }
    }
}
