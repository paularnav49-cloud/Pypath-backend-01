package com.pypath.app.ui.screens.reference

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pypath.app.data.model.CheatItem
import com.pypath.app.data.model.CheatSheet
import com.pypath.app.domain.CourseSnapshot
import com.pypath.app.domain.LevelState
import com.pypath.app.domain.NodeStatus
import com.pypath.app.ui.components.AppButton
import com.pypath.app.ui.components.AppCard
import com.pypath.app.ui.components.AppTopBar
import com.pypath.app.ui.components.ButtonKind
import com.pypath.app.ui.components.Eyebrow
import com.pypath.app.ui.components.Pill
import com.pypath.app.ui.components.PythonHighlighter
import com.pypath.app.ui.components.StatusNode
import com.pypath.app.ui.theme.AppTheme
import com.pypath.app.ui.theme.CodeTextStyle
import kotlinx.coroutines.launch

/** Every level's cheat sheet; each unlocks when its level is finished. */
@Composable
fun CheatSheetsScreen(
    snapshot: CourseSnapshot,
    onBack: () -> Unit,
    onOpenCheatSheet: (levelId: String) -> Unit,
) {
    val c = AppTheme.colors
    val levels = snapshot.levels.filter { it.level.cheatSheet != null }
    val unlocked = levels.count { it.cheatSheetUnlocked }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    Box(Modifier.fillMaxSize().background(c.background)) {
        Column(Modifier.fillMaxSize()) {
            AppTopBar("Cheat sheets", "$unlocked of ${levels.size} unlocked", onBack)
            LazyColumn(
                contentPadding = PaddingValues(
                    start = 20.dp, end = 20.dp, top = 4.dp,
                    bottom = 24.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Text(
                        "A one-page summary of each level. Finish a level to unlock its sheet.",
                        style = MaterialTheme.typography.bodyMedium, color = c.textMuted,
                    )
                    Spacer(Modifier.height(4.dp))
                }
                items(levels, key = { it.level.id }) { lvl ->
                    CheatSheetRow(lvl) {
                        if (lvl.cheatSheetUnlocked) onOpenCheatSheet(lvl.level.id)
                        else scope.launch { snackbar.showSnackbar("Finish Level ${lvl.level.number} to unlock this cheat sheet") }
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(16.dp))
    }
}

/** One row per level; also used on the level screen. */
@Composable
fun CheatSheetRow(lvl: LevelState, onClick: () -> Unit) {
    val c = AppTheme.colors
    val open = lvl.cheatSheetUnlocked
    AppCard(Modifier.fillMaxWidth(), onClick = onClick, background = if (open) c.surface else c.surface.copy(alpha = 0.6f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusNode(if (open) NodeStatus.AVAILABLE else NodeStatus.LOCKED, "${lvl.level.number}", size = 40.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("LEVEL ${lvl.level.number} CHEAT SHEET", style = MaterialTheme.typography.labelSmall, color = if (open) c.brand else c.textFaint)
                Text(lvl.level.cheatSheet?.title ?: lvl.level.title, style = MaterialTheme.typography.titleMedium, color = if (open) c.text else c.textMuted)
                Text(
                    if (open) "One-page summary · ${lvl.level.cheatSheet?.sections?.size ?: 0} topics"
                    else "Unlocks when you finish Level ${lvl.level.number}",
                    style = MaterialTheme.typography.bodySmall, color = c.textMuted,
                )
            }
            if (open) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = c.textFaint)
            else Pill("Locked", c.textFaint, c.surfaceAlt)
        }
    }
}

/** The one-page summary for one level. */
@Composable
fun CheatSheetScreen(snapshot: CourseSnapshot, levelId: String, onBack: () -> Unit) {
    val c = AppTheme.colors
    val lvl = snapshot.level(levelId)
    val sheet = lvl?.level?.cheatSheet
    Column(Modifier.fillMaxSize().background(c.background)) {
        AppTopBar(
            if (sheet != null) "Cheat sheet: ${sheet.title}" else "Cheat sheet",
            lvl?.let { "Level ${it.level.number}" }, onBack,
        )
        if (lvl == null || sheet == null) {
            Text("This cheat sheet isn't available.", color = c.textMuted, modifier = Modifier.padding(20.dp))
            return
        }
        if (!lvl.cheatSheetUnlocked) {
            AppCard(Modifier.padding(20.dp).fillMaxWidth()) {
                Text("This cheat sheet is locked", style = MaterialTheme.typography.titleSmall, color = c.text)
                Text(
                    "Finish every sub-level in Level ${lvl.level.number} (${lvl.completedCount} of ${lvl.total} done) to unlock it.",
                    style = MaterialTheme.typography.bodySmall, color = c.textMuted,
                )
            }
            return
        }
        val clipboard = LocalClipboardManager.current
        var copied by remember { mutableStateOf(false) }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Eyebrow("Level ${lvl.level.number} cheat sheet")
            Spacer(Modifier.height(6.dp))
            Text(sheet.title, style = MaterialTheme.typography.headlineSmall, color = c.text, modifier = Modifier.semantics { heading() })
            sheet.sections.forEach { sec ->
                Spacer(Modifier.height(20.dp))
                Text(sec.heading, style = MaterialTheme.typography.titleMedium, color = c.text)
                sec.items.forEach { Spacer(Modifier.height(10.dp)); Snippet(it) }
            }
            if (sheet.remember.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                AppCard(Modifier.fillMaxWidth(), background = c.accentSoft, borderColor = c.accentSoft) {
                    Text("Remember", style = MaterialTheme.typography.titleSmall, color = c.text)
                    sheet.remember.forEach {
                        Row(Modifier.padding(top = 6.dp)) {
                            Text("•", color = c.text, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(10.dp))
                            Text(it, style = MaterialTheme.typography.bodyMedium, color = c.text)
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
        Box(Modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
            AppButton(
                if (copied) "Copied" else "Copy as text",
                { clipboard.setText(AnnotatedString(asText(lvl.level.number, sheet))); copied = true },
                Modifier.fillMaxWidth(), kind = ButtonKind.Secondary,
            )
        }
    }
}

@Composable
private fun Snippet(it: CheatItem) {
    val c = AppTheme.colors
    val cc = c.code
    val highlighted = remember(it.code) { PythonHighlighter.highlight(it.code, cc) }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.surface)) {
        Box(Modifier.fillMaxWidth().background(cc.background).horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(highlighted, style = CodeTextStyle, softWrap = false)
        }
        it.output?.let { out ->
            Column(Modifier.fillMaxWidth().background(cc.outputBg).horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 8.dp)) {
                Text(out, style = CodeTextStyle, color = cc.outputText, softWrap = false)
            }
        }
        Text(it.note, style = MaterialTheme.typography.bodySmall, color = c.text, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
    }
}

/** Plain-text version for the clipboard. */
internal fun asText(levelNumber: Int, sheet: CheatSheet): String = buildString {
    appendLine("PyPath - Level $levelNumber cheat sheet: ${sheet.title}")
    sheet.sections.forEach { sec ->
        appendLine()
        appendLine("## ${sec.heading}")
        sec.items.forEach { item ->
            appendLine(item.code)
            item.output?.let { out -> appendLine(out.lines().joinToString("\n") { "# -> $it" }) }
            appendLine("# ${item.note}")
            appendLine()
        }
    }
    if (sheet.remember.isNotEmpty()) {
        appendLine("Remember:")
        sheet.remember.forEach { appendLine("- $it") }
    }
}.trimEnd()
