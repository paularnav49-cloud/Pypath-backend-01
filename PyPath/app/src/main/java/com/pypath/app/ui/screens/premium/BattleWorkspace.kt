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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.pypath.app.practical.PracticalTask
import com.pypath.app.ui.components.AppButton
import com.pypath.app.ui.components.ButtonKind
import com.pypath.app.ui.components.CodeBlock
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
 * Code Battle editor: the same editor, key bar, Run/Stop and console as the Premium workspace,
 * using the shared [PracticalViewModel] (Chaquopy runner, input, 10 s limit). There is no Check
 * button: the battle's Submit runs the tests. After submitting, the code is shown read-only.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun BattleWorkspace(
    vm: PracticalViewModel,
    battle: CodeBattleViewModel,
    task: PracticalTask,
    attemptStartedAt: Long,
    savedCode: String?,
    submittedCode: String?,
    editable: Boolean,
) {
    val c = AppTheme.colors
    val editing by rememberUpdatedState(editable)
    DisposableEffect(task.id) {
        vm.warmUp()
        vm.switchToExternalWorkspace()
        // A new battle starts from its saved code (or the starter code), never from an older battle's draft.
        if (battle.draftAttempt != attemptStartedAt) {
            val start = savedCode ?: task.starterCode
            vm.updateCode(task, TextFieldValue(start, TextRange(start.length)))
            battle.draftAttempt = attemptStartedAt
        }
        battle.liveCode = { vm.code(task).text }
        onDispose {
            if (editing) battle.saveCode(vm.code(task).text)
            battle.liveCode = null
            vm.switchToExternalWorkspace()
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { if (editing) battle.saveCode(vm.code(task).text) }

    if (!editable) {
        CodeBlock(submittedCode ?: vm.code(task).text, Modifier.fillMaxWidth(), title = "Submitted code")
        return
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
    val goToLine: (Int) -> Unit = { line ->
        vm.updateCode(task, code.copy(selection = TextRange(lineStartOffset(code.text, line))))
        editorFocus.requestFocus()
    }

    Column(Modifier.fillMaxWidth()) {
        CodeEditor(
            value = code,
            onValueChange = { vm.updateCode(task, it) },
            onReset = { confirmReset = true },
            focusRequester = editorFocus,
            errorLine = result?.errorLine,
            fileName = "battle.py",
        )
        Spacer(Modifier.height(8.dp))
        EditorKeyBar(code, { vm.updateCode(task, it) })
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AppButton(
                text = if (vm.hasRunOnce) "Run again" else "Run",
                onClick = {
                    focus.clearFocus(); keyboard?.hide()
                    battle.saveCode(code.text)
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
        Spacer(Modifier.height(16.dp))
        if (result != null && result.kind in IssueKinds) IssueCard(result, onGoToLine = goToLine)
        Console(vm, Modifier.bringIntoViewRequester(consoleBring))
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset code?") },
            text = { Text("Your code will be replaced with the starting code. The timer keeps running.") },
            confirmButton = { TextButton(onClick = { confirmReset = false; vm.resetCode(task) }) { Text("Reset", color = c.error) } },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
            containerColor = c.surface,
        )
    }
}
