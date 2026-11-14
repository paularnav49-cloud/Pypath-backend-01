package com.pypath.app.premium

import android.content.Context
import com.pypath.app.practical.CheckReport
import com.pypath.app.practical.PracticalTask
import com.pypath.app.practical.TaskTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.math.roundToInt

/**
 * Premium Code Battle: timed Python challenges checked by hidden tests, plus personal records and
 * trophies.
 *
 * Content is bundled in the APK (assets/premium/battle.json, written and validated by
 * content/code_battle_content.py). Code is run by the existing Chaquopy runner through
 * [com.pypath.app.ui.screens.practical.PracticalViewModel.check] (same sandbox, limits and output
 * checker as the Practical tab). Records live in their own file (files/premium/battle.json), apart
 * from course progress, XP, streaks, saved programs and the Phase 2 study file.
 */

// ─────────────────────────── Content ───────────────────────────

object BattleDifficulty {
    const val EASY = "easy"
    const val MEDIUM = "medium"
    const val HARD = "hard"
    val all = listOf(EASY, MEDIUM, HARD)

    fun label(d: String): String = when (d) { MEDIUM -> "Medium"; HARD -> "Hard"; else -> "Easy" }

    /** Battle points: best score × this. */
    fun multiplier(d: String): Int = when (d) { MEDIUM -> 2; HARD -> 3; else -> 1 }
}

/** One example shown to the learner: the input lines and what the program prints. */
@Serializable
data class BattleExample(val input: String, val output: String)

@Serializable
data class BattleChallenge(
    /** Always starts with [PremiumIds.BATTLE_PREFIX]. */
    val id: String,
    val difficulty: String = BattleDifficulty.EASY,
    val order: Int = 0,
    val title: String,
    val minutes: Int = 5,
    val concepts: List<String> = emptyList(),
    val description: String,
    val requirements: List<String> = emptyList(),
    val examples: List<BattleExample> = emptyList(),
    val hint: String? = null,
    val starterCode: String = "",
    /** Visible example tests first, then hidden tests. Hidden tests never reveal input or output. */
    val tests: List<TaskTest> = emptyList(),
) {
    val timeLimitMs: Long get() = minutes * 60_000L
    val hiddenCount: Int get() = tests.count { it.hidden }

    /** The challenge as a Practical task, so the existing editor, runner and checker are reused. */
    fun task(): PracticalTask = PracticalTask(
        id = id, title = title, concepts = concepts, instructions = description, requirements = requirements,
        hint = hint, starterCode = starterCode, difficulty = difficulty, checkMode = "exact", tests = tests,
    )
}

@Serializable
data class BattleCatalog(val schemaVersion: Int = 1, val challenges: List<BattleChallenge> = emptyList()) {
    fun of(difficulty: String): List<BattleChallenge> = challenges.filter { it.difficulty == difficulty }.sortedBy { it.order }
    fun challenge(id: String): BattleChallenge? = challenges.firstOrNull { it.id == id }
}

object BattleContentParser {
    private val json = Json { ignoreUnknownKeys = true }
    fun parse(text: String): BattleCatalog = json.decodeFromString(BattleCatalog.serializer(), text)
}

fun loadBattleCatalog(context: Context): BattleCatalog =
    context.assets.open("premium/battle.json").bufferedReader().use { BattleContentParser.parse(it.readText()) }

// ─────────────────────────── Saved data ───────────────────────────

/** A battle in progress. [deadline] is wall-clock time, so leaving the app never pauses the timer. */
@Serializable
data class BattleAttempt(
    val challengeId: String,
    val startedAt: Long,
    val timeLimitMs: Long,
    val deadline: Long,
    /** Latest code typed (saved when the screen closes or the app goes to the background). */
    val code: String? = null,
    /** Set once the code is submitted (by the learner or automatically when time runs out). */
    val submittedAt: Long? = null,
    val submittedCode: String? = null,
    val timedOut: Boolean = false,
) {
    val submitted: Boolean get() = submittedAt != null
}

/** Personal record for one challenge. */
@Serializable
data class BattleRecord(
    val challengeId: String,
    val attempts: Int = 0,
    val solves: Int = 0,
    val bestScore: Int = 0,
    /** Fastest time of a solved attempt. */
    val bestTimeMs: Long? = null,
    val lastScore: Int = 0,
    val firstSolvedAt: Long? = null,
) {
    val solved: Boolean get() = solves > 0
}

