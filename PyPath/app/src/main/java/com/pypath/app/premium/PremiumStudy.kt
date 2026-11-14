package com.pypath.app.premium

import com.pypath.app.data.model.Course
import com.pypath.app.data.model.Question
import com.pypath.app.domain.CourseSnapshot
import com.pypath.app.domain.NodeStatus
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
import kotlin.random.Random

/**
 * Premium Phase 2: what the Project Library and the Quiz Arena remember.
 *
 * Stored in its own small file (files/premium/study.json), apart from course progress, XP, streaks,
 * question history and saved programs, so none of those change. Every field has a default, so a file
 * from an older build (or no file at all) always loads.
 */
@Serializable
data class PremiumStudyData(
    val version: Int = 1,
    /** Premium challenges whose automatic tests all passed. */
    val passedChallenges: Set<String> = emptySet(),
    /** Code typed in Premium project / challenge editors, by workspace task id. */
    val drafts: Map<String, String> = emptyMap(),
    /** Latest finished result per Arena quiz key (e.g. "timed-l3", "hard-loops", "mock-core"). */
    val results: Map<String, ArenaResult> = emptyMap(),
    /** Best percentage per Arena quiz key. */
    val best: Map<String, Int> = emptyMap(),
    /** The unfinished Arena attempt, if any (kept so the timer survives leaving or reopening the app). */
    val active: ArenaAttempt? = null,
)

object ArenaMode {
    const val TIMED = "timed"
    const val HARD = "hard"
    const val MOCK = "mock"
}

@Serializable
data class ArenaAnswer(val questionId: String, val correct: Boolean, val answer: String? = null)

/** An attempt in progress. [deadline] is wall-clock time in ms; null means untimed. */
@Serializable
data class ArenaAttempt(
    val key: String,
    val mode: String,
    val title: String,
    val questionIds: List<String>,
    val startedAt: Long,
    val timeLimitMs: Long? = null,
    val deadline: Long? = null,
    val answers: List<ArenaAnswer> = emptyList(),
) {
    val timed: Boolean get() = deadline != null
    val answeredCount: Int get() = answers.size
    val isComplete: Boolean get() = answers.size >= questionIds.size
}

@Serializable
data class ArenaItem(
    val questionId: String,
    val topic: String,
    val correct: Boolean,
    val answered: Boolean,
    val answer: String? = null,
)

@Serializable
data class TopicScore(val topic: String, val correct: Int, val total: Int) {
    val percent: Int get() = if (total == 0) 0 else correct * 100 / total
}

@Serializable
data class ArenaResult(
    val key: String,
    val mode: String,
    val title: String,
    val startedAt: Long,
    val finishedAt: Long,
    val timeLimitMs: Long? = null,
    /** True when the time ran out and the answers were submitted automatically. */
    val timedOut: Boolean = false,
    val items: List<ArenaItem> = emptyList(),
) {
    val total: Int get() = items.size
    val correct: Int get() = items.count { it.correct }
    val answered: Int get() = items.count { it.answered }
    /** Score: correct answers out of every question. */
    val percent: Int get() = if (total == 0) 0 else correct * 100 / total
    /** Accuracy: correct answers out of the questions actually answered. */
    val accuracy: Int get() = if (answered == 0) 0 else correct * 100 / answered
    val timeUsedMs: Long get() = (finishedAt - startedAt).coerceAtLeast(0).let { used -> timeLimitMs?.let { minOf(used, it) } ?: used }

    /** Results per topic, in the order topics first appear. */
    fun topics(): List<TopicScore> = items.groupBy { it.topic }.map { (t, list) -> TopicScore(t, list.count { it.correct }, list.size) }
}

/** One question the Arena can ask, with the topic used for topic-wise results. */
data class ArenaQuestion(
    val question: Question,
    val levelNumber: Int,
    /** e.g. "Level 3 · Loops" (mock tests). */
    val levelTopic: String,
    /** e.g. "3.1 for loops" (timed quizzes) or the difficult set's title. */
    val subTopic: String,
)

/** Every question the Arena can use, indexed by id: the bundled course quizzes plus the difficult sets. */
class ArenaPool(course: Course, val catalog: ArenaCatalog) {
    val byId: Map<String, ArenaQuestion>
    /** Course quiz questions per level number (levels without questions are absent). */
    val levels: Map<Int, List<ArenaQuestion>>
    val levelTitles: Map<Int, String>

