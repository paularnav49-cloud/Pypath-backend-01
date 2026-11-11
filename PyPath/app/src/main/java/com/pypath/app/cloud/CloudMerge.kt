package com.pypath.app.cloud

import com.pypath.app.data.activity.ActivityData
import com.pypath.app.data.activity.ActivityLimits
import com.pypath.app.data.activity.DayTally
import com.pypath.app.data.activity.QuestionRecord
import com.pypath.app.data.programs.ProgramBook
import com.pypath.app.data.programs.ProgramLibrary
import com.pypath.app.data.programs.SavedProgram
import com.pypath.app.data.progress.ProjectProgress
import com.pypath.app.data.progress.QuizResult
import com.pypath.app.data.progress.SubLevelProgress
import com.pypath.app.data.progress.UserProgress

/**
 * Conflict handling for cloud sync. Pure functions (no Android, no I/O), covered by unit tests.
 *
 * Rules: learning is only ever added up, never lost. Completed steps, badges, tasks and active days
 * are unions; counters take the larger value; for things that can only have one value (current
 * lesson, streak freezes, a saved program's text) the more recent side wins. Merging is symmetric
 * enough that syncing again changes nothing (idempotent), so retries are always safe.
 */
object CloudMerge {

    /** Keeps the newest deletion markers only, so the programs file cannot grow without limit. */
    const val MAX_TOMBSTONES = 500

    fun snapshot(local: CloudSnapshot, remote: CloudSnapshot?): CloudSnapshot {
        if (remote == null) return local
        val (progress, activity) = progressAndActivity(local.progress, local.activity, remote.progress, remote.activity)
        return CloudSnapshot(
            progress = progress,
            activity = activity,
            programs = ProgramsEnvelope(data = programs(local.programs.data, remote.programs.data)),
            settings = SettingsEnvelope(data = settings(local.settings.data, remote.settings.data)),
        )
    }

    /** "Reset progress" handling: the side with the newer reset wins wholesale; otherwise both are merged. */
    fun progressAndActivity(
        lp: ProgressEnvelope, la: ActivityEnvelope, rp: ProgressEnvelope, ra: ActivityEnvelope,
    ): Pair<ProgressEnvelope, ActivityEnvelope> {
        val localReset = maxOf(lp.resetAt, la.resetAt)
        val remoteReset = maxOf(rp.resetAt, ra.resetAt)
        return when {
            localReset > remoteReset -> lp.copy(resetAt = localReset) to la.copy(resetAt = localReset)
            remoteReset > localReset -> {
                // Another phone reset progress: keep its state, but never forget that this phone saw onboarding.
                val p = rp.data.copy(onboardingCompleted = rp.data.onboardingCompleted || lp.data.onboardingCompleted)
                rp.copy(data = p, resetAt = remoteReset) to ra.copy(resetAt = remoteReset)
            }
            else -> ProgressEnvelope(resetAt = localReset, data = progress(lp.data, rp.data)) to
                ActivityEnvelope(resetAt = localReset, data = activity(la.data, ra.data))
        }
    }

    // ───────────── Learning progress ─────────────

    fun progress(a: UserProgress, b: UserProgress): UserProgress {
        val ids = a.subLevels.keys + b.subLevels.keys
        val subs = ids.associateWith { id ->
            val x = a.subLevels[id]
            val y = b.subLevels[id]
            if (x == null) y!! else if (y == null) x else subLevel(x, y)
        }
        val lastA = a.subLevels.values.maxOfOrNull { it.lastOpenedAt ?: 0L } ?: 0L
        val lastB = b.subLevels.values.maxOfOrNull { it.lastOpenedAt ?: 0L } ?: 0L
        val current = if (lastB > lastA) b.currentSubLevelId ?: a.currentSubLevelId else a.currentSubLevelId ?: b.currentSubLevelId
        val xp = (a.xpAwards.keys + b.xpAwards.keys).associateWith { maxOf(a.xpAwards[it] ?: 0, b.xpAwards[it] ?: 0) }
        return UserProgress(
            schemaVersion = maxOf(a.schemaVersion, b.schemaVersion),
            onboardingCompleted = a.onboardingCompleted || b.onboardingCompleted,
            currentSubLevelId = current,
            subLevels = subs,
            earnedBadges = a.earnedBadges + b.earnedBadges,
            completedTasks = a.completedTasks + b.completedTasks,
            xpAwards = xp,
        )
    }

    fun subLevel(a: SubLevelProgress, b: SubLevelProgress): SubLevelProgress = SubLevelProgress(
        completedSteps = a.completedSteps + b.completedSteps,
        quiz = quiz(a.quiz, b.quiz),
        completed = a.completed || b.completed,
        completedAt = listOfNotNull(a.completedAt, b.completedAt).minOrNull(),
        lastOpenedAt = listOfNotNull(a.lastOpenedAt, b.lastOpenedAt).maxOrNull(),
        project = project(a.project, b.project),
    )

    fun quiz(a: QuizResult?, b: QuizResult?): QuizResult? {
        if (a == null) return b
        if (b == null) return a
        val best = if (b.bestPercent > a.bestPercent || (b.bestPercent == a.bestPercent && b.bestOutOf > a.bestOutOf)) b else a
        val latest = if (b.attempts > a.attempts) b else a
        return QuizResult(
            lastScore = latest.lastScore,
            bestScore = best.bestScore,
            total = latest.total,
            attempts = maxOf(a.attempts, b.attempts),
            passed = a.passed || b.passed,
            bestTotal = best.bestOutOf,
        )
    }