/** One finished battle, newest first in [BattleData.history]. */
@Serializable
data class BattleHistoryEntry(
    val challengeId: String,
    val title: String,
    val difficulty: String,
    val finishedAt: Long,
    val score: Int,
    val passed: Int,
    val total: Int,
    /** True only when every test, visible and hidden, passed. */
    val solved: Boolean,
    val timedOut: Boolean = false,
    val gaveUp: Boolean = false,
    val timeUsedMs: Long = 0,
) {
    val failed: Int get() = (total - passed).coerceAtLeast(0)
}

@Serializable
data class BattleData(
    val version: Int = 1,
    val records: Map<String, BattleRecord> = emptyMap(),
    val history: List<BattleHistoryEntry> = emptyList(),
    /** Trophy id -> time it was earned. */
    val trophies: Map<String, Long> = emptyMap(),
    val active: BattleAttempt? = null,
)

// ─────────────────────────── Trophies ───────────────────────────

enum class Trophy(val title: String, val description: String) {
    FIRST_WIN("First Victory", "Solve your first challenge"),
    FLAWLESS("Flawless", "Solve a challenge on your first try"),
    SPEED_CODER("Speed Coder", "Solve a challenge in half its time or less"),
    COMEBACK("Comeback", "Solve a challenge you didn't solve before"),
    HARD_HITTER("Hard Hitter", "Solve a Hard challenge"),
    EASY_SWEEP("Easy Sweep", "Solve every Easy challenge"),
    MEDIUM_SWEEP("Medium Sweep", "Solve every Medium challenge"),
    DEDICATED("Dedicated", "Finish 10 battles"),
    BATTLE_MASTER("Battle Master", "Solve all 15 challenges"),
    ;

    val id: String get() = name.lowercase()

    companion object {
        fun byId(id: String): Trophy? = entries.firstOrNull { it.id == id }
    }
}

/** Totals shown on the Records tab. */
data class BattleSummary(val solved: Int, val total: Int, val battles: Int, val points: Int, val maxPoints: Int, val trophies: Int)

/** What a finished battle produced (shown on the result view). */
data class BattleOutcome(val entry: BattleHistoryEntry, val newTrophies: List<Trophy>, val report: CheckReport?)

// ─────────────────────────── Rules ───────────────────────────

/** Pure Code Battle rules (no Android types, covered by JVM unit tests). */
object BattleEngine {
    const val MAX_HISTORY = 100
    const val MAX_CODE = 200_000
    /** Share of the score that comes from tests; the rest is a time bonus for a full solve. */
    const val TEST_POINTS = 80
    const val TIME_POINTS = 20
    const val DEDICATED_BATTLES = 10

    fun newAttempt(ch: BattleChallenge, now: Long): BattleAttempt =
        BattleAttempt(ch.id, startedAt = now, timeLimitMs = ch.timeLimitMs, deadline = now + ch.timeLimitMs)

    /** Time left; never more than the limit (moving the clock back can't add time). */
    fun remainingMs(a: BattleAttempt, now: Long): Long {
        if (a.submittedAt != null) return (a.deadline - a.submittedAt).coerceIn(0, a.timeLimitMs)
        return (a.deadline - now).coerceIn(0, a.timeLimitMs)
    }

    fun isExpired(a: BattleAttempt, now: Long): Boolean = !a.submitted && remainingMs(a, now) == 0L

    /** Saves the latest typed code (ignored once submitted). */
    fun saveCode(a: BattleAttempt, code: String): BattleAttempt =
        if (a.submitted) a else a.copy(code = code.take(MAX_CODE))

    /**
     * Locks in [code] for testing. Submitting after the deadline (or because of it) counts as timed
     * out and the submit time is capped at the deadline. A second submit is ignored.
     */
    fun submit(a: BattleAttempt, code: String, now: Long, timedOut: Boolean): BattleAttempt {
        if (a.submitted) return a
        val late = timedOut || now >= a.deadline
        val at = minOf(now, a.deadline).coerceAtLeast(a.startedAt)
        val c = code.take(MAX_CODE)
        return a.copy(code = c, submittedAt = at, submittedCode = c, timedOut = late)
    }

    fun timeUsedMs(a: BattleAttempt, now: Long): Long =
        ((a.submittedAt ?: now) - a.startedAt).coerceIn(0, a.timeLimitMs)

