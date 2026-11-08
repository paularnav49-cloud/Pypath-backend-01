package com.pypath.app

import com.pypath.app.data.progress.ProgressUpdates
import com.pypath.app.practical.CheckMode
import com.pypath.app.practical.CheckReport
import com.pypath.app.practical.OutputCheck
import com.pypath.app.practical.PracticalCatalogParser
import com.pypath.app.practical.PracticalTask
import com.pypath.app.practical.RunEnd
import com.pypath.app.practical.RunOutcome
import com.pypath.app.practical.TaskTest
import com.pypath.app.practical.TestRun
import com.pypath.app.practical.evaluateTest
import com.pypath.app.practical.failureReason
import com.pypath.app.practical.runEndFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Automatic checking of Practical tasks (Batch 2): normalization, check modes, results, storage. */
class TaskCheckTest {

    private val catalog = PracticalCatalogParser.parse(File("src/main/assets/practical/tasks.json").readText())

    private fun task(mode: String = "exact", mustContain: List<String> = emptyList(), pattern: String? = null, vararg tests: TaskTest) =
        PracticalTask(id = "t", title = "T", instructions = "i", checkMode = mode, mustContain = mustContain, pattern = pattern, tests = tests.toList())

    private fun ok(output: String) = TestRun(output, RunEnd.FINISHED)

    // ───────────── normalize ─────────────

    @Test fun normalizeWindowsLineEndings() {
        assertEquals("a\nb", OutputCheck.normalize("a\r\nb\r\n"))
    }

    @Test fun normalizeStripsTrailingSpacesOnEveryLine() {
        assertEquals("Name: Asha\nHello Asha", OutputCheck.normalize("Name: Asha  \nHello Asha\t \n"))
    }

    @Test fun normalizeDropsLeadingAndTrailingBlankLinesOnly() {
        assertEquals("a\n\nb", OutputCheck.normalize("\n  \n\na\n\nb\n\n \n"))
    }

    @Test fun normalizeKeepsLeadingSpacesAndCase() {
        assertEquals("  Indented\nCASE", OutputCheck.normalize("  Indented\nCASE\n"))
        assertTrue(OutputCheck.normalize("Hello") != OutputCheck.normalize("hello"))
    }

    @Test fun normalizeEmpty() {
        assertEquals("", OutputCheck.normalize(""))
        assertEquals("", OutputCheck.normalize("\n\n  \n"))
    }

    // ───────────── modes ─────────────

    @Test fun exactModeUsesNormalization() {
        val t = TaskTest(expectedOutput = "Age: 15\nYou can vote in 3 years")
        val tk = task(tests = arrayOf(t))
        assertTrue(OutputCheck.matches(tk, t, "Age: 15\r\nYou can vote in 3 years   \n\n"))
        assertFalse(OutputCheck.matches(tk, t, "Age: 15\nyou can vote in 3 years"))
        assertFalse(OutputCheck.matches(tk, t, "Age: 15\n\nYou can vote in 3 years"))
    }

    @Test fun unknownModeFallsBackToExact() {
        assertEquals(CheckMode.EXACT, CheckMode.from("weird"))
        assertEquals(CheckMode.EXACT, CheckMode.from(null))
        assertEquals(CheckMode.CONTAINS, CheckMode.from("contains"))
        assertEquals(CheckMode.REGEX, CheckMode.from("regex"))
    }

    @Test fun containsModeTaskAndTestLevel() {
        val t1 = TaskTest(expectedOutput = "x")
        val t2 = TaskTest(expectedOutput = "x", mustContain = listOf("Ravi"))
        val tk = task(mode = "contains", mustContain = listOf("Hello"), tests = arrayOf(t1, t2))
        assertTrue(OutputCheck.matches(tk, t1, "Hello there"))
        assertFalse(OutputCheck.matches(tk, t1, "hello there"))
        assertTrue(OutputCheck.matches(tk, t2, "Hi Ravi"))
        assertEquals(listOf("Ravi"), OutputCheck.missing(tk, t2, "Hi Asha"))
    }

