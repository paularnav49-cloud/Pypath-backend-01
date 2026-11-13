package com.pypath.app

import com.pypath.app.practical.CheckReport
import com.pypath.app.practical.RunEnd
import com.pypath.app.practical.TestRun
import com.pypath.app.practical.evaluateTest
import com.pypath.app.premium.BattleContentParser
import com.pypath.app.premium.BattleData
import com.pypath.app.premium.BattleDifficulty
import com.pypath.app.premium.BattleEngine
import com.pypath.app.premium.BattleStore
import com.pypath.app.premium.Entitlements
import com.pypath.app.premium.FileBattleRepository
import com.pypath.app.premium.PremiumFeature
import com.pypath.app.premium.PremiumIds
import com.pypath.app.premium.PremiumSource
import com.pypath.app.premium.Trophy
import com.pypath.app.ui.screens.premium.hiddenFailure
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** Premium Code Battle: content, gating, timer, scoring, records, trophies, storage, test evaluation. */
class CodeBattleTest {
    private val catalog = BattleContentParser.parse(File("src/main/assets/premium/battle.json").readText())
    private val t0 = 1_000_000L

    // ───────────── Gating ─────────────

    @Test fun codeBattleIsPremiumOnly() {
        assertFalse(Entitlements.FREE.has(PremiumFeature.CODE_BATTLE))
        assertTrue(Entitlements.all(PremiumSource.LOCAL_DEV).has(PremiumFeature.CODE_BATTLE))
    }

    // ───────────── Content ─────────────

    @Test fun fiveChallengesPerDifficultyWithIncreasingTime() {
        assertEquals(15, catalog.challenges.size)
        val minutes = BattleDifficulty.all.map { d ->
            val list = catalog.of(d)
            assertEquals(d, 5, list.size)
            assertEquals(d, (1..5).toList(), list.map { it.order })
            list.map { it.minutes }.distinct().single()
        }
        assertTrue(minutes.zipWithNext().all { (a, b) -> a < b })
        assertEquals(catalog.challenges.size, catalog.challenges.map { it.id }.toSet().size)
    }

    @Test fun everyChallengeIsCompleteAndHasHiddenTests() {
        catalog.challenges.forEach { ch ->
            assertTrue(ch.id, PremiumIds.isBattle(ch.id))
            assertTrue(ch.id, ch.title.isNotBlank() && ch.description.isNotBlank())
            assertTrue(ch.id, ch.requirements.isNotEmpty())
            assertTrue(ch.id, ch.hiddenCount >= 3)
            val visible = ch.tests.filter { !it.hidden }
            assertEquals(ch.id, visible.size, ch.examples.size)
            assertTrue(ch.id, ch.examples.isNotEmpty())
            // Each example shows the input lines and the program's output (expected minus echoed input).
            ch.examples.zip(visible).forEach { (ex, t) ->
                assertEquals(ch.id, t.stdin.joinToString("\n"), ex.input)
                assertTrue(ch.id, t.expectedOutput.endsWith(ex.output))
            }
            val task = ch.task()
            assertTrue(ch.id, task.checkable)
            assertEquals(ch.id, task.id)
            assertEquals(ch.tests, task.tests)
        }
        assertFalse(PremiumIds.isBattle("premium-ch-x"))
    }

    // ───────────── Timer ─────────────

    private val ch get() = catalog.of(BattleDifficulty.EASY).first()
    private val hard get() = catalog.of(BattleDifficulty.HARD).first()

    @Test fun timerCountsDownAndClamps() {
        val a = BattleEngine.newAttempt(ch, t0)
        assertEquals(ch.timeLimitMs, BattleEngine.remainingMs(a, t0))
        assertEquals(ch.timeLimitMs, BattleEngine.remainingMs(a, t0 - 60_000)) // clock moved back: no extra time
        assertEquals(ch.timeLimitMs - 30_000, BattleEngine.remainingMs(a, t0 + 30_000))
        assertFalse(BattleEngine.isExpired(a, t0 + ch.timeLimitMs - 1))
        assertTrue(BattleEngine.isExpired(a, t0 + ch.timeLimitMs))
        assertEquals(0, BattleEngine.remainingMs(a, t0 + ch.timeLimitMs * 3))
        assertEquals("5:00", BattleEngine.formatClock(ch.timeLimitMs))
        assertEquals("0:01", BattleEngine.formatClock(1))
        assertEquals("0:00", BattleEngine.formatClock(-5))
    }