    /**
     * Score out of 100: up to [TEST_POINTS] for the share of tests passed, plus up to [TIME_POINTS]
     * for time left, only when every test passed and time didn't run out.
     */
    fun score(passed: Int, total: Int, timeUsedMs: Long, timeLimitMs: Long, timedOut: Boolean): Int {
        if (total <= 0) return 0
        val p = passed.coerceIn(0, total)
        val tests = (TEST_POINTS * p / total.toDouble()).roundToInt()
        val bonus = if (p == total && !timedOut && timeLimitMs > 0) {
            val left = (timeLimitMs - timeUsedMs.coerceIn(0, timeLimitMs)).toDouble()
            (TIME_POINTS * left / timeLimitMs).roundToInt()
        } else 0
        return (tests + bonus).coerceIn(0, 100)
    }

    /** Stores a tested battle: history, record, newly earned trophies; clears the active attempt. */
    fun finish(
        data: BattleData, catalog: BattleCatalog, ch: BattleChallenge, a: BattleAttempt,
        passed: Int, total: Int, now: Long,
    ): Pair<BattleData, List<Trophy>> {
        val used = timeUsedMs(a, now)
        val solved = total > 0 && passed >= total
        val entry = BattleHistoryEntry(
            challengeId = ch.id, title = ch.title, difficulty = ch.difficulty,
            finishedAt = a.submittedAt ?: now,
            score = score(passed, total, used, a.timeLimitMs, a.timedOut),
            passed = passed.coerceIn(0, total), total = total, solved = solved,
            timedOut = a.timedOut, timeUsedMs = used,
        )
        return record(data, catalog, entry)
    }

    /** Ends the active battle without testing it: score 0, not solved. */
    fun giveUp(data: BattleData, catalog: BattleCatalog, ch: BattleChallenge, a: BattleAttempt, now: Long): Pair<BattleData, List<Trophy>> {
        val entry = BattleHistoryEntry(
            challengeId = ch.id, title = ch.title, difficulty = ch.difficulty, finishedAt = minOf(now, a.deadline),
            score = 0, passed = 0, total = ch.tests.size, solved = false, gaveUp = true, timeUsedMs = timeUsedMs(a, now),
        )
        return record(data, catalog, entry)
    }

    private fun record(data: BattleData, catalog: BattleCatalog, entry: BattleHistoryEntry): Pair<BattleData, List<Trophy>> {
        val prev = data.records[entry.challengeId]
        val old = prev ?: BattleRecord(entry.challengeId)
        val rec = old.copy(
            attempts = old.attempts + 1,
            solves = old.solves + if (entry.solved) 1 else 0,
            bestScore = maxOf(old.bestScore, entry.score),
            bestTimeMs = if (entry.solved) minOf(old.bestTimeMs ?: Long.MAX_VALUE, entry.timeUsedMs) else old.bestTimeMs,
            lastScore = entry.score,
            firstSolvedAt = old.firstSolvedAt ?: entry.finishedAt.takeIf { entry.solved },
        )
        val next = data.copy(
            records = data.records + (entry.challengeId to rec),
            history = (listOf(entry) + data.history).take(MAX_HISTORY),
            active = null,
        )
        val earned = earnedTrophies(next, catalog, entry, prev).filter { it.id !in data.trophies }
        val withTrophies = next.copy(trophies = next.trophies + earned.associate { it.id to entry.finishedAt })
        return withTrophies to earned
    }

    /** Every trophy the learner qualifies for after [entry] ([prev] = record before it). */
    fun earnedTrophies(data: BattleData, catalog: BattleCatalog, entry: BattleHistoryEntry, prev: BattleRecord?): Set<Trophy> {
        val out = mutableSetOf<Trophy>()
        val solvedIds = data.records.values.filter { it.solved }.map { it.challengeId }.toSet()
        fun all(d: String) = catalog.of(d).let { list -> list.isNotEmpty() && list.all { it.id in solvedIds } }
        if (solvedIds.isNotEmpty()) out += Trophy.FIRST_WIN
        if (entry.solved && (prev == null || prev.attempts == 0)) out += Trophy.FLAWLESS
        val limit = catalog.challenge(entry.challengeId)?.timeLimitMs
        if (entry.solved && limit != null && entry.timeUsedMs * 2 <= limit) out += Trophy.SPEED_CODER
        if (entry.solved && prev != null && prev.attempts > 0 && prev.solves == 0) out += Trophy.COMEBACK
        if (catalog.of(BattleDifficulty.HARD).any { it.id in solvedIds }) out += Trophy.HARD_HITTER
        if (all(BattleDifficulty.EASY)) out += Trophy.EASY_SWEEP
        if (all(BattleDifficulty.MEDIUM)) out += Trophy.MEDIUM_SWEEP
        if (data.records.values.sumOf { it.attempts } >= DEDICATED_BATTLES) out += Trophy.DEDICATED
        if (catalog.challenges.isNotEmpty() && catalog.challenges.all { it.id in solvedIds }) out += Trophy.BATTLE_MASTER
        return out
    }

