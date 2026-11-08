package com.pypath.app.ui.screens.practical

import android.text.format.DateUtils
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pypath.app.data.programs.ProgramBook
import com.pypath.app.data.programs.ProgramLibrary
import com.pypath.app.data.programs.SavedProgram
import com.pypath.app.domain.PythonSnippet
import com.pypath.app.domain.PythonSnippets
import com.pypath.app.ui.components.AppButton
import com.pypath.app.ui.components.AppCard
import com.pypath.app.ui.components.ButtonKind
import com.pypath.app.ui.components.CodeBlock
import com.pypath.app.ui.components.Pill
import com.pypath.app.ui.theme.AppTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Premium Phase 1: the Advanced IDE ("My programs"). Reuses the existing CodeEditor, EditorKeyBar,
// runner (vm.run / vm.stop), IssueCard and Console; only the program management is new.

/** Segmented switch above the editor, shown to Premium users only. */
@Composable
internal fun IdeModeSwitch(mode: IdeMode, onChange: (IdeMode) -> Unit, modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    val shape = RoundedCornerShape(14.dp)
    Row(modifier.fillMaxWidth().clip(shape).background(c.surfaceAlt).padding(4.dp)) {
        listOf(IdeMode.TASKS to "Tasks", IdeMode.PROGRAMS to "My programs").forEach { (m, label) ->
            val on = m == mode
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(11.dp)).background(if (on) c.surface else Color.Transparent)
                    .clickable { onChange(m) }.padding(vertical = 9.dp)
                    .semantics { contentDescription = "$label${if (on) ", selected" else ""}" },
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(label, style = MaterialTheme.typography.labelLarge, color = if (on) c.text else c.textMuted)
                    if (m == IdeMode.PROGRAMS) {
                        Spacer(Modifier.width(6.dp))
                        Pill("PRO", c.onBrand, c.brand)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun ProgramsPane(vm: PracticalViewModel, modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    val lib = vm.library
    if (lib == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = c.brand) }
        return
    }
    val program = vm.currentProgram
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val editorFocus = remember { FocusRequester() }
    val consoleBring = remember { BringIntoViewRequester() }

    var showManager by rememberSaveable { mutableStateOf(false) }
    var showSnippets by rememberSaveable { mutableStateOf(false) }
    var newDialog by rememberSaveable { mutableStateOf(false) }
    var renameId by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteId by rememberSaveable { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(notice) { if (notice != null) { delay(2500); notice = null } }

    Column(modifier.imePadding().verticalScroll(scroll).padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(4.dp))
        if (program == null) {
            AppCard(Modifier.fillMaxWidth()) {
                Text("No programs yet", style = MaterialTheme.typography.titleMedium, color = c.text)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Create a program to write, run and keep your own Python code. Programs are saved on this phone.",
                    style = MaterialTheme.typography.bodyMedium, color = c.textMuted,
                )
                Spacer(Modifier.height(14.dp))
                AppButton("New program", { newDialog = true }, Modifier.fillMaxWidth(), leading = Icons.Filled.Add)
            }
        } else {
            ProgramHeader(
                program, vm.saveStatus, notice, programCount = lib.programs.size,
                onOpenManager = { showManager = true }, onNew = { newDialog = true }, onRename = { renameId = program.id },
            )
            Spacer(Modifier.height(12.dp))
            val task = vm.programTask(program)
            val code = vm.code(task)
            val running = vm.phase != RunPhase.IDLE
            val result = vm.lastResult
            val goToLine: (Int) -> Unit = { line ->
                vm.updateProgramCode(program, code.copy(selection = TextRange(lineStartOffset(code.text, line))))
                editorFocus.requestFocus()
            }
            CodeEditor(
                value = code,
                onValueChange = { vm.updateProgramCode(program, it) },
                onReset = { showSnippets = true },
                focusRequester = editorFocus,
                errorLine = result?.errorLine,
                fileName = programFileName(program.name),
                actionLabel = "Snippets",
                actionIcon = Icons.Filled.Build,
            )
            Spacer(Modifier.height(8.dp))
            EditorKeyBar(code, { vm.updateProgramCode(program, it) })
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
            Spacer(Modifier.height(16.dp))
            AnimatedVisibility(result != null && result.kind in IssueKinds) {
                if (result != null) IssueCard(result, onGoToLine = goToLine)
            }
            Console(vm, Modifier.bringIntoViewRequester(consoleBring))
        }
        Spacer(Modifier.height(24.dp))
    }

    if (showManager) {
        val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val close: () -> Unit = { scope.launch { sheet.hide() }.invokeOnCompletion { showManager = false } }
        ModalBottomSheet(onDismissRequest = { showManager = false }, sheetState = sheet, containerColor = c.surface) {
            ProgramManager(
                lib, vm.currentProgramId,
                onOpen = { vm.openProgram(it); close() },
                onNew = { newDialog = true },
                onRename = { renameId = it },
                onDelete = { deleteId = it },
            )
        }
    }

    if (showSnippets && program != null) {
        val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { showSnippets = false }, sheetState = sheet, containerColor = c.surface) {
            SnippetLibrary { s ->
                vm.insertSnippetIntoProgram(program, s.code)
                notice = "Inserted \u201c${s.title}\u201d"
                scope.launch { sheet.hide() }.invokeOnCompletion { showSnippets = false }
            }
        }
    }

    if (newDialog) {
        ProgramDialog(
            title = "New program", confirm = "Create",
            initialName = ProgramBook.uniqueName(lib, "Untitled"), initialFolder = program?.folder.orEmpty(),
            folders = lib.folders,
            onConfirm = { name, folder -> newDialog = false; showManager = false; vm.createProgram(name, folder); notice = "Program created" },
            onDismiss = { newDialog = false },
        )
    }

    renameId?.let { id ->
        val p = lib.find(id)
        if (p == null) renameId = null
        else ProgramDialog(
            title = "Rename program", confirm = "Save",
            initialName = p.name, initialFolder = p.folder, folders = lib.folders,
            onConfirm = { name, folder -> renameId = null; vm.renameProgram(id, name, folder); notice = "Saved" },
            onDismiss = { renameId = null },
        )
    }

    deleteId?.let { id ->
        val p = lib.find(id)
        if (p == null) deleteId = null
        else AlertDialog(
            onDismissRequest = { deleteId = null },
            title = { Text("Delete program?") },
            text = { Text("\u201c${p.name}\u201d will be deleted from this phone. This can't be undone.") },
            confirmButton = { TextButton(onClick = { deleteId = null; vm.deleteProgram(id); notice = "Program deleted" }) { Text("Delete", color = c.error) } },
            dismissButton = { TextButton(onClick = { deleteId = null }) { Text("Cancel") } },
            containerColor = c.surface,
        )
    }
}

