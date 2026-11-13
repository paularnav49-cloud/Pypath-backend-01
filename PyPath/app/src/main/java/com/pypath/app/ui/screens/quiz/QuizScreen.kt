package com.pypath.app.ui.screens.quiz

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pypath.app.audio.LocalSounds
import com.pypath.app.audio.Sfx
import com.pypath.app.domain.SubLevelState
import com.pypath.app.domain.XpRules
import com.pypath.app.ui.components.AppButton
import com.pypath.app.ui.components.AppCard
import com.pypath.app.ui.components.AppTopBar
import com.pypath.app.ui.components.ButtonKind
import com.pypath.app.ui.components.ProgressBar
import com.pypath.app.ui.components.ProgressRing
import com.pypath.app.ui.components.rememberWrongAnswerHaptic
import com.pypath.app.ui.mcq.McqPhase
import com.pypath.app.ui.mcq.McqQuestionCard
import com.pypath.app.ui.mcq.McqSession
import com.pypath.app.ui.mcq.McqViewModel
import com.pypath.app.ui.theme.AppTheme

@Composable
fun QuizScreen(
    state: SubLevelState?,
    onClose: () -> Unit,
    onSubmitResult: (score: Int, total: Int, passed: Boolean) -> Unit,
    onPassedContinue: () -> Unit,
    onReviewLesson: () -> Unit,
    /** Batch 3: records an answer (question id, correct, hint opened) and returns the XP it earned. */
    onAnswer: (questionId: String, correct: Boolean, hintUsed: Boolean) -> Int = { _, _, _ -> 0 },
) {
    val c = AppTheme.colors
    val quiz = state?.subLevel?.quiz
    if (state == null || quiz == null || quiz.questions.isEmpty()) {
        Box(Modifier.fillMaxSize().background(c.background), contentAlignment = Alignment.Center) {
            Text("Questions for this sub-level are coming soon.", color = c.textMuted)
        }
        return
    }
    val vm: McqViewModel = viewModel()
    val session = vm.ensure(quiz.questions, quiz.passPercent)
    var confirmExit by rememberSaveable { mutableStateOf(false) }
    val sounds = LocalSounds.current
    val wrongHaptic = rememberWrongAnswerHaptic()
    val checkAnswer: () -> Unit = {
        if (session.canCheck) {
            session.check()
            if (session.isCorrect) sounds.play(Sfx.CORRECT) else { sounds.play(Sfx.WRONG); wrongHaptic() }
            session.addXp(onAnswer(session.current.id, session.isCorrect, session.hintShown))
        }
    }
    // Saved progress before this attempt's result is stored (the save happens after the effect below).
    val passedBefore by rememberUpdatedState(state.progress.quiz?.passed == true)

    val requestClose = { if (session.inProgress) confirmExit = true else onClose() }
    BackHandler(onBack = requestClose)

    // Persist the result exactly once per finished attempt.
    LaunchedEffect(session.attempt) {
        if (session.phase == McqPhase.FINISHED && session.attempt > session.reportedAttempt) {
            session.reportedAttempt = session.attempt
            session.replay = passedBefore
            session.bonusXp = if (session.passed && !passedBefore) XpRules.QUIZ_PASS else 0
            sounds.play(if (session.passed) Sfx.PASS else Sfx.FAIL)
            onSubmitResult(session.score, session.total, session.passed)
        }
    }

    // Batch 3 Part 3: on tablets the content is centred with a max width of 640dp; phones are unchanged.
    Box(Modifier.fillMaxSize().background(c.background), contentAlignment = Alignment.TopCenter) {
    Column(Modifier.fillMaxHeight().widthIn(max = 640.dp).fillMaxWidth()) {
        AppTopBar(
            title = "${state.subLevel.code} ${state.subLevel.title}",
            subtitle = "Step 2 · MCQs",
            onBack = requestClose,
            backIcon = Icons.Filled.Close,
        )
        if (session.phase != McqPhase.FINISHED) {
            Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                ProgressBar(session.progress, Modifier.weight(1f), height = 6.dp)
                Spacer(Modifier.width(12.dp))
                Text("${session.index + 1}/${session.total}", style = MaterialTheme.typography.bodySmall, color = c.textMuted)
            }
            AnimatedContent(
                targetState = session.index,
                transitionSpec = { (slideInHorizontally(tween(300)) { it / 5 } + fadeIn(tween(300))) togetherWith fadeOut(tween(150)) },
                modifier = Modifier.weight(1f),
                label = "question",
            ) { _ ->
                McqQuestionCard(
                    session,
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 20.dp),
                )
            }
            Box(Modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
                when (session.phase) {
                    McqPhase.ANSWERING -> AppButton(
                        "Check answer", checkAnswer, Modifier.fillMaxWidth(), enabled = session.canCheck,
                    )
                    else -> AppButton(
                        if (session.isLastQuestion) "See results" else "Next question",
                        { session.next() }, Modifier.fillMaxWidth(),
                        kind = if (session.isCorrect) ButtonKind.Success else ButtonKind.Primary,
                        trailing = Icons.AutoMirrored.Filled.ArrowForward,
                    )
                }
            }
        } else {
            QuizResult(
                session = session,
                onContinue = onPassedContinue,
                onRetry = { session.restart() },
                onReview = onReviewLesson,
            )
        }
    }
    }

    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text("Leave the MCQs?") },
            text = { Text("Your answers for this attempt won't be saved, and the sub-level won't be marked complete.") },
            confirmButton = { TextButton(onClick = { confirmExit = false; onClose() }) { Text("Leave", color = c.error) } },
            dismissButton = { TextButton(onClick = { confirmExit = false }) { Text("Keep going") } },
            containerColor = c.surface,
        )
    }
}