    init {
        val map = LinkedHashMap<String, ArenaQuestion>()
        val perLevel = LinkedHashMap<Int, MutableList<ArenaQuestion>>()
        val titles = LinkedHashMap<Int, String>()
        course.levels.forEach { lvl ->
            val topic = "Level ${lvl.number} · ${lvl.title.substringBefore(" (")}"
            lvl.subLevels.forEach { s ->
                s.quiz?.questions.orEmpty().forEach { q ->
                    val aq = ArenaQuestion(q, lvl.number, topic, "${s.code} ${s.title}")
                    if (q.id !in map) {
                        map[q.id] = aq
                        perLevel.getOrPut(lvl.number) { mutableListOf() }.add(aq)
                        titles[lvl.number] = lvl.title.substringBefore(" (")
                    }
                }
            }
        }
        catalog.hardSets.forEach { set ->
            set.questions.forEach { q -> map.putIfAbsent(q.id, ArenaQuestion(q, 0, set.title, set.title)) }
        }
        byId = map
        levels = perLevel
        levelTitles = titles
    }

    /** Levels that have enough questions for a timed quiz. */
    fun timedLevels(): List<Int> = levels.filter { it.value.size >= catalog.timed.questionCount }.keys.sorted()
}

/** Pure Arena rules (no Android types, covered by JVM unit tests). */
object ArenaEngine {
    const val MAX_DRAFT = 200_000

    fun timedKey(level: Int) = "${ArenaMode.TIMED}-l$level"
    fun hardKey(set: HardSet) = "${ArenaMode.HARD}-${set.id}"
    fun mockKey(mock: MockDef) = mock.id

    /** [count] random questions from one level; [seed] makes the choice repeatable (tests, restore). */
    fun timedQuestions(pool: ArenaPool, level: Int, count: Int, seed: Long): List<String> =
        pool.levels[level].orEmpty().shuffled(Random(seed)).take(count).map { it.question.id }

    /** A fixed selection per mock test: [MockDef.perLevel] questions from each level, levels in order. */
    fun mockQuestions(pool: ArenaPool, mock: MockDef): List<String> = mock.levels.flatMap { lv ->
        pool.levels[lv].orEmpty().shuffled(Random(mock.seed * 100 + lv)).take(mock.perLevel).map { it.question.id }
    }

    fun hardQuestions(set: HardSet): List<String> = set.questions.map { it.id }

    /** A difficult set unlocks once its sub-level is completed (only concepts already taught). */
    fun hardUnlocked(set: HardSet, snapshot: CourseSnapshot): Boolean =
        set.afterSubLevel == null || snapshot.subLevel(set.afterSubLevel)?.status == NodeStatus.COMPLETED

    fun newAttempt(
        key: String, mode: String, title: String, questionIds: List<String>, now: Long, timeLimitMs: Long?,
    ): ArenaAttempt = ArenaAttempt(
        key = key, mode = mode, title = title, questionIds = questionIds, startedAt = now,
        timeLimitMs = timeLimitMs, deadline = timeLimitMs?.let { now + it },
    )

    /**
     * Time left. Never more than the time limit, so moving the phone clock backwards can't add time;
     * moving it forwards can only end the attempt early.
     */
    fun remainingMs(a: ArenaAttempt, now: Long): Long? {
        val deadline = a.deadline ?: return null
        val left = (deadline - now).coerceAtLeast(0)
        return a.timeLimitMs?.let { minOf(left, it) } ?: left
    }

    fun isExpired(a: ArenaAttempt, now: Long): Boolean = remainingMs(a, now) == 0L

    /** Records one answer. Ignored when the question was already answered, isn't part of the attempt, or time is up. */
    fun record(a: ArenaAttempt, answer: ArenaAnswer, now: Long): ArenaAttempt {
        if (answer.questionId !in a.questionIds) return a
        if (a.answers.any { it.questionId == answer.questionId }) return a
        if (isExpired(a, now)) return a
        return a.copy(answers = a.answers + answer)
    }

    /** Index of the next unanswered question (questions are answered in order). */
    fun nextIndex(a: ArenaAttempt): Int = a.answers.size.coerceAtMost(a.questionIds.size)

