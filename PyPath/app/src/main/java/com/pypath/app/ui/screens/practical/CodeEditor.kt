package com.pypath.app.ui.screens.practical

import androidx.compose.ui.text.style.TextOverflow

import androidx.compose.ui.graphics.vector.ImageVector

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pypath.app.ui.components.PythonHighlighter
import com.pypath.app.ui.theme.AppTheme
import com.pypath.app.ui.theme.CodeColors
import com.pypath.app.ui.theme.CodeTextStyle
import com.pypath.app.ui.theme.Mono

private const val INDENT = "    "

/**
 * Editable Python editor: a real [BasicTextField] (cursor, selection, copy/paste, IME and
 * hardware keyboards) with live syntax highlighting, line numbers, auto-indent after `:` and
 * Tab / Shift+Tab indentation.
 */
@Composable
fun CodeEditor(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester = remember { FocusRequester() },
    errorLine: Int? = null,
    /** Premium Phase 1: optional header label and action (defaults keep the original "main.py" / Reset). */
    fileName: String = "main.py",
    actionLabel: String = "Reset",
    actionIcon: ImageVector = Icons.Filled.Refresh,
) {
    val c = AppTheme.colors.code
    val shape = RoundedCornerShape(16.dp)
    val highlighter = remember(c) { HighlightTransformation(c) }
    val lineCount = value.text.count { it == '\n' } + 1
    val (curLine, curCol) = remember(value.text, value.selection) { lineAndColumn(value.text, value.selection.start) }

    Column(modifier.fillMaxWidth().clip(shape).background(c.background)) {
        Row(
            Modifier.fillMaxWidth().background(c.header).padding(start = 14.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf(Color(0xFFFF5F57), Color(0xFFFEBC2E), Color(0xFF28C840)).forEach {
                Box(Modifier.size(9.dp).clip(CircleShape).background(it.copy(alpha = 0.85f)))
                Spacer(Modifier.width(6.dp))
            }
            Spacer(Modifier.width(6.dp))
            Text(
                fileName, style = MaterialTheme.typography.bodySmall.copy(fontFamily = Mono), color = c.comment,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 140.dp),
            )
            Spacer(Modifier.weight(1f))
            Text("Ln $curLine, Col $curCol", style = MaterialTheme.typography.bodySmall.copy(fontFamily = Mono), color = c.comment)
            Spacer(Modifier.width(4.dp))
            Row(
                Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onReset).padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(actionIcon, null, tint = c.text.copy(alpha = 0.8f), modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(4.dp))
                Text(actionLabel, style = MaterialTheme.typography.labelMedium, color = c.text.copy(alpha = 0.8f))
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val viewport = maxWidth
            Row(Modifier.fillMaxWidth().padding(vertical = 14.dp)) {
                // Line numbers: lines never soft-wrap (the code area scrolls sideways), so one
                // number per text line lines up exactly with the code.
                Text(
                    buildAnnotatedString {
                        for (i in 1..lineCount) {
                            val style = when (i) {
                                errorLine -> SpanStyle(color = AppTheme.colors.error)
                                curLine -> SpanStyle(color = c.text.copy(alpha = 0.85f))
                                else -> SpanStyle(color = c.comment.copy(alpha = 0.6f))
                            }
                            pushStyle(style); append(i.toString()); pop()
                            if (i < lineCount) append('\n')
                        }
                    },
                    style = CodeTextStyle, textAlign = TextAlign.End,
                    modifier = Modifier.padding(start = 10.dp).widthIn(min = 22.dp),
                )
                Spacer(Modifier.width(12.dp))
                Box(Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
                    BasicTextField(
                        value = value,
                        onValueChange = { onValueChange(autoIndent(value, it)) },
                        textStyle = CodeTextStyle.copy(color = c.text),
                        cursorBrush = SolidColor(c.function),
                        visualTransformation = highlighter,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.None,
                            autoCorrectEnabled = false,
                            keyboardType = KeyboardType.Ascii,
                            imeAction = ImeAction.None,
                        ),
                        modifier = Modifier
                            .widthIn(min = viewport - 56.dp)
                            .heightIn(min = 230.dp)
                            .padding(end = 16.dp)
                            .focusRequester(focusRequester)
                            .semantics { contentDescription = "Python code editor" }
                            .onPreviewKeyEvent { e ->
                                if (e.type == KeyEventType.KeyDown && e.key == Key.Tab) {
                                    onValueChange(if (e.isShiftPressed) dedent(value) else indent(value)); true
                                } else false
                            },
                    )
                }
            }
        }
    }
}

