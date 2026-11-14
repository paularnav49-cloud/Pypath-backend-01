package com.pypath.app.ui.screens.practical

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.pypath.app.premium.Entitlements
import com.pypath.app.premium.PremiumFeature

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pypath.app.audio.LocalSounds
import com.pypath.app.audio.Sfx
import com.pypath.app.domain.CourseSnapshot
import com.pypath.app.practical.CheckMode
import com.pypath.app.practical.EngineState
import com.pypath.app.practical.OutputCheck
import com.pypath.app.practical.PracticalTask
import com.pypath.app.practical.TaskAvailability
import com.pypath.app.practical.TestResult
import com.pypath.app.practical.availability
import com.pypath.app.practical.CompletionStatus
import com.pypath.app.practical.failureReason
import com.pypath.app.ui.components.AppButton
import com.pypath.app.ui.components.AppCard
import com.pypath.app.ui.components.AppTopBar
import com.pypath.app.ui.components.ButtonKind
import com.pypath.app.ui.components.Eyebrow
import com.pypath.app.ui.components.Pill
import com.pypath.app.ui.theme.AppTheme
import com.pypath.app.ui.theme.CodeTextStyle
import com.pypath.app.ui.theme.Mono
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Picks the most advanced unlocked task, falling back to the playground. */
fun defaultTaskId(list: List<TaskAvailability>): String? =
    list.lastOrNull { it.unlocked && it.task.afterSubLevel != null }?.task?.id ?: list.firstOrNull { it.unlocked }?.task?.id

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PracticalScreen(
    snapshot: CourseSnapshot,
    vm: PracticalViewModel,
    /** Premium Phase 1: Free (default) shows exactly the original Practical tab. */
    premium: Entitlements = Entitlements.FREE,
    /**
     * Final frontend polish: when set, Free users also see the "My programs" switch with a lock; tapping
     * it calls this (the app opens the Premium offer). Null keeps the original Free layout.
     */
    onPremiumLocked: (() -> Unit)? = null,
) {
    val c = AppTheme.colors
    LaunchedEffect(Unit) { vm.warmUp() }
    // Premium Phase 1: Advanced IDE gate. If Premium is turned off, fall back to the task view.
    val advancedIde = premium.has(PremiumFeature.ADVANCED_IDE)
    LaunchedEffect(advancedIde) { if (!advancedIde) vm.setMode(IdeMode.TASKS) }
    // Saved programs are written right away when the app goes to the background (no-op for Free).
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.flushPrograms() }

    val list = remember(vm.tasks, snapshot) { availability(vm.tasks, snapshot) }
    // Stored completions (from DataStore via the snapshot) plus any made a moment ago.
    val completed = snapshot.progress.completedTasks + vm.completedThisSession
    LaunchedEffect(list) {
        val sel = list.firstOrNull { it.task.id == vm.selectedTaskId }
        // Never switch task under a running Check/Run (that would cancel it and clear its results),
        // and keep a task that was just completed even if the course snapshot briefly lags behind.
        val keep = sel != null && (sel.unlocked || sel.task.id in completed || vm.phase != RunPhase.IDLE)
        if (!keep) defaultTaskId(list)?.let { vm.select(it) }
    }
    val task = vm.selectedTask
    val selected = list.firstOrNull { it.task.id == task?.id }
    val sounds = LocalSounds.current
    LaunchedEffect(vm.successSoundPending) {
        if (vm.successSoundPending) { sounds.play(Sfx.CORRECT); vm.consumeSuccessSound() }
    }

    var showPicker by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val editorFocus = remember { FocusRequester() }
    val consoleBring = remember { BringIntoViewRequester() }

    Column(Modifier.fillMaxSize().background(c.background)) {
        AppTopBar(
            title = "Practical",
            subtitle = "Write and run real Python",
            actions = {
                val (label, fg, bg) = when (val e = vm.engine) {
                    is EngineState.Ready -> Triple("Python ${e.pythonVersion.substringBefore(' ')}", c.success, c.successSoft)
                    is EngineState.Failed -> Triple("Python unavailable", c.error, c.errorSoft)
                    EngineState.Starting -> Triple("Starting Python…", c.textMuted, c.surfaceAlt)
                }
                Pill(label, fg, bg, Modifier.padding(end = 12.dp))
            },
        )

        if (!advancedIde && onPremiumLocked != null) {
            IdeModeSwitch(
                IdeMode.TASKS, { m -> if (m == IdeMode.PROGRAMS) onPremiumLocked() },
                Modifier.padding(horizontal = 20.dp, vertical = 4.dp), locked = true,
            )
        }
        if (advancedIde) {
            IdeModeSwitch(vm.ideMode, vm::setMode, Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
            if (vm.ideMode == IdeMode.PROGRAMS) {
                ProgramsPane(vm, Modifier.weight(1f))
                return@Column
            }
        }

        if (task == null) {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(vm.loadError ?: "Loading tasks…", color = if (vm.loadError != null) c.error else c.textMuted)
            }
            return@Column
        }

        val code = vm.code(task)
        val running = vm.phase != RunPhase.IDLE
        val result = vm.lastResult
        val checkState = vm.checkState?.takeIf { it.taskId == task.id }
        val goToLine: (Int) -> Unit = { line ->
            val start = lineStartOffset(code.text, line)
            vm.updateCode(task, code.copy(selection = TextRange(start)))
            editorFocus.requestFocus()
        }
        // Error line of the first visible failing test, so "Go to line" works after Check too.
        val checkErrorLine = checkState?.report?.results?.firstOrNull { !it.passed && !it.test.hidden && it.run.errorLine != null }?.run?.errorLine

        Column(
            Modifier.weight(1f).imePadding().verticalScroll(scroll).padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(4.dp))
            TaskPickerCard(selected, list, completed = task.id in completed, onClick = { showPicker = true })
            Spacer(Modifier.height(12.dp))
            InstructionsCard(task)
            Spacer(Modifier.height(16.dp))

            CodeEditor(
                value = code,
                onValueChange = { vm.updateCode(task, it) },
                onReset = { confirmReset = true },
                focusRequester = editorFocus,
                errorLine = result?.errorLine ?: checkErrorLine,
            )
            Spacer(Modifier.height(8.dp))
            EditorKeyBar(code, { vm.updateCode(task, it) })
            Spacer(Modifier.height(14.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AppButton(
                    text = if (vm.hasRunOnce) "Run again" else "Run",
                    onClick = {
                        focus.clearFocus(); keyboard?.hide()
                        vm.run(task)
                        scope.launch { consoleBring.bringIntoView() }
                    },
                    modifier = Modifier.weight(1f).semantics { contentDescription = "Run code" },
                    enabled = !running,
                    leading = Icons.Filled.PlayArrow,
                )
                AppButton(
                    text = "Stop",
                    onClick = vm::stop,
                    modifier = Modifier.weight(1f).semantics { contentDescription = "Stop execution" },
                    kind = ButtonKind.Secondary,
                    enabled = running,
                    leading = StopIcon,
                )
            }
            if (task.checkable) {
                Spacer(Modifier.height(10.dp))
                AppButton(
                    text = "Check (${testsLabel(task.tests.size)})",
                    onClick = {
                        focus.clearFocus(); keyboard?.hide()
                        vm.check(task)
                    },
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Check code" },
                    kind = ButtonKind.Success,
                    enabled = !running,
                    leading = Icons.Filled.Check,
                )
                vm.checkNotice?.let { notice ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        notice, style = MaterialTheme.typography.bodyMedium, color = c.error,
                        modifier = Modifier.semantics { contentDescription = notice },
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            if (checkState != null) CheckResultsCard(
                task, checkState, onGoToLine = goToLine,
                saveStatus = vm.completionSaves[task.id], onRetrySave = { vm.retrySaveCompletion(task.id) },
            )

            AnimatedVisibility(result != null && result.kind in IssueKinds) {
                if (result != null) IssueCard(result, onGoToLine = goToLine)
            }

            Console(vm, Modifier.bringIntoViewRequester(consoleBring))
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showPicker && vm.ideMode == IdeMode.TASKS) {
        val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { showPicker = false }, sheetState = sheet, containerColor = c.surface) {
            TaskList(list, vm.selectedTaskId, completed) { id ->
                vm.select(id)
                scope.launch { sheet.hide() }.invokeOnCompletion { showPicker = false }
            }
        }
    }

    if (confirmReset && task != null) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset code?") },
            text = { Text("Your code for \"${task.title}\" will be replaced with the starting code.") },
            confirmButton = { TextButton(onClick = { confirmReset = false; vm.resetCode(task) }) { Text("Reset", color = c.error) } },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
            containerColor = c.surface,
        )
    }
}

