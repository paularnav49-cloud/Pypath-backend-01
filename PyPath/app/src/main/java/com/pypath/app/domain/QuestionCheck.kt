package com.pypath.app.domain

import com.pypath.app.data.model.Question
import com.pypath.app.data.model.QuestionType
import kotlin.random.Random

/**
 * Batch 3: how answers to every question type are checked, and the starting layout of the
 * "fill" token bank and the "order" lines. Pure functions, so the rules are unit-tested and the
 * same everywhere (quizzes and review sessions).
 */
object QuestionCheck {

    /** Marker for blank n (1-based) in a "fill" question's code. */
    fun blankMarker(n: Int) = "{{$n}}"

    private val MARKER = Regex("""\{\{(\d+)\}\}""")

    // ───────────── predict ─────────────

    /**
     * Normalises printed output for comparison: each line trimmed, runs of spaces or tabs inside a
     * line become one space, blank lines at the start and end are dropped, Windows line endings are
     * ignored, and letters are lower-cased unless [caseSensitive]. Line breaks themselves matter.
     */
    fun normalizeOutput(text: String, caseSensitive: Boolean): String {
        val lines = text.replace("\r\n", "\n").replace('\r', '\n').split('\n')
            .map { it.trim().replace(Regex("[ \\t]+"), " ") }
            .dropWhile { it.isEmpty() }
            .dropLastWhile { it.isEmpty() }
        val joined = lines.joinToString("\n")
        return if (caseSensitive) joined else joined.lowercase()
    }

    fun predictCorrect(q: Question, typed: String): Boolean {
        val expected = listOfNotNull(q.answer) + q.alternatives
        if (expected.isEmpty()) return false
        val got = normalizeOutput(typed, q.caseSensitive)
        if (got.isEmpty()) return false
        return expected.any { normalizeOutput(it, q.caseSensitive) == got }
    }

    /** The comparison rule, in plain words, shown under the text field. */
    fun predictRule(q: Question): String =
        "Type each printed line on its own line. Extra spaces and blank lines don't matter" +
            if (q.caseSensitive) ", but capital letters do." else ", and neither do capital letters."

    // ───────────── fill ─────────────

    /** Number of blanks marked in the code (1 to 3 in valid content). */
    fun blankCount(q: Question): Int = q.blanks.size

    /**
     * The code split around the blanks: text pieces and blank numbers (0-based) in reading order,
     * for example "print({{1}} + {{2}})" -> ["print(", 0, " + ", 1, ")"].
     */
    sealed interface FillPart {
        data class Text(val text: String) : FillPart
        data class Blank(val index: Int) : FillPart
    }

    fun fillParts(q: Question): List<FillPart> {
        val code = q.code.orEmpty()
        val out = mutableListOf<FillPart>()
        var last = 0
        for (m in MARKER.findAll(code)) {
            if (m.range.first > last) out += FillPart.Text(code.substring(last, m.range.first))
            val n = m.groupValues[1].toInt() - 1
            out += if (n in q.blanks.indices) FillPart.Blank(n) else FillPart.Text(m.value)
            last = m.range.last + 1
        }
        if (last < code.length) out += FillPart.Text(code.substring(last))
        return out
    }

    /** The token bank: every correct token plus the distractors, in a stable shuffled order. */
    fun fillBank(q: Question): List<String> = stableShuffle(q.blanks + q.distractors, q.id)

    /** [chosen] holds, per blank, the index of a bank token (or null when empty). */
    fun fillCorrect(q: Question, chosen: List<Int?>): Boolean {
        if (q.blanks.isEmpty() || chosen.size != q.blanks.size) return false
        val bank = fillBank(q)
        return chosen.withIndex().all { (i, b) -> b != null && bank.getOrNull(b) == q.blanks[i] }
    }

    /** The code with the chosen tokens filled in (empty blanks shown as "____"). */
    fun fillPreview(q: Question, chosen: List<Int?>): String {
        val bank = fillBank(q)
        return fillParts(q).joinToString("") { p ->
            when (p) {
                is FillPart.Text -> p.text
                is FillPart.Blank -> chosen.getOrNull(p.index)?.let { bank.getOrNull(it) } ?: "____"
            }
        }
    }

    /** The code with the correct tokens filled in, for the feedback panel. */
    fun fillSolution(q: Question): String = fillParts(q).joinToString("") { p ->
        when (p) {
            is FillPart.Text -> p.text
            is FillPart.Blank -> q.blanks[p.index]
        }
    }

    // ───────────── order ─────────────

    /** Starting order (indexes into [Question.lines]): shuffled, never already correct. */
    fun orderStart(q: Question): List<Int> {
        val n = q.lines.size
        if (n < 2) return q.lines.indices.toList()
        var order = stableShuffle(q.lines.indices.toList(), q.id)
        var tries = 0
        while (orderCorrect(q, order) && tries < n) {
            order = order.drop(1) + order.first() // rotate until it's not a solution
            tries++
        }
        return order
    }

    fun orderCorrect(q: Question, order: List<Int>): Boolean {
        if (q.lines.isEmpty() || order.size != q.lines.size) return false
        if (order == q.lines.indices.toList()) return true
        return q.alternativeOrders.any { it == order }
    }

    /** Moves the line at position [from] one place up (-1) or down (+1). Out of range does nothing. */
    fun move(order: List<Int>, from: Int, by: Int): List<Int> {
        val to = from + by
        if (from !in order.indices || to !in order.indices) return order
        val m = order.toMutableList()
        val tmp = m[from]; m[from] = m[to]; m[to] = tmp
        return m
    }

    /** Indentation level of a line (4 spaces per level), used for display and TalkBack. */
    fun indentLevel(line: String): Int = (line.length - line.trimStart(' ').length) / 4

    /** How TalkBack reads one line, e.g. "indented 1 level: print(n)". */
    fun spokenLine(line: String): String {
        val level = indentLevel(line)
        val text = line.trim()
        return when (level) {
            0 -> text
            1 -> "indented 1 level: $text"
            else -> "indented $level levels: $text"
        }
    }

    // ───────────── shared ─────────────

    /** The correct answer as text, for the "Correct answer" line after a wrong answer. */
    fun correctAnswerText(q: Question): String = when (q.kind) {
        QuestionType.PREDICT -> q.answer.orEmpty()
        QuestionType.FILL -> fillSolution(q)
        QuestionType.ORDER -> q.lines.joinToString("\n")
        else -> q.options.getOrElse(q.correctIndex) { "" }
    }

    /**
     * Shuffle that gives the same result on every device and every launch (so rotating the phone or
     * reopening a quiz shows the same layout). Uses the question id as the seed.
     */
    fun <T> stableShuffle(items: List<T>, seed: String): List<T> = items.shuffled(Random(seed.hashCode()))
}
