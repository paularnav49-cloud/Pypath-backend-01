package com.pypath.app

import com.pypath.app.data.model.QuestionType
import com.pypath.app.domain.CourseSnapshot
import com.pypath.app.domain.QuestionCheck
import com.pypath.app.data.progress.UserProgress
import com.pypath.app.practical.PracticalTask
import com.pypath.app.premium.ArenaAnswer
import com.pypath.app.premium.ArenaEngine
import com.pypath.app.premium.ArenaMode
import com.pypath.app.premium.ArenaPool
import com.pypath.app.premium.ArenaResult
import com.pypath.app.premium.Entitlements
import com.pypath.app.premium.FilePremiumStudyRepository
import com.pypath.app.premium.MemoryPremiumStudyRepository
import com.pypath.app.premium.PremiumContentParser
import com.pypath.app.premium.PremiumFeature
import com.pypath.app.premium.PremiumIds
import com.pypath.app.premium.PremiumProject
import com.pypath.app.premium.PremiumSource
import com.pypath.app.premium.PremiumStudyData
import com.pypath.app.premium.PremiumStudyStore
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** Premium Phase 2: Premium Project Library + Advanced Quiz Arena (content, gating, engine, storage). */
class PremiumPhase2Test {
    private val course = loadTestCourse()
    private val projects = PremiumContentParser.projects(File("src/main/assets/premium/projects.json").readText())
    private val arena = PremiumContentParser.arena(File("src/main/assets/premium/arena.json").readText())
    private val pool = ArenaPool(course, arena)

    // ───────────── Gating ─────────────

    @Test fun freeHasNeitherPhase2Feature() {
        assertFalse(Entitlements.FREE.has(PremiumFeature.PROJECT_LIBRARY))
        assertFalse(Entitlements.FREE.has(PremiumFeature.QUIZ_ARENA))
    }

    @Test fun premiumUnlocksAllFourFeatures() {
        val p = Entitlements.all(PremiumSource.LOCAL_DEV)
        assertEquals(5, PremiumFeature.entries.size) // Code Battle added the fifth

        PremiumFeature.entries.forEach { assertTrue(it.name, p.has(it)) }
    }

    // ───────────── Project Library content ─────────────

    @Test fun projectCollectionsAreComplete() {
        assertTrue(projects.advanced.size >= 5)
        assertTrue(projects.realWorld.size >= 5)
        projects.projects.forEach { p ->
            assertTrue(p.id, p.collection in setOf(PremiumProject.COLLECTION_ADVANCED, PremiumProject.COLLECTION_REAL_WORLD))
            assertTrue(p.id, p.summary.isNotBlank() && p.objectives.isNotEmpty() && p.steps.size >= 3)
            assertTrue(p.id, p.fileName.endsWith(".py"))
            p.recommendedAfter?.let { id -> assertNotNull("${p.id} -> $id", course.levels.flatMap { it.subLevels }.firstOrNull { it.id == id }) }
        }
        assertEquals(projects.projects.size, projects.projects.map { it.id }.toSet().size)
        // Advanced projects go from easy to hard.
        val order = listOf("easy", "medium", "hard")
        val diffs = projects.advanced.map { order.indexOf(it.difficulty) }
        assertEquals(diffs.sorted(), diffs)
    }

    @Test fun realWorldCollectionCoversRequestedKinds() {
        val titles = projects.realWorld.joinToString(" ") { it.title.lowercase() }
        listOf("calculator", "expense", "quiz", "password", "contact").forEach { assertTrue(it, it in titles) }
    }

    @Test fun everyChallengeHasTestsAndBelongsToAProject() {
        assertTrue(projects.challenges.size >= 8)
        projects.challenges.forEach { t ->
            assertTrue(t.id, PremiumIds.isChallenge(t.id))
            assertTrue(t.id, t.checkable && t.tests.size >= 3)
            assertNotNull(t.id, projects.projectOf(t.id))
        }
        projects.projects.flatMap { it.challengeIds }.forEach { assertNotNull(it, projects.challenge(it)) }
    }

    @Test fun premiumIdsNeverCollideWithPracticalTasks() {
        val tasks = Json { ignoreUnknownKeys = true }.decodeFromString<List<PracticalTask>>(
            Json.parseToJsonElement(File("src/main/assets/practical/tasks.json").readText()).let { el ->
                (if (el is kotlinx.serialization.json.JsonObject) el["tasks"]!! else el).toString()
            },
        )
        assertTrue(tasks.isNotEmpty())
        tasks.forEach { assertFalse(it.id, PremiumIds.isPremiumTask(it.id)) }
        projects.projects.forEach { assertTrue(PremiumIds.isPremiumTask(it.workspaceTask().id)) }
        assertFalse(projects.projects.first().workspaceTask().checkable)
    }

    // ───────────── Arena content ─────────────

