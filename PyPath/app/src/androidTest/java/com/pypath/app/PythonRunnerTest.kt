package com.pypath.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pypath.app.practical.EngineState
import com.pypath.app.practical.PythonRunner
import com.pypath.app.practical.RunOutcome
import com.pypath.app.practical.RunnerListener
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * Runs real Python on the device through the same [PythonRunner] / `:python` service the
 * Practical tab uses. Nothing here is mocked.
 */
@RunWith(AndroidJUnit4::class)
class PythonRunnerTest {

    data class Result(val stdout: String, val stderr: String, val prompts: List<String>, val outcome: RunOutcome, val elapsedMs: Long)

    private class Harness : RunnerListener {
        val out = StringBuffer()
        val err = StringBuffer()
        val prompts = mutableListOf<String>()
        val answers = ArrayDeque<String>()
        @Volatile var outcome: RunOutcome? = null
        @Volatile var elapsed = 0L
        @Volatile var done = CountDownLatch(1)
        lateinit var runner: PythonRunner

        override fun onEngineState(state: EngineState) {}
        override fun onStarted() {}
        override fun onOutput(stderr: Boolean, text: String) { (if (stderr) err else out).append(text) }
        override fun onInputRequested(prompt: String) {
            prompts += prompt
            answers.removeFirstOrNull()?.let { runner.sendInput(it) }
        }
        override fun onFinished(outcome: RunOutcome, elapsedMs: Long) {
            this.outcome = outcome; elapsed = elapsedMs; done.countDown()
        }
    }

    private val instr = InstrumentationRegistry.getInstrumentation()
    private val ctx = instr.targetContext
    private val h = Harness()

    @Before fun setUp() = instr.runOnMainSync { h.runner = PythonRunner(ctx, h).also { it.start() } }
    @After fun tearDown() = instr.runOnMainSync { h.runner.release() }

    private fun run(code: String, vararg answers: String, waitSec: Long = 120, onRunning: (() -> Unit)? = null): Result {
        h.out.setLength(0); h.err.setLength(0); h.prompts.clear(); h.answers.clear(); h.answers.addAll(answers)
        h.outcome = null; h.done = CountDownLatch(1)
        instr.runOnMainSync { h.runner.run(code) }
        onRunning?.invoke()
        assertTrue("run did not finish: $code", h.done.await(waitSec, TimeUnit.SECONDS))
        return Result(h.out.toString(), h.err.toString(), h.prompts.toList(), h.outcome!!, h.elapsed)
    }

    private fun Result.ok() = assertEquals("stderr=$stderr", RunOutcome.Completed("ok"), outcome)

    @Test fun test1_helloWorld() {
        val r = run("print(\"Hello World\")")
        r.ok(); assertEquals("Hello World\n", r.stdout)
    }

    @Test fun test2_addition() {
        val r = run("a = 10\nb = 20\nprint(a + b)")
        r.ok(); assertEquals("30\n", r.stdout)
    }

    @Test fun test3_input() {
        val r = run("name = input(\"Enter your name: \")\nprint(\"Hello\", name)", "Arnav")
        r.ok()
        assertEquals(listOf("Enter your name: "), r.prompts)
        assertEquals("Enter your name: Hello Arnav\n", r.stdout) // prompt is echoed by Python itself
    }

    @Test fun test4_nameErrorTraceback() {
        val r = run("print(undefined_variable)")
        assertEquals(RunOutcome.Completed("error"), r.outcome)
        assertTrue(r.stderr, r.stderr.contains("File \"main.py\", line 1"))
        assertTrue(r.stderr, r.stderr.trimEnd().endsWith("NameError: name 'undefined_variable' is not defined"))
        assertFalse("runner internals leaked: ${r.stderr}", r.stderr.contains("pypath_runner"))
    }

    @Test fun test5_loop() {
        val r = run("total = 0\nfor i in range(1, 6):\n    total += i\n    print(i, total)\nprint(\"done\")")
        r.ok(); assertEquals("1 1\n2 3\n3 6\n4 10\n5 15\ndone\n", r.stdout)
    }

    @Test fun test6_infiniteLoopTimesOut_thenRecovers() {
        val r = run("while True:\n    pass", waitSec = 90)
        assertTrue("${r.outcome}", r.outcome is RunOutcome.TimedOut)
        assertTrue(r.elapsedMs >= PythonRunner.DEFAULT_TIME_LIMIT_MS)
        // A fresh interpreter is started automatically; the next run works.
        val again = run("print('alive', 6 * 7)")
        again.ok(); assertEquals("alive 42\n", again.stdout)
    }

