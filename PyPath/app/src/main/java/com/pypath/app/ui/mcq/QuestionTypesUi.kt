package com.pypath.app.ui.mcq

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pypath.app.data.model.Question
import com.pypath.app.domain.QuestionCheck
import com.pypath.app.domain.QuestionCheck.FillPart
import com.pypath.app.ui.components.CodeBlock
import com.pypath.app.ui.theme.AppTheme
import com.pypath.app.ui.theme.CodeTextStyle

private val CodeSmall = CodeTextStyle.copy(fontSize = 14.sp)

/** "What does this print?" Code plus a text field; [QuestionCheck.predictCorrect] compares. */
@Composable
internal fun PredictBody(session: McqSession, q: Question) {
    val c = AppTheme.colors
    q.code?.let { CodeBlock(it) }
    OutlinedTextField(
        value = session.typed,
        onValueChange = session::type,
        modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
        readOnly = session.phase != McqPhase.ANSWERING,
        label = { Text("Output") },
        placeholder = { Text("Type exactly what the program prints") },
        textStyle = CodeSmall.copy(color = c.text),
        minLines = 2,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            keyboardType = KeyboardType.Ascii,
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = c.brand, unfocusedBorderColor = c.border,
            focusedContainerColor = c.surface, unfocusedContainerColor = c.surface,
            focusedLabelColor = c.brand, unfocusedLabelColor = c.textMuted,
            cursorColor = c.brand,
        ),
    )
    Text(QuestionCheck.predictRule(q), style = MaterialTheme.typography.bodySmall, color = c.textMuted)
}

/** Shared "It should print" box for fill and order questions. */
@Composable
private fun ExpectedOutput(output: String?) {
    val c = AppTheme.colors
    if (output.isNullOrBlank()) return
    val code = c.code
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(code.outputBg)
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .semantics(mergeDescendants = true) {},
    ) {
        Text("IT SHOULD PRINT", style = MaterialTheme.typography.labelSmall, color = code.comment)
        Spacer(Modifier.height(4.dp))
        Text(output, style = CodeSmall, color = code.outputText, softWrap = false, modifier = Modifier.horizontalScroll(rememberScrollState()))
    }
}

/** Splits fill parts into display lines (text pieces may contain line breaks). */
private fun fillLines(parts: List<FillPart>): List<List<FillPart>> {
    val lines = mutableListOf(mutableListOf<FillPart>())
    for (p in parts) {
        when (p) {
            is FillPart.Blank -> lines.last() += p
            is FillPart.Text -> {
                val pieces = p.text.split('\n')
                pieces.forEachIndexed { i, piece ->
                    if (i > 0) lines += mutableListOf<FillPart>()
                    if (piece.isNotEmpty()) lines.last() += FillPart.Text(piece)
                }
            }
        }
    }
    return lines
}