    @Test fun regexModeSearchesNormalizedOutput() {
        val t = TaskTest(expectedOutput = "x", pattern = """\n3\n2\n1\nLift off!\Z""")
        val tk = task(mode = "regex", tests = arrayOf(t))
        assertTrue(OutputCheck.matches(tk, t, "Start: 3\n3\n2\n1\nLift off!\n\n"))
        assertFalse(OutputCheck.matches(tk, t, "Start: 3\n3\n2\n1\n0\nLift off!\n"))
        val bad = TaskTest(expectedOutput = "x", pattern = "(")
        assertFalse(OutputCheck.matches(task(mode = "regex", tests = arrayOf(bad)), bad, "("))
    }

    @Test fun firstDifferenceLine() {
        assertNull(OutputCheck.firstDifferenceLine("a\nb", "a\nb  \n"))
        assertEquals(2, OutputCheck.firstDifferenceLine("a\nb", "a\nc"))
        assertEquals(3, OutputCheck.firstDifferenceLine("a\nb\nc", "a\nb"))
        assertEquals(1, OutputCheck.firstDifferenceLine("a", "b\na"))
    }

    // ───────────── results ─────────────

    @Test fun onlyAFinishedRunCanPass() {
        val t = TaskTest(expectedOutput = "Total: 5")
        val tk = task(tests = arrayOf(t))
        assertTrue(evaluateTest(tk, 0, t, ok("Total: 5\n")).passed)
        for (end in RunEnd.entries.filter { it != RunEnd.FINISHED }) {
            assertFalse(end.name, evaluateTest(tk, 0, t, TestRun("Total: 5\n", end)).passed)
        }
    }

    @Test fun runEndMapping() {
        assertEquals(RunEnd.FINISHED, runEndFor(RunOutcome.Completed("ok"), false).first)
        assertEquals(RunEnd.FINISHED, runEndFor(RunOutcome.Completed("exit:0"), false).first)
        assertEquals(RunEnd.EXITED to 3, runEndFor(RunOutcome.Completed("exit:3"), false))
        assertEquals(RunEnd.ERROR, runEndFor(RunOutcome.Completed("error"), false).first)
        assertEquals(RunEnd.INTERNAL, runEndFor(RunOutcome.Completed("internal:boom"), false).first)
        assertEquals(RunEnd.TIMED_OUT, runEndFor(RunOutcome.TimedOut(10_000), false).first)
        assertEquals(RunEnd.CRASHED, runEndFor(RunOutcome.Crashed, false).first)
        // The check stops a program that asks for more input than the test has.
        assertEquals(RunEnd.NEEDS_MORE_INPUT, runEndFor(RunOutcome.StoppedByUser, true).first)
    }

    @Test fun failureReasons() {
        val t = TaskTest(expectedOutput = "a\nb")
        val tk = task(tests = arrayOf(t))
        assertNull(failureReason(tk, evaluateTest(tk, 0, t, ok("a\nb"))))
        assertEquals(
            "Your output is different from the expected output (first difference on line 2).",
            failureReason(tk, evaluateTest(tk, 0, t, ok("a\nc"))),
        )
        val err = TestRun("a\n", RunEnd.ERROR, errorSummary = "NameError: name 'x' is not defined", errorLine = 2)
        assertEquals("Your program stopped with an error: NameError: name 'x' is not defined", failureReason(tk, evaluateTest(tk, 0, t, err)))
        assertTrue(failureReason(tk, evaluateTest(tk, 0, t, TestRun("", RunEnd.TIMED_OUT)))!!.contains("10 seconds"))
        assertTrue(failureReason(tk, evaluateTest(tk, 0, t, TestRun("", RunEnd.NEEDS_MORE_INPUT)))!!.contains("more input"))
    }

    @Test fun reportNeedsEveryTestToPass() {
        val t = TaskTest(expectedOutput = "x")
        val tk = task(tests = arrayOf(t, t))
        val pass = evaluateTest(tk, 0, t, ok("x"))
        val fail = evaluateTest(tk, 1, t, ok("y"))
        assertFalse(CheckReport(2, listOf(pass)).allPassed) // not finished yet
        assertFalse(CheckReport(2, listOf(pass, fail)).allPassed)
        assertTrue(CheckReport(2, listOf(pass, pass.copy(index = 1))).allPassed)
        assertEquals(1, CheckReport(2, listOf(pass, fail)).passedCount)
        assertFalse(CheckReport(0, emptyList()).allPassed)
    }

