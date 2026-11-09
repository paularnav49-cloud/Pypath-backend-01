package com.pypath.app.ui.screens.project

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pypath.app.audio.LocalSounds
import com.pypath.app.audio.Sfx
import com.pypath.app.data.model.SubLevel
import com.pypath.app.data.progress.ProjectProgress
import com.pypath.app.domain.CourseSnapshot
import com.pypath.app.ui.components.AppButton
import com.pypath.app.ui.components.AppCard
import com.pypath.app.ui.components.AppTopBar
import com.pypath.app.ui.components.ButtonKind
import com.pypath.app.ui.components.CodeBlock
import com.pypath.app.ui.components.Eyebrow
import com.pypath.app.ui.components.Pill
import com.pypath.app.ui.components.ProgressBar
import com.pypath.app.ui.components.PythonHighlighter
import com.pypath.app.ui.components.rememberReducedMotion
import com.pypath.app.ui.components.rememberWrongAnswerHaptic
import com.pypath.app.ui.mcq.McqOption
import com.pypath.app.ui.mcq.OptionState
import com.pypath.app.ui.theme.AppTheme
import com.pypath.app.ui.theme.CodeTextStyle
import kotlinx.coroutines.delay

private enum class Phase { BRIEFING, BUILD, DONE }

/**
 * Guided project: the Level 7 final project, or an optional level project (Level.projects). Step A: briefing. Step B: one stage at a time; choosing the right
 * option types its line(s) into the program. Step C: the finished program and a walkthrough.
 * Progress (briefing seen, solved stages, wrong attempts) is saved after every answer.
 */
@Composable
fun ProjectScreen(
    snapshot: CourseSnapshot,
    subLevelId: String,
    onBack: () -> Unit,
    onOpened: () -> Unit,
    onOpenConcept: (String) -> Unit,
    onBriefingSeen: () -> Unit,
    onStageCorrect: (stageId: String) -> Unit,
    onMiss: () -> Unit,
    onFinish: () -> Unit,
) {
    val c = AppTheme.colors
    val s = snapshot.subLevel(subLevelId)
    val sub = s?.subLevel
    if (s == null || sub == null || !sub.isProject || sub.stages.isEmpty()) {
        Box(Modifier.fillMaxSize().background(c.background), contentAlignment = Alignment.Center) {
            Text("This project is coming soon.", color = c.textMuted)
        }
        return
    }
    if (!s.isPlayable) {
        Column(Modifier.fillMaxSize().background(c.background)) {
            AppTopBar(sub.title, "Level ${s.level.number} · ${s.level.title}", onBack)
            AppCard(Modifier.padding(20.dp).fillMaxWidth()) {
                Text("This project is locked", style = MaterialTheme.typography.titleSmall, color = c.text)
                Text(
                    if (s.isBonus) "Finish every sub-level in Level ${s.level.number} first."
                    else "Finish ${s.unlockedBy?.code ?: "Level 6"} first.",
                    style = MaterialTheme.typography.bodySmall, color = c.textMuted,
                )
            }
        }
        return
    }
    val kind = if (s.isBonus) "Level ${s.level.number} project" else "Final project"
    LaunchedEffect(subLevelId) { onOpened() }
    val pp = s.progress.project ?: ProjectProgress()
    val allDone = sub.stages.all { it.id in pp.completedStages }
    var phase by rememberSaveable {
        mutableStateOf(if (!pp.briefingSeen) Phase.BRIEFING else if (allDone) Phase.DONE else Phase.BUILD)
    }
    BackHandler { if (phase == Phase.BRIEFING && pp.briefingSeen && !allDone) phase = Phase.BUILD else onBack() }

    Column(Modifier.fillMaxSize().background(c.background)) {
        when (phase) {
            Phase.BRIEFING -> Briefing(
                sub, pp, kind, onBack, onOpenConcept,
                onStart = { onBriefingSeen(); phase = if (allDone) Phase.DONE else Phase.BUILD },
            )
            Phase.BUILD -> Build(
                sub, pp, onBack,
                onShowBriefing = { phase = Phase.BRIEFING },
                onStageCorrect = onStageCorrect, onMiss = onMiss,
                onAllDone = { phase = Phase.DONE },
            )
            Phase.DONE -> Done(
                sub, pp, kind, isBonus = s.isBonus,
                levelBadgeEarned = s.level.id in snapshot.progress.earnedBadges, onBack = onBack, onFinish = onFinish,
            )
        }
    }
}