    @Test fun test7_stopButtonKillsRunningCode() {
        val r = run("i = 0\nwhile True:\n    i += 1", waitSec = 60, onRunning = {
            Thread.sleep(2500)
            instr.runOnMainSync { h.runner.stop() }
        })
        assertEquals(RunOutcome.StoppedByUser, r.outcome)
        val again = run("print('restarted')")
        again.ok(); assertEquals("restarted\n", again.stdout)
    }

    @Test fun test8_dynamicCodeNeverSeenBefore() {
        repeat(3) {
            val a = Random.nextInt(1, 100_000); val b = Random.nextInt(1, 1_000)
            val r = run("a = $a\nb = $b\nprint(a * b + a // b, a % b)\nprint(str(a)[::-1])")
            r.ok(); assertEquals("${a.toLong() * b + a / b} ${a % b}\n${a.toString().reversed()}\n", r.stdout)
        }
        val r = run("x = 25\ny = 15\nprint(x + y)")
        r.ok(); assertEquals("40\n", r.stdout)
        val r2 = run("x = 100\ny = 200\nprint(x * y)")
        r2.ok(); assertEquals("20000\n", r2.stdout)
    }

    @Test fun test9_syntaxErrorAndMultipleInputs() {
        val s = run("if True\n    print(1)")
        assertEquals(RunOutcome.Completed("error"), s.outcome)
        assertTrue(s.stderr, s.stderr.contains("SyntaxError"))
        val m = run("a = int(input('A: '))\nb = int(input('B: '))\nif a > b:\n    print('A is bigger')\nelif a == b:\n    print('Same')\nelse:\n    print('B is bigger by', b - a)", "6", "19")
        m.ok(); assertEquals(listOf("A: ", "B: "), m.prompts); assertEquals("A: B: B is bigger by 13\n", m.stdout)
    }

    @Test fun test10_stdlibAndExit() {
        val r = run("import math, random, json, datetime, statistics\nfrom collections import Counter\nprint(math.factorial(10), json.dumps({'k': [1, 2]}), statistics.mean([2, 4, 9]))\nprint(Counter('banana').most_common(1))")
        r.ok(); assertEquals("3628800 {\"k\": [1, 2]} 5\n[('a', 3)]\n", r.stdout)
        val e = run("import sys\nprint('bye')\nsys.exit(3)")
        assertEquals(RunOutcome.Completed("exit:3"), e.outcome)
    }

    @Test fun test11_sandbox() {
        val secret = File(ctx.filesDir, "secret.txt").apply { writeText("token=abc123") }
        val r = run("print(open(r'${secret.absolutePath}').read())")
        assertEquals(RunOutcome.Completed("error"), r.outcome)
        assertFalse(r.stdout.contains("abc123"))
        assertTrue(r.stderr, r.stderr.contains("SandboxViolation"))

        val ls = run("import os\nprint(os.listdir(r'${ctx.filesDir.absolutePath}'))")
        assertTrue(ls.stderr, ls.stderr.contains("SandboxViolation"))

        val net = run("import socket")
        assertTrue(net.stderr, net.stderr.contains("not available"))
        val java = run("from java import jclass")
        assertTrue(java.stderr, java.stderr.contains("not available"))
        val mods = run("import sys\nprint([m for m in sys.modules if m.split('.')[0] in ('java', 'chaquopy', 'android')])")
        mods.ok(); assertEquals("[]\n", mods.stdout)
        val proc = run("import os\nos.system('ls')")
        assertTrue(proc.stderr, proc.stderr.contains("SandboxViolation"))

        // Learner code may use its own scratch directory, which is wiped after every run.
        val scratch = run("open('notes.txt', 'w').write('hi')\nprint(open('notes.txt').read())")
        scratch.ok(); assertEquals("hi\n", scratch.stdout)
        val gone = run("import os\nprint(os.path.exists('notes.txt'))")
        gone.ok(); assertEquals("False\n", gone.stdout)
        secret.delete()
    }

    @Test fun test12_largeOutputAndRecursion() {
        val big = run("for i in range(20000):\n    print(i)")
        big.ok(); assertTrue(big.stdout.endsWith("19999\n")); assertEquals(20000, big.stdout.lines().size - 1)
        val rec = run("def f(n):\n    return f(n + 1)\nf(0)")
        assertTrue(rec.stderr, rec.stderr.contains("RecursionError"))
    }
}
