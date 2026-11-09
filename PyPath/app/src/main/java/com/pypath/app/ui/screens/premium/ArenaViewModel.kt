package com.pypath.app.ui.screens.premium

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pypath.app.PyPathApp
import com.pypath.app.data.model.Course
import com.pypath.app.data.model.Question
import com.pypath.app.data.model.QuestionType
import com.pypath.app.domain.QuestionCheck
import com.pypath.app.premium.ArenaAnswer
import com.pypath.app.premium.ArenaAttempt
import com.pypath.app.premium.ArenaCatalog
import com.pypath.app.premium.ArenaEngine
import com.pypath.app.premium.ArenaMode
import com.pypath.app.premium.ArenaPool
import com.pypath.app.premium.MemoryPremiumStudyRepository
import com.pypath.app.premium.PremiumStudyStore
import com.pypath.app.premium.PremiumProjectCatalog
import com.pypath.app.premium.loadArenaCatalog
import com.pypath.app.premium.loadPremiumProjects
import com.pypath.app.ui.mcq.McqSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Premium Phase 2 · Advanced Quiz Arena. Drives one attempt at a time with the existing
 * [McqSession] question engine and stores everything in the Premium study store (never in the
 * course progress, XP or question history).
 *
 * Timer: the deadline is a wall-clock time saved with the attempt, so leaving the screen, sending
 * the app to the background or even a process restart can't pause it. While the app runs, a ticker
 * auto-submits at the deadline; if the deadline passed while the app was closed, the attempt is
 * scored as soon as the Arena is opened again.
 */
class ArenaViewModel(app: Application) : AndroidViewModel(app) {
    private val store: PremiumStudyStore =
        (app as? PyPathApp)?.container?.premiumStudyStore ?: PremiumStudyStore(MemoryPremiumStudyRepository())

    var catalog by mutableStateOf<ArenaCatalog?>(null); private set
    var pool by mutableStateOf<ArenaPool?>(null); private set
    var loadError by mutableStateOf<String?>(null); private set
    var attempt by mutableStateOf<ArenaAttempt?>(null); private set
    var session by mutableStateOf<McqSession?>(null); private set
    var now by mutableLongStateOf(System.currentTimeMillis()); private set
    /** Key of the result that was just stored (the play screen navigates to it). */
    var finishedKey by mutableStateOf<String?>(null); private set
    /** True when the last finish was an automatic submit because time ran out. */
    var autoSubmitted by mutableStateOf(false); private set

    val studyData get() = store.data

    private var course: Course? = null
    private var ticker: Job? = null
    private var attaching = false

    /** Loads the bundled Arena content and restores an unfinished attempt (safe to call repeatedly). */
    fun attach(course: Course) {
        if (pool != null && this.course === course) { tick(); return }
        if (attaching) return
        attaching = true
        this.course = course
        viewModelScope.launch {
            try {
                val cat = catalog ?: withContext(Dispatchers.IO) { loadArenaCatalog(getApplication()) }
                val p = withContext(Dispatchers.Default) { ArenaPool(course, cat) }
                catalog = cat
                pool = p
                val data = store.load()
                val active = data.active
                if (active != null && attempt == null) {
                    when {
                        !ArenaEngine.resolvable(active, p) -> store.update { it.copy(active = null) }
                        ArenaEngine.isExpired(active, System.currentTimeMillis()) -> { attempt = active; finish(timedOut = true) }
                        else -> resume(active)
                    }
                }
            } catch (e: Exception) {
                loadError = "Quiz Arena content could not be loaded."
            } finally {
                attaching = false
            }
        }
    }

    /** Ensures the study data is loaded (for best scores and results). */
    fun loadStudy() { viewModelScope.launch { store.load() } }

    // ── Premium Project Library (same store; content loaded only when the library is opened) ──
    var projects by mutableStateOf<PremiumProjectCatalog?>(null); private set
    var projectsError by mutableStateOf<String?>(null); private set
    private var loadingProjects = false

    fun loadProjects() {
        loadStudy()
        if (projects != null || loadingProjects) return
        loadingProjects = true
        viewModelScope.launch {
            try {
                projects = withContext(Dispatchers.IO) { loadPremiumProjects(getApplication()) }
            } catch (e: Exception) {
                projectsError = "Premium projects could not be loaded."
            } finally {
                loadingProjects = false
            }
        }
    }