/** "My first program" → "my_first_program.py" (display only; tracebacks still say main.py). */
internal fun programFileName(name: String): String {
    val base = name.trim().lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_').take(28)
    return (base.ifEmpty { "program" }) + ".py"
}

@Composable
private fun ProgramHeader(
    p: SavedProgram, status: SaveStatus, notice: String?, programCount: Int,
    onOpenManager: () -> Unit, onNew: () -> Unit, onRename: () -> Unit,
) {
    val c = AppTheme.colors
    AppCard(Modifier.fillMaxWidth(), padding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 6.dp, top = 12.dp, bottom = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(p.name, style = MaterialTheme.typography.titleMedium, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() })
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (p.folder.isNotBlank()) {
                        Pill(p.folder, c.brand, c.brandSoft)
                        Spacer(Modifier.width(8.dp))
                    }
                    val (label, color) = when {
                        notice != null -> notice to c.success
                        status == SaveStatus.FAILED -> "Couldn't save. Check free space" to c.error
                        status == SaveStatus.PENDING -> "Saving\u2026" to c.textMuted
                        else -> "Saved on this phone" to c.textMuted
                    }
                    if (status == SaveStatus.SAVED && notice == null) {
                        Icon(Icons.Filled.Check, null, tint = c.success, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                    } else if (status == SaveStatus.FAILED && notice == null) {
                        Icon(Icons.Filled.Warning, null, tint = c.error, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(label, style = MaterialTheme.typography.bodySmall, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            IconButton(onClick = onRename) { Icon(Icons.Filled.Edit, "Rename program", tint = c.textMuted) }
            IconButton(onClick = onNew) { Icon(Icons.Filled.Add, "New program", tint = c.textMuted) }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.padding(end = 10.dp)) {
            AppButton(
                "All programs ($programCount)", onOpenManager, Modifier.fillMaxWidth(),
                kind = ButtonKind.Secondary, leading = Icons.AutoMirrored.Filled.List,
            )
        }
    }
}

// ───────────── Saved-program manager ─────────────

private const val ALL = "\u0000all"
private const val NO_FOLDER = "\u0000none"

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ProgramManager(
    lib: ProgramLibrary, currentId: String?,
    onOpen: (String) -> Unit, onNew: () -> Unit, onRename: (String) -> Unit, onDelete: (String) -> Unit,
) {
    val c = AppTheme.colors
    var filter by rememberSaveable { mutableStateOf(ALL) }
    val folders = lib.folders
    val hasUnfiled = lib.programs.any { it.folder.isBlank() }
    if (filter != ALL && filter != NO_FOLDER && filter !in folders) filter = ALL
    val shown = lib.sorted.filter {
        when (filter) {
            ALL -> true
            NO_FOLDER -> it.folder.isBlank()
            else -> it.folder == filter
        }
    }
    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("My programs", style = MaterialTheme.typography.titleLarge, color = c.text, modifier = Modifier.semantics { heading() })
                Text(
                    "${lib.programs.size} saved on this phone",
                    style = MaterialTheme.typography.bodySmall, color = c.textMuted,
                )
            }
            AppButton("New", onNew, kind = ButtonKind.Primary, leading = Icons.Filled.Add)
        }
        if (folders.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("All", filter == ALL) { filter = ALL }
                folders.forEach { f -> Chip(f, filter == f) { filter = f } }
                if (hasUnfiled) Chip("No folder", filter == NO_FOLDER) { filter = NO_FOLDER }
            }
        }
        Spacer(Modifier.height(12.dp))
        Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
            if (shown.isEmpty()) {
                Text("No programs here.", style = MaterialTheme.typography.bodyMedium, color = c.textMuted, modifier = Modifier.padding(vertical = 16.dp))
            }
            shown.forEach { p ->
                ProgramRow(p, p.id == currentId, onOpen = { onOpen(p.id) }, onRename = { onRename(p.id) }, onDelete = { onDelete(p.id) })
                Spacer(Modifier.height(8.dp))
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun ProgramRow(p: SavedProgram, current: Boolean, onOpen: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit) {
    val c = AppTheme.colors
    var menu by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape)
            .background(if (current) c.brandSoft else c.surfaceAlt)
            .border(1.dp, if (current) c.brand.copy(alpha = 0.5f) else Color.Transparent, shape)
            .clickable(onClick = onOpen).padding(start = 14.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(p.name, style = MaterialTheme.typography.titleSmall, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            val now = System.currentTimeMillis()
            val edited = when {
                p.updatedAt <= 0 -> ""
                now - p.updatedAt < DateUtils.MINUTE_IN_MILLIS -> "just now"
                else -> DateUtils.getRelativeTimeSpanString(p.updatedAt, now, DateUtils.MINUTE_IN_MILLIS).toString()
            }
            val meta = listOfNotNull(
                p.folder.ifBlank { null },
                if (p.lineCount == 1) "1 line" else "${p.lineCount} lines",
                edited.ifBlank { null }?.let { "edited $it" },
            ).joinToString(" \u00b7 ")
            Text(meta, style = MaterialTheme.typography.bodySmall, color = c.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (current) Pill("Open", c.brand, c.surface)
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More for ${p.name}", tint = c.textMuted) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Open") }, leadingIcon = { Icon(Icons.Filled.PlayArrow, null) }, onClick = { menu = false; onOpen() })
                DropdownMenuItem(text = { Text("Rename or move") }, leadingIcon = { Icon(Icons.Filled.Edit, null) }, onClick = { menu = false; onRename() })
                DropdownMenuItem(
                    text = { Text("Delete", color = c.error) },
                    leadingIcon = { Icon(Icons.Filled.Delete, null, tint = c.error) },
                    onClick = { menu = false; onDelete() },
                )
            }
        }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    val c = AppTheme.colors
    val shape = CircleShape
    Box(
        Modifier.clip(shape).background(if (selected) c.brand else c.surfaceAlt)
            .border(1.dp, if (selected) c.brand else c.border, shape)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 7.dp)
            .semantics { contentDescription = "$label${if (selected) ", selected" else ""}" },
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (selected) c.onBrand else c.text, maxLines = 1)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProgramDialog(
    title: String, confirm: String, initialName: String, initialFolder: String, folders: List<String>,
    onConfirm: (String, String) -> Unit, onDismiss: () -> Unit,
) {
    val c = AppTheme.colors
    var name by rememberSaveable { mutableStateOf(initialName) }
    var folder by rememberSaveable { mutableStateOf(initialFolder) }
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = c.brand, unfocusedBorderColor = c.border,
        focusedContainerColor = c.surface, unfocusedContainerColor = c.surface,
        focusedTextColor = c.text, unfocusedTextColor = c.text, cursorColor = c.brand,
        focusedLabelColor = c.brand, unfocusedLabelColor = c.textMuted,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(ProgramBook.MAX_NAME) },
                    label = { Text("Program name") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                    shape = RoundedCornerShape(14.dp), colors = fieldColors,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = folder, onValueChange = { folder = it.take(ProgramBook.MAX_FOLDER) },
                    label = { Text("Folder (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("e.g. Practice, School, Games") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                    shape = RoundedCornerShape(14.dp), colors = fieldColors,
                )
                if (folders.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        folders.forEach { f -> Chip(f, folder.trim() == f) { folder = if (folder.trim() == f) "" else f } }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name, folder) }, enabled = ProgramBook.cleanName(name).isNotEmpty()) { Text(confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        containerColor = c.surface,
    )
}

