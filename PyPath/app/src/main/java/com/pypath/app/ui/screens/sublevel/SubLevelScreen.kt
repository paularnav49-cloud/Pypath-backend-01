package com.pypath.app.ui.screens.sublevel

import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pypath.app.data.model.StepType
import com.pypath.app.domain.CourseSnapshot
import com.pypath.app.domain.NodeStatus
import com.pypath.app.practical.PracticalTask
import com.pypath.app.ui.components.AppButton
import com.pypath.app.ui.components.AppCard
import com.pypath.app.ui.components.AppTopBar
import com.pypath.app.ui.components.ButtonKind
import com.pypath.app.ui.components.Eyebrow
import com.pypath.app.ui.components.Pill
import com.pypath.app.ui.theme.AppTheme

private enum class StepUi { DONE, CURRENT, LOCKED, SOON }

@Composable
fun SubLevelScreen(
    snapshot: CourseSnapshot,
    subLevelId: String,
    onBack: () -> Unit,
    onOpened: () -> Unit,
    onStartLesson: () -> Unit,
    onStartQuiz: () -> Unit,
    /** Practical task that unlocks with this sub-level, if any. */
    practiceTask: PracticalTask? = null,
    onOpenPractice: (String) -> Unit = {},
    /** Opens the guided build for project sub-levels (Level 7). */
    onStartProject: () -> Unit = {},
) {
    val c = AppTheme.colors
    val s = snapshot.subLevel(subLevelId) ?: return
    LaunchedEffect(subLevelId) { if (s.isPlayable) onOpened() }

    val p = s.progress
    val learnDone = StepType.LEARN in p.completedSteps
    val quizDone = StepType.QUIZ in p.completedSteps
    val index = s.indexInLevel + 1

    Column(Modifier.fillMaxSize().background(c.background)) {
        AppTopBar(
            title = "${s.subLevel.code} ${s.subLevel.title}",
            subtitle = "Level ${s.level.number} · ${s.level.title}",
            onBack = onBack,
        )
        Column(
            // Batch 3 Part 3: centred with a max width on tablets; unchanged on phones.
            Modifier.weight(1f).align(Alignment.CenterHorizontally).widthIn(max = 640.dp).fillMaxWidth()
                .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(4.dp))
            Eyebrow("Sub-level $index of ${s.level.subLevels.size}")
            Spacer(Modifier.height(6.dp))
            Text(s.subLevel.title, style = MaterialTheme.typography.headlineMedium, color = c.text, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(8.dp))
            Text(s.subLevel.summary, style = MaterialTheme.typography.bodyLarge, color = c.textMuted)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("${s.subLevel.estimatedMinutes} min", c.brand, c.brandSoft)
                s.subLevel.quiz?.let { Pill("${it.questions.size} questions", c.textMuted, c.surfaceAlt) }
                if (s.status == NodeStatus.COMPLETED) Pill("Completed", c.success, c.successSoft, icon = Icons.Filled.Check)
            }

            Spacer(Modifier.height(28.dp))
            Text("Steps", style = MaterialTheme.typography.titleLarge, color = c.text, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(12.dp))

            if (!s.isPlayable) {
                AppCard(Modifier.fillMaxWidth()) {
                    Text("This sub-level is locked", style = MaterialTheme.typography.titleSmall, color = c.text)
                    Text("Finish ${s.unlockedBy?.code ?: "the previous sub-level"} first.", style = MaterialTheme.typography.bodySmall, color = c.textMuted)
                }
            } else {
                val steps = s.subLevel.steps.toMutableList()
                if (StepType.PRACTICE !in steps && !s.subLevel.isProject) steps += StepType.PRACTICE // preview of what's coming
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    steps.forEachIndexed { i, step ->
                        val practical = step == StepType.PRACTICE && practiceTask != null
                        val ui = when {
                            practical && s.status == NodeStatus.COMPLETED -> StepUi.CURRENT
                            practical -> StepUi.LOCKED
                            !step.isSupported -> StepUi.SOON
                            step in p.completedSteps -> StepUi.DONE
                            step == StepType.LEARN || step == StepType.PROJECT -> StepUi.CURRENT
                            step == StepType.QUIZ && learnDone -> StepUi.CURRENT
                            else -> StepUi.LOCKED
                        }
                        val detail = when (step) {
                            StepType.LEARN -> "${s.subLevel.lesson?.pages?.size ?: 0} short pages with code examples"
                            StepType.QUIZ -> {
                                val q = p.quiz
                                when {
                                    q != null && quizDone -> "Best score ${q.bestScore}/${q.bestOutOf}"
                                    q != null -> "Last try ${q.lastScore}/${q.total} · need ${s.subLevel.quiz?.passPercent ?: 60}% to pass"
                                    !learnDone -> "Unlocks after the lesson"
                                    else -> "Answer ${s.subLevel.quiz?.questions?.size ?: 0} questions about this lesson"
                                }
                            }
                            StepType.PROJECT -> {
                                val done = p.project?.completedStages?.size ?: 0
                                val total = s.subLevel.stages.size
                                if (done == 0) "Build a complete program in $total guided stages"
                                else "$done of $total stages built"
                            }
                            else -> when {
                                practiceTask == null -> "Hands-on coding is coming in a future update"
                                s.status == NodeStatus.COMPLETED -> "Practical: ${practiceTask.title} · write and run it in the Practical tab"
                                else -> "Practical \"${practiceTask.title}\" unlocks when you finish this sub-level"
                            }
                        }
                        StepRow(
                            i + 1, step.label, detail, ui,
                            onClick = when {
                                practical && ui == StepUi.CURRENT -> ({ onOpenPractice(practiceTask!!.id) })
                                step == StepType.PROJECT -> onStartProject
                                else -> null
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }

        if (s.isPlayable) {
            Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when {
                    s.subLevel.isProject -> {
                        val started = p.project?.briefingSeen == true
                        AppButton(
                            when {
                                s.status == NodeStatus.COMPLETED -> "Review project"
                                started -> "Continue project"
                                else -> "Start project"
                            },
                            onStartProject, Modifier.fillMaxWidth(), leading = Icons.Filled.PlayArrow,
                        )
                    }
                    !learnDone -> AppButton("Start lesson", onStartLesson, Modifier.fillMaxWidth(), leading = Icons.Filled.PlayArrow)
                    !quizDone -> {
                        AppButton(if (p.quiz == null) "Start MCQs" else "Retry MCQs", onStartQuiz, Modifier.fillMaxWidth(), trailing = Icons.AutoMirrored.Filled.ArrowForward)
                        AppButton("Review lesson", onStartLesson, Modifier.fillMaxWidth(), kind = ButtonKind.Secondary)
                    }
                    else -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        AppButton("Review lesson", onStartLesson, Modifier.weight(1f), kind = ButtonKind.Secondary)
                        AppButton("Retake MCQs", onStartQuiz, Modifier.weight(1f), kind = ButtonKind.Secondary, leading = Icons.Filled.Refresh)
                    }
                }
            }
        }
    }
}