    fun summary(data: BattleData, catalog: BattleCatalog): BattleSummary {
        val ids = catalog.challenges.associateBy { it.id }
        val points = data.records.values.sumOf { r -> ids[r.challengeId]?.let { r.bestScore * BattleDifficulty.multiplier(it.difficulty) } ?: 0 }
        val max = catalog.challenges.sumOf { 100 * BattleDifficulty.multiplier(it.difficulty) }
        return BattleSummary(
            solved = data.records.values.count { it.solved && it.challengeId in ids },
            total = catalog.challenges.size,
            battles = data.records.values.sumOf { it.attempts },
            points = points, maxPoints = max,
            trophies = data.trophies.keys.count { Trophy.byId(it) != null },
        )
    }

    /** An attempt whose challenge no longer exists (content update) is dropped, never crashes. */
    fun resolvable(a: BattleAttempt, catalog: BattleCatalog): Boolean = catalog.challenge(a.challengeId) != null

    /** Passed / total of a finished Check, or null when it didn't run every test (stopped, Python problem). */
    fun countsOf(report: CheckReport): Pair<Int, Int>? = if (report.finished && report.total > 0) report.passedCount to report.total else null

    fun formatClock(ms: Long): String {
        val total = (ms.coerceAtLeast(0) + 999) / 1000
        return "%d:%02d".format(total / 60, total % 60)
    }
}

// ─────────────────────────── Storage ───────────────────────────

interface BattleRepository {
    suspend fun load(): BattleData
    suspend fun save(data: BattleData)
}

/**
 * files/premium/battle.json, written through a temporary file (a crash mid-write never loses the old
 * file). A file that can't be read is kept as battle.json.bak and an empty state is used.
 */
class FileBattleRepository(private val dir: File) : BattleRepository {
    private val file get() = File(dir, "battle.json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    override suspend fun load(): BattleData = withContext(Dispatchers.IO) {
        val f = file
        if (!f.exists()) return@withContext BattleData()
        runCatching { json.decodeFromString(BattleData.serializer(), f.readText()) }.getOrElse {
            runCatching { f.copyTo(File(dir, "battle.json.bak"), overwrite = true) }
            BattleData()
        }
    }

    override suspend fun save(data: BattleData) = withContext(Dispatchers.IO) {
        dir.mkdirs()
        val tmp = File(dir, "battle.json.tmp")
        tmp.writeText(json.encodeToString(BattleData.serializer(), data))
        if (!tmp.renameTo(file)) {
            file.delete()
            if (!tmp.renameTo(file)) { tmp.copyTo(file, overwrite = true); tmp.delete() }
        }
    }
}

class MemoryBattleRepository(var stored: BattleData = BattleData()) : BattleRepository {
    override suspend fun load(): BattleData = stored
    override suspend fun save(data: BattleData) { stored = data }
}

/** Shared, in-memory copy of [BattleData]; every change is saved right away, one at a time. */
class BattleStore(private val repo: BattleRepository) {
    private val _data = MutableStateFlow<BattleData?>(null)
    /** Null until first loaded. */
    val data: StateFlow<BattleData?> = _data.asStateFlow()
    private val mutex = Mutex()

    suspend fun load(): BattleData = mutex.withLock { _data.value ?: repo.load().also { _data.value = it } }

    suspend fun update(transform: (BattleData) -> BattleData): BattleData = mutex.withLock {
        val current = _data.value ?: repo.load()
        val next = transform(current)
        _data.value = next
        if (next != current) runCatching { repo.save(next) }
        next
    }
}
