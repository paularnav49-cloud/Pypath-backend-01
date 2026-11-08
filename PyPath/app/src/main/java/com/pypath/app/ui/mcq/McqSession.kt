package com.pypath.app.ui.mcq

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.pypath.app.data.model.Question
import com.pypath.app.data.model.QuestionType
import com.pypath.app.domain.QuestionCheck

enum class McqPhase { ANSWERING, FEEDBACK, FINISHED }

/**
 * Reusable question engine. Knows nothing about sub-levels, so the same session drives sub-level
 * quizzes and review sessions. Batch 3: besides MCQs it runs "predict", "fill" and "order"
 * questions; the checking rules live in [QuestionCheck].
 *
 * [numberOffset] and [displayTotal] only change the "Question n of m" label (a resumed review
 * session continues at its real position).
 */
class McqSession(
    val questions: List<Question>,
    val passPercent: Int,
    val numberOffset: Int = 0,
    val displayTotal: Int = questions.size,
) {
    var index by mutableIntStateOf(0); private set
    var selected by mutableStateOf<Int?>(null); private set
    var phase by mutableStateOf(McqPhase.ANSWERING); private set
    /** Number of completed attempts — used to fire result callbacks exactly once per attempt. */
    var attempt by mutableIntStateOf(0); private set
    /** Last attempt whose result has been persisted. */
    var reportedAttempt: Int = 0
    /** MCQ: the chosen option per answered question. Other types store -1. */
    private val _answers = mutableStateListOf<Int>()
    val answers: List<Int> get() = _answers
    /** Batch 3: whether each answered question was right (works for every type). */
    private val _results = mutableStateListOf<Boolean>()
    val results: List<Boolean> get() = _results

    val current: Question get() = questions[index]
    val total: Int get() = questions.size
    val isLastQuestion: Boolean get() = index == questions.lastIndex
    val score: Int get() = _results.count { it }
    val percent: Int get() = if (total == 0) 0 else score * 100 / total
    val passed: Boolean get() = percent >= passPercent
    /** While answering: whether the current answer would be right. After checking: whether it was. */
    val isCorrect: Boolean
        get() = if (phase == McqPhase.FEEDBACK) _results.getOrElse(index) { false } else evaluate(current)
    /** Progress through the quiz, counting the current question once it is answered. */
    val progress: Float get() = if (total == 0) 0f else (_results.size.toFloat()) / total

    // ── Batch 1: optional hints. Counted for information only; they never change scoring or block progress. ──
    /** True once the learner opened the hint of the current question. */
    var hintShown by mutableStateOf(false); private set
    /** Number of questions in this attempt where the hint was opened. */
    var hintsUsed by mutableIntStateOf(0); private set
    val hasHint: Boolean get() = !current.hint.isNullOrBlank()

    fun showHint() {
        if (phase != McqPhase.ANSWERING || hintShown || !hasHint) return
        hintShown = true
        hintsUsed++
    }

    // ── Batch 3: XP shown on the results screen (the stored XP is computed by AppViewModel). ──
    /** XP earned by answers in this attempt. */
    var xpThisAttempt by mutableIntStateOf(0); private set
    /** +20 when this attempt passed the quiz for the first time (set when the attempt finishes). */
    var bonusXp by mutableIntStateOf(0)
    /** True when the quiz had already been passed before this attempt (replays earn no XP). */
    var replay by mutableStateOf(false)

    fun addXp(xp: Int) {
        if (xp > 0) xpThisAttempt += xp
    }

    // ── Batch 3: answers for the new question types (kept here, so rotation keeps them) ──
    /** predict: the text typed so far. */
    var typed by mutableStateOf(""); private set
    /** fill: per blank, the index of the bank token placed there (null = empty). */
    private val _fill = mutableStateListOf<Int?>()
    val fill: List<Int?> get() = _fill
    /** fill: the blank that the next tapped token goes into. */
    var activeBlank by mutableIntStateOf(0); private set
    /** order: the current order of the lines (indexes into Question.lines). */
    private val _order = mutableStateListOf<Int>()
    val order: List<Int> get() = _order

    init {
        prepare()
    }

    /** Resets the answer state for the current question. */
    private fun prepare() {
        typed = ""
        _fill.clear()
        activeBlank = 0
        _order.clear()
        if (questions.isEmpty()) return
        val q = current
        when (q.kind) {
            QuestionType.FILL -> repeat(QuestionCheck.blankCount(q)) { _fill.add(null) }
            QuestionType.ORDER -> _order.addAll(QuestionCheck.orderStart(q))
        }
    }

    fun select(option: Int) {
        if (phase == McqPhase.ANSWERING) selected = option
    }

    fun type(text: String) {
        if (phase == McqPhase.ANSWERING) typed = text
    }

    /** fill: chooses which blank the next token goes into. Tapping a filled blank empties it. */
    fun tapBlank(blank: Int) {
        if (phase != McqPhase.ANSWERING || blank !in _fill.indices) return
        if (_fill[blank] != null) _fill[blank] = null
        activeBlank = blank
    }

    /** fill: places bank token [token] into the active blank (or takes it back out if already used). */
    fun tapToken(token: Int) {
        if (phase != McqPhase.ANSWERING || _fill.isEmpty()) return
        val used = _fill.indexOf(token)
        if (used >= 0) {
            _fill[used] = null
            activeBlank = used
            return
        }
        val target = if (_fill.getOrNull(activeBlank) == null) activeBlank else _fill.indexOfFirst { it == null }
        if (target < 0) return
        _fill[target] = token
        val nextEmpty = _fill.indexOfFirst { it == null }
        activeBlank = if (nextEmpty >= 0) nextEmpty else target
    }

    /** order: moves the line at [position] up (-1) or down (+1). */
    fun moveLine(position: Int, by: Int) {
        if (phase != McqPhase.ANSWERING) return
        val moved = QuestionCheck.move(_order, position, by)
        if (moved != _order.toList()) {
            _order.clear(); _order.addAll(moved)
        }
    }

    /** True when the current question has an answer that can be checked. */
    val canCheck: Boolean
        get() = phase == McqPhase.ANSWERING && when (current.kind) {
            QuestionType.PREDICT -> typed.isNotBlank()
            QuestionType.FILL -> _fill.isNotEmpty() && _fill.all { it != null }
            QuestionType.ORDER -> _order.isNotEmpty()
            else -> selected != null
        }

    private fun evaluate(q: Question): Boolean = when (q.kind) {
        QuestionType.PREDICT -> QuestionCheck.predictCorrect(q, typed)
        QuestionType.FILL -> QuestionCheck.fillCorrect(q, _fill)
        QuestionType.ORDER -> QuestionCheck.orderCorrect(q, _order)
        else -> selected == q.correctIndex
    }

    fun check() {
        if (!canCheck) return
        val right = evaluate(current)
        _answers += if (current.isMcq) selected ?: -1 else -1
        _results += right
        phase = McqPhase.FEEDBACK
    }

    fun next() {
        if (phase != McqPhase.FEEDBACK) return
        if (isLastQuestion) {
            phase = McqPhase.FINISHED
            attempt++
        } else {
            index++
            selected = null
            hintShown = false
            prepare()
            phase = McqPhase.ANSWERING
        }
    }

    fun restart() {
        index = 0; selected = null; _answers.clear(); _results.clear(); phase = McqPhase.ANSWERING
        hintShown = false; hintsUsed = 0
        xpThisAttempt = 0; bonusXp = 0; replay = false
        prepare()
    }

    val inProgress: Boolean
        get() = phase != McqPhase.FINISHED &&
            (_results.isNotEmpty() || selected != null || typed.isNotEmpty() || _fill.any { it != null })
}

/** Keeps the session alive across configuration changes; scoped to the quiz nav entry. */
class McqViewModel : ViewModel() {
    var session: McqSession? = null
        private set

    fun ensure(questions: List<Question>, passPercent: Int): McqSession =
        session ?: McqSession(questions, passPercent).also { session = it }
}