internal val IssueKinds = setOf(RunResult.Kind.ERROR, RunResult.Kind.TIMEOUT, RunResult.Kind.CRASHED, RunResult.Kind.INTERNAL)

/** Filled square "stop" glyph (not part of material-icons-core). */
internal val StopIcon: ImageVector = ImageVector.Builder("Stop", 24.dp, 24.dp, 24f, 24f).apply {
    path(fill = SolidColor(Color.Black)) {
        moveTo(7f, 7f); lineTo(17f, 7f); lineTo(17f, 17f); lineTo(7f, 17f); close()
    }
}.build()

// ───────────── Task picker ─────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TaskPickerCard(selected: TaskAvailability?, list: List<TaskAvailability>, completed: Boolean, onClick: () -> Unit) {
    val c = AppTheme.colors
    val t = selected?.task ?: return
    val number = list.filter { it.task.afterSubLevel != null }.indexOfFirst { it.task.id == t.id }
    AppCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Eyebrow(
                    if (t.afterSubLevel == null) "Playground"
                    else "Task ${number + 1} of ${list.count { it.task.afterSubLevel != null }} · after ${selected.requirementLabel?.substringBefore(' ') ?: ""}"
                )
                Spacer(Modifier.height(4.dp))
                Text(t.title, style = MaterialTheme.typography.titleLarge, color = c.text)
            }
            Row(
                Modifier.clip(CircleShape).background(c.brandSoft).padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Change", style = MaterialTheme.typography.labelMedium, color = c.brand)
                Icon(Icons.Filled.KeyboardArrowDown, null, tint = c.brand, modifier = Modifier.size(18.dp))
            }
        }
        if (t.concepts.isNotEmpty() || completed || t.difficulty != null) {
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (completed) Pill("Completed", c.success, c.successSoft, icon = Icons.Filled.CheckCircle)
                difficultyLabel(t.difficulty)?.let { Pill(it, c.accent, c.accentSoft) }
                t.concepts.forEach { Pill(it, c.textMuted, c.surfaceAlt) }
            }
        }
    }
}