    fun saveDraft(taskId: String, code: String, starter: String) {
        // Survives leaving the screen: the store write isn't tied to the composable.
        viewModelScope.launch { store.saveDraft(taskId, code, starter) }
    }

    fun start(key: String, mode: String, title: String, questionIds: List<String>, timeLimitMs: Long?) {
        val p = pool ?: return
        if (questionIds.isEmpty() || questionIds.any { it !in p.byId }) return
        val a = ArenaEngine.newAttempt(key, mode, title, questionIds, System.currentTimeMillis(), timeLimitMs)
        finishedKey = null
        autoSubmitted = false
        resume(a)
        viewModelScope.launch { store.update { it.copy(active = a) } }
    }

    private fun resume(a: ArenaAttempt) {
        val p = pool ?: return
        val exam = a.mode != ArenaMode.HARD
        val remaining = a.questionIds.drop(ArenaEngine.nextIndex(a)).mapNotNull { p.byId[it]?.question }
            .map { if (exam) it.copy(hint = null) else it }
        attempt = a
        session = if (remaining.isEmpty()) null else McqSession(remaining, passPercent = 60, numberOffset = a.answeredCount, displayTotal = a.questionIds.size)
        if (remaining.isEmpty()) { finish(timedOut = false); return }
        startTicker()
    }

    val isExam: Boolean get() = attempt?.mode != ArenaMode.HARD

    /**
     * Checks the current answer. Exam modes (timed, mock) save it and move straight on without
     * revealing anything; Difficult mode shows the feedback panel with the explanation.
     */
    fun check() {
        val a = attempt ?: return
        val s = session ?: return
        if (!s.canCheck) return
        if (a.timed && ArenaEngine.isExpired(a, System.currentTimeMillis())) { finish(timedOut = true); return }
        val q = s.current
        val text = describeAnswer(s, q)
        s.check()
        val right = s.results.lastOrNull() == true
        val updated = ArenaEngine.record(a, ArenaAnswer(q.id, right, text), System.currentTimeMillis())
        attempt = updated
        viewModelScope.launch { store.update { it.copy(active = updated) } }
        if (isExam) {
            if (updated.isComplete) finish(timedOut = false) else s.next()
        }
    }

    /** Difficult mode: next question after reading the explanation (or the results after the last one). */
    fun next() {
        val a = attempt ?: return
        val s = session ?: return
        if (a.isComplete) finish(timedOut = false) else s.next()
    }

    /** Submits now; unanswered questions count as wrong. */
    fun submit() { if (attempt != null) finish(timedOut = false) }

    fun consumeFinished() { finishedKey = null }

    private fun finish(timedOut: Boolean) {
        val a = attempt ?: return
        val p = pool ?: return
        val result = ArenaEngine.score(a, p, System.currentTimeMillis(), timedOut)
        attempt = null
        session = null
        ticker?.cancel(); ticker = null
        autoSubmitted = timedOut
        finishedKey = a.key
        viewModelScope.launch { store.update { ArenaEngine.finish(it, result) } }
    }

    private fun tick() {
        now = System.currentTimeMillis()
        val a = attempt ?: return
        if (a.timed && ArenaEngine.isExpired(a, now)) finish(timedOut = true)
    }

    private fun startTicker() {
        ticker?.cancel()
        val a = attempt ?: return
        if (!a.timed) return
        ticker = viewModelScope.launch {
            while (isActive && attempt != null) {
                tick()
                delay(250)
            }
        }
    }

    fun remainingMs(): Long? = attempt?.let { ArenaEngine.remainingMs(it, now) }

    companion object {
        /** The learner's answer as text, for the review list. */
        fun describeAnswer(s: McqSession, q: Question): String? = when (q.kind) {
            QuestionType.PREDICT -> s.typed.trim()
            QuestionType.FILL -> QuestionCheck.fillPreview(q, s.fill)
            QuestionType.ORDER -> s.order.joinToString("\n") { q.lines.getOrElse(it) { "" } }
            else -> s.selected?.let { q.options.getOrNull(it) }
        }
    }
}
