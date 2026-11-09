package com.pypath.app.domain

/**
 * Batch 3 Part 3: plain-Kotlin text for screen readers (no Android or Compose types, so it is
 * unit-tested directly).
 */
object A11y {

    /** Indent level of a code line: 4 spaces (or one tab) per level, partial levels round up. */
    fun indentLevel(line: String): Int {
        var spaces = 0
        for (ch in line) {
            when (ch) {
                ' ' -> spaces += 1
                '\t' -> spaces += 4
                else -> break
            }
        }
        return (spaces + 3) / 4
    }

    /**
     * Reads code line by line, e.g. "Code, 2 lines. Line 1: for i in range(3):. Line 2, indented 1 level: print(i)."
     * Blank lines are spoken as "blank". Trailing empty lines are ignored.
     */
    fun describeCode(code: String, output: String? = null): String {
        val lines = code.trimEnd('\n', '\r').lines()
        val sb = StringBuilder()
        sb.append("Code, ").append(lines.size).append(if (lines.size == 1) " line. " else " lines. ")
        lines.forEachIndexed { i, raw ->
            val line = raw.trimEnd()
            sb.append("Line ").append(i + 1)
            when {
                line.isBlank() -> sb.append(": blank. ")
                else -> {
                    val level = indentLevel(line)
                    if (level > 0) sb.append(", indented ").append(level).append(if (level == 1) " level" else " levels")
                    sb.append(": ").append(line.trim()).append(". ")
                }
            }
        }
        if (!output.isNullOrBlank()) sb.append("Output: ").append(output.trimEnd()).append('.')
        return sb.toString().trimEnd()
    }

    /** "3 of 5" style progress text for bars and rings. */
    fun progress(done: Int, total: Int): String = "$done of $total"

    /** Percent text for a 0..1 fraction. */
    fun percent(fraction: Float): String = "${(fraction.coerceIn(0f, 1f) * 100).toInt()} percent"

    /** A level card read as one item. */
    fun levelCard(number: Int, title: String, status: String, done: Int, total: Int, locked: Boolean, unlockHint: String?): String =
        buildString {
            append("Level ").append(number).append(", ").append(title).append(". ")
            if (locked) {
                append("Locked")
                if (!unlockHint.isNullOrBlank()) append(". ").append(unlockHint)
                append('.')
            } else {
                append(status).append(". ").append(done).append(" of ").append(total).append(" sub-levels done.")
            }
        }

    /** A badge read as its name plus earned or locked. */
    fun badge(name: String, earned: Boolean): String = "$name badge, " + if (earned) "earned" else "locked"
}