// ───────────── Snippets library ─────────────

@Composable
internal fun SnippetLibrary(onInsert: (PythonSnippet) -> Unit) {
    val c = AppTheme.colors
    var category by rememberSaveable { mutableStateOf(PythonSnippets.categories.first()) }
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp)) {
        Text("Snippets", style = MaterialTheme.typography.titleLarge, color = c.text, modifier = Modifier.semantics { heading() })
        Text(
            "Tap a snippet to preview it, then insert it at the cursor.",
            style = MaterialTheme.typography.bodySmall, color = c.textMuted,
        )
        Spacer(Modifier.height(12.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PythonSnippets.categories.forEach { cat -> Chip(cat, cat == category) { category = cat; expanded = null } }
        }
        Spacer(Modifier.height(12.dp))
        Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
            PythonSnippets.all.filter { it.category == category }.forEach { s ->
                val open = expanded == s.id
                val shape = RoundedCornerShape(14.dp)
                Column(
                    Modifier.fillMaxWidth().clip(shape).background(c.surfaceAlt)
                        .clickable { expanded = if (open) null else s.id }.padding(14.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(s.title, style = MaterialTheme.typography.titleSmall, color = c.text)
                            Text(s.description, style = MaterialTheme.typography.bodySmall, color = c.textMuted)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Insert", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold), color = c.brand,
                            modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { onInsert(s) }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                .semantics { contentDescription = "Insert ${s.title}" },
                        )
                    }
                    if (open) {
                        Spacer(Modifier.height(10.dp))
                        CodeBlock(s.code, title = "snippet.py")
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}