@Composable
private fun QuizResult(session: McqSession, onContinue: () -> Unit, onRetry: () -> Unit, onReview: () -> Unit) {
    val c = AppTheme.colors
    val passed = session.passed
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(24.dp))
            ProgressRing(
                fraction = session.score.toFloat() / session.total, size = 140.dp, stroke = 12.dp,
                color = if (passed) c.success else c.error,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${session.score}/${session.total}", style = MaterialTheme.typography.headlineMedium, color = c.text)
                    Text("${session.percent}%", style = MaterialTheme.typography.bodySmall, color = c.textMuted)
                }
            }
            Spacer(Modifier.height(24.dp))
            Text(if (passed) "Nicely done!" else "Almost there", style = MaterialTheme.typography.headlineMedium, color = c.text, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(8.dp))
            Text(
                if (passed) "You passed the MCQs for this sub-level."
                else "You need ${session.passPercent}% to complete this sub-level. Review the lesson or try again.",
                style = MaterialTheme.typography.bodyLarge, color = c.textMuted, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            XpEarnedLine(session)
            Spacer(Modifier.height(16.dp))
            AppCard(Modifier.fillMaxWidth()) {
                Text("Your answers", style = MaterialTheme.typography.titleSmall, color = c.text)
                Spacer(Modifier.height(10.dp))
                session.questions.forEachIndexed { i, q ->
                    val ok = session.results.getOrNull(i) == true
                    Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(22.dp).clip(CircleShape).background(if (ok) c.success else c.error),
                            contentAlignment = Alignment.Center,
                        ) { Icon(if (ok) Icons.Filled.Check else Icons.Filled.Close, if (ok) "Correct" else "Wrong", tint = if (ok) c.onSuccess else c.onError, modifier = Modifier.size(14.dp)) }
                        Spacer(Modifier.width(10.dp))
                        Text("Q${i + 1}", style = MaterialTheme.typography.labelMedium, color = c.textMuted, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(8.dp))
                        Text(q.prompt, style = MaterialTheme.typography.bodyMedium, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
        Column(
            Modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (passed) {
                AppButton("Continue", onContinue, Modifier.fillMaxWidth(), kind = ButtonKind.Success, trailing = Icons.AutoMirrored.Filled.ArrowForward)
            } else {
                AppButton("Try again", onRetry, Modifier.fillMaxWidth(), leading = Icons.Filled.Refresh)
                AppButton("Review lesson", onReview, Modifier.fillMaxWidth(), kind = ButtonKind.Secondary)
            }
        }
    }
}

/** Batch 3: XP earned in this attempt, or why there is none. */
@Composable
private fun XpEarnedLine(session: McqSession) {
    val c = AppTheme.colors
    val total = session.xpThisAttempt + session.bonusXp
    val text = when {
        total > 0 && session.bonusXp > 0 -> "+$total XP (${session.xpThisAttempt} for answers, ${session.bonusXp} for passing)"
        total > 0 -> "+$total XP for correct answers"
        session.replay -> "You already passed this quiz, so replaying it earns no new XP."
        else -> "No XP this time. Correct answers earn XP, so try again."
    }
    Text(
        text, style = MaterialTheme.typography.bodyMedium,
        color = if (total > 0) c.text else c.textMuted, textAlign = TextAlign.Center,
        fontWeight = if (total > 0) FontWeight.Bold else FontWeight.Normal,
    )
}