    /** Turns an attempt into a result. Unanswered questions count as wrong. */
    fun score(a: ArenaAttempt, pool: ArenaPool, now: Long, timedOut: Boolean): ArenaResult {
        val answers = a.answers.associateBy { it.questionId }
        val items = a.questionIds.map { id ->
            val q = pool.byId[id]
            val topic = when {
                q == null -> "Other"
                a.mode == ArenaMode.MOCK -> q.levelTopic
                else -> q.subTopic
            }
            val ans = answers[id]
            ArenaItem(id, topic, correct = ans?.correct == true, answered = ans != null, answer = ans?.answer)
        }
        val finished = if (timedOut && a.deadline != null) minOf(now, a.deadline) else now
        return ArenaResult(a.key, a.mode, a.title, a.startedAt, finished.coerceAtLeast(a.startedAt), a.timeLimitMs, timedOut, items)
    }

    /** Stores [result] as the latest for its key, updates the best score and clears the active attempt. */
    fun finish(data: PremiumStudyData, result: ArenaResult): PremiumStudyData {
        val best = maxOf(data.best[result.key] ?: 0, result.percent)
        return data.copy(results = data.results + (result.key to result), best = data.best + (result.key to best), active = null)
    }

    /** Questions of an attempt that still exist in the bundled content (content updates never crash a restore). */
    fun resolvable(a: ArenaAttempt, pool: ArenaPool): Boolean = a.questionIds.isNotEmpty() && a.questionIds.all { it in pool.byId }

    fun formatClock(ms: Long): String {
        val total = (ms + 999) / 1000
        return "%d:%02d".format(total / 60, total % 60)
    }
}

// ─────────────────────────── Storage ───────────────────────────

interface PremiumStudyRepository {
    suspend fun load(): PremiumStudyData
    suspend fun save(data: PremiumStudyData)
}

/**
 * files/premium/study.json, written through a temporary file (a crash mid-write never loses the
 * old file). A file that can't be read is kept as study.json.bak and an empty state is used.
 */
class FilePremiumStudyRepository(private val dir: File) : PremiumStudyRepository {
    private val file get() = File(dir, "study.json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    override suspend fun load(): PremiumStudyData = withContext(Dispatchers.IO) {
        val f = file
        if (!f.exists()) return@withContext PremiumStudyData()
        runCatching { json.decodeFromString(PremiumStudyData.serializer(), f.readText()) }.getOrElse {
            runCatching { f.copyTo(File(dir, "study.json.bak"), overwrite = true) }
            PremiumStudyData()
        }
    }

    override suspend fun save(data: PremiumStudyData) = withContext(Dispatchers.IO) {
        dir.mkdirs()
        val tmp = File(dir, "study.json.tmp")
        tmp.writeText(json.encodeToString(PremiumStudyData.serializer(), data))
        if (!tmp.renameTo(file)) {
            file.delete()
            if (!tmp.renameTo(file)) { tmp.copyTo(file, overwrite = true); tmp.delete() }
        }
    }
}

class MemoryPremiumStudyRepository(var stored: PremiumStudyData = PremiumStudyData()) : PremiumStudyRepository {
    override suspend fun load(): PremiumStudyData = stored
    override suspend fun save(data: PremiumStudyData) { stored = data }
}

/** Shared, in-memory copy of [PremiumStudyData]; every change is saved right away, one at a time. */
class PremiumStudyStore(private val repo: PremiumStudyRepository) {
    private val _data = MutableStateFlow<PremiumStudyData?>(null)
    /** Null until first loaded. */
    val data: StateFlow<PremiumStudyData?> = _data.asStateFlow()
    private val mutex = Mutex()

    suspend fun load(): PremiumStudyData = mutex.withLock { _data.value ?: repo.load().also { _data.value = it } }

    suspend fun update(transform: (PremiumStudyData) -> PremiumStudyData): PremiumStudyData = mutex.withLock {
        val current = _data.value ?: repo.load()
        val next = transform(current)
        _data.value = next
        if (next != current) runCatching { repo.save(next) }
        next
    }

    suspend fun markChallengePassed(id: String) = update { it.copy(passedChallenges = it.passedChallenges + id) }

    /** Saves [code] for a workspace; the starter code (or blank) removes the draft. */
    suspend fun saveDraft(taskId: String, code: String, starter: String) = update {
        if (code == starter || code.isBlank()) it.copy(drafts = it.drafts - taskId)
        else it.copy(drafts = it.drafts + (taskId to code.take(ArenaEngine.MAX_DRAFT)))
    }
}
