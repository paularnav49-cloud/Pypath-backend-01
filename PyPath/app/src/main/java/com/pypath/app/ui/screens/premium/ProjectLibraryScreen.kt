package com.pypath.app.ui.screens.premium

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pypath.app.practical.PracticalTask
import com.pypath.app.premium.PremiumProject
import com.pypath.app.premium.PremiumProjectCatalog
import com.pypath.app.ui.components.AppCard
import com.pypath.app.ui.components.AppTopBar
import com.pypath.app.ui.components.Eyebrow
import com.pypath.app.ui.components.Pill
import com.pypath.app.ui.screens.practical.InstructionsCard
import com.pypath.app.ui.screens.practical.difficultyLabel
import com.pypath.app.ui.theme.AppTheme
import com.pypath.app.ui.theme.Mono

private val LibraryTabs = listOf("Advanced", "Real-world", "Challenges")

/** Premium Phase 2 · Premium Project Library: the three collections. */
@Composable
fun ProjectLibraryScreen(
    catalog: PremiumProjectCatalog?,
    passed: Set<String>,
    onBack: () -> Unit,
    onOpenProject: (String) -> Unit,
    onOpenChallenge: (String) -> Unit,
    initialTab: Int = 0,
) {
    val c = AppTheme.colors
    var tab by rememberSaveable { mutableIntStateOf(initialTab) }
    Column(Modifier.fillMaxSize().background(c.background)) {
        AppTopBar(title = "Premium projects", subtitle = "Build bigger programs, step by step", onBack = onBack)
        if (catalog == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = c.brand) }
            return@Column
        }
        PremiumTabs(LibraryTabs, tab, { tab = it }, Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            when (tab) {
                0, 1 -> {
                    val list = if (tab == 0) catalog.advanced else catalog.realWorld
                    item {
                        Text(
                            if (tab == 0) "A beginner-to-advanced path. Each project combines several things you've learned."
                            else "Practical programs people really use, built one step at a time.",
                            style = MaterialTheme.typography.bodySmall, color = c.textMuted,
                        )
                    }
                    itemsIndexed(list, key = { _, p -> p.id }) { i, p ->
                        val done = p.challengeIds.isNotEmpty() && p.challengeIds.all { it in passed }
                        ProjectRow(p, i + 1, done) { onOpenProject(p.id) }
                    }
                }
                else -> {
                    item {
                        Text(
                            "Each challenge belongs to a project. Run the tests to check your program automatically.",
                            style = MaterialTheme.typography.bodySmall, color = c.textMuted,
                        )
                    }
                    items(catalog.challenges, key = { it.id }) { t ->
                        ChallengeRow(t, catalog.projectOf(t.id), t.id in passed) { onOpenChallenge(t.id) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProjectRow(p: PremiumProject, number: Int, done: Boolean, onClick: () -> Unit) {
    val c = AppTheme.colors
    AppCard(Modifier.fillMaxWidth(), onClick = onClick, padding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(c.brandSoft), contentAlignment = Alignment.Center) {
                Text("$number", style = MaterialTheme.typography.titleSmall, color = c.brand, fontWeight = FontWeight.ExtraBold)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(p.title, style = MaterialTheme.typography.titleMedium, color = c.text)
                Text(p.summary, style = MaterialTheme.typography.bodySmall, color = c.textMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val (fg, bg) = difficultyColors(p.difficulty)
                    difficultyLabel(p.difficulty)?.let { Pill(it, fg, bg) }
                    if (p.challengeIds.isNotEmpty()) Pill("${p.challengeIds.size} challenge${if (p.challengeIds.size == 1) "" else "s"}", c.textMuted, c.surfaceAlt)
                }
            }
            if (done) Icon(Icons.Filled.CheckCircle, "Challenges passed", tint = c.success, modifier = Modifier.padding(start = 8.dp))
            else Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = c.textFaint)
        }
    }
}

@Composable
private fun ChallengeRow(t: PracticalTask, project: PremiumProject?, passed: Boolean, onClick: () -> Unit) {
    val c = AppTheme.colors
    AppCard(Modifier.fillMaxWidth(), onClick = onClick, padding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(if (passed) c.successSoft else c.accentSoft), contentAlignment = Alignment.Center) {
                Text("{ }", style = MaterialTheme.typography.titleSmall.copy(fontFamily = Mono), color = if (passed) c.success else c.accent)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(t.title, style = MaterialTheme.typography.titleMedium, color = c.text)
                Text(
                    listOfNotNull(project?.title, "${t.tests.size} tests", difficultyLabel(t.difficulty)).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = c.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            if (passed) Icon(Icons.Filled.CheckCircle, "Passed", tint = c.success, modifier = Modifier.padding(start = 8.dp))
            else Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = c.textFaint)
        }
    }
}

/** One project: goal, objectives, steps, example run, its challenges and a Run-only editor. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PremiumProjectScreen(
    project: PremiumProject,
    catalog: PremiumProjectCatalog,
    passed: Set<String>,
    onBack: () -> Unit,
    onOpenChallenge: (String) -> Unit,
    /** Turns a sub-level id (e.g. "l8s2") into a readable name such as "8.2 Reading files". */
    lessonName: (String) -> String? = { null },
    workspace: @Composable () -> Unit,
) {
    val c = AppTheme.colors
    Column(Modifier.fillMaxSize().background(c.background)) {
        AppTopBar(
            title = project.title,
            subtitle = if (project.collection == PremiumProject.COLLECTION_ADVANCED) "Advanced project ${project.order}" else "Real-world project",
            onBack = onBack,
        )
        Column(Modifier.weight(1f).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(4.dp))
            AppCard(Modifier.fillMaxWidth()) {
                Eyebrow(if (project.collection == PremiumProject.COLLECTION_ADVANCED) "Advanced Python project" else "Real-world project")
                Spacer(Modifier.height(6.dp))
                Text(project.summary, style = MaterialTheme.typography.bodyMedium, color = c.text)
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val (fg, bg) = difficultyColors(project.difficulty)
                    difficultyLabel(project.difficulty)?.let { Pill(it, fg, bg) }
                    project.concepts.forEach { Pill(it, c.textMuted, c.surfaceAlt) }
                }
                project.recommendedAfter?.let { lessonName(it) }?.let {
                    Spacer(Modifier.height(8.dp))
                    Text("Best after $it", style = MaterialTheme.typography.labelMedium, color = c.textMuted)
                }
            }
            if (project.objectives.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                AppCard(Modifier.fillMaxWidth()) {
                    Text("What you'll practise", style = MaterialTheme.typography.titleSmall, color = c.text)
                    Spacer(Modifier.height(6.dp))
                    project.objectives.forEach {
                        Row(Modifier.padding(vertical = 2.dp)) {
                            Text("•", color = c.brand, modifier = Modifier.width(16.dp))
                            Text(it, style = MaterialTheme.typography.bodySmall, color = c.textMuted)
                        }
                    }
                }
            }
            if (project.steps.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                AppCard(Modifier.fillMaxWidth()) {
                    Text("Step by step", style = MaterialTheme.typography.titleSmall, color = c.text)
                    project.steps.forEachIndexed { i, s ->
                        Spacer(Modifier.height(10.dp))
                        Row {
                            Box(Modifier.size(24.dp).clip(RoundedCornerShape(8.dp)).background(c.brandSoft), contentAlignment = Alignment.Center) {
                                Text("${i + 1}", style = MaterialTheme.typography.labelMedium, color = c.brand, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(s.title, style = MaterialTheme.typography.labelLarge, color = c.text)
                                Text(s.text, style = MaterialTheme.typography.bodySmall, color = c.textMuted)
                            }
                        }
                    }
                }
            }
            if (project.sampleRun.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                AppCard(Modifier.fillMaxWidth()) {
                    Text("Example run", style = MaterialTheme.typography.titleSmall, color = c.text)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        project.sampleRun,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = Mono),
                        color = c.text,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(c.surfaceAlt).padding(10.dp),
                    )
                    project.sampleNote?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(it, style = MaterialTheme.typography.bodySmall, color = c.textMuted)
                    }
                }
            }
            val challenges = project.challengeIds.mapNotNull { catalog.challenge(it) }
            if (challenges.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text("Project challenge", style = MaterialTheme.typography.titleSmall, color = c.text)
                Spacer(Modifier.height(6.dp))
                challenges.forEach { t ->
                    ChallengeRow(t, null, t.id in passed) { onOpenChallenge(t.id) }
                    Spacer(Modifier.height(8.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("Build it", style = MaterialTheme.typography.titleSmall, color = c.text)
            Text(
                "Write and run the project here. Your code is saved on this device.",
                style = MaterialTheme.typography.bodySmall, color = c.textMuted,
            )
            Spacer(Modifier.height(8.dp))
            workspace()
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** One challenge: instructions (with example and hint), editor, Run and automatic tests. */
@Composable
fun PremiumChallengeScreen(
    task: PracticalTask,
    project: PremiumProject?,
    passed: Boolean,
    onBack: () -> Unit,
    onOpenProject: ((String) -> Unit)?,
    workspace: @Composable () -> Unit,
) {
    val c = AppTheme.colors
    Column(Modifier.fillMaxSize().background(c.background)) {
        AppTopBar(title = task.title, subtitle = project?.let { "Challenge · ${it.title}" } ?: "Project challenge", onBack = onBack)
        Column(Modifier.weight(1f).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(4.dp))
            if (passed) {
                Pill("Passed", c.success, c.successSoft, icon = Icons.Filled.CheckCircle)
                Spacer(Modifier.height(10.dp))
            }
            InstructionsCard(task)
            if (project != null && onOpenProject != null) {
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { onOpenProject(project.id) }) {
                    Text("Open the project guide", style = MaterialTheme.typography.labelLarge, color = c.brand)
                }
            }
            Spacer(Modifier.height(16.dp))
            workspace()
            Spacer(Modifier.height(24.dp))
        }
    }
}