    fun project(a: ProjectProgress?, b: ProjectProgress?): ProjectProgress? {
        if (a == null) return b
        if (b == null) return a
        return ProjectProgress(
            briefingSeen = a.briefingSeen || b.briefingSeen,
            completedStages = (a.completedStages + b.completedStages).distinct(),
            wrongAttempts = maxOf(a.wrongAttempts, b.wrongAttempts),
        )
    }

    // ───────────── Activity and streaks ─────────────

    fun activity(a: ActivityData, b: ActivityData): ActivityData {
        val questions = (a.questions.keys + b.questions.keys).associateWith { id ->
            val x = a.questions[id]
            val y = b.questions[id]
            if (x == null) y!! else if (y == null) x else question(x, y)
        }
        val days = (a.days.keys + b.days.keys).sortedDescending().take(ActivityLimits.DAY_TALLIES).associateWith { d ->
            val x = a.days[d] ?: DayTally()
            val y = b.days[d] ?: DayTally()
            DayTally(xp = maxOf(x.xp, y.xp), actions = maxOf(x.actions, y.actions), reviews = maxOf(x.reviews, y.reviews))
        }
        val active = (a.activeDays + b.activeDays).toSortedSet().toList().takeLast(ActivityLimits.ACTIVE_DAYS)
        val activeSet = active.toSet()
        val frozen = (a.frozenDays + b.frozenDays).toSortedSet().filter { it !in activeSet }.takeLast(ActivityLimits.ACTIVE_DAYS)
        // Single-valued streak state comes from the side that was used most recently.
        val lastA = a.activeDays.maxOrNull() ?: Long.MIN_VALUE
        val lastB = b.activeDays.maxOrNull() ?: Long.MIN_VALUE
        val dom = if (lastB > lastA || (lastB == lastA && b.runLength > a.runLength)) b else a
        return dom.copy(
            schemaVersion = maxOf(a.schemaVersion, b.schemaVersion),
            questions = questions,
            days = days,
            activeDays = active,
            frozenDays = frozen,
            longestStreak = maxOf(a.longestStreak, b.longestStreak, dom.runLength),
            reviewXp = maxOf(a.reviewXp, b.reviewXp),
        )
    }

    fun question(a: QuestionRecord, b: QuestionRecord): QuestionRecord = when {
        b.attempts > a.attempts -> b
        a.attempts > b.attempts -> a
        b.lastDay > a.lastDay -> b
        else -> a
    }.let { it.copy(xp = maxOf(a.xp, b.xp)) }

    // ───────────── Saved programs ─────────────

    fun programs(a: ProgramLibrary, b: ProgramLibrary): ProgramLibrary {
        val tomb = (a.deleted.keys + b.deleted.keys).associateWith { maxOf(a.deleted[it] ?: 0L, b.deleted[it] ?: 0L) }
            .entries.sortedByDescending { it.value }.take(MAX_TOMBSTONES).associate { it.key to it.value }
        val byId = LinkedHashMap<String, SavedProgram>()
        for (p in a.programs + b.programs) {
            val old = byId[p.id]
            byId[p.id] = if (old == null) p else newer(old, p)
        }
        // A program edited after it was deleted elsewhere survives; otherwise the deletion wins.
        val alive = byId.values.filter { p -> (tomb[p.id] ?: Long.MIN_VALUE) < p.updatedAt }
        val tombstones = tomb.filterKeys { id -> alive.none { it.id == id } }
        // Two different programs may have the same name after merging: keep names unique.
        val named = ArrayList<SavedProgram>()
        for (p in alive.sortedBy { it.createdAt }) {
            val name = ProgramBook.uniqueName(ProgramLibrary(programs = named), p.name)
            named += if (name == p.name) p else p.copy(name = name)
        }
        val order = alive.map { it.id }
        val programs = named.sortedBy { order.indexOf(it.id) }
        val lastOpened = a.lastOpenedId?.takeIf { id -> programs.any { it.id == id } }
            ?: b.lastOpenedId?.takeIf { id -> programs.any { it.id == id } }
        return ProgramLibrary(version = maxOf(a.version, b.version), programs = programs, lastOpenedId = lastOpened, deleted = tombstones)
    }

    private fun newer(x: SavedProgram, y: SavedProgram): SavedProgram = when {
        y.updatedAt > x.updatedAt -> y
        x.updatedAt > y.updatedAt -> x
        y.code.length > x.code.length -> y // same time: keep the longer text, deterministically
        else -> x
    }.let { it.copy(createdAt = listOf(x.createdAt, y.createdAt).filter { c -> c > 0 }.minOrNull() ?: it.createdAt) }

    // ───────────── Settings ─────────────

    fun settings(a: CloudSettings, b: CloudSettings): CloudSettings {
        fun pick(key: String) = (b.stamps[key] ?: 0L) > (a.stamps[key] ?: 0L)
        val stamps = (a.stamps.keys + b.stamps.keys).associateWith { maxOf(a.stamps[it] ?: 0L, b.stamps[it] ?: 0L) }
        return CloudSettings(
            soundEffects = if (pick(CloudSettings.SOUND)) b.soundEffects else a.soundEffects,
            certificateName = if (pick(CloudSettings.NAME)) b.certificateName else a.certificateName,
            dailyGoal = if (pick(CloudSettings.GOAL)) b.dailyGoal else a.dailyGoal,
            learningMinutes = if (pick(CloudSettings.MINUTES)) b.learningMinutes else a.learningMinutes,
            introOfferShown = a.introOfferShown || b.introOfferShown,
            stamps = stamps,
        )
    }
}