    // ───────────── tasks.json ─────────────

    /** Same check as content/validate_tasks.py, but with Kotlin's (Java) regex engine. */
    @Test fun everyExpectedOutputPassesItsOwnTest() {
        val checked = catalog.tasks.filter { it.checkable }
        assertTrue(checked.size >= 20)
        checked.forEach { t ->
            t.tests.forEachIndexed { i, test ->
                assertTrue("${t.id} test ${i + 1}", evaluateTest(t, i, test, ok(test.expectedOutput)).passed)
            }
        }
    }

    @Test fun printingNothingFailsEveryTest() {
        catalog.tasks.filter { it.checkable }.forEach { t ->
            t.tests.forEachIndexed { i, test -> assertFalse("${t.id} test ${i + 1}", evaluateTest(t, i, test, ok("")).passed) }
        }
    }

    @Test fun regexTasksRejectTypicalMistakes() {
        fun t(id: String) = catalog.tasks.first { it.id == id }
        val grades = t("p-grades")
        assertFalse(OutputCheck.matches(grades, grades.tests[1], "Mark: 90\nGrade B")) // 90 must be A
        val voting = t("p-voting")
        assertFalse(OutputCheck.matches(voting, voting.tests[2], "Age: 18\nYou can vote in 0 years")) // > instead of >=
        val countdown = t("p-countdown")
        assertFalse(OutputCheck.matches(countdown, countdown.tests[0], "Start: 3\n3\n2\n1\n0\nLift off!"))
        val greeting = t("p-greeting")
        assertFalse(OutputCheck.matches(greeting, greeting.tests[0], "Enter your name: Arnav\nHello"))
    }

    @Test fun testsAreWellFormed() {
        catalog.tasks.forEach { t ->
            assertTrue("${t.id} has a hint", !t.hint.isNullOrBlank())
            t.tests.forEach { test -> test.stdin.forEach { assertFalse("${t.id}: one input line per entry", '\n' in it || '\r' in it) } }
            if (t.difficulty == "medium" || t.difficulty == "hard") {
                if (t.tests.isNotEmpty()) assertTrue("${t.id} has a hidden test", t.tests.any { it.hidden })
            }
        }
        assertTrue(catalog.tasks.first { it.id == "playground" }.tests.isEmpty())
    }

    @Test fun existingTaskIdsStillPresent() {
        val ids = catalog.tasks.map { it.id }.toSet()
        listOf(
            "playground", "p-first-program", "p-shopping-bill", "p-type-detective", "p-greeting", "p-birthday-maths",
            "p-compare", "p-voting", "p-grades", "p-times-table", "p-countdown", "p-list-stats",
        ).forEach { assertTrue(it, it in ids) }
    }

    @Test fun olderTaskJsonStillParses() {
        val old = """{"schemaVersion":1,"tasks":[{"id":"a","title":"A","instructions":"x"}]}"""
        val t = PracticalCatalogParser.parse(old).tasks.single()
        assertFalse(t.checkable)
        assertEquals(CheckMode.EXACT, t.mode)
    }

    // ───────────── storage ─────────────

    @Test fun oldSavedProgressLoadsWithNoCompletedTasks() {
        val saved = """{"schemaVersion":2,"onboardingCompleted":true,"currentSubLevelId":"l1s2","subLevels":{},"earnedBadges":["l1"]}"""
        val p = ProgressUpdates.decode(saved)
        assertTrue(p.onboardingCompleted)
        assertEquals(setOf("l1"), p.earnedBadges)
        assertTrue(p.completedTasks.isEmpty())
    }

    @Test fun completedTasksAreStoredOnceAndSurviveEncoding() {
        var p = ProgressUpdates.decode(null)
        p = ProgressUpdates.markTaskCompleted(p, "p-piggy-bank")
        p = ProgressUpdates.markTaskCompleted(p, "p-piggy-bank")
        p = ProgressUpdates.markTaskCompleted(p, "p-banner")
        assertEquals(setOf("p-piggy-bank", "p-banner"), p.completedTasks)
        assertEquals(p, ProgressUpdates.decode(ProgressUpdates.encode(p)))
    }
}