/** Shortcut keys that are awkward to reach on phone keyboards. */
@Composable
fun EditorKeyBar(value: TextFieldValue, onValueChange: (TextFieldValue) -> Unit, modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    val keys = listOf("Tab", "⇤", ":", "(", ")", "\"", "'", "=", "[", "]", "{", "}", "#", "+", "-", "*", "/", "<", ">", "_", ",", ".")
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        keys.forEach { k ->
            Box(
                Modifier.clip(RoundedCornerShape(10.dp)).background(c.surface)
                    .clickable {
                        onValueChange(
                            when (k) {
                                "Tab" -> indent(value)
                                "⇤" -> dedent(value)
                                else -> insert(value, k)
                            }
                        )
                    }
                    .padding(PaddingValues(horizontal = 12.dp, vertical = 8.dp))
                    .semantics { contentDescription = if (k == "⇤") "Unindent" else "Insert $k" },
                contentAlignment = Alignment.Center,
            ) {
                Text(k, style = MaterialTheme.typography.labelLarge.copy(fontFamily = Mono), color = c.text)
            }
        }
    }
}

private class HighlightTransformation(private val colors: CodeColors) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val highlighted = PythonHighlighter.highlight(text.text, colors)
        // The highlighter only adds styles; guard anyway so offsets can never mismatch.
        val out = if (highlighted.text == text.text) highlighted else text
        return TransformedText(out, OffsetMapping.Identity)
    }
}

// ───────────── text editing helpers (pure functions) ─────────────

internal fun lineAndColumn(text: String, offset: Int): Pair<Int, Int> {
    val o = offset.coerceIn(0, text.length)
    var line = 1
    var lineStart = 0
    for (i in 0 until o) if (text[i] == '\n') { line++; lineStart = i + 1 }
    return line to (o - lineStart + 1)
}

/** Offset of the first character of 1-based [line]. */
internal fun lineStartOffset(text: String, line: Int): Int {
    var l = 1
    if (line <= 1) return 0
    for (i in text.indices) if (text[i] == '\n') { l++; if (l == line) return i + 1 }
    return text.length
}

internal fun insert(v: TextFieldValue, s: String): TextFieldValue {
    val start = minOf(v.selection.start, v.selection.end)
    val end = maxOf(v.selection.start, v.selection.end)
    val text = v.text.substring(0, start) + s + v.text.substring(end)
    return TextFieldValue(text, TextRange(start + s.length))
}

/**
 * When the IME inserts a single newline, carry the previous line's indentation over and add one
 * level after a line ending in `:` (like Python editors do).
 */
internal fun autoIndent(old: TextFieldValue, new: TextFieldValue): TextFieldValue {
    val cursor = new.selection.start
    if (!new.selection.collapsed || cursor == 0) return new
    val removed = old.text.length - (maxOf(old.selection.start, old.selection.end) - minOf(old.selection.start, old.selection.end))
    if (new.text.length != removed + 1 || new.text[cursor - 1] != '\n') return new
    val before = new.text.substring(0, cursor - 1)
    if (before + new.text.substring(cursor) != old.text.removeRange(minOf(old.selection.start, old.selection.end), maxOf(old.selection.start, old.selection.end))) return new
    val lineStart = before.lastIndexOf('\n') + 1
    val line = before.substring(lineStart)
    var indent = line.takeWhile { it == ' ' || it == '\t' }
    if (line.trimEnd().endsWith(":")) indent += INDENT
    if (indent.isEmpty()) return new
    val text = new.text.substring(0, cursor) + indent + new.text.substring(cursor)
    return new.copy(text = text, selection = TextRange(cursor + indent.length), composition = null)
}

/** Indent: with a selection spanning lines, indents every selected line; otherwise inserts 4 spaces. */
internal fun indent(v: TextFieldValue): TextFieldValue {
    val s = minOf(v.selection.start, v.selection.end)
    val e = maxOf(v.selection.start, v.selection.end)
    if (s == e || !v.text.substring(s, e).contains('\n')) return insert(v, INDENT)
    val first = v.text.lastIndexOf('\n', s - 1) + 1
    val lines = v.text.substring(first, e).split('\n')
    val replaced = lines.joinToString("\n") { INDENT + it }
    val text = v.text.substring(0, first) + replaced + v.text.substring(e)
    return TextFieldValue(text, TextRange(s + INDENT.length, e + INDENT.length * lines.size))
}

/** Removes up to 4 leading spaces from the current (or every selected) line. */
internal fun dedent(v: TextFieldValue): TextFieldValue {
    val s = minOf(v.selection.start, v.selection.end)
    val e = maxOf(v.selection.start, v.selection.end)
    val first = v.text.lastIndexOf('\n', (s - 1).coerceAtLeast(0)).let { if (s == 0) 0 else it + 1 }
    val lastEnd = v.text.indexOf('\n', e).let { if (it == -1) v.text.length else it }
    val lines = v.text.substring(first, lastEnd).split('\n')
    var removedFirst = 0
    var removedTotal = 0
    val newLines = lines.mapIndexed { i, line ->
        val n = line.takeWhile { it == ' ' }.length.coerceAtMost(INDENT.length)
        if (i == 0) removedFirst = n
        removedTotal += n
        line.substring(n)
    }
    val text = v.text.substring(0, first) + newLines.joinToString("\n") + v.text.substring(lastEnd)
    val ns = (s - removedFirst).coerceAtLeast(first)
    val ne = (e - removedTotal).coerceAtLeast(ns)
    return TextFieldValue(text, TextRange(ns, ne))
}
