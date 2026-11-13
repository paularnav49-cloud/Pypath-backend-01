package com.pypath.app.ui.screens.review

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pypath.app.audio.LocalSounds
import com.pypath.app.audio.Sfx
import com.pypath.app.data.activity.ActivityData
import com.pypath.app.data.activity.ReviewSessionState
import com.pypath.app.domain.Review
import com.pypath.app.domain.ReviewItem
import com.pypath.app.domain.XpRules
import com.pypath.app.ui.components.AppButton
import com.pypath.app.ui.components.AppCard
import com.pypath.app.ui.components.AppTopBar
import com.pypath.app.ui.components.ButtonKind
import com.pypath.app.ui.components.ProgressBar
import com.pypath.app.ui.components.rememberWrongAnswerHaptic
import com.pypath.app.ui.mcq.McqPhase
import com.pypath.app.ui.mcq.McqQuestionCard
import com.pypath.app.ui.mcq.McqSession
import com.pypath.app.ui.theme.AppTheme

/** Holds the question session across rotation; rebuilt from saved state after the app is killed. */
class ReviewViewModel : ViewModel() {
    var session: McqSession? = null
        private set
    var items: List<ReviewItem> = emptyList()
        private set

    fun ensure(remaining: List<ReviewItem>, answeredBefore: Int): McqSession? {
        session?.let { return it }
        if (remaining.isEmpty()) return null
        items = remaining
        return McqSession(
            remaining.map { it.question }, passPercent = 0,
            numberOffset = answeredBefore, displayTotal = answeredBefore + remaining.size,
        ).also { session = it }
    }
}

/**
 * Spaced review: same question UI as quizzes (hint, per-option feedback, sounds). No pass mark and
 * no penalty. Each answer is saved straight away, so leaving or killing the app keeps the place.
 */
@Composable
fun ReviewScreen(
    activity: ActivityData,
    index: Map<String, ReviewItem>,
    onAnswer: (item: ReviewItem, correct: Boolean, hintUsed: Boolean) -> Unit,
    onFinishEarly: () -> Unit,
    onDone: () -> Unit,
    onLeave: () -> Unit,
) {
    val c = AppTheme.colors
    val saved = activity.reviewSession
    val vm: ReviewViewModel = viewModel()
    val session = vm.ensure(Review.remaining(activity, index), saved?.answered ?: 0)
    val sounds = LocalSounds.current
    val wrongHaptic = rememberWrongAnswerHaptic()

    // A saved session whose remaining questions all vanished from the course: finish it quietly.
    LaunchedEffect(saved, session) {
        if (session == null && saved != null && !saved.finished) onFinishEarly()
    }
    BackHandler(onBack = { if (saved?.finished == true) onDone() else onLeave() })

    Column(Modifier.fillMaxSize().background(c.background)) {
        AppTopBar(
            title = "Review",
            subtitle = if (saved?.scope?.isNotEmpty() == true) "Weakest topics" else "Questions you found tricky",
            onBack = { if (saved?.finished == true) onDone() else onLeave() },
            backIcon = Icons.Filled.Close,
        )
        val showSummary = saved?.finished == true && (session == null || session.phase == McqPhase.FINISHED)
        when {
            showSummary -> Summary(saved!!, onDone)
            session == null || saved == null -> Empty(onDone)
            session.phase == McqPhase.FINISHED -> Waiting()
            else -> {
                Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    ProgressBar(
                        (session.numberOffset + session.results.size).toFloat() / session.displayTotal.coerceAtLeast(1),
                        Modifier.weight(1f), height = 6.dp,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "${session.numberOffset + session.index + 1}/${session.displayTotal}",
                        style = MaterialTheme.typography.bodySmall, color = c.textMuted,
                    )
                }
                AnimatedContent(
                    targetState = session.index,
                    transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(150)) },
                    modifier = Modifier.weight(1f),
                    label = "review",
                ) { _ ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                        McqQuestionCard(
                            session,
                            Modifier.widthIn(max = 640.dp).fillMaxSize().verticalScroll(rememberScrollState())
                                .padding(horizontal = 20.dp, vertical = 20.dp),
                        )
                    }
                }
                Box(Modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
                    if (session.phase == McqPhase.ANSWERING) {
                        AppButton(
                            "Check answer",
                            {
                                if (session.canCheck) {
                                    val item = vm.items[session.index]
                                    session.check()
                                    if (session.isCorrect) sounds.play(Sfx.CORRECT) else { sounds.play(Sfx.WRONG); wrongHaptic() }
                                    onAnswer(item, session.isCorrect, session.hintShown)
                                }
                            },
                            Modifier.fillMaxWidth(), enabled = session.canCheck,
                        )
                    } else {
                        AppButton(
                            if (session.isLastQuestion) "See summary" else "Next question",
                            { session.next() }, Modifier.fillMaxWidth(),
                            kind = if (session.isCorrect) ButtonKind.Success else ButtonKind.Primary,
                            trailing = Icons.AutoMirrored.Filled.ArrowForward,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Summary(s: ReviewSessionState, onDone: () -> Unit) {
    val c = AppTheme.colors
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(24.dp))
            Text(
                "Review done", style = MaterialTheme.typography.headlineMedium, color = c.text,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Questions you got right come back later; the others come back tomorrow.",
                style = MaterialTheme.typography.bodyLarge, color = c.textMuted, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            AppCard(Modifier.widthIn(max = 640.dp).fillMaxWidth()) {
                SummaryRow("Correct", "${s.correct} of ${s.answered}")
                SummaryRow("XP earned", if (s.xpEarned > 0) "+${s.xpEarned} XP" else "0 XP")
                SummaryRow("Moved up a box", "${s.movedUp}")
                if (s.xpEarned == 0 && s.answered > 0) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Review XP is paid for ${XpRules.REVIEW_SESSIONS_WITH_XP_PER_DAY} sessions a day. Practice still counts for your streak.",
                        style = MaterialTheme.typography.bodySmall, color = c.textMuted,
                    )
                }
            }
        }
        Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
            AppButton("Done", onDone, Modifier.fillMaxWidth(), kind = ButtonKind.Success)
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    val c = AppTheme.colors
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = c.textMuted)
        Text(value, style = MaterialTheme.typography.titleMedium, color = c.text)
    }
}

@Composable
private fun Empty(onDone: () -> Unit) {
    val c = AppTheme.colors
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("All caught up", style = MaterialTheme.typography.headlineSmall, color = c.text, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(8.dp))
        Text(
            "Nothing to review right now. Questions you answer wrong come back here.",
            style = MaterialTheme.typography.bodyLarge, color = c.textMuted, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        AppButton("Back", onDone, Modifier.fillMaxWidth(), kind = ButtonKind.Secondary)
    }
}

/** The last answer is being saved (a few milliseconds). */
@Composable
private fun Waiting() {
    val c = AppTheme.colors
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Saving…", style = MaterialTheme.typography.bodyLarge, color = c.textMuted)
    }
}