@Composable
private fun StepRow(n: Int, title: String, detail: String, ui: StepUi, onClick: (() -> Unit)? = null) {
    val c = AppTheme.colors
    val (bg, fg, icon) = when (ui) {
        StepUi.DONE -> Triple(c.success, c.onSuccess, Icons.Filled.Check as ImageVector?)
        StepUi.CURRENT -> Triple(c.brand, c.onBrand, null)
        StepUi.LOCKED -> Triple(c.surfaceAlt, c.locked, Icons.Filled.Lock)
        StepUi.SOON -> Triple(c.surfaceAlt, c.locked, Icons.Filled.Info)
    }
    AppCard(
        Modifier.fillMaxWidth(),
        onClick = onClick,
        borderColor = if (ui == StepUi.CURRENT) c.brand.copy(alpha = 0.5f) else c.border,
        background = if (ui == StepUi.SOON) c.surface.copy(alpha = 0.55f) else c.surface,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(bg), contentAlignment = Alignment.Center) {
                if (icon != null) Icon(icon, null, tint = fg, modifier = Modifier.size(20.dp))
                else Text("$n", style = MaterialTheme.typography.titleSmall, color = fg, fontWeight = FontWeight.ExtraBold)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = if (ui == StepUi.SOON || ui == StepUi.LOCKED) c.textMuted else c.text)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = c.textMuted)
            }
            when (ui) {
                StepUi.DONE -> Pill("Done", c.success, c.successSoft)
                StepUi.SOON -> Pill("Soon", c.textFaint, c.surfaceAlt)
                StepUi.CURRENT -> if (onClick != null) Pill("Open", c.brand, c.brandSoft)
                else -> {}
            }
        }
    }
}
