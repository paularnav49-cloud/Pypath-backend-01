package com.pypath.app.ui.screens.projects

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pypath.app.domain.CourseSnapshot
import com.pypath.app.domain.NodeStatus
import com.pypath.app.domain.SubLevelState
import com.pypath.app.ui.components.AppCard
import com.pypath.app.ui.components.AppTopBar
import com.pypath.app.ui.components.Pill
import com.pypath.app.ui.components.ProgressBar
import com.pypath.app.ui.components.StatusNode
import com.pypath.app.ui.theme.AppTheme
import kotlinx.coroutines.launch

/** All guided projects: one per level plus the final project. */
@Composable
fun ProjectsScreen(
    snapshot: CourseSnapshot,
    onBack: () -> Unit,
    onOpenProject: (String) -> Unit,
) {
    val c = AppTheme.colors
    val projects = snapshot.allProjects
    val built = projects.count { it.status == NodeStatus.COMPLETED }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    Box(Modifier.fillMaxSize().background(c.background)) {
        Column(Modifier.fillMaxSize()) {
            AppTopBar("Projects", "$built of ${projects.size} built", onBack)
            LazyColumn(
                contentPadding = PaddingValues(
                    start = 20.dp, end = 20.dp, top = 4.dp,
                    bottom = 24.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    AppCard(Modifier.fillMaxWidth()) {
                        Text("Build real programs", style = MaterialTheme.typography.titleMedium, color = c.text)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Each level has a guided project that unlocks when you finish the level. They're optional and " +
                                "don't block your path. Level 7 is the final project.",
                            style = MaterialTheme.typography.bodySmall, color = c.textMuted,
                        )
                        Spacer(Modifier.height(12.dp))
                        ProgressBar(if (projects.isEmpty()) 0f else built.toFloat() / projects.size, Modifier.fillMaxWidth(), color = c.success)
                    }
                }
                items(projects, key = { it.subLevel.id }) { p ->
                    ProjectRow(p) {
                        when {
                            p.isPlayable -> onOpenProject(p.subLevel.id)
                            p.isBonus -> scope.launch { snackbar.showSnackbar("Finish every sub-level in Level ${p.level.number} to unlock this project") }
                            else -> scope.launch { snackbar.showSnackbar("Finish Levels 1-6 to unlock the final project") }
                        }
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(16.dp))
    }
}

@Composable
fun ProjectRow(p: SubLevelState, onClick: () -> Unit) {
    val c = AppTheme.colors
    val playable = p.isPlayable
    AppCard(Modifier.fillMaxWidth(), onClick = onClick, background = if (playable) c.surface else c.surface.copy(alpha = 0.6f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusNode(p.status, "${p.level.number}", size = 44.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (p.isBonus) "LEVEL ${p.level.number} PROJECT" else "FINAL PROJECT",
                    style = MaterialTheme.typography.labelSmall, color = if (playable) c.brand else c.textFaint,
                )
                Text(p.subLevel.title, style = MaterialTheme.typography.titleMedium, color = if (playable) c.text else c.textMuted)
                Text(p.subLevel.summary, style = MaterialTheme.typography.bodySmall, color = c.textMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    when (p.status) {
                        NodeStatus.COMPLETED -> Pill("Built", c.success, c.successSoft)
                        NodeStatus.IN_PROGRESS -> {
                            val done = p.progress.project?.completedStages?.size ?: 0
                            Pill("Stage $done of ${p.subLevel.stages.size}", c.brand, c.brandSoft)
                        }
                        NodeStatus.AVAILABLE -> Pill("${p.subLevel.stages.size} stages · ${p.subLevel.estimatedMinutes} min", c.brand, c.brandSoft)
                        NodeStatus.LOCKED -> Pill("Locked", c.textFaint, c.surfaceAlt)
                        NodeStatus.COMING_SOON -> Pill("Coming soon", c.textFaint, c.surfaceAlt)
                    }
                }
            }
            if (playable) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = c.textFaint)
        }
    }
}
