package com.pypath.app.ui.components

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import com.pypath.app.ui.theme.CodeColors

/** Lightweight single-pass Python tokenizer for syntax highlighting. */
object PythonHighlighter {

    private val keywords = setOf(
        "False", "None", "True", "and", "as", "assert", "async", "await", "break", "class", "continue",
        "def", "del", "elif", "else", "except", "finally", "for", "from", "global", "if", "import", "in",
        "is", "lambda", "nonlocal", "not", "or", "pass", "raise", "return", "try", "while", "with", "yield",
    )
    private val builtins = setOf(
        "print", "input", "int", "float", "str", "bool", "type", "len", "range", "list", "dict", "set",
        "tuple", "abs", "min", "max", "sum", "round", "sorted", "enumerate", "zip", "open",
    )
    private val operators = "+-*/%=<>!&|^~:".toSet()

    fun highlight(code: String, c: CodeColors): AnnotatedString = buildAnnotatedString {
        var i = 0
        val n = code.length
        fun styled(text: String, style: SpanStyle) {
            val start = length; append(text); addStyle(style, start, length)
        }
        while (i < n) {
            val ch = code[i]
            when {
                ch == '#' -> {
                    val end = code.indexOf('\n', i).let { if (it == -1) n else it }
                    styled(code.substring(i, end), SpanStyle(color = c.comment, fontStyle = FontStyle.Italic)); i = end
                }
                ch == '"' || ch == '\'' || ((ch == 'f' || ch == 'F') && i + 1 < n && (code[i + 1] == '"' || code[i + 1] == '\'')) -> {
                    val isF = ch == 'f' || ch == 'F'
                    val quote = if (isF) code[i + 1] else ch
                    var j = i + if (isF) 2 else 1
                    while (j < n && code[j] != quote && code[j] != '\n') { if (code[j] == '\\') j++; j++ }
                    val end = minOf(j + 1, n)
                    val s = code.substring(i, end)
                    if (isF) appendFString(s, c) else styled(s, SpanStyle(color = c.string))
                    i = end
                }
                ch.isDigit() -> {
                    var j = i
                    while (j < n && (code[j].isDigit() || code[j] == '.' || code[j] == '_')) j++
                    styled(code.substring(i, j), SpanStyle(color = c.number)); i = j
                }
                ch.isLetter() || ch == '_' -> {
                    var j = i
                    while (j < n && (code[j].isLetterOrDigit() || code[j] == '_')) j++
                    val word = code.substring(i, j)
                    val nextIsParen = j < n && code[j] == '('
                    val style = when {
                        word in keywords -> SpanStyle(color = c.keyword)
                        word in builtins -> SpanStyle(color = c.builtin)
                        nextIsParen -> SpanStyle(color = c.function)
                        else -> SpanStyle(color = c.text)
                    }
                    styled(word, style); i = j
                }
                ch in operators -> { styled(ch.toString(), SpanStyle(color = c.operator)); i++ }
                else -> { styled(ch.toString(), SpanStyle(color = c.text)); i++ }
            }
        }
    }

    /** f-strings: string colour, with {expressions} in the normal text colour. */
    private fun AnnotatedString.Builder.appendFString(s: String, c: CodeColors) {
        var k = 0
        while (k < s.length) {
            val open = s.indexOf('{', k)
            if (open == -1) { add(s.substring(k), c.string); break }
            add(s.substring(k, open), c.string)
            val close = s.indexOf('}', open)
            // Unclosed brace (common while typing in the editor): keep the text unchanged.
            if (close == -1) { add(s.substring(open), c.string); break }
            add("{", c.operator); add(s.substring(open + 1, close), c.text); add("}", c.operator)
            k = close + 1
        }
    }

    private fun AnnotatedString.Builder.add(t: String, color: androidx.compose.ui.graphics.Color) {
        if (t.isEmpty()) return
        val start = length; append(t); addStyle(SpanStyle(color = color), start, length)
    }
}