// ───────────── Step A: briefing ─────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.Briefing(
    sub: SubLevel, pp: ProjectProgress, kind: String, onBack: () -> Unit, onOpenConcept: (String) -> Unit, onStart: () -> Unit,
) {
    val c = AppTheme.colors
    val b = sub.briefing
    AppTopBar("${sub.code} ${sub.title}", "$kind · Briefing", onBack)
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        Eyebrow(kind)
        Spacer(Modifier.height(6.dp))
        Text(b?.title ?: sub.title, style = MaterialTheme.typography.headlineMedium, color = c.text, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("${sub.stages.size} stages", c.brand, c.brandSoft)
            Pill("${sub.estimatedMinutes} min", c.textMuted, c.surfaceAlt)
        }
        b?.text?.forEach {
            Spacer(Modifier.height(12.dp))
            Text(it, style = MaterialTheme.typography.bodyLarge, color = c.text)
        }
        if (!b?.why.isNullOrEmpty()) {
            Spacer(Modifier.height(22.dp))
            Text("Why it's useful", style = MaterialTheme.typography.titleMedium, color = c.text)
            b!!.why.forEach {
                Row(Modifier.padding(top = 8.dp)) {
                    Text("•", color = c.brand, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(10.dp))
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = c.text)
                }
            }
        }
        if (!b?.concepts.isNullOrEmpty()) {
            Spacer(Modifier.height(22.dp))
            Text("Concepts you'll use", style = MaterialTheme.typography.titleMedium, color = c.text)
            Text("Tap one to review that sub-level.", style = MaterialTheme.typography.bodySmall, color = c.textMuted)
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                b!!.concepts.forEach { concept ->
                    val code = concept.subLevelId.removePrefix("l").replace('s', '.')
                    TextButton(
                        onClick = { onOpenConcept(concept.subLevelId) },
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(c.surfaceAlt).heightIn(min = 36.dp),
                    ) { Text("$code  ${concept.label}", style = MaterialTheme.typography.labelMedium, color = c.text) }
                }
            }
        }
        if (!b?.sampleOutput.isNullOrEmpty()) {
            Spacer(Modifier.height(22.dp))
            Text(
                if (b.usesInput) "A sample run of the finished program" else "When it's finished, it prints exactly this",
                style = MaterialTheme.typography.titleMedium, color = c.text,
            )
            if (b.usesInput && b.sampleInputs.isNotEmpty()) {
                Text(
                    "In this run the user typed: ${b.sampleInputs.joinToString(", ")}",
                    style = MaterialTheme.typography.bodySmall, color = c.textMuted,
                )
            }
            Spacer(Modifier.height(10.dp))
            OutputBox(b!!.sampleOutput)
        }
        Spacer(Modifier.height(24.dp))
    }
    Box(Modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
        AppButton(
            if (pp.completedStages.isEmpty()) "Start building" else "Continue building",
            onStart, Modifier.fillMaxWidth(), leading = Icons.Filled.PlayArrow,
        )
    }
}

@Composable
private fun OutputBox(text: String) {
    val cc = AppTheme.colors.code
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(cc.outputBg).padding(16.dp)) {
        Text("OUTPUT", style = MaterialTheme.typography.labelSmall, color = cc.comment)
        Spacer(Modifier.height(6.dp))
        Text(text, style = CodeTextStyle, color = cc.outputText, softWrap = false, modifier = Modifier.horizontalScroll(rememberScrollState()))
    }
}

