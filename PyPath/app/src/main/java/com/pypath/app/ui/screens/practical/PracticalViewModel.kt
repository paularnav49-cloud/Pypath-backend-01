package com.pypath.app.ui.screens.practical

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pypath.app.PyPathApp
import com.pypath.app.premium.PremiumIds
import com.pypath.app.practical.CheckReport
import com.pypath.app.practical.EngineState
import com.pypath.app.practical.PracticalTask
import com.pypath.app.practical.PythonRunner
import com.pypath.app.practical.RunEnd
import com.pypath.app.practical.RunOutcome
import com.pypath.app.practical.RunnerListener
import com.pypath.app.practical.TestRun
import com.pypath.app.practical.evaluateTest
import com.pypath.app.practical.loadPracticalCatalog
import com.pypath.app.practical.runEndFor
import com.pypath.app.data.programs.FileProgramRepository
import com.pypath.app.data.programs.ProgramBook
import com.pypath.app.data.programs.ProgramLibrary
import com.pypath.app.data.programs.ProgramRepository
import com.pypath.app.data.programs.SavedProgram
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ConsoleKind { STDOUT, STDERR, INPUT, SYSTEM }

data class ConsoleSegment(val kind: ConsoleKind, val text: String)

enum class RunPhase { IDLE, STARTING, RUNNING, WAITING_INPUT }

/** Premium Phase 1: the Practical tab shows the task list (everyone) or saved programs (Premium). */
enum class IdeMode { TASKS, PROGRAMS }

/** Premium Phase 1: autosave state of saved programs. */
enum class SaveStatus { SAVED, PENDING, FAILED }

/** Result of the most recent run, shown in the status pill and error card. */
data class RunResult(
    val kind: Kind,
    val elapsedMs: Long,
    val exitCode: Int = 0,
    /** Last line of the traceback, e.g. "NameError: name 'x' is not defined". */
    val errorSummary: String? = null,
    /** Line in main.py where the error happened, parsed from the real traceback. */
    val errorLine: Int? = null,
) {
    enum class Kind { SUCCESS, ERROR, EXITED, TIMEOUT, STOPPED, CRASHED, INTERNAL }
}

/**
 * State of the latest Check of one task (see [PracticalViewModel.check] and [PracticalViewModel.checkState]).
 * [report] grows as tests finish; [running] is true until the last test is done.
 */
data class CheckState(
    val taskId: String,
    val runId: Int,
    val report: CheckReport,
    val running: Boolean,
    /** Stop was pressed (or the task was changed) before every test had run. */
    val stopped: Boolean = false,
    /** Set when Python could not run at all (the check ends early). */
    val problem: String? = null,
) {
    /** 1-based number of the test that is running now. */
    val currentTest: Int get() = minOf(report.results.size + 1, report.total)
    val allPassed: Boolean get() = !running && !stopped && problem == null && report.allPassed
}

/**
 * State for the Practical tab. Task code lives only in memory for this app session: it is never
 * written to disk, uploaded or synced. (Premium Phase 1: only Premium "My programs" are saved, on
 * the phone only; see the section at the end.) Activity-scoped, so it survives tab switches and rotation.
 */
class PracticalViewModel(app: Application) : AndroidViewModel(app), RunnerListener {

    var tasks by mutableStateOf<List<PracticalTask>>(emptyList()); private set
    var loadError by mutableStateOf<String?>(null); private set
    var selectedTaskId by mutableStateOf<String?>(null); private set
    private val drafts = mutableStateMapOf<String, TextFieldValue>()

    val console = mutableStateListOf<ConsoleSegment>()
    var engine by mutableStateOf<EngineState>(EngineState.Starting); private set
    var phase by mutableStateOf(RunPhase.IDLE); private set
    var inputPrompt by mutableStateOf(""); private set
    var lastResult by mutableStateOf<RunResult?>(null); private set
    var elapsedMs by mutableStateOf(0L); private set
    var hasRunOnce by mutableStateOf(false); private set
    private var stderrBuffer = StringBuilder()
    private var consoleChars = 0