/** Fill in the blank: tap a blank, then a token from the bank. Every target is at least 48dp. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FillBody(session: McqSession, q: Question) {
    val c = AppTheme.colors
    val code = c.code
    val bank = QuestionCheck.fillBank(q)
    val answering = session.phase == McqPhase.ANSWERING
    ExpectedOutput(q.output)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(code.background)
            .horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        fillLines(QuestionCheck.fillParts(q)).forEach { line ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 24.dp)) {
                if (line.isEmpty()) Text(" ", style = CodeSmall, color = code.text)
                line.forEach { part ->
                    when (part) {
                        is FillPart.Text -> Text(part.text, style = CodeSmall, color = code.text, softWrap = false)
                        is FillPart.Blank -> {
                            val token = session.fill.getOrNull(part.index)?.let { bank.getOrNull(it) }
                            val active = answering && session.activeBlank == part.index
                            val label = "Blank ${part.index + 1} of ${q.blanks.size}"
                            Box(
                                Modifier.padding(horizontal = 2.dp, vertical = 2.dp)
                                    .heightIn(min = 48.dp).widthIn(min = 64.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (token != null) c.brandSoft else c.surface)
                                    .border(2.dp, if (active) c.brand else c.border, RoundedCornerShape(10.dp))
                                    .clickable(enabled = answering, role = Role.Button) { session.tapBlank(part.index) }
                                    .semantics {
                                        contentDescription = label
                                        stateDescription = token?.let { "filled with $it" } ?: "empty"
                                        selected = active
                                    }
                                    .padding(horizontal = 10.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(token ?: "____", style = CodeSmall, color = if (token != null) c.text else c.textMuted, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
    if (answering) {
        Text(
            "Tap a blank, then tap the word that belongs there. Tap a filled blank to empty it.",
            style = MaterialTheme.typography.bodySmall, color = c.textMuted,
        )
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        bank.forEachIndexed { i, token ->
            val usedIn = session.fill.indexOf(i)
            val used = usedIn >= 0
            Box(
                Modifier.heightIn(min = 48.dp).widthIn(min = 48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (used) c.surfaceAlt else c.surface)
                    .border(1.dp, if (used) c.border else c.brand, RoundedCornerShape(12.dp))
                    .clickable(enabled = answering, role = Role.Button) { session.tapToken(i) }
                    .semantics {
                        contentDescription = "Word $token"
                        stateDescription = if (used) "placed in blank ${usedIn + 1}" else "not used"
                    }
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(token, style = CodeSmall, color = if (used) c.textMuted else c.text, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** Put the lines in order with Move up / Move down buttons (also available as TalkBack actions). */
@Composable
internal fun OrderBody(session: McqSession, q: Question) {
    val c = AppTheme.colors
    val code = c.code
    val answering = session.phase == McqPhase.ANSWERING
    ExpectedOutput(q.output)
    if (answering) {
        Text(
            "Use the arrows to move each line. Indentation is already part of each line.",
            style = MaterialTheme.typography.bodySmall, color = c.textMuted,
        )
    }
    val order = session.order
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        order.forEachIndexed { pos, lineIndex ->
            val line = q.lines.getOrElse(lineIndex) { "" }
            val spoken = "Line ${pos + 1} of ${order.size}: ${QuestionCheck.spokenLine(line)}"
            val actions = buildList {
                if (answering && pos > 0) add(CustomAccessibilityAction("Move up") { session.moveLine(pos, -1); true })
                if (answering && pos < order.lastIndex) add(CustomAccessibilityAction("Move down") { session.moveLine(pos, 1); true })
            }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(code.background)
                    .semantics(mergeDescendants = true) {
                        contentDescription = spoken
                        customActions = actions
                    }
                    .padding(start = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${pos + 1}", style = CodeSmall, color = code.comment, modifier = Modifier.width(22.dp)
                        .clearAndSetSemantics {},
                )
                Text(
                    line, style = CodeSmall, color = code.text, softWrap = false,
                    modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()).padding(vertical = 12.dp)
                        .clearAndSetSemantics {},
                )
                if (answering) {
                    IconButton(onClick = { session.moveLine(pos, -1) }, enabled = pos > 0, modifier = Modifier.clearAndSetSemantics {}) {
                        Icon(Icons.Filled.KeyboardArrowUp, null, tint = if (pos > 0) code.text else code.comment.copy(alpha = 0.4f), modifier = Modifier.size(24.dp))
                    }
                    IconButton(onClick = { session.moveLine(pos, 1) }, enabled = pos < order.lastIndex, modifier = Modifier.clearAndSetSemantics {}) {
                        Icon(Icons.Filled.KeyboardArrowDown, null, tint = if (pos < order.lastIndex) code.text else code.comment.copy(alpha = 0.4f), modifier = Modifier.size(24.dp))
                    }
                }
            }
        }
    }
}

/** Label above the question for the new types, so the learner knows what to do. */
internal fun typeLabel(q: Question): String? = when (q.kind) {
    com.pypath.app.data.model.QuestionType.PREDICT -> "Predict the output"
    com.pypath.app.data.model.QuestionType.FILL -> "Fill in the blanks"
    com.pypath.app.data.model.QuestionType.ORDER -> "Put the lines in order"
    else -> null
}
