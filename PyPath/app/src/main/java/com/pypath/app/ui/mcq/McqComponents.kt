package com.pypath.app.ui.mcq

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pypath.app.data.model.Question
import com.pypath.app.data.model.QuestionType
import com.pypath.app.domain.QuestionCheck
import com.pypath.app.ui.components.CodeBlock
import com.pypath.app.ui.components.Eyebrow
import com.pypath.app.ui.theme.AppTheme
import com.pypath.app.ui.theme.CodeTextStyle

enum class OptionState { IDLE, SELECTED, CORRECT, WRONG, DIMMED }

/** Question prompt + optional code + four options. Pure UI driven by [McqSession]. */
@Composable
fun McqQuestionCard(session: McqSession, modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    val q = session.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Eyebrow("Question ${session.numberOffset + session.index + 1} of ${session.displayTotal}")
        // Batch 3: the new question types say what to do; MCQs look exactly as before.
        typeLabel(q)?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = c.textMuted) }
        Text(q.prompt, style = MaterialTheme.typography.headlineSmall, color = c.text)
        if (q.isMcq) q.code?.let { CodeBlock(it) }
        // Batch 1: optional hint. Without a hint this block draws nothing, so the card looks exactly as before.
        if (session.hasHint) HintArea(session, q)
        Spacer(Modifier.height(4.dp))
        when (q.kind) {
            QuestionType.PREDICT -> PredictBody(session, q)
            QuestionType.FILL -> FillBody(session, q)
            QuestionType.ORDER -> OrderBody(session, q)
            else -> q.options.forEachIndexed { i, text ->
                val state = optionState(session, q, i)
                McqOption(letter = ('A' + i).toString(), text = text, state = state, onClick = { session.select(i) })
            }
        }
        AnimatedVisibility(session.phase == McqPhase.FEEDBACK, enter = fadeIn(tween(250)) + expandVertically(tween(250))) {
            FeedbackPanel(
                correct = session.isCorrect, explanation = q.explanation, correctAnswer = QuestionCheck.correctAnswerText(q),
                // Batch 1: feedback for the chosen option, shown only after a wrong answer (null = none written).
                chosenFeedback = if (session.isCorrect) null else q.feedbackFor(session.selected),
                answerIsCode = !q.isMcq,
            )
        }
    }
}

private fun optionState(s: McqSession, q: Question, i: Int): OptionState = when (s.phase) {
    McqPhase.ANSWERING -> if (s.selected == i) OptionState.SELECTED else OptionState.IDLE
    else -> when {
        i == q.correctIndex -> OptionState.CORRECT
        i == s.selected -> OptionState.WRONG
        else -> OptionState.DIMMED
    }
}

@Composable
fun McqOption(letter: String, text: String, state: OptionState, onClick: () -> Unit, mono: Boolean = false) {
    val c = AppTheme.colors
    val border by animateColorAsState(
        when (state) {
            OptionState.SELECTED -> c.brand; OptionState.CORRECT -> c.success; OptionState.WRONG -> c.error; else -> c.border
        }, label = "ob",
    )
    val bg by animateColorAsState(
        when (state) {
            OptionState.SELECTED -> c.brandSoft; OptionState.CORRECT -> c.successSoft; OptionState.WRONG -> c.errorSoft; else -> c.surface
        }, label = "obg",
    )
    // Small shake for wrong answers, pop for correct ones.
    val shake = remember { Animatable(0f) }
    val pop = remember { Animatable(1f) }
    LaunchedEffect(state) {
        when (state) {
            OptionState.WRONG -> for (x in listOf(10f, -8f, 6f, -4f, 0f)) shake.animateTo(x, tween(55))
            OptionState.CORRECT -> { pop.animateTo(1.03f, tween(120)); pop.animateTo(1f, tween(160)) }
            else -> {}
        }
    }
    val shape = RoundedCornerShape(16.dp)
    val interactive = state == OptionState.IDLE || state == OptionState.SELECTED
    Row(
        Modifier.fillMaxWidth()
            .graphicsLayer { translationX = shake.value; scaleX = pop.value; scaleY = pop.value; alpha = if (state == OptionState.DIMMED) 0.55f else 1f }
            .clip(shape).background(bg).border(if (state == OptionState.IDLE || state == OptionState.DIMMED) 1.dp else 2.dp, border, shape)
            .clickable(enabled = interactive, role = Role.RadioButton, onClick = onClick)
            // Batch 3 Part 3: TalkBack hears selected / correct / wrong; changes are announced.
            .semantics(mergeDescendants = true) {
                selected = state == OptionState.SELECTED || state == OptionState.WRONG
                optionStateText(state)?.let { stateDescription = it }
                if (state == OptionState.CORRECT || state == OptionState.WRONG) liveRegion = LiveRegionMode.Polite
            }
            .heightIn(min = 48.dp)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val (badgeBg, badgeFg) = when (state) {
            OptionState.SELECTED -> c.brand to c.onBrand
            OptionState.CORRECT -> c.success to c.onSuccess
            OptionState.WRONG -> c.error to c.onError
            else -> c.surfaceAlt to c.textMuted
        }
        Box(Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(badgeBg), contentAlignment = Alignment.Center) {
            when (state) {
                OptionState.CORRECT -> Icon(Icons.Filled.Check, null, tint = badgeFg, modifier = Modifier.size(18.dp))
                OptionState.WRONG -> Icon(Icons.Filled.Close, null, tint = badgeFg, modifier = Modifier.size(18.dp))
                else -> Text(letter, style = MaterialTheme.typography.titleSmall, color = badgeFg, fontWeight = FontWeight.ExtraBold)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text,
                style = if (mono) CodeTextStyle.copy(fontSize = 14.sp) else MaterialTheme.typography.bodyLarge,
                color = c.text,
            )
            // Batch 3 Part 3: a text label next to the icon, so the result never relies on colour alone.
            val label = when (state) {
                OptionState.CORRECT -> "Correct answer"
                OptionState.WRONG -> "Your answer"
                else -> null
            }
            if (label != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                    color = if (state == OptionState.CORRECT) c.success else c.error,
                )
            }
        }
    }
}

