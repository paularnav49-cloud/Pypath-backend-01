package com.pypath.app.ui.screens.premium

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pypath.app.PyPathApp
import com.pypath.app.premium.BattleAttempt
import com.pypath.app.premium.BattleCatalog
import com.pypath.app.premium.BattleChallenge
import com.pypath.app.premium.BattleEngine
import com.pypath.app.premium.BattleOutcome
import com.pypath.app.premium.BattleStore
import com.pypath.app.premium.MemoryBattleRepository
import com.pypath.app.premium.loadBattleCatalog
import com.pypath.app.ui.screens.practical.PracticalViewModel
import com.pypath.app.ui.screens.practical.RunPhase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Premium Code Battle. One battle at a time; its deadline is wall-clock time saved with the attempt,
 * so leaving the screen or closing the app never pauses the timer. When time runs out the current
 * code is submitted automatically (immediately if the battle screen is open, otherwise from the
 * last saved code the next time Code Battle is opened).
 *
 * Testing uses the existing [PracticalViewModel.check] (same Chaquopy runner, sandbox, 10 s limit
 * per run and output checker). A result is stored only after every test has really run.
 */
class CodeBattleViewModel(app: Application) : AndroidViewModel(app) {
    private val store: BattleStore = (app as? PyPathApp)?.container?.battleStore ?: BattleStore(MemoryBattleRepository())

    var catalog by mutableStateOf<BattleCatalog?>(null); private set
    var loadError by mutableStateOf<String?>(null); private set
    var now by mutableLongStateOf(System.currentTimeMillis()); private set
    /** Result of the battle that just finished (shown once on the battle screen). */
    var outcome by mutableStateOf<BattleOutcome?>(null); private set
    /** Set when Python couldn't run the tests; the learner can try again. Nothing is stored. */
    var testProblem by mutableStateOf<String?>(null); private set

    val data get() = store.data
    val active: BattleAttempt? get() = store.data.value?.active

    /** Gives the battle screen's current editor text (set while that screen is open). */
    var liveCode: (() -> String)? = null
    /** startedAt of the battle whose code is currently in the shared editor draft. */
    var draftAttempt: Long? = null
    private var evaluatingRunId: Int? = null
    private var ticker: Job? = null
    private var loading = false

    fun load() {
        startTicker()
        if (catalog != null || loading) return
        loading = true
        viewModelScope.launch {
            try {
                val cat = withContext(Dispatchers.IO) { loadBattleCatalog(getApplication()) }
                val d = store.load()
                d.active?.let { a -> if (!BattleEngine.resolvable(a, cat)) store.update { it.copy(active = null) } }
                catalog = cat
                tick()
            } catch (e: Exception) {
                loadError = "Code Battle challenges could not be loaded."
            } finally {
                loading = false
            }
        }
    }

    fun start(ch: BattleChallenge) {
        viewModelScope.launch {
            store.update { d -> if (d.active != null) d else d.copy(active = BattleEngine.newAttempt(ch, System.currentTimeMillis())) }
            outcome = null
            testProblem = null
            evaluatingRunId = null
            startTicker()
        }
    }

    fun saveCode(code: String) {
        viewModelScope.launch { store.update { d -> d.active?.let { d.copy(active = BattleEngine.saveCode(it, code)) } ?: d } }
    }

    fun submit(code: String, timedOut: Boolean = false) {
        viewModelScope.launch {
            store.update { d -> d.active?.let { d.copy(active = BattleEngine.submit(it, code, System.currentTimeMillis(), timedOut)) } ?: d }
            testProblem = null
        }
    }

    fun giveUp() {
        val cat = catalog ?: return
        viewModelScope.launch {
            var result: BattleOutcome? = null
            store.update { d ->
                val a = d.active ?: return@update d
                val ch = cat.challenge(a.challengeId) ?: return@update d.copy(active = null)
                val (next, trophies) = BattleEngine.giveUp(d, cat, ch, a, System.currentTimeMillis())
                result = BattleOutcome(next.history.first(), trophies, null)
                next
            }
            evaluatingRunId = null
            outcome = result
        }
    }

    fun retryTests() { testProblem = null }

    fun consumeOutcome() { outcome = null }

    /**
     * Drives testing of a submitted battle with the shared Practical runner. Called whenever the
     * attempt, the runner phase or the check state changes. Stops a manual Run first, restarts a
     * check that was interrupted (e.g. the screen was left), and stores the result only once every
     * test has run.
     */
    fun drive(p: PracticalViewModel, ch: BattleChallenge) {
        val a = active ?: return
        if (a.challengeId != ch.id || !a.submitted) return
        val task = ch.task()
        val cs = p.checkState
        val mine = evaluatingRunId?.let { id -> cs?.takeIf { it.taskId == task.id && it.runId == id } }
        if (mine != null) {
            if (mine.running) return
            val counts = BattleEngine.countsOf(mine.report)
            when {
                mine.problem != null -> { testProblem = mine.problem; evaluatingRunId = null; return }
                !mine.stopped && counts != null -> { complete(ch, mine.report, counts.first, counts.second); return }
                else -> evaluatingRunId = null // interrupted: test again below
            }
        } else if (evaluatingRunId != null) {
            evaluatingRunId = null // our check was cleared (screen switched): test again
        }
        if (testProblem != null) return
        if (p.phase != RunPhase.IDLE) { p.stop(); return }
        val code = a.submittedCode ?: a.code ?: ch.starterCode
        p.updateCode(task, TextFieldValue(code, TextRange(code.length)))
        p.check(task)
        evaluatingRunId = p.checkState?.takeIf { it.taskId == task.id && it.running }?.runId
    }

    /** True while the tests of the submitted battle are running. */
    fun isTesting(ch: BattleChallenge): Boolean =
        active?.let { it.challengeId == ch.id && it.submitted } == true && testProblem == null

    private fun complete(ch: BattleChallenge, report: com.pypath.app.practical.CheckReport, passed: Int, total: Int) {
        val cat = catalog ?: return
        evaluatingRunId = null
        viewModelScope.launch {
            var result: BattleOutcome? = null
            store.update { d ->
                val a = d.active?.takeIf { it.challengeId == ch.id && it.submitted } ?: return@update d
                val (next, trophies) = BattleEngine.finish(d, cat, ch, a, passed, total, System.currentTimeMillis())
                result = BattleOutcome(next.history.first(), trophies, report)
                next
            }
            if (result != null) outcome = result
        }
    }

    private fun tick() {
        now = System.currentTimeMillis()
        val a = active ?: return
        if (BattleEngine.isExpired(a, now)) {
            val code = liveCode?.invoke() ?: a.code ?: catalog?.challenge(a.challengeId)?.starterCode.orEmpty()
            submit(code, timedOut = true)
        }
    }

    private fun startTicker() {
        if (ticker?.isActive == true) return
        ticker = viewModelScope.launch {
            store.load()
            // Runs only while a battle's timer is running.
            while (isActive && active?.submitted == false) {
                tick()
                delay(250)
            }
            now = System.currentTimeMillis()
        }
    }

    fun remainingMs(): Long? = active?.let { BattleEngine.remainingMs(it, now) }
}
