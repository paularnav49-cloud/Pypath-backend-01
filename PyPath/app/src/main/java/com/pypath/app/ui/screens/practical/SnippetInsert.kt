package com.pypath.app.ui.screens.practical

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/**
 * Premium Phase 1: inserts [snippet] at the cursor, keeping the indentation of the current line.
 * - Cursor on an empty / whitespace-only line: the snippet starts right there.
 * - Cursor after code on a line: the snippet goes on a new line below it.
 * - A selection is replaced by the snippet.
 * The cursor ends just after the inserted code.
 */
internal fun insertSnippet(v: TextFieldValue, snippet: String): TextFieldValue {
    val body = snippet.replace("\r\n", "\n").trimEnd('\n')
    if (body.isEmpty()) return v
    val start = minOf(v.selection.start, v.selection.end).coerceIn(0, v.text.length)
    val end = maxOf(v.selection.start, v.selection.end).coerceIn(0, v.text.length)
    val lineStart = v.text.lastIndexOf('\n', start - 1) + 1
    val beforeOnLine = v.text.substring(lineStart, start)
    val indent = beforeOnLine.takeWhile { it == ' ' || it == '\t' }

    val (insertAt, prefix) = if (beforeOnLine.isBlank()) {
        start to ""
    } else {
        // After code: continue on a new line under the current one.
        val lineEnd = v.text.indexOf('\n', end).let { if (it == -1) v.text.length else it }
        lineEnd to "\n$indent"
    }
    val removeEnd = if (beforeOnLine.isBlank()) end else insertAt
    val indented = body.lines().mapIndexed { i, line -> if (i == 0 || line.isEmpty()) line else indent + line }.joinToString("\n")
    val after = v.text.substring(removeEnd)
    val suffix = if (after.isEmpty() || after.startsWith("\n")) "" else "\n"
    val inserted = prefix + indented + suffix
    val text = v.text.substring(0, insertAt) + inserted + after
    return TextFieldValue(text, TextRange(insertAt + prefix.length + indented.length))
}