/** Batch 3 Part 3: spoken state of an option (null when there is nothing to add). */
private fun optionStateText(state: OptionState): String? = when (state) {
    OptionState.SELECTED -> "Selected"
    OptionState.CORRECT -> "Correct answer"
    OptionState.WRONG -> "Your answer, wrong"
    OptionState.DIMMED -> "Not chosen"
    OptionState.IDLE -> null
}

@Composable
private fun FeedbackPanel(
    correct: Boolean, explanation: String, correctAnswer: String, chosenFeedback: String? = null,
    /** Batch 3: show the correct answer as code (predict output, filled tokens, ordered lines). */
    answerIsCode: Boolean = false,
) {
    val c = AppTheme.colors
    val accent = if (correct) c.success else c.error
    Column(
        Modifier.fillMaxWidth().padding(top = 4.dp).clip(RoundedCornerShape(16.dp))
            .background(if (correct) c.successSoft else c.errorSoft)
            // Batch 3 Part 3: the result is announced by TalkBack as soon as it appears.
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(24.dp).clip(CircleShape).background(accent), contentAlignment = Alignment.Center) {
                Icon(if (correct) Icons.Filled.Check else Icons.Filled.Close, null, tint = if (correct) c.onSuccess else c.onError, modifier = Modifier.size(15.dp))
            }
            Spacer(Modifier.width(10.dp))
            Text(if (correct) "Correct!" else "Not quite", style = MaterialTheme.typography.titleMedium, color = accent)
        }
        if (!correct && !answerIsCode) {
            Spacer(Modifier.height(8.dp))
            Text("Correct answer: $correctAnswer", style = MaterialTheme.typography.titleSmall, color = c.text)
        }
        if (!correct && answerIsCode) {
            Spacer(Modifier.height(8.dp))
            Text("Correct answer:", style = MaterialTheme.typography.titleSmall, color = c.text)
            Spacer(Modifier.height(4.dp))
            Text(
                correctAnswer, style = CodeTextStyle.copy(fontSize = 14.sp), color = c.text, softWrap = false,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(c.surface)
                    .horizontalScroll(rememberScrollState()).padding(10.dp),
            )
        }
        if (!correct && chosenFeedback != null) {
            Spacer(Modifier.height(6.dp))
            Text(chosenFeedback, style = MaterialTheme.typography.bodyMedium, color = c.text, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(6.dp))
        Text(explanation, style = MaterialTheme.typography.bodyMedium, color = c.text.copy(alpha = 0.85f))
    }
}

/**
 * Batch 1: "Hint" button shown before answering; tapping it reveals the hint text (counted in
 * [McqSession.hintsUsed], never blocks progress). Only called when the question has a hint.
 */
@Composable
private fun HintArea(session: McqSession, q: Question) {
    val c = AppTheme.colors
    val hint = q.hint ?: return
    if (!session.hintShown) {
        if (session.phase == McqPhase.ANSWERING) {
            Text(
                "Hint",
                style = MaterialTheme.typography.labelLarge, color = c.brand, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).border(1.dp, c.border, RoundedCornerShape(12.dp))
                    .clickable(role = Role.Button, onClickLabel = "Show hint", onClick = session::showHint)
                    .heightIn(min = 48.dp).wrapContentHeight(Alignment.CenterVertically)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
        return
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.brandSoft).padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text("Hint", style = MaterialTheme.typography.labelLarge, color = c.brand, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(hint, style = MaterialTheme.typography.bodyMedium, color = c.text)
    }
}