    @Test fun submitLocksCodeAndTimeoutIsCapped() {
        val a = BattleEngine.newAttempt(ch, t0)
        val s = BattleEngine.submit(a, "print(1)", t0 + 60_000, timedOut = false)
        assertTrue(s.submitted)
        assertFalse(s.timedOut)
        assertEquals("print(1)", s.submittedCode)
        assertEquals(s, BattleEngine.submit(s, "other", t0 + 70_000, false)) // second submit ignored
        assertEquals(s, BattleEngine.saveCode(s, "changed")) // read-only after submit
        assertFalse(BattleEngine.isExpired(s, t0 + ch.timeLimitMs * 2))

        val late = BattleEngine.submit(a, "x", t0 + ch.timeLimitMs + 99_000, timedOut = false)
        assertTrue(late.timedOut)
        assertEquals(a.deadline, late.submittedAt)
        assertEquals(ch.timeLimitMs, BattleEngine.timeUsedMs(late, t0 + 10 * ch.timeLimitMs))
    }

    // ───────────── Score ─────────────

    @Test fun scoreRewardsTestsAndTimeLeft() {
        val limit = 300_000L
        assertEquals(100, BattleEngine.score(6, 6, 0, limit, false))
        assertEquals(90, BattleEngine.score(6, 6, 150_000, limit, false))
        assertEquals(80, BattleEngine.score(6, 6, limit, limit, false))
        assertEquals(80, BattleEngine.score(6, 6, 10_000, limit, timedOut = true)) // no time bonus after timeout
        assertEquals(40, BattleEngine.score(3, 6, 0, limit, false)) // no time bonus unless every test passes
        assertEquals(0, BattleEngine.score(0, 6, 0, limit, false))
        assertEquals(0, BattleEngine.score(0, 0, 0, limit, false))
        assertEquals(100, BattleEngine.score(9, 6, -5, limit, false)) // clamped
    }

    // ───────────── Records, history and trophies ─────────────

    private fun play(data: BattleData, c: com.pypath.app.premium.BattleChallenge, passed: Int, usedMs: Long, start: Long = t0): Pair<BattleData, List<Trophy>> {
        val a = BattleEngine.submit(BattleEngine.newAttempt(c, start), "code", start + usedMs, false)
        return BattleEngine.finish(data.copy(active = a), catalog, c, a, passed, c.tests.size, start + usedMs + 5_000)
    }

    @Test fun finishStoresRecordHistoryAndClearsActive() {
        val (d1, t1) = play(BattleData(), ch, 2, 60_000)
        assertNull(d1.active)
        val r = d1.records.getValue(ch.id)
        assertEquals(1, r.attempts)
        assertFalse(r.solved)
        assertEquals(BattleEngine.score(2, ch.tests.size, 60_000, ch.timeLimitMs, false), r.bestScore)
        assertNull(r.bestTimeMs)
        assertEquals(1, d1.history.size)
        val h = d1.history.first()
        assertEquals(ch.difficulty, h.difficulty)
        assertFalse(h.solved)
        assertEquals(ch.tests.size - 2, h.failed)
        assertTrue(t1.isEmpty())

        val (d2, t2) = play(d1, ch, ch.tests.size, 120_000)
        val r2 = d2.records.getValue(ch.id)
        assertEquals(2, r2.attempts)
        assertEquals(1, r2.solves)
        assertEquals(120_000L, r2.bestTimeMs)
        assertEquals(2, d2.history.size)
        assertTrue(d2.history.first().solved) // newest first
        assertTrue(Trophy.FIRST_WIN in t2)
        assertTrue(Trophy.COMEBACK in t2)
        assertFalse(Trophy.FLAWLESS in t2)
        assertTrue(Trophy.SPEED_CODER in t2) // 2 of 5 minutes

        // Lower score later never lowers the best.
        val (d3, t3) = play(d2, ch, 1, 10_000)
        assertEquals(r2.bestScore, d3.records.getValue(ch.id).bestScore)
        assertTrue(t3.isEmpty()) // trophies are given only once
    }

    @Test fun flawlessHardAndSweepsAndMaster() {
        val (d1, t1) = play(BattleData(), hard, hard.tests.size, hard.timeLimitMs - 1_000)
        assertTrue(Trophy.FLAWLESS in t1)
        assertTrue(Trophy.HARD_HITTER in t1)
        assertFalse(Trophy.SPEED_CODER in t1)
        var d = d1
        val got = mutableSetOf<Trophy>().apply { addAll(t1) }
        catalog.of(BattleDifficulty.EASY).forEach { c -> val (n, t) = play(d, c, c.tests.size, c.timeLimitMs); d = n; got += t }
        assertTrue(Trophy.EASY_SWEEP in got)
        assertFalse(Trophy.MEDIUM_SWEEP in got)
        catalog.of(BattleDifficulty.MEDIUM).forEach { c -> val (n, t) = play(d, c, c.tests.size, c.timeLimitMs); d = n; got += t }
        assertTrue(Trophy.MEDIUM_SWEEP in got)
        assertTrue(Trophy.DEDICATED in got) // 11 battles
        assertFalse(Trophy.BATTLE_MASTER in got)
        catalog.of(BattleDifficulty.HARD).drop(1).forEach { c -> val (n, t) = play(d, c, c.tests.size, c.timeLimitMs); d = n; got += t }
        assertTrue(Trophy.BATTLE_MASTER in got)
        assertEquals(Trophy.entries.toSet() - Trophy.COMEBACK - Trophy.SPEED_CODER, got)
        val s = BattleEngine.summary(d, catalog)
        assertEquals(15, s.solved)
        assertEquals(15, s.total)
        assertEquals(15, s.battles)
        assertEquals(d.trophies.size, s.trophies)
        assertTrue(s.points in 1..s.maxPoints)
        assertEquals(100 * (5 * 1 + 5 * 2 + 5 * 3), s.maxPoints)
    }

