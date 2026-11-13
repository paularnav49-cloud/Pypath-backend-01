package com.pypath.app.ui.screens.premium

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.pypath.app.audio.LocalSounds
import com.pypath.app.audio.Sfx
import com.pypath.app.practical.PracticalTask
import com.pypath.app.ui.components.AppButton
import com.pypath.app.ui.components.ButtonKind
import com.pypath.app.ui.screens.practical.CheckResultsCard
import com.pypath.app.ui.screens.practical.CodeEditor
import com.pypath.app.ui.screens.practical.Console
import com.pypath.app.ui.screens.practical.EditorKeyBar
import com.pypath.app.ui.screens.practical.IssueCard
import com.pypath.app.ui.screens.practical.IssueKinds
import com.pypath.app.ui.screens.practical.PracticalViewModel
import com.pypath.app.ui.screens.practical.RunPhase
import com.pypath.app.ui.screens.practical.StopIcon
import com.pypath.app.ui.screens.practical.lineStartOffset
import com.pypath.app.ui.theme.AppTheme
import kotlinx.coroutines.launch

/**
 * Premium Phase 2: editor + Run/Stop (+ Check for challenges) + console for a Premium project or
 * challenge. It reuses the Practical tab's [PracticalViewModel], i.e. the same Chaquopy runner,
 * input handling, time limits and test checker. Drafts are kept in the Premium study store.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PremiumWorkspace(
    vm: PracticalViewModel,
    task: PracticalTask,
    fileName: String,
    savedDraft: String?,
    onSaveDraft: (taskId: String, code: String, starter: String) -> Unit,
) {
    val c = AppTheme.colors
    val save by rememberUpdatedState(onSaveDraft)
    DisposableEffect(task.id) {
        vm.warmUp()
        vm.switchToExternalWorkspace()
        vm.restoreDraft(task, savedDraft)
        onDispose {
            save(task.id, vm.code(task).text, task.starterCode)
            vm.switchToExternalWorkspace()
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { save(task.id, vm.code(task).text, task.starterCode) }

    val sounds = LocalSounds.current
    LaunchedEffect(vm.successSoundPending) {
        if (vm.successSoundPending) { sounds.play(Sfx.CORRECT); vm.consumeSuccessSound() }
    }

    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val editorFocus = remember { FocusRequester() }
    val consoleBring = remember { BringIntoViewRequester() }
    var confirmReset by remember { mutableStateOf(false) }

    val code = vm.code(task)
    val running = vm.phase != RunPhase.IDLE
    val result = vm.lastResult
    val checkState = vm.checkState?.takeIf { it.taskId == task.id }
    val goToLine: (Int) -> Unit = { line ->
        vm.updateCode(task, code.copy(selection = TextRange(lineStartOffset(code.text, line))))
        editorFocus.requestFocus()
    }
    val checkErrorLine = checkState?.report?.results?.firstOrNull { !it.passed && !it.test.hidden && it.run.errorLine != null }?.run?.errorLine

    Column(Modifier.fillMaxWidth()) {
        CodeEditor(
            value = code,
            onValueChange = { vm.updateCode(task, it) },
            onReset = { confirmReset = true },
            focusRequester = editorFocus,
            errorLine = result?.errorLine ?: checkErrorLine,
            fileName = fileName,
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
                text = "Check (${if (task.tests.size == 1) "1 test" else "${task.tests.size} tests"})",
                onClick = { focus.clearFocus(); keyboard?.hide(); vm.check(task) },
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Check code" },
                kind = ButtonKind.Success,
                enabled = !running,
                leading = Icons.Filled.Check,
            )
        }
        Spacer(Modifier.height(16.dp))
        if (checkState != null) CheckResultsCard(task, checkState, onGoToLine = goToLine)
        if (result != null && result.kind in IssueKinds) IssueCard(result, onGoToLine = goToLine)
        Console(vm, Modifier.bringIntoViewRequester(consoleBring))
    }

    if (confirmReset) {
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