    @Test fun arenaContentResolves() {
        assertEquals(listOf(3, 5, 10), arena.timed.minuteOptions)
        assertTrue(pool.timedLevels().isNotEmpty())
        arena.hardSets.forEach { set ->
            assertNotNull(set.id, course.levels.flatMap { it.subLevels }.firstOrNull { it.id == set.afterSubLevel })
            assertTrue(set.id, set.questions.size >= 5)
            set.questions.forEach { q ->
                assertTrue(q.id, q.explanation.isNotBlank())
                if (q.kind == QuestionType.MCQ) {
                    assertEquals(q.id, q.options.size, q.options.toSet().size)
                    assertTrue(q.id, q.correctIndex in q.options.indices)
                }
                assertTrue(q.id, pool.byId.containsKey(q.id))
            }
        }
        assertEquals(3, arena.mocks.size)
        arena.mocks.forEach { m ->
            val ids = ArenaEngine.mockQuestions(pool, m)
            assertEquals(m.id, m.questionCount, ids.size)
            assertEquals(m.id, ids.size, ids.toSet().size)
            assertEquals(m.id, ids, ArenaEngine.mockQuestions(pool, m)) // fixed selection
            assertTrue(m.id, ids.map { pool.byId[it]!!.levelNumber }.toSet().size >= 3) // several levels
        }
    }