// ───────────── Step B: build ─────────────

@Composable
private fun ColumnScope.Build(
    sub: SubLevel,
    pp: ProjectProgress,
    onBack: () -> Unit,
    onShowBriefing: () -> Unit,
    onStageCorrect: (String) -> Unit,
    onMiss: () -> Unit,
    onAllDone: () -> Unit,
) {
    val c = AppTheme.colors
    val sounds = LocalSounds.current
    val haptic = rememberWrongAnswerHaptic()
    val reduced = rememberReducedMotion()
    val stages = sub.stages
    val firstOpen = stages.indexOfFirst { it.id !in pp.completedStages }.let { if (it < 0) stages.lastIndex else it }

    var stageIndex by rememberSaveable { mutableIntStateOf(firstOpen) }
    val stage = stages[stageIndex]
    var selected by rememberSaveable(stage.id) { mutableStateOf<Int?>(null) }
    var tried by rememberSaveable(stage.id) { mutableStateOf(listOf<Int>()) }
    var solved by rememberSaveable(stage.id) { mutableStateOf(stage.id in pp.completedStages) }
    var lastWrong by rememberSaveable(stage.id) { mutableStateOf<Int?>(null) }
    // Lines being typed in right now (only after a correct answer in this session).
    var typing by remember { mutableStateOf<Set<Int>>(emptySet()) }
    val typeProgress = remember { Animatable(1f) }
    val flash = remember { Animatable(0f) }

    val revealed = remember(pp.completedStages, solved, stage.id) {
        val done = stages.filter { it.id in pp.completedStages || (it.id == stage.id && solved) }
        done.flatMap { it.revealsLines }.toSet()
    }
    val doneCount = stages.count { it.id in pp.completedStages || (it.id == stage.id && solved) }

    val codeScroll = rememberScrollState()
    val lineHeightPx = with(LocalDensity.current) { CodeTextStyle.lineHeight.toPx() }
    LaunchedEffect(stage.id) {
        val target = ((stage.revealsLines.minOrNull() ?: 1) - 3).coerceAtLeast(0)
        codeScroll.animateScrollTo((target * lineHeightPx).toInt())
    }
    LaunchedEffect(typing) {
        if (typing.isEmpty()) return@LaunchedEffect
        delay(220)
        sounds.play(Sfx.FILL)
        if (reduced) typeProgress.snapTo(1f)
        else { typeProgress.snapTo(0f); typeProgress.animateTo(1f, tween(450 + 120 * typing.size, easing = LinearEasing)) }
        flash.snapTo(1f)
        flash.animateTo(0f, tween(if (reduced) 1 else 900))
    }

    AppTopBar(
        "${sub.code} ${sub.title}", "Stage ${stageIndex + 1} of ${stages.size}", onBack,
        actions = { TextButton(onClick = onShowBriefing) { Text("Briefing", color = c.brand) } },
    )
    Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        ProgressBar(doneCount.toFloat() / stages.size, Modifier.weight(1f), height = 6.dp)
        Spacer(Modifier.width(12.dp))
        Text("${stageIndex + 1} of ${stages.size}", style = MaterialTheme.typography.bodySmall, color = c.textMuted)
    }
    Column(
        Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Eyebrow("Stage ${stageIndex + 1} · ${stage.title}")
        Text(stage.instruction, style = MaterialTheme.typography.bodyLarge, color = c.text)
        ProjectCode(
            sub.finalCode, sub.briefing?.fileName ?: "main.py", revealed, current = stage.revealsLines.toSet(), typing = typing,
            typeProgress = typeProgress.value, flash = flash.value,
            modifier = Modifier.height(260.dp), scroll = codeScroll,
        )
        Text(stage.question.prompt, style = MaterialTheme.typography.titleMedium, color = c.text)
        stage.question.code?.let { CodeBlock(it) }
        stage.question.options.forEachIndexed { i, text ->
            val state = when {
                solved && i == stage.question.correctIndex -> OptionState.CORRECT
                i in tried -> OptionState.WRONG
                solved -> OptionState.DIMMED
                selected == i -> OptionState.SELECTED
                else -> OptionState.IDLE
            }
            McqOption(('A' + i).toString(), text, state, onClick = { selected = i; lastWrong = null }, mono = stage.optionsAreCode)
        }
        AnimatedVisibility(solved || lastWrong != null, enter = fadeIn(tween(250)) + expandVertically(tween(250))) {
            val ok = solved
            val msg = when {
                ok -> stage.optionFeedback.getOrNull(stage.question.correctIndex) ?: stage.question.explanation
                else -> stage.optionFeedback.getOrNull(lastWrong ?: -1) ?: "That's not it. Read the instruction again and try another option."
            }
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if (ok) c.successSoft else c.errorSoft).padding(16.dp),
            ) {
                Text(if (ok) "Correct!" else "Not quite, try again", style = MaterialTheme.typography.titleMedium, color = if (ok) c.success else c.error)
                Spacer(Modifier.height(6.dp))
                Text(msg, style = MaterialTheme.typography.bodyMedium, color = c.text.copy(alpha = 0.9f))
                if (ok && stage.question.explanation != msg) {
                    Spacer(Modifier.height(6.dp))
                    Text(stage.question.explanation, style = MaterialTheme.typography.bodySmall, color = c.textMuted)
                }
            }
        }
    }
    Box(Modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
        if (!solved) {
            AppButton(
                "Check", {
                    val sel = selected ?: return@AppButton
                    if (sel == stage.question.correctIndex) {
                        solved = true
                        sounds.play(Sfx.CORRECT)
                        onStageCorrect(stage.id)
                        typing = stage.revealsLines.toSet()
                    } else {
                        tried = tried + sel
                        lastWrong = sel
                        selected = null
                        sounds.play(Sfx.WRONG)
                        haptic()
                        onMiss()
                    }
                },
                Modifier.fillMaxWidth(), enabled = selected != null,
            )
        } else {
            val last = stageIndex == stages.lastIndex
            AppButton(
                if (last) "See the finished program" else "Next stage",
                {
                    typing = emptySet()
                    val next = stages.indexOfFirst { it.id !in pp.completedStages && it.id != stage.id }
                    if (next < 0) onAllDone() else stageIndex = next
                },
                Modifier.fillMaxWidth(), kind = ButtonKind.Success, trailing = Icons.AutoMirrored.Filled.ArrowForward,
            )
        }
    }
}