    /** Latest Check results for the selected task, or null. */
    var checkState by mutableStateOf<CheckState?>(null); private set
    /** Set when a Check passes every test; the screen plays the "correct" sound and clears it. */
    var successSoundPending by mutableStateOf(false); private set
    /** Tasks completed in this app session (the stored set arrives through the course snapshot). */
    var completedThisSession by mutableStateOf<Set<String>>(emptySet()); private set
    /** Premium Phase 2: Premium challenges passed in this app session. */
    var premiumPassedThisSession by mutableStateOf<Set<String>>(emptySet()); private set
    private var checkSession: CheckSession? = null
    private var nextCheckRunId = 0

    private val runner = PythonRunner(app, this)

    init {
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { loadPracticalCatalog(getApplication()) } }
                .onSuccess { tasks = it.tasks }
                .onFailure { loadError = it.message ?: "Could not load practical tasks" }
        }
    }

    /** Called when the Practical tab is shown: starts the interpreter in the background. */
    fun warmUp() = runner.start()

    val selectedTask: PracticalTask? get() = tasks.firstOrNull { it.id == selectedTaskId }

    fun select(taskId: String) {
        if (taskId == selectedTaskId) return
        // Premium Phase 1: while "My programs" is shown, a task change (e.g. the default task picked
        // when tasks load) must not stop or clear the program that is running.
        if (ideMode == IdeMode.PROGRAMS) { selectedTaskId = taskId; return }
        leaveCurrentRun()
        selectedTaskId = taskId
    }

    /** Stops whatever runs or checks for the current code and clears the console (used by [select]). */
    private fun leaveCurrentRun() {
        val s = checkSession
        if (s != null) {
            // A Check in progress belongs to the old task: cancel it (its run ends via onFinished).
            s.cancelled = true
            if (runner.isRunning) runner.stop() else endCheckSession()
        } else if (phase == RunPhase.RUNNING || phase == RunPhase.WAITING_INPUT) {
            runner.stop()
        }
        console.clear(); consoleChars = 0
        lastResult = null
        hasRunOnce = false
        checkState = null
    }

    /**
     * Premium Phase 2: a Premium project or challenge screen opens or closes its own editor. Whatever
     * runs is stopped and the shared console is cleared, so the Practical tab and the Premium screen
     * never show each other's output. The Practical tab's selected task and drafts are untouched.
     */
    fun switchToExternalWorkspace() {
        leaveCurrentRun()
    }

    /** Premium Phase 2: restores a saved draft for a Premium workspace (only if nothing is typed yet). */
    fun restoreDraft(task: PracticalTask, code: String?) {
        if (code == null || drafts.containsKey(task.id)) return
        drafts[task.id] = TextFieldValue(code, TextRange(code.length))
    }

    /** Picks a default task the first time the tab opens. */
    fun ensureSelection(defaultId: String?) {
        if (selectedTaskId == null && defaultId != null) selectedTaskId = defaultId
    }

    fun code(task: PracticalTask): TextFieldValue =
        drafts[task.id] ?: TextFieldValue(task.starterCode, TextRange(task.starterCode.length))

    fun updateCode(task: PracticalTask, value: TextFieldValue) { drafts[task.id] = value }

    fun resetCode(task: PracticalTask) {
        drafts.remove(task.id)
        if (lastResult?.errorLine != null) lastResult = lastResult?.copy(errorLine = null)
    }

    fun run(task: PracticalTask) {
        if (phase != RunPhase.IDLE) return
        val code = code(task).text
        console.clear(); consoleChars = 0
        stderrBuffer = StringBuilder()
        lastResult = null
        hasRunOnce = true
        elapsedMs = 0
        phase = if (engine is EngineState.Ready) RunPhase.RUNNING else RunPhase.STARTING
        append(ConsoleKind.SYSTEM, if (phase == RunPhase.STARTING) "Starting Python…\n" else "")
        runner.run(code)
        viewModelScope.launch {
            while (isActive && phase != RunPhase.IDLE) {
                elapsedMs = runner.elapsedMs()
                delay(100)
            }
        }
    }

    fun stop() {
        val s = checkSession
        if (s != null) {
            s.cancelled = true
            // Between two tests nothing is running, so finish the check right away.
            if (!runner.isRunning) { endCheckSession(); markCheckStopped(s) } else runner.stop()
            return
        }
        runner.stop()
    }

    // ───────────── Check (automatic tests) ─────────────

    /** One press of Check: runs [task]'s tests one after another with the existing runner and limits. */
    private class CheckSession(val task: PracticalTask, val code: String, val runId: Int) {
        var index = 0
        val transcript = StringBuilder()
        val stderr = StringBuilder()
        var pendingInput = ArrayDeque<String>()
        var ranOutOfInput = false
        /** Stop pressed or task changed: the next finished run ends the check. */
        var cancelled = false
    }

    /**
     * Runs every test of [task] with its preset input lines and records pass/fail. Uses the normal
     * runner: same 10 s limit per run, same sandbox, same :python process, Stop works as usual.
     * Completion is stored only here, and only when every test passes.
     */
    fun check(task: PracticalTask) {
        if (phase != RunPhase.IDLE || !task.checkable || checkSession != null) return
        val s = CheckSession(task, code(task).text, ++nextCheckRunId)
        checkSession = s
        lastResult = null
        elapsedMs = 0
        checkState = CheckState(task.id, s.runId, CheckReport(task.tests.size, emptyList()), running = true)
        phase = if (engine is EngineState.Ready) RunPhase.RUNNING else RunPhase.STARTING
        startTest(s)
        viewModelScope.launch {
            while (isActive && checkSession === s) {
                elapsedMs = runner.elapsedMs()
                delay(100)
            }
        }
    }

    fun consumeSuccessSound() { successSoundPending = false }

    private fun startTest(s: CheckSession) {
        val test = s.task.tests[s.index]
        s.transcript.clear()
        s.stderr.clear()
        s.pendingInput = ArrayDeque(test.stdin)
        s.ranOutOfInput = false
        runner.run(s.code)
    }

    private fun onCheckInput(s: CheckSession) {
        val line = s.pendingInput.removeFirstOrNull()
        if (line == null) {
            // The test has no more answers. Without touching the runner the only way to end the
            // program is the normal Stop (the run is reported as "needs more input").
            s.ranOutOfInput = true
            runner.stop()
            return
        }
        s.transcript.append(line).append('\n') // the console echoes answers the same way
        runner.sendInput(line)
    }

    private fun onCheckFinished(s: CheckSession, outcome: RunOutcome) {
        if (s.cancelled || (outcome == RunOutcome.StoppedByUser && !s.ranOutOfInput)) {
            endCheckSession()
            markCheckStopped(s)
            return
        }
        val (end, exitCode) = runEndFor(outcome, s.ranOutOfInput)
        val (summary, line) = if (end == RunEnd.ERROR) parseTraceback(s.stderr.toString()) else null to null
        val internal = (outcome as? RunOutcome.Completed)?.status?.takeIf { end == RunEnd.INTERNAL }?.removePrefix("internal:")
        val run = TestRun(s.transcript.toString(), end, errorSummary = summary ?: internal, errorLine = line, exitCode = exitCode)
        val result = evaluateTest(s.task, s.index, s.task.tests[s.index], run)
        val prev = checkState?.takeIf { it.runId == s.runId } ?: return endCheckSession()
        val report = prev.report.copy(results = prev.report.results + result)
        if (end == RunEnd.INTERNAL) { // Python itself is unavailable: no point running the rest
            endCheckSession()
            checkState = prev.copy(report = report, running = false, problem = internal ?: "Python could not start")
            return
        }
        s.index++
        if (s.index < s.task.tests.size) {
            checkState = prev.copy(report = report)
            // Start the next test on a later main-loop turn, after the runner has finished its own
            // clean-up (e.g. restarting the interpreter after a time-out or a stopped run).
            viewModelScope.launch {
                delay(NEXT_TEST_DELAY_MS)
                if (checkSession === s && !s.cancelled) startTest(s)
                else if (checkSession === s) { endCheckSession(); markCheckStopped(s) }
            }
            return
        }
        endCheckSession()
        val done = prev.copy(report = report, running = false)
        checkState = done
        if (done.allPassed) markCompleted(s.task.id)
    }

    private fun endCheckSession() {
        checkSession = null
        phase = RunPhase.IDLE
        inputPrompt = ""
    }

    private fun markCheckStopped(s: CheckSession) {
        checkState = checkState?.takeIf { it.runId == s.runId }?.copy(running = false, stopped = true)
    }

    private fun markCompleted(taskId: String) {
        successSoundPending = true
        // Premium Phase 2: Premium challenges are stored apart from course progress, so Practical
        // task counts, XP and stats are unchanged.
        if (PremiumIds.isChallenge(taskId)) {
            premiumPassedThisSession = premiumPassedThisSession + taskId
            val store = (getApplication<Application>() as? PyPathApp)?.container?.premiumStudyStore ?: return
            viewModelScope.launch { runCatching { store.markChallengePassed(taskId) } }
            return
        }
        completedThisSession = completedThisSession + taskId
        val repo = (getApplication<Application>() as? PyPathApp)?.container?.progressRepository ?: return
        viewModelScope.launch { runCatching { repo.markTaskCompleted(taskId) } }
    }

    fun submitInput(text: String) {
        if (phase != RunPhase.WAITING_INPUT) return
        append(ConsoleKind.INPUT, text + "\n")
        phase = RunPhase.RUNNING
        inputPrompt = ""
        runner.sendInput(text)
    }

    fun clearConsole() {
        console.clear(); consoleChars = 0
    }

    // ───────────── RunnerListener (main thread) ─────────────

    override fun onEngineState(state: EngineState) { engine = state }

    override fun onStarted() {
        if (checkSession != null) { if (phase == RunPhase.STARTING) phase = RunPhase.RUNNING; return }
        if (phase == RunPhase.STARTING) {
            phase = RunPhase.RUNNING
            // drop the "Starting Python…" line now that code is running
            if (console.firstOrNull()?.kind == ConsoleKind.SYSTEM) { consoleChars -= console.first().text.length; console.removeAt(0) }
        }
    }

    override fun onOutput(stderr: Boolean, text: String) {
        checkSession?.let { s ->
            if (stderr) s.stderr.append(text).also { if (it.length > 20_000) it.delete(0, it.length - 20_000) }
            else if (s.transcript.length < MAX_CHECK_OUTPUT_CHARS) s.transcript.append(text)
            return
        }
        if (stderr) stderrBuffer.append(text).also { if (it.length > 20_000) it.delete(0, it.length - 20_000) }
        append(if (stderr) ConsoleKind.STDERR else ConsoleKind.STDOUT, text)
    }

    override fun onInputRequested(prompt: String) {
        checkSession?.let { onCheckInput(it); return }
        inputPrompt = prompt
        phase = RunPhase.WAITING_INPUT
    }

    override fun onFinished(outcome: RunOutcome, elapsedMs: Long) {
        checkSession?.let { onCheckFinished(it, outcome); return }
        this.elapsedMs = elapsedMs
        phase = RunPhase.IDLE
        inputPrompt = ""
        val secs = formatSeconds(elapsedMs)
        val result = when (outcome) {
            is RunOutcome.Completed -> when {
                outcome.status == "ok" -> RunResult(RunResult.Kind.SUCCESS, elapsedMs)
                outcome.status == "error" -> {
                    val (summary, line) = parseTraceback(stderrBuffer.toString())
                    RunResult(RunResult.Kind.ERROR, elapsedMs, errorSummary = summary, errorLine = line)
                }
                outcome.status.startsWith("exit:") -> {
                    val code = outcome.status.removePrefix("exit:").toIntOrNull() ?: 1
                    RunResult(if (code == 0) RunResult.Kind.SUCCESS else RunResult.Kind.EXITED, elapsedMs, exitCode = code)
                }
                else -> RunResult(RunResult.Kind.INTERNAL, elapsedMs, errorSummary = outcome.status.removePrefix("internal:"))
            }
            RunOutcome.StoppedByUser -> RunResult(RunResult.Kind.STOPPED, elapsedMs)
            is RunOutcome.TimedOut -> RunResult(RunResult.Kind.TIMEOUT, elapsedMs)
            RunOutcome.Crashed -> RunResult(RunResult.Kind.CRASHED, elapsedMs)
        }
        lastResult = result
        val line = when (result.kind) {
            RunResult.Kind.SUCCESS -> if (result.exitCode == 0 && outcome is RunOutcome.Completed && outcome.status == "exit:0") "Program exited (code 0) · $secs" else "Finished in $secs"
            RunResult.Kind.ERROR -> "Finished with an error · $secs"
            RunResult.Kind.EXITED -> "Program exited with code ${result.exitCode} · $secs"
            RunResult.Kind.TIMEOUT -> "Execution timed out after ${runner.timeLimitMs / 1000} seconds and was stopped. Check for a loop that never ends."
            RunResult.Kind.STOPPED -> "Execution stopped by you after $secs"
            RunResult.Kind.CRASHED -> "The Python process ended unexpectedly (it may have run out of memory). A fresh one has been started."
            RunResult.Kind.INTERNAL -> "Python could not run the code: ${result.errorSummary}"
        }
        val needsBreak = console.lastOrNull()?.text?.endsWith("\n") == false
        append(ConsoleKind.SYSTEM, (if (needsBreak) "\n" else "") + "— $line\n")
    }

    private fun append(kind: ConsoleKind, text: String) {
        if (text.isEmpty()) return
        consoleChars += text.length
        val last = console.lastOrNull()
        if (last != null && last.kind == kind) console[console.size - 1] = last.copy(text = last.text + text)
        else console.add(ConsoleSegment(kind, text))
        // Keep the UI responsive: retain at most ~120k chars (Python caps output at 200k).
        while (consoleChars > MAX_CONSOLE_CHARS && console.size > 1) {
            consoleChars -= console.first().text.length
            console.removeAt(0)
        }
        if (consoleChars > MAX_CONSOLE_CHARS && console.size == 1) {
            val seg = console[0]
            val trimmed = seg.text.takeLast(MAX_CONSOLE_CHARS)
            console[0] = seg.copy(text = trimmed)
            consoleChars = trimmed.length
        }
    }

    // ───────────── Premium Phase 1: Advanced IDE (saved programs) ─────────────
    //
    // Free users never reach this code: the mode stays TASKS and nothing is read from or written
    // to disk (task drafts above still live only in memory). Premium users can switch to
    // PROGRAMS, where each program is saved once in files/programs/programs.json.

    var ideMode by mutableStateOf(IdeMode.TASKS); private set
    /** null until loaded from the phone (only loaded the first time "My programs" opens). */
    var library by mutableStateOf<ProgramLibrary?>(null); private set
    var currentProgramId by mutableStateOf<String?>(null); private set
    var saveStatus by mutableStateOf(SaveStatus.SAVED); private set
    private var loadingLibrary = false
    private var saveJob: Job? = null

    private val programRepo: ProgramRepository by lazy {
        (getApplication<Application>() as? PyPathApp)?.container?.programRepository
            ?: FileProgramRepository(java.io.File(getApplication<Application>().filesDir, "programs"))
    }

    val currentProgram: SavedProgram? get() = library?.find(currentProgramId)

    /** A program wrapped as a test-less task, so the existing editor, Run, Stop and console are reused. */
    fun programTask(p: SavedProgram): PracticalTask =
        PracticalTask(id = PROGRAM_PREFIX + p.id, title = p.name, instructions = "", starterCode = p.code)

    fun setMode(mode: IdeMode) {
        if (mode == ideMode) return
        if (mode == IdeMode.TASKS) flushPrograms()
        leaveCurrentRun()
        ideMode = mode
        if (mode == IdeMode.PROGRAMS) ensureLibrary()
    }

    private fun ensureLibrary() {
        if (library != null || loadingLibrary) return
        loadingLibrary = true
        viewModelScope.launch {
            var lib = runCatching { programRepo.load() }.getOrElse { ProgramLibrary() }
            if (lib.programs.isEmpty()) {
                lib = ProgramBook.create(lib, "My first program", code = ProgramBook.STARTER_CODE).first
                persist(lib)
            }
            library = lib
            currentProgramId = lib.find(lib.lastOpenedId)?.id ?: lib.sorted.firstOrNull()?.id
            loadingLibrary = false
        }
    }

    fun updateProgramCode(p: SavedProgram, value: TextFieldValue) {
        val key = PROGRAM_PREFIX + p.id
        val old = drafts[key]?.text ?: p.code
        drafts[key] = value
        if (value.text != old) {
            library = library?.let { ProgramBook.updateCode(it, p.id, value.text) }
            scheduleSave()
        }
    }

    fun insertSnippetIntoProgram(p: SavedProgram, snippetCode: String) {
        val task = programTask(p)
        updateProgramCode(p, insertSnippet(code(task), snippetCode))
    }

    fun openProgram(id: String) {
        val lib = library ?: return
        if (id == currentProgramId || lib.find(id) == null) return
        leaveCurrentRun()
        currentProgramId = id
        library = ProgramBook.open(lib, id)
        saveNow()
    }

    fun createProgram(name: String, folder: String, code: String = ProgramBook.NEW_PROGRAM_CODE) {
        val lib = library ?: return
        val (next, p) = ProgramBook.create(lib, name, folder, code)
        leaveCurrentRun()
        library = next
        currentProgramId = p.id
        saveNow()
    }

    fun renameProgram(id: String, name: String, folder: String) {
        val lib = library ?: return
        library = ProgramBook.rename(lib, id, name, folder)
        saveNow()
    }

    fun deleteProgram(id: String) {
        val lib = library ?: return
        if (id == currentProgramId) leaveCurrentRun()
        val next = ProgramBook.delete(lib, id)
        drafts.remove(PROGRAM_PREFIX + id)
        library = next
        if (id == currentProgramId) currentProgramId = next.lastOpenedId ?: next.sorted.firstOrNull()?.id
        saveNow()
    }

    /** Autosave: written shortly after typing pauses. */
    private fun scheduleSave() {
        saveStatus = SaveStatus.PENDING
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(AUTOSAVE_DELAY_MS)
            library?.let { persist(it) }
        }
    }

    private fun saveNow() {
        saveJob?.cancel()
        val lib = library ?: return
        saveStatus = SaveStatus.PENDING
        saveJob = viewModelScope.launch { persist(lib) }
    }

    /** Writes any pending change right away (called when the app goes to the background). */
    fun flushPrograms() {
        if (saveStatus == SaveStatus.SAVED) return
        val lib = library ?: return
        saveJob?.cancel()
        // NonCancellable: finishes even if the screen or view model goes away meanwhile.
        saveJob = viewModelScope.launch(NonCancellable) { persist(lib) }
    }

    private suspend fun persist(lib: ProgramLibrary) {
        // The write itself always completes; if a newer save replaced this one, that one sets the status.
        val ok = withContext(NonCancellable) { runCatching { programRepo.save(lib) }.isSuccess }
        saveStatus = when {
            !ok -> SaveStatus.FAILED
            library == lib -> SaveStatus.SAVED
            else -> saveStatus
        }
    }

    override fun onCleared() {
        runner.release()
        super.onCleared()
    }

    companion object {
        private const val MAX_CONSOLE_CHARS = 120_000
        /** Premium Phase 1: id prefix of programs wrapped as tasks (see [programTask]). */
        const val PROGRAM_PREFIX = "program:"
        private const val AUTOSAVE_DELAY_MS = 700L
        /** A correct answer is far shorter; this only stops a print loop from filling memory. */
        private const val MAX_CHECK_OUTPUT_CHARS = 200_000
        private const val NEXT_TEST_DELAY_MS = 30L

        fun formatSeconds(ms: Long): String = if (ms < 10_000) "%.2fs".format(ms / 1000.0) else "%.1fs".format(ms / 1000.0)

        /** Extracts "ErrorType: message" and the last main.py line number from a real traceback. */
        fun parseTraceback(tb: String): Pair<String?, Int?> {
            val lines = tb.trimEnd().lines().filter { it.isNotBlank() }
            val summary = lines.lastOrNull()?.trim()
            val line = Regex("""File "main\.py", line (\d+)""").findAll(tb).lastOrNull()?.groupValues?.get(1)?.toIntOrNull()
            return summary to line
        }
    }
}
