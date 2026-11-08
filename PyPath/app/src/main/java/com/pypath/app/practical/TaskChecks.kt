package com.pypath.app.practical

/**
 * Pure checking rules for Practical tasks (no Android types, so JVM unit tests cover them).
 *
 * The same rules are implemented in content/validate_tasks.py, which proves that every task's
 * reference solution passes its own tests. Keep both in sync.
 *
 * What is compared: the console transcript of one run — everything the program printed to
 * stdout, with each input() answer echoed followed by a newline, exactly like the console shows
 * after a manual Run. Error output (stderr) is never compared; a run that ends with an error,
 * a time-out or a crash fails the test whatever it printed.
 */
enum class CheckMode {
    EXACT, CONTAINS, REGEX;

    companion object {
        /** Unknown or missing values fall back to [EXACT]; content/validate_tasks.py rejects them. */
        fun from(value: String?): CheckMode = when (value?.trim()?.lowercase()) {
            "contains" -> CONTAINS
            "regex" -> REGEX
            else -> EXACT
        }
    }
}

object OutputCheck {

    /**
     * Normalization applied to both sides before comparing:
     *  - "\r\n" becomes "\n"
     *  - trailing spaces and tabs are removed from every line
     *  - blank lines at the very start and very end are removed
     * Comparison stays case-sensitive and blank lines in the middle still count.
     */
    fun normalize(text: String): String {
        val lines = text.replace("\r\n", "\n").split("\n").map { it.trimEnd(' ', '\t') }
        var start = 0
        var end = lines.size
        while (start < end && lines[start].isEmpty()) start++
        while (end > start && lines[end - 1].isEmpty()) end--
        return lines.subList(start, end).joinToString("\n")
    }

    /** A test's own non-empty value overrides the task's (same as the Python validator). */
    fun needles(task: PracticalTask, test: TaskTest): List<String> = test.mustContain?.takeIf { it.isNotEmpty() } ?: task.mustContain

    fun pattern(task: PracticalTask, test: TaskTest): String? = test.pattern?.takeIf { it.isNotEmpty() } ?: task.pattern?.takeIf { it.isNotEmpty() }

    /** True when [actual] satisfies [test] under the task's check mode. */
    fun matches(task: PracticalTask, test: TaskTest, actual: String): Boolean {
        val out = normalize(actual)
        return when (task.mode) {
            CheckMode.EXACT -> out == normalize(test.expectedOutput)
            CheckMode.CONTAINS -> needles(task, test).all { it in out }
            CheckMode.REGEX -> {
                val p = pattern(task, test) ?: return false
                runCatching { Regex(p).containsMatchIn(out) }.getOrDefault(false)
            }
        }
    }

    /** 1-based number of the first line that differs after normalization, or null if equal. */
    fun firstDifferenceLine(expected: String, actual: String): Int? {
        val e = normalize(expected).split("\n")
        val a = normalize(actual).split("\n")
        if (e == a) return null
        val n = minOf(e.size, a.size)
        for (i in 0 until n) if (e[i] != a[i]) return i + 1
        return n + 1
    }

    /** Strings from [needles] that are missing from [actual]. */
    fun missing(task: PracticalTask, test: TaskTest, actual: String): List<String> {
        val out = normalize(actual)
        return needles(task, test).filter { it !in out }
    }
}

/** How one test run ended. */
enum class RunEnd { FINISHED, ERROR, EXITED, TIMED_OUT, CRASHED, NEEDS_MORE_INPUT, INTERNAL }

/** Raw facts about one run of the learner's code for one test. */
data class TestRun(
    /** Console transcript: stdout plus echoed input lines. */
    val output: String,
    val end: RunEnd,
    /** e.g. "NameError: name 'x' is not defined" for [RunEnd.ERROR]; message for [RunEnd.INTERNAL]. */
    val errorSummary: String? = null,
    /** Line in main.py where the error happened. */
    val errorLine: Int? = null,
    val exitCode: Int = 0,
)

data class TestResult(
    /** 0-based position of the test in the task. */
    val index: Int,
    val test: TaskTest,
    val passed: Boolean,
    val run: TestRun,
)

/** Maps the runner's final status to [RunEnd]. Status strings come from pypath_runner.run(). */
fun runEndFor(outcome: RunOutcome, ranOutOfInput: Boolean): Pair<RunEnd, Int> = when {
    ranOutOfInput -> RunEnd.NEEDS_MORE_INPUT to 0
    outcome is RunOutcome.Completed -> when {
        outcome.status == "ok" -> RunEnd.FINISHED to 0
        outcome.status == "error" -> RunEnd.ERROR to 0
        outcome.status.startsWith("exit:") -> {
            val code = outcome.status.removePrefix("exit:").toIntOrNull() ?: 1
            (if (code == 0) RunEnd.FINISHED else RunEnd.EXITED) to code
        }
        else -> RunEnd.INTERNAL to 0
    }
    outcome is RunOutcome.TimedOut -> RunEnd.TIMED_OUT to 0
    outcome == RunOutcome.Crashed -> RunEnd.CRASHED to 0
    else -> RunEnd.INTERNAL to 0 // StoppedByUser is handled by the caller (it cancels the check)
}

/** A test passes only when the program finished normally and its output matches. */
fun evaluateTest(task: PracticalTask, index: Int, test: TaskTest, run: TestRun): TestResult =
    TestResult(index, test, run.end == RunEnd.FINISHED && OutputCheck.matches(task, test, run.output), run)

/** Why a visible test failed, in beginner-friendly words. Null when it passed. */
fun failureReason(task: PracticalTask, result: TestResult, timeLimitSeconds: Long = PythonRunner.DEFAULT_TIME_LIMIT_MS / 1000): String? {
    if (result.passed) return null
    val run = result.run
    return when (run.end) {
        RunEnd.NEEDS_MORE_INPUT -> "Your program asked for more input than this test gives. Check how many times you call input()."
        RunEnd.TIMED_OUT -> "Your program ran for more than $timeLimitSeconds seconds and was stopped. Look for a loop that never ends."
        RunEnd.ERROR -> "Your program stopped with an error" + (run.errorSummary?.let { ": $it" } ?: ".")
        RunEnd.EXITED -> "Your program called exit() with code ${run.exitCode} before it finished."
        RunEnd.CRASHED -> "Python stopped unexpectedly (it may have run out of memory)."
        RunEnd.INTERNAL -> "Python could not run the code" + (run.errorSummary?.let { ": $it" } ?: ".")
        RunEnd.FINISHED -> when (task.mode) {
            CheckMode.EXACT -> {
                val line = OutputCheck.firstDifferenceLine(result.test.expectedOutput, run.output)
                "Your output is different from the expected output" + (line?.let { " (first difference on line $it)." } ?: ".")
            }
            CheckMode.CONTAINS -> {
                val miss = OutputCheck.missing(task, result.test, run.output)
                "Your output must include " + miss.joinToString(", ") { "\"$it\"" } + "."
            }
            CheckMode.REGEX -> "Your output doesn't match what this test looks for" + (result.test.description?.let { ": $it." } ?: ".")
        }
    }
}

/** Results of pressing Check once. */
data class CheckReport(val total: Int, val results: List<TestResult>) {
    val finished: Boolean get() = results.size == total
    val passedCount: Int get() = results.count { it.passed }
    val allPassed: Boolean get() = total > 0 && finished && results.all { it.passed }
}