/** The program so far: revealed lines are highlighted Python, the rest are grey "# ??? (line N)" placeholders. */
@Composable
private fun ProjectCode(
    lines: List<String>,
    fileName: String,
    revealed: Set<Int>,
    current: Set<Int>,
    typing: Set<Int>,
    typeProgress: Float,
    flash: Float,
    modifier: Modifier,
    scroll: androidx.compose.foundation.ScrollState,
) {
    val cc = AppTheme.colors.code
    val brand = AppTheme.colors.brand
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(cc.background)) {
        Row(Modifier.fillMaxWidth().background(cc.header).padding(horizontal = 14.dp, vertical = 8.dp)) {
            Text(fileName, style = MaterialTheme.typography.bodySmall, color = cc.comment)
            Spacer(Modifier.weight(1f))
            Text("${revealed.size}/${lines.size} lines", style = MaterialTheme.typography.bodySmall, color = cc.comment)
        }
        Box(Modifier.weight(1f).verticalScroll(scroll)) {
            Column(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 10.dp)) {
                lines.forEachIndexed { i, raw ->
                    val n = i + 1
                    val shown = n in revealed
                    val text: AnnotatedString = if (shown) {
                        val full = PythonHighlighter.highlight(raw, cc)
                        if (n in typing && typeProgress < 1f) {
                            // Type the stage's lines one after another.
                            val order = typing.sorted()
                            val total = order.sumOf { lines[it - 1].length.coerceAtLeast(1) }
                            val before = order.takeWhile { it < n }.sumOf { lines[it - 1].length.coerceAtLeast(1) }
                            val chars = ((typeProgress * total) - before).toInt().coerceIn(0, raw.length)
                            full.subSequence(0, chars)
                        } else full
                    } else {
                        val indent = raw.takeWhile { it == ' ' }
                        buildAnnotatedString {
                            append(indent)
                            pushStyle(SpanStyle(color = cc.comment, fontStyle = FontStyle.Italic)) // Batch 3 Part 3: full colour, 5.10:1
                            append("# ??? (line $n)")
                            pop()
                        }
                    }
                    val bg = when {
                        n in typing && flash > 0f -> brand.copy(alpha = 0.35f * flash)
                        !shown && n in current -> brand.copy(alpha = 0.12f)
                        else -> androidx.compose.ui.graphics.Color.Transparent
                    }
                    Row(Modifier.background(bg).padding(horizontal = 12.dp)) {
                        Text("$n", style = CodeTextStyle, color = cc.comment, textAlign = TextAlign.End, modifier = Modifier.width(22.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(text, style = CodeTextStyle, color = cc.text, softWrap = false)
                        Spacer(Modifier.width(12.dp))
                    }
                }
            }
        }
    }
}