@Composable
private fun TaskList(list: List<TaskAvailability>, selectedId: String?, completed: Set<String>, onSelect: (String) -> Unit) {
    val c = AppTheme.colors
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Practical tasks", style = MaterialTheme.typography.titleLarge, color = c.text)
        Text(
            "Tasks unlock as you finish sub-levels, so they only use what you've learned.",
            style = MaterialTheme.typography.bodySmall, color = c.textMuted,
        )
        Spacer(Modifier.height(4.dp))
        var n = 0
        list.forEach { a ->
            val numbered = a.task.afterSubLevel != null
            if (numbered) n++
            val isSel = a.task.id == selectedId
            AppCard(
                Modifier.fillMaxWidth(),
                onClick = if (a.unlocked) ({ onSelect(a.task.id) }) else null,
                background = if (a.unlocked) c.surface else c.surface.copy(alpha = 0.6f),
                borderColor = if (isSel) c.brand.copy(alpha = 0.6f) else c.border,
                padding = PaddingValues(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val (bg, fg) = when {
                        !a.unlocked -> c.surfaceAlt to c.locked
                        isSel -> c.brand to Color.White
                        else -> c.brandSoft to c.brand
                    }
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(bg), contentAlignment = Alignment.Center) {
                        when {
                            !a.unlocked -> Icon(Icons.Filled.Lock, "Locked", tint = fg, modifier = Modifier.size(18.dp))
                            !numbered -> Text("{ }", style = MaterialTheme.typography.titleSmall.copy(fontFamily = Mono), color = fg)
                            else -> Text("$n", style = MaterialTheme.typography.titleSmall, color = fg, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(a.task.title, style = MaterialTheme.typography.titleMedium, color = if (a.unlocked) c.text else c.textMuted)
                        Text(
                            when {
                                !numbered -> "Always open · write any code"
                                a.unlocked -> "Uses ${a.task.concepts.joinToString(", ")}"
                                a.comingSoon -> "Coming soon with ${a.requirementLabel ?: "a future level"}"
                                else -> "Finish ${a.requirementLabel ?: "earlier lessons"} to unlock"
                            },
                            style = MaterialTheme.typography.bodySmall, color = c.textMuted, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (a.task.id in completed) {
                        Icon(Icons.Filled.CheckCircle, "Completed", tint = c.success, modifier = Modifier.padding(start = 8.dp))
                    } else if (isSel) {
                        Icon(Icons.Filled.Check, "Selected", tint = c.brand)
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

// ───────────── Instructions ─────────────

@Composable
internal fun InstructionsCard(task: PracticalTask) {
    val c = AppTheme.colors
    var showHint by remember(task.id) { mutableStateOf(false) }
    AppCard(Modifier.fillMaxWidth()) {
        Text("Your task", style = MaterialTheme.typography.titleSmall, color = c.text)
        Spacer(Modifier.height(4.dp))
        Text(task.instructions, style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
        if (task.requirements.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            task.requirements.forEach { r ->
                Row(Modifier.padding(vertical = 2.dp)) {
                    Box(Modifier.padding(top = 8.dp).size(6.dp).clip(CircleShape).background(c.brand))
                    Spacer(Modifier.width(10.dp))
                    Text(r, style = MaterialTheme.typography.bodyMedium, color = c.text)
                }
            }
        }
        task.example?.let { ex ->
            Spacer(Modifier.height(12.dp))
            Text("EXAMPLE RUN", style = MaterialTheme.typography.labelSmall, color = c.textFaint)
            Spacer(Modifier.height(6.dp))
            Text(
                ex, style = CodeTextStyle, color = c.text,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.surfaceAlt).padding(horizontal = 12.dp, vertical = 10.dp),
            )
        }
        if (task.checkable) {
            val hidden = task.tests.count { it.hidden }
            Spacer(Modifier.height(10.dp))
            Text(
                "Press Check to test your code automatically with ${testsLabel(task.tests.size)}" +
                    (if (hidden > 0) " ($hidden hidden)" else "") + ". Run is still there for trying things out.",
                style = MaterialTheme.typography.bodySmall, color = c.textMuted,
            )
        }
        task.hint?.let { hint ->
            Spacer(Modifier.height(10.dp))
            Text(
                if (showHint) "Hide hint" else "Show hint",
                style = MaterialTheme.typography.labelLarge, color = c.brand,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { showHint = !showHint }.padding(vertical = 4.dp),
            )
            AnimatedVisibility(showHint) {
                Text(
                    hint, style = MaterialTheme.typography.bodyMedium, color = c.text,
                    modifier = Modifier.padding(top = 6.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.accentSoft).padding(12.dp),
                )
            }
        }
    }
}

// ───────────── Check results ─────────────

private fun testsLabel(n: Int): String = if (n == 1) "1 test" else "$n tests"

fun difficultyLabel(difficulty: String?): String? = when (difficulty?.lowercase()) {
    "easy" -> "Easy"
    "medium" -> "Medium"
    "hard" -> "Hard"
    else -> null
}

/** Longest output shown in a results box; the full text is rarely useful on a phone. */
private const val MAX_SHOWN_CHARS = 4_000

private fun shown(text: String): String =
    if (text.length <= MAX_SHOWN_CHARS) text else text.take(MAX_SHOWN_CHARS) + "\n… (output shortened)"

@Composable
internal fun CheckResultsCard(
    task: PracticalTask,
    state: CheckState,
    onGoToLine: (Int) -> Unit,
    saveStatus: CompletionStatus? = null,
    onRetrySave: () -> Unit = {},
) {
    val c = AppTheme.colors
    val report = state.report
    val (title, color, soft) = when {
        state.running -> Triple("Checking… test ${state.currentTest} of ${report.total}", c.brand, c.brandSoft)
        state.problem != null -> Triple("Python couldn't run the tests", c.error, c.errorSoft)
        state.stopped -> Triple("Check stopped", c.textMuted, c.surfaceAlt)
        state.allPassed -> Triple(if (report.total == 1) "The test passed" else "All ${report.total} tests passed", c.success, c.successSoft)
        else -> Triple("${report.passedCount} of ${report.total} tests passed", c.accent, c.accentSoft)
    }
    Column(
        Modifier.fillMaxWidth().padding(bottom = 12.dp).clip(RoundedCornerShape(20.dp)).background(soft)
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(20.dp)).padding(16.dp)
            .semantics { contentDescription = "Check results" },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(if (state.allPassed) Icons.Filled.CheckCircle else Icons.Filled.Check, null, tint = color, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(title, style = MaterialTheme.typography.titleSmall, color = color, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(6.dp))
        when {
            state.running -> Text("Each test runs your code with its own input. Press Stop to cancel.", style = MaterialTheme.typography.bodyMedium, color = c.text)
            state.problem != null -> Text(state.problem, style = MaterialTheme.typography.bodyMedium, color = c.text)
            state.stopped -> Text("Press Check to run the tests again.", style = MaterialTheme.typography.bodyMedium, color = c.text)
            state.allPassed && saveStatus == CompletionStatus.SAVING -> Text("All tests passed. Saving your progress…", style = MaterialTheme.typography.bodyMedium, color = c.text)
            state.allPassed && saveStatus == CompletionStatus.FAILED -> Column {
                Text("Your solution is correct, but it could not be saved on this phone yet. Your code is kept - try saving again.", style = MaterialTheme.typography.bodyMedium, color = c.error)
                Spacer(Modifier.height(8.dp))
                AppButton(
                    text = "Try saving again", onClick = onRetrySave, kind = ButtonKind.Secondary,
                    modifier = Modifier.semantics { contentDescription = "Try saving again" },
                )
            }
            state.allPassed -> Text("Task complete! Your program gives the right output for every test. It is marked with a green tick in the task list.", style = MaterialTheme.typography.bodyMedium, color = c.text)
            else -> Text("Not quite yet - that's normal. Compare your output with the expected output below, fix your code and press Check again. There's no limit on tries.", style = MaterialTheme.typography.bodyMedium, color = c.text)
        }
        report.results.forEach { r ->
            Spacer(Modifier.height(10.dp))
            TestResultRow(task, r, onGoToLine)
        }
        val failed = !state.running && !state.stopped && state.problem == null && !state.allPassed
        if (failed) task.hint?.let { hint ->
            Spacer(Modifier.height(12.dp))
            Text("HINT", style = MaterialTheme.typography.labelSmall, color = c.textFaint)
            Spacer(Modifier.height(4.dp))
            Text(
                hint, style = MaterialTheme.typography.bodyMedium, color = c.text,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.surface).padding(12.dp),
            )
        }
    }
}

@Composable
private fun TestResultRow(task: PracticalTask, r: TestResult, onGoToLine: (Int) -> Unit) {
    val c = AppTheme.colors
    val n = r.index + 1
    val label = when {
        r.test.hidden && r.passed -> "Hidden test passed"
        r.test.hidden -> "Hidden test failed"
        else -> "Test $n" + (r.test.description?.let { " · $it" } ?: "")
    }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.surface).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (r.passed) Icon(Icons.Filled.CheckCircle, "Passed", tint = c.success, modifier = Modifier.size(18.dp))
            else Icon(Icons.Filled.Clear, "Failed", tint = c.error, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = c.text, modifier = Modifier.weight(1f))
        }
        // Hidden tests never reveal their input, output or reason.
        if (r.passed || r.test.hidden) return@Column
        failureReason(task, r)?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium, color = c.text)
        }
        r.run.errorLine?.let { line ->
            Spacer(Modifier.height(4.dp))
            Text(
                "Go to line $line",
                style = MaterialTheme.typography.labelMedium, color = c.error,
                modifier = Modifier.clip(CircleShape).clickable { onGoToLine(line) }.padding(vertical = 4.dp),
            )
        }
        // Stacked, not side by side, so long lines stay readable on a phone.
        OutputBox("INPUT", if (r.test.stdin.isEmpty()) "(no input)" else r.test.stdin.joinToString("\n"))
        when (task.mode) {
            CheckMode.EXACT -> OutputBox("EXPECTED OUTPUT", OutputCheck.normalize(r.test.expectedOutput))
            CheckMode.CONTAINS -> OutputBox("MUST INCLUDE", OutputCheck.needles(task, r.test).joinToString("\n"))
            CheckMode.REGEX -> OutputBox("EXAMPLE OF A CORRECT OUTPUT", OutputCheck.normalize(r.test.expectedOutput))
        }
        OutputBox("YOUR OUTPUT", OutputCheck.normalize(r.run.output).ifEmpty { "(nothing printed)" })
    }
}

@Composable
private fun OutputBox(label: String, text: String) {
    val c = AppTheme.colors
    Spacer(Modifier.height(10.dp))
    Text(label, style = MaterialTheme.typography.labelSmall, color = c.textFaint)
    Spacer(Modifier.height(4.dp))
    SelectionContainer {
        Text(
            shown(text), style = CodeTextStyle, color = c.text,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(c.surfaceAlt).padding(horizontal = 10.dp, vertical = 8.dp),
        )
    }
}

// ───────────── Errors ─────────────

@Composable
internal fun IssueCard(result: RunResult, onGoToLine: (Int) -> Unit) {
    val c = AppTheme.colors
    val (title, message) = when (result.kind) {
        RunResult.Kind.ERROR -> {
            val s = result.errorSummary.orEmpty()
            val type = s.substringBefore(':').trim().ifEmpty { "Error" }
            type to s.substringAfter(':', "").trim()
        }
        RunResult.Kind.TIMEOUT -> "Execution timed out" to "Your program ran for more than ${PythonRunnerLimitSeconds}s and was stopped. Look for a loop that never ends."
        RunResult.Kind.CRASHED -> "Python stopped unexpectedly" to "The program may have used too much memory. Python has been restarted, so you can edit and run again."
        else -> "Python couldn't run" to result.errorSummary.orEmpty()
    }
    Column(
        Modifier.fillMaxWidth().padding(bottom = 12.dp).clip(RoundedCornerShape(20.dp)).background(c.errorSoft)
            .border(1.dp, c.error.copy(alpha = 0.35f), RoundedCornerShape(20.dp)).padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Warning, null, tint = c.error, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(title, style = MaterialTheme.typography.titleSmall, color = c.error, modifier = Modifier.weight(1f))
            result.errorLine?.let { line ->
                Text(
                    "Go to line $line",
                    style = MaterialTheme.typography.labelMedium, color = c.error,
                    modifier = Modifier.clip(CircleShape).clickable { onGoToLine(line) }.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
        if (message.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium, color = c.text)
        }
        if (result.kind == RunResult.Kind.ERROR) {
            Spacer(Modifier.height(4.dp))
            Text("Full traceback is in the console below.", style = MaterialTheme.typography.bodySmall, color = c.textMuted)
        }
    }
}

private val PythonRunnerLimitSeconds = com.pypath.app.practical.PythonRunner.DEFAULT_TIME_LIMIT_MS / 1000

// ───────────── Console ─────────────

@Composable
internal fun Console(vm: PracticalViewModel, modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    val code = c.code
    val shape = RoundedCornerShape(16.dp)
    val scroll = rememberScrollState()
    val total = vm.console.sumOf { it.text.length }
    LaunchedEffect(total, vm.phase) { scroll.animateScrollTo(scroll.maxValue) }

    Column(modifier.fillMaxWidth().clip(shape).background(code.outputBg)) {
        Row(
            Modifier.fillMaxWidth().background(code.header).padding(start = 16.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("CONSOLE", style = MaterialTheme.typography.labelSmall, color = code.comment)
            Spacer(Modifier.width(10.dp))
            StatusChip(vm)
            Spacer(Modifier.weight(1f))
            Row(
                Modifier.clip(RoundedCornerShape(10.dp)).clickable(enabled = vm.console.isNotEmpty(), onClick = vm::clearConsole)
                    .padding(horizontal = 8.dp, vertical = 6.dp)
                    .semantics { contentDescription = "Clear output" },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Clear, null, tint = code.text.copy(alpha = if (vm.console.isEmpty()) 0.35f else 0.8f), modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(4.dp))
                Text("Clear", style = MaterialTheme.typography.labelMedium, color = code.text.copy(alpha = if (vm.console.isEmpty()) 0.35f else 0.8f))
            }
        }
        Box(Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 340.dp).verticalScroll(scroll).padding(horizontal = 16.dp, vertical = 12.dp)) {
            if (vm.console.isEmpty()) {
                Text(
                    if (vm.phase == RunPhase.IDLE) "Output will appear here. Press Run to execute your code." else "",
                    style = CodeTextStyle, color = code.comment,
                )
            } else {
                val stderrColor = Color(0xFFFF8A80)
                val text = buildAnnotatedString {
                    vm.console.forEach { seg ->
                        val style = when (seg.kind) {
                            ConsoleKind.STDOUT -> SpanStyle(color = code.outputText)
                            ConsoleKind.STDERR -> SpanStyle(color = stderrColor)
                            ConsoleKind.INPUT -> SpanStyle(color = code.string, fontWeight = FontWeight.Medium)
                            ConsoleKind.SYSTEM -> SpanStyle(color = code.comment, fontStyle = FontStyle.Italic)
                        }
                        pushStyle(style); append(seg.text); pop()
                    }
                }
                SelectionContainer { Text(text, style = CodeTextStyle, modifier = Modifier.semantics { contentDescription = "Console output" }) }
            }
        }
        if (vm.phase == RunPhase.WAITING_INPUT) InputRow(vm)
    }
}

@Composable
private fun StatusChip(vm: PracticalViewModel) {
    val c = AppTheme.colors
    val r = vm.lastResult
    val secs = PracticalViewModel.formatSeconds(vm.elapsedMs)
    val (label, color) = when (vm.phase) {
        RunPhase.STARTING -> "Starting Python…" to c.code.comment
        RunPhase.RUNNING -> "Running · $secs" to c.code.builtin
        RunPhase.WAITING_INPUT -> "Waiting for input" to c.accent
        RunPhase.IDLE -> when (r?.kind) {
            null -> (if (vm.engine is EngineState.Ready) "Ready" else "Idle") to c.code.comment
            RunResult.Kind.SUCCESS -> "Done · $secs" to Color(0xFF34D399)
            RunResult.Kind.ERROR -> "Error" to Color(0xFFFF8A80)
            RunResult.Kind.EXITED -> "Exit code ${r.exitCode}" to c.accent
            RunResult.Kind.TIMEOUT -> "Timed out" to Color(0xFFFF8A80)
            RunResult.Kind.STOPPED -> "Stopped" to c.accent
            RunResult.Kind.CRASHED -> "Crashed" to Color(0xFFFF8A80)
            RunResult.Kind.INTERNAL -> "Failed" to Color(0xFFFF8A80)
        }
    }
    Row(
        Modifier.clip(CircleShape).background(color.copy(alpha = 0.14f)).padding(horizontal = 8.dp, vertical = 3.dp)
            .semantics { contentDescription = "Status: $label" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(5.dp))
        Text(label, style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp), color = color)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun InputRow(vm: PracticalViewModel) {
    val code = AppTheme.colors.code
    var value by remember { mutableStateOf(TextFieldValue("")) }
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val bring = remember { BringIntoViewRequester() }
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
    LaunchedEffect(Unit) { focus.requestFocus() }
    // Keep the prompt and field visible above the keyboard as it opens.
    LaunchedEffect(imeBottom) { delay(120); bring.bringIntoView() }
    val submit = {
        vm.submitInput(value.text)
        value = TextFieldValue("")
        focusManager.clearFocus()
    }
    Column(Modifier.fillMaxWidth().bringIntoViewRequester(bring).background(code.header).padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp)) {
        Text(
            if (vm.inputPrompt.isNotBlank()) "input(\"${vm.inputPrompt.trimEnd()}\")" else "input()",
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = Mono), color = code.comment,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("›", style = CodeTextStyle, color = code.string)
            Spacer(Modifier.width(8.dp))
            BasicTextField(
                value = value,
                onValueChange = { value = it.copy(text = it.text.replace("\n", "")) },
                singleLine = true,
                textStyle = CodeTextStyle.copy(color = code.string),
                cursorBrush = SolidColor(code.string),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Text, imeAction = ImeAction.Send,
                ),
                keyboardActions = KeyboardActions(onSend = { submit() }),
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(code.outputBg)
                    .padding(horizontal = 10.dp, vertical = 10.dp).focusRequester(focus)
                    .semantics { contentDescription = "Program input" },
            )
            IconButton(onClick = submit, modifier = Modifier.semantics { contentDescription = "Send input" }) {
                Icon(Icons.AutoMirrored.Filled.Send, null, tint = code.string)
            }
        }
    }
}