    @Test fun timedSelectionComesFromOneLevel() {
        val lv = pool.timedLevels().first()
        val ids = ArenaEngine.timedQuestions(pool, lv, arena.timed.questionCount, 42)
        assertEquals(arena.timed.questionCount, ids.size)
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.all { pool.byId[it]!!.levelNumber == lv })
        assertEquals(ids, ArenaEngine.timedQuestions(pool, lv, arena.timed.questionCount, 42))
    }

    @Test fun hardSetsUnlockOnlyAfterTheirSubLevel() {
        val set = arena.hardSets.first()
        assertFalse(ArenaEngine.hardUnlocked(set, CourseSnapshot(course, UserProgress())))
        val before = course.levels.flatMap { it.subLevels }.map { it.id }.takeWhile { it != set.afterSubLevel } + set.afterSubLevel!!
        assertTrue(ArenaEngine.hardUnlocked(set, CourseSnapshot(course, completed(*before.toTypedArray()))))
    }

    // ───────────── Engine: timing ─────────────

    private fun attempt(now: Long = 1_000_000L, limit: Long? = 60_000L, mode: String = ArenaMode.TIMED) =
        ArenaEngine.newAttempt("k", mode, "T", pool.timedLevels().first().let { ArenaEngine.timedQuestions(pool, it, 5, 1) }, now, limit)

    @Test fun countdownAndExpiry() {
        val a = attempt()
        assertEquals(60_000L, ArenaEngine.remainingMs(a, 1_000_000L))
        assertEquals(30_000L, ArenaEngine.remainingMs(a, 1_030_000L))
        assertFalse(ArenaEngine.isExpired(a, 1_059_999L))
        assertTrue(ArenaEngine.isExpired(a, 1_060_000L))
        assertEquals(0L, ArenaEngine.remainingMs(a, 2_000_000L))
        assertEquals("1:00", ArenaEngine.formatClock(60_000)); assertEquals("0:01", ArenaEngine.formatClock(1)); assertEquals("10:00", ArenaEngine.formatClock(600_000))
    }

    @Test fun movingClockBackDoesNotAddTime() {
        val a = attempt()
        assertEquals(60_000L, ArenaEngine.remainingMs(a, 500_000L))
    }

    @Test fun untimedAttemptNeverExpires() {
        val a = attempt(limit = null, mode = ArenaMode.HARD)
        assertNull(ArenaEngine.remainingMs(a, Long.MAX_VALUE / 2))
        assertFalse(ArenaEngine.isExpired(a, Long.MAX_VALUE / 2))
    }

    @Test fun recordIgnoresDuplicatesForeignAndLateAnswers() {
        var a = attempt()
        val q = a.questionIds[0]
        a = ArenaEngine.record(a, ArenaAnswer(q, true, "x"), 1_001_000L)
        assertEquals(1, a.answeredCount)
        assertEquals(a, ArenaEngine.record(a, ArenaAnswer(q, false, "y"), 1_002_000L))
        assertEquals(a, ArenaEngine.record(a, ArenaAnswer("nope", true), 1_002_000L))
        assertEquals(a, ArenaEngine.record(a, ArenaAnswer(a.questionIds[1], true), 1_070_000L))
        assertEquals(1, ArenaEngine.nextIndex(a))
    }

    // ───────────── Engine: scoring ─────────────

    @Test fun scoreCountsUnansweredAsWrongAndAccuracyUsesAnswered() {
        var a = attempt()
        a = ArenaEngine.record(a, ArenaAnswer(a.questionIds[0], true, "a"), 1_001_000L)
        a = ArenaEngine.record(a, ArenaAnswer(a.questionIds[1], true, "b"), 1_002_000L)
        a = ArenaEngine.record(a, ArenaAnswer(a.questionIds[2], false, "c"), 1_003_000L)
        val r = ArenaEngine.score(a, pool, 1_090_000L, timedOut = true)
        assertEquals(5, r.total); assertEquals(2, r.correct); assertEquals(3, r.answered)
        assertEquals(40, r.percent); assertEquals(66, r.accuracy)
        assertTrue(r.timedOut)
        assertEquals(60_000L, r.timeUsedMs) // capped at the deadline
        assertEquals(2, r.items.count { !it.answered })
        assertEquals(5, r.topics().sumOf { it.total })
    }

    @Test fun mockTopicsAreLevels() {
        val m = arena.mocks.first()
        val ids = ArenaEngine.mockQuestions(pool, m)
        var a = ArenaEngine.newAttempt(m.id, ArenaMode.MOCK, m.title, ids, 0L, m.minutes * 60_000L)
        ids.forEachIndexed { i, id -> a = ArenaEngine.record(a, ArenaAnswer(id, i % 2 == 0), 1_000L) }
        val r = ArenaEngine.score(a, pool, 10_000L, timedOut = false)
        assertEquals(m.levels.size, r.topics().size)
        r.topics().forEach { assertTrue(it.topic, it.topic.startsWith("Level ")); assertEquals(m.perLevel, it.total) }
        assertEquals(ids.size, r.answered)
        assertEquals(r.correct * 100 / r.total, r.percent)
        assertEquals(10_000L, r.timeUsedMs)
    }

    @Test fun finishStoresResultKeepsBestAndClearsActive() {
        val a = attempt()
        val good = ArenaResult("k", ArenaMode.TIMED, "T", 0, 1, items = List(4) { com.pypath.app.premium.ArenaItem("q$it", "t", correct = true, answered = true) })
        val bad = good.copy(items = good.items.mapIndexed { i, it -> it.copy(correct = i == 0) })
        var d = PremiumStudyData(active = a)
        d = ArenaEngine.finish(d, good)
        assertNull(d.active); assertEquals(100, d.best["k"])
        d = ArenaEngine.finish(d, bad)
        assertEquals(25, d.results["k"]!!.percent); assertEquals(100, d.best["k"])
    }

    @Test fun resolvableRejectsRemovedQuestions() {
        val a = attempt()
        assertTrue(ArenaEngine.resolvable(a, pool))
        assertFalse(ArenaEngine.resolvable(a.copy(questionIds = a.questionIds + "gone"), pool))
    }

    @Test fun correctAnswerTextExistsForEveryArenaQuestion() {
        pool.byId.values.forEach { assertTrue(it.question.id, QuestionCheck.correctAnswerText(it.question).isNotBlank()) }
    }

    // ───────────── Storage ─────────────

    @Test fun storeRoundTripsAndKeepsAttemptAcrossRestart(): Unit = runBlocking {
        val dir = Files.createTempDirectory("study").toFile()
        val a = attempt()
        PremiumStudyStore(FilePremiumStudyRepository(dir)).apply {
            load()
            update { it.copy(active = a) }
            markChallengePassed("premium-ch-calculator")
            saveDraft("premium-pr-rw-calculator", "print(1)", "")
        }
        val again = PremiumStudyStore(FilePremiumStudyRepository(dir)).load()
        assertEquals(a, again.active)
        assertEquals(setOf("premium-ch-calculator"), again.passedChallenges)
        assertEquals("print(1)", again.drafts["premium-pr-rw-calculator"])
        dir.deleteRecursively()
    }

    @Test fun draftEqualToStarterIsRemoved(): Unit = runBlocking {
        val store = PremiumStudyStore(MemoryPremiumStudyRepository())
        store.saveDraft("t", "x = 1", "")
        assertEquals("x = 1", store.load().drafts["t"])
        store.saveDraft("t", "start", "start")
        assertNull(store.load().drafts["t"])
    }

    @Test fun corruptFileFallsBackToEmptyAndIsKept(): Unit = runBlocking {
        val dir = Files.createTempDirectory("study").toFile()
        File(dir, "study.json").writeText("{ not json")
        val d = FilePremiumStudyRepository(dir).load()
        assertEquals(PremiumStudyData(), d)
        assertTrue(dir.listFiles().orEmpty().any { it.name.endsWith(".bak") })
        dir.deleteRecursively()
    }

    @Test fun olderFileWithoutNewFieldsStillLoads(): Unit = runBlocking {
        val dir = Files.createTempDirectory("study").toFile()
        File(dir, "study.json").writeText("""{"passedChallenges":["premium-ch-grades"],"futureField":1}""")
        val d = FilePremiumStudyRepository(dir).load()
        assertEquals(setOf("premium-ch-grades"), d.passedChallenges)
        assertNull(d.active)
        dir.deleteRecursively()
    }
}