// ───────────── Step C: done ─────────────

@Composable
private fun ColumnScope.Done(
    sub: SubLevel, pp: ProjectProgress, kind: String, isBonus: Boolean,
    levelBadgeEarned: Boolean, onBack: () -> Unit, onFinish: () -> Unit,
) {
    val c = AppTheme.colors
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    val code = sub.finalCode.joinToString("\n")
    AppTopBar("${sub.code} ${sub.title}", "$kind · Complete", onBack, backIcon = Icons.Filled.Close)
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        Eyebrow("Project complete", color = c.success)
        Spacer(Modifier.height(6.dp))
        Text("You built it!", style = MaterialTheme.typography.headlineMedium, color = c.text, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(8.dp))
        Text(
            "All ${sub.stages.size} stages done. Here is your complete program and what it prints.",
            style = MaterialTheme.typography.bodyLarge, color = c.textMuted,
        )
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("${sub.stages.size} stages", c.success, c.successSoft, icon = Icons.Filled.Check)
            Pill("Total attempts: ${sub.stages.size + pp.wrongAttempts}", c.brand, c.brandSoft)
            Pill("Wrong: ${pp.wrongAttempts}", c.textMuted, c.surfaceAlt)
        }
        Spacer(Modifier.height(16.dp))
        CodeBlock(code, output = sub.briefing?.sampleOutput, title = sub.briefing?.fileName ?: "main.py")
        Spacer(Modifier.height(10.dp))
        AppButton(
            if (copied) "Copied" else "Copy code",
            { clipboard.setText(AnnotatedString(code)); copied = true },
            Modifier.fillMaxWidth(), kind = ButtonKind.Secondary,
        )
        if (sub.walkthrough.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            Text("How it works", style = MaterialTheme.typography.titleLarge, color = c.text, modifier = Modifier.semantics { heading() })
            sub.walkthrough.forEachIndexed { i, step ->
                Row(Modifier.padding(top = 10.dp)) {
                    Text("${i + 1}.", style = MaterialTheme.typography.titleSmall, color = c.brand, modifier = Modifier.width(26.dp))
                    Text(step, style = MaterialTheme.typography.bodyMedium, color = c.text)
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
    Box(Modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
        AppButton(
            when {
                isBonus -> "Back to the level"
                levelBadgeEarned -> "Back to dashboard"
                else -> "Claim your badge"
            },
            onFinish, Modifier.fillMaxWidth(), kind = ButtonKind.Success, trailing = Icons.AutoMirrored.Filled.ArrowForward,
        )
    }
}