    @Test fun giveUpCountsAsUnsolvedZero() {
        val a = BattleEngine.newAttempt(ch, t0)
        val (d, t) = BattleEngine.giveUp(BattleData(active = a), catalog, ch, a, t0 + 10_000)
        assertNull(d.active)
        val h = d.history.single()
        assertTrue(h.gaveUp)
        assertEquals(0, h.score)
        assertFalse(h.solved)
        assertEquals(1, d.records.getValue(ch.id).attempts)
        assertTrue(t.isEmpty())
    }

    @Test fun historyIsCapped() {
        var d = BattleData()
        repeat(BattleEngine.MAX_HISTORY + 5) { i -> d = play(d, ch, 0, 1_000, start = t0 + i * 1_000_000L).first }
        assertEquals(BattleEngine.MAX_HISTORY, d.history.size)
        assertEquals(BattleEngine.MAX_HISTORY + 5, d.records.getValue(ch.id).attempts)
    }

    // ───────────── Hidden test evaluation (same checker as Practical) ─────────────

    @Test fun onlyRealPassesCount() {
        val task = ch.task()
        val results = task.tests.mapIndexed { i, test ->
            val run = when (i) {
                0 -> TestRun(test.expectedOutput, RunEnd.FINISHED) // correct
                1 -> TestRun("wrong", RunEnd.FINISHED) // wrong output
                2 -> TestRun(test.expectedOutput, RunEnd.ERROR, "ValueError: bad", 2) // error after right output
                3 -> TestRun("", RunEnd.TIMED_OUT) // endless loop
                4 -> TestRun("", RunEnd.NEEDS_MORE_INPUT)
                else -> TestRun("", RunEnd.INTERNAL)
            }
            evaluateTest(task, i, test, run)
        }
        assertEquals(listOf(true, false, false, false, false), results.take(5).map { it.passed })
        assertTrue(results.drop(5).none { it.passed })
        val report = CheckReport(task.tests.size, results)
        assertEquals(1 to task.tests.size, BattleEngine.countsOf(report))
        assertNull(BattleEngine.countsOf(CheckReport(task.tests.size, results.take(2)))) // unfinished: not scored
        assertEquals("too slow", hiddenFailure(RunEnd.TIMED_OUT))
        assertEquals("error", hiddenFailure(RunEnd.ERROR))
        assertEquals("wrong output", hiddenFailure(RunEnd.FINISHED))
    }

    // ───────────── Storage ─────────────

    @Test fun recordsSurviveRestartAndCorruptFileIsKept() = runBlocking {
        val dir = Files.createTempDirectory("battle").toFile()
        val store = BattleStore(FileBattleRepository(dir))
        store.load()
        val a = BattleEngine.newAttempt(hard, t0)
        store.update { it.copy(active = a) }
        store.update { d -> BattleEngine.finish(d, catalog, hard, BattleEngine.submit(a, "c", t0 + 1000, false), hard.tests.size, hard.tests.size, t0 + 2000).first }
        val saved = store.data.value!!

        val reloaded = BattleStore(FileBattleRepository(dir)).load() // "restart"
        assertEquals(saved, reloaded)
        assertTrue(reloaded.records.getValue(hard.id).solved)
        assertTrue(Trophy.FIRST_WIN.id in reloaded.trophies)

        // An unfinished battle is kept across a restart too (its timer keeps its deadline).
        store.update { it.copy(active = BattleEngine.newAttempt(ch, t0)) }
        val again = BattleStore(FileBattleRepository(dir)).load()
        assertNotNull(again.active)
        assertEquals(t0 + ch.timeLimitMs, again.active!!.deadline)

        File(dir, "battle.json").writeText("{ not json")
        val broken = BattleStore(FileBattleRepository(dir)).load()
        assertEquals(BattleData(), broken)
        assertTrue(File(dir, "battle.json.bak").exists())
        dir.deleteRecursively()
        Unit
    }
}
