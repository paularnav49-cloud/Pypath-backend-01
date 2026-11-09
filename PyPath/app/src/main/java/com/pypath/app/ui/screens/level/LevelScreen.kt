package com.pypath.app.ui.screens.level

import androidx.compose.ui.semantics.selected
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pypath.app.domain.CourseSnapshot
import com.pypath.app.domain.NodeStatus
import com.pypath.app.domain.SubLevelState
import com.pypath.app.ui.components.AppCard
import com.pypath.app.ui.components.AppTopBar
import com.pypath.app.ui.components.Eyebrow
import com.pypath.app.ui.components.Pill
import com.pypath.app.ui.components.ProgressBar
import com.pypath.app.ui.components.StatusNode
import com.pypath.app.ui.theme.AppTheme
import com.pypath.app.ui.screens.projects.ProjectRow
import com.pypath.app.ui.screens.reference.CheatSheetRow
import kotlinx.coroutines.launch

@Composable
fun LevelScreen(
    snapshot: CourseSnapshot,
    levelId: String,
    onBack: () -> Unit,
    onOpenSubLevel: (String) -> Unit,
    onOpenProject: (String) -> Unit = {},
    onOpenCheatSheet: (String) -> Unit = {},
    /** Batch 3 Part 3: in the two-pane tablet layout, the sub-level shown on the right. */
    selectedSubLevelId: String? = null,
) {
    val c = AppTheme.colors
    val lvl = snapshot.level(levelId) ?: return
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Box(Modifier.fillMaxSize().background(c.background)) {
        Column(Modifier.fillMaxSize()) {
            AppTopBar(title = lvl.level.title, subtitle = "Level ${lvl.level.number}", onBack = onBack)
            LazyColumn(
                // Batch 3 Part 3: centred with a max width on tablets; unchanged on phones.
                modifier = Modifier.align(Alignment.CenterHorizontally).widthIn(max = 640.dp).fillMaxWidth(),
                contentPadding = PaddingValues(
                    start = 20.dp, end = 20.dp, top = 4.dp,
                    bottom = 24.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
                ),
            ) {
                item {
                    AppCard(Modifier.fillMaxWidth()) {
                        Eyebrow("Level ${lvl.level.number}")
                        Spacer(Modifier.height(6.dp))
                        Text(lvl.level.title, style = MaterialTheme.typography.headlineSmall, color = c.text, modifier = Modifier.semantics { heading() })
                        Spacer(Modifier.height(6.dp))
                        Text(lvl.level.description, style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
                        Spacer(Modifier.height(16.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ProgressBar(lvl.fraction, Modifier.weight(1f), color = if (lvl.status == NodeStatus.COMPLETED) c.success else c.brand)
                            Spacer(Modifier.width(12.dp))
                            Text("${lvl.completedCount} of ${lvl.total} done", style = MaterialTheme.typography.bodySmall, color = c.textMuted)
                        }
                    }
                    if (lvl.status == NodeStatus.COMING_SOON) {
                        Spacer(Modifier.height(12.dp))
                        AppCard(Modifier.fillMaxWidth(), background = c.accentSoft, borderColor = c.accentSoft) {
                            Text("Coming soon", style = MaterialTheme.typography.titleSmall, color = c.text)
                            Text("Lessons for this level are being prepared. Here's what it will cover.", style = MaterialTheme.typography.bodySmall, color = c.textMuted)
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                    Text("Sub-levels", style = MaterialTheme.typography.titleLarge, color = c.text, modifier = Modifier.semantics { heading() })
                    Spacer(Modifier.height(12.dp))
                }
                itemsIndexed(lvl.subLevels, key = { _, s -> s.subLevel.id }) { i, s ->
                    SubLevelRow(
                        s, isLast = i == lvl.subLevels.lastIndex,
                        selected = s.subLevel.id == selectedSubLevelId,
                        onClick = {
                            when {
                                s.isPlayable -> onOpenSubLevel(s.subLevel.id)
                                s.status == NodeStatus.COMING_SOON -> scope.launch { snackbar.showSnackbar("This sub-level is coming soon") }
                                else -> scope.launch {
                                    snackbar.showSnackbar("Complete ${s.unlockedBy?.code ?: "the previous sub-level"} ${s.unlockedBy?.title ?: ""} to unlock".trim())
                                }
                            }
                        },
                    )
                }
                // Optional level project and the level's cheat sheet (both unlock when the level is finished).
                if (lvl.projects.isNotEmpty() && lvl.status != NodeStatus.COMING_SOON) {
                    item {
                        Spacer(Modifier.height(24.dp))
                        Text("Level project", style = MaterialTheme.typography.titleLarge, color = c.text, modifier = Modifier.semantics { heading() })
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Optional. Build a small program with what you learned in this level.",
                            style = MaterialTheme.typography.bodySmall, color = c.textMuted,
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                    items(lvl.projects, key = { it.subLevel.id }) { p ->
                        ProjectRow(p) {
                            if (p.isPlayable) onOpenProject(p.subLevel.id)
                            else scope.launch { snackbar.showSnackbar("Finish every sub-level in Level ${lvl.level.number} to unlock this project") }
                        }
                    }
                }
                if (lvl.level.cheatSheet != null && lvl.status != NodeStatus.COMING_SOON) {
                    item {
                        Spacer(Modifier.height(24.dp))
                        Text("Cheat sheet", style = MaterialTheme.typography.titleLarge, color = c.text, modifier = Modifier.semantics { heading() })
                        Spacer(Modifier.height(12.dp))
                        CheatSheetRow(lvl) {
                            if (lvl.cheatSheetUnlocked) onOpenCheatSheet(lvl.level.id)
                            else scope.launch { snackbar.showSnackbar("Finish Level ${lvl.level.number} to unlock its cheat sheet") }
                        }
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(16.dp))
    }
}

@Composable
private fun SubLevelRow(s: SubLevelState, isLast: Boolean, onClick: () -> Unit, selected: Boolean = false) {
    val c = AppTheme.colors
    val active = s.status == NodeStatus.AVAILABLE || s.status == NodeStatus.IN_PROGRESS
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        // Path rail
        Column(Modifier.width(44.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(14.dp))
            StatusNode(s.status, s.subLevel.code, size = 44.dp)
            if (!isLast) {
                Box(
                    Modifier.width(2.dp).weight(1f)
                        .background(if (s.status == NodeStatus.COMPLETED) c.success.copy(alpha = 0.5f) else c.border)
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        val shape = RoundedCornerShape(18.dp)
        Row(
            Modifier.weight(1f).padding(bottom = 12.dp)
                .clip(shape)
                .background(if (active) c.surface else c.surface.copy(alpha = 0.55f))
                .border(
                    if (selected) 2.5.dp else if (active) 1.5.dp else 1.dp,
                    if (selected) c.brand else if (active) c.brand.copy(alpha = 0.5f) else c.border, shape,
                )
                .clickable(onClick = onClick)
                .semantics { if (selected) this.selected = true }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    s.subLevel.code,
                    style = MaterialTheme.typography.labelSmall,
                    color = when (s.status) { NodeStatus.COMPLETED -> c.success; NodeStatus.LOCKED, NodeStatus.COMING_SOON -> c.textFaint; else -> c.brand },
                )
                Text(s.subLevel.title, style = MaterialTheme.typography.titleMedium, color = if (s.isPlayable) c.text else c.textMuted)
                Text(s.subLevel.summary, style = MaterialTheme.typography.bodySmall, color = c.textMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    when (s.status) {
                        NodeStatus.COMPLETED -> {
                            Pill("Completed", c.success, c.successSoft)
                            s.progress.quiz?.let { Pill("Best ${it.bestScore}/${it.bestOutOf}", c.textMuted, c.surfaceAlt) }
                        }
                        NodeStatus.IN_PROGRESS -> Pill("In progress", c.brand, c.brandSoft)
                        NodeStatus.AVAILABLE -> Pill("${s.subLevel.estimatedMinutes} min", c.brand, c.brandSoft)
                        NodeStatus.LOCKED -> Pill("Locked", c.textFaint, c.surfaceAlt)
                        NodeStatus.COMING_SOON -> Pill("Coming soon", c.textFaint, c.surfaceAlt)
                    }
                }
            }
            if (s.isPlayable) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = c.textFaint, modifier = Modifier.size(22.dp))
        }
    }
}
