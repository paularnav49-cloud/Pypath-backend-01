package com.pypath.app.ui.screens.premium

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pypath.app.premium.BattleAttempt
import com.pypath.app.premium.BattleCatalog
import com.pypath.app.premium.BattleChallenge
import com.pypath.app.premium.BattleData
import com.pypath.app.premium.BattleDifficulty
import com.pypath.app.premium.BattleEngine
import com.pypath.app.premium.BattleHistoryEntry
import com.pypath.app.premium.BattleOutcome
import com.pypath.app.premium.BattleRecord
import com.pypath.app.premium.Trophy
import com.pypath.app.practical.RunEnd
import com.pypath.app.practical.TestResult
import com.pypath.app.practical.failureReason
import com.pypath.app.ui.components.AppButton
import com.pypath.app.ui.components.AppCard
import com.pypath.app.ui.components.AppTopBar
import com.pypath.app.ui.components.ButtonKind
import com.pypath.app.ui.components.Eyebrow
import com.pypath.app.ui.components.Pill
import com.pypath.app.ui.components.ProgressBar
import com.pypath.app.ui.components.ProgressRing
import com.pypath.app.ui.theme.AppTheme
import com.pypath.app.ui.theme.CodeTextStyle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val BattleTabs = listOf("Easy", "Medium", "Hard", "Records")

// ─────────────────────────── Home: challenges + records ───────────────────────────

/** Premium Code Battle home: Easy / Medium / Hard challenge lists and the Records tab. */
@Composable
fun CodeBattleScreen(
    catalog: BattleCatalog?,
    data: BattleData?,
    remainingMs: Long?,
    loadError: String?,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    initialTab: Int = 0,
) {
    val c = AppTheme.colors
    var tab by rememberSaveable { mutableIntStateOf(initialTab) }
    Column(Modifier.fillMaxSize().background(c.background)) {
        AppTopBar(title = "Code Battle", subtitle = "Timed Python challenges with hidden tests", onBack = onBack)
        if (catalog == null || data == null) {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                if (loadError != null) Text(loadError, color = c.error) else CircularProgressIndicator(color = c.brand)
            }
            return@Column
        }
        PremiumTabs(BattleTabs, tab, { tab = it }, Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            data.active?.let { a ->
                catalog.challenge(a.challengeId)?.let { ch ->
                    item(key = "active") { ActiveBattleBanner(ch, a, remainingMs) { onOpen(ch.id) } }
                }
            }
            if (tab < 3) {
                val difficulty = BattleDifficulty.all[tab]
                val list = catalog.of(difficulty)
                item(key = "intro-$difficulty") {
                    val minutes = list.firstOrNull()?.minutes ?: 0
                    Text(
                        "${BattleDifficulty.label(difficulty)} · $minutes minutes per challenge. Read the problem, start the timer, " +
                            "then submit: your code is tested against the examples and hidden tests.",
                        style = MaterialTheme.typography.bodySmall, color = c.textMuted,
                    )
                }
                items(list, key = { it.id }) { ch -> ChallengeBattleRow(ch, data.records[ch.id], data.active?.challengeId == ch.id) { onOpen(ch.id) } }
            } else {
                recordsTab(catalog, data)
            }
        }
    }
}

@Composable
private fun ActiveBattleBanner(ch: BattleChallenge, a: BattleAttempt, remainingMs: Long?, onOpen: () -> Unit) {
    val c = AppTheme.colors
    AppCard(Modifier.fillMaxWidth(), background = c.brandSoft, borderColor = c.brand.copy(alpha = 0.35f)) {
        Eyebrow(if (a.submitted) "Submitted" else "Battle in progress")
        Spacer(Modifier.height(4.dp))
        Text(ch.title, style = MaterialTheme.typography.titleMedium, color = c.text)
        Text(
            when {
                a.submitted -> "Open it to run the tests and see your score."
                remainingMs != null && remainingMs > 0 -> "${BattleEngine.formatClock(remainingMs)} left · the timer keeps running"
                else -> "Time's up. Your code is submitted automatically."
            },
            style = MaterialTheme.typography.bodySmall, color = c.textMuted,
        )
        Spacer(Modifier.height(10.dp))
        AppButton(if (a.submitted) "See result" else "Resume battle", onOpen, Modifier.fillMaxWidth())
    }
}

@Composable
private fun ChallengeBattleRow(ch: BattleChallenge, record: BattleRecord?, running: Boolean, onClick: () -> Unit) {
    val c = AppTheme.colors
    val (fg, bg) = difficultyColors(ch.difficulty)
    AppCard(Modifier.fillMaxWidth(), onClick = onClick, padding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(bg), contentAlignment = Alignment.Center) {
                Text("${ch.order}", style = MaterialTheme.typography.titleSmall, color = fg, fontWeight = FontWeight.ExtraBold)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(ch.title, style = MaterialTheme.typography.titleMedium, color = c.text)
                Text(
                    "${ch.minutes} min · ${ch.tests.size} tests (${ch.hiddenCount} hidden) · " + ch.concepts.joinToString(", "),
                    style = MaterialTheme.typography.bodySmall, color = c.textMuted, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
            when {
                running -> Pill("Running", c.brand, c.brandSoft, Modifier.padding(start = 8.dp))
                record != null && record.solved -> Pill("Best ${record.bestScore}", c.success, c.successSoft, Modifier.padding(start = 8.dp), icon = Icons.Filled.CheckCircle)
                record != null -> Pill("Best ${record.bestScore}", c.textMuted, c.surfaceAlt, Modifier.padding(start = 8.dp))
                else -> Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = c.textFaint)
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.recordsTab(catalog: BattleCatalog, data: BattleData) {
    val summary = BattleEngine.summary(data, catalog)
    item(key = "summary") {
        val c = AppTheme.colors
        AppCard(Modifier.fillMaxWidth()) {
            Text("Personal records", style = MaterialTheme.typography.titleSmall, color = c.text)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProgressRing(summary.solved / summary.total.coerceAtLeast(1).toFloat(), size = 72.dp, color = c.success) {
                    Text("${summary.solved}/${summary.total}", style = MaterialTheme.typography.titleSmall, color = c.text, fontWeight = FontWeight.ExtraBold)
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    RecordLine("Solved", "${summary.solved} of ${summary.total} challenges")
                    RecordLine("Points", "${summary.points} of ${summary.maxPoints}")
                    RecordLine("Battles", "${summary.battles}")
                    RecordLine("Trophies", "${summary.trophies} of ${Trophy.entries.size}")
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "Points add up your best score on each challenge: ×1 Easy, ×2 Medium, ×3 Hard.",
                style = MaterialTheme.typography.bodySmall, color = c.textMuted,
            )
        }
    }
    item(key = "trophies") { TrophyGrid(data) }
    item(key = "bestTitle") { SectionTitle("Best scores") }
    val tried = catalog.challenges.filter { data.records[it.id] != null }
    if (tried.isEmpty()) item(key = "bestEmpty") { EmptyLine("Finish a battle to see your best scores here.") }
    items(tried, key = { "best-" + it.id }) { ch -> BestScoreRow(ch, data.records.getValue(ch.id)) }
    item(key = "historyTitle") { SectionTitle("Challenge history") }
    if (data.history.isEmpty()) item(key = "historyEmpty") { EmptyLine("No battles yet.") }
    items(data.history.take(30).withIndex().toList(), key = { "h-${it.index}-${it.value.finishedAt}" }) { (_, h) -> HistoryRow(h) }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = AppTheme.colors.text, modifier = Modifier.padding(top = 6.dp))
}

@Composable
private fun EmptyLine(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.textMuted)
}

@Composable
private fun RecordLine(label: String, value: String) {
    val c = AppTheme.colors
    Row {
        Text(label, style = MaterialTheme.typography.bodySmall, color = c.textMuted, modifier = Modifier.width(72.dp))
        Text(value, style = MaterialTheme.typography.labelLarge, color = c.text)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TrophyGrid(data: BattleData) {
    val c = AppTheme.colors
    AppCard(Modifier.fillMaxWidth()) {
        Text("Trophies", style = MaterialTheme.typography.titleSmall, color = c.text)
        Spacer(Modifier.height(10.dp))
        Trophy.entries.forEach { t ->
            val earned = t.id in data.trophies
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp).semantics(mergeDescendants = true) {
                contentDescription = "${t.title}, ${t.description}, ${if (earned) "earned" else "not earned yet"}"
            }, verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(34.dp).clip(CircleShape).background(if (earned) c.accentSoft else c.surfaceAlt),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(if (earned) Icons.Filled.Star else Icons.Filled.Lock, null, tint = if (earned) c.accent else c.textFaint, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(t.title, style = MaterialTheme.typography.labelLarge, color = if (earned) c.text else c.textMuted)
                    Text(t.description, style = MaterialTheme.typography.bodySmall, color = c.textMuted)
                }
            }
        }
    }
}

@Composable
private fun BestScoreRow(ch: BattleChallenge, r: BattleRecord) {
    val c = AppTheme.colors
    val (fg, bg) = difficultyColors(ch.difficulty)
    AppCard(Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(ch.title, style = MaterialTheme.typography.labelLarge, color = c.text)
                Text(
                    "${r.attempts} battle${if (r.attempts == 1) "" else "s"} · " +
                        (if (r.solved) "solved ${r.solves}×" + (r.bestTimeMs?.let { " · fastest ${BattleEngine.formatClock(it)}" } ?: "") else "not solved yet"),
                    style = MaterialTheme.typography.bodySmall, color = c.textMuted,
                )
            }
            Pill(BattleDifficulty.label(ch.difficulty), fg, bg, Modifier.padding(end = 8.dp))
            Text("${r.bestScore}", style = MaterialTheme.typography.titleMedium, color = if (r.solved) c.success else c.text, fontWeight = FontWeight.ExtraBold)
        }
    }
}

private fun statusLabel(h: BattleHistoryEntry): String = when {
    h.gaveUp -> "Gave up"
    h.solved && h.timedOut -> "Solved · time up"
    h.solved -> "Solved"
    h.timedOut -> "Time up"
    else -> "Not solved"
}

private fun formatWhen(ms: Long): String = SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date(ms))

@Composable
private fun HistoryRow(h: BattleHistoryEntry) {
    val c = AppTheme.colors
    val (fg, bg) = difficultyColors(h.difficulty)
    AppCard(Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(h.title, style = MaterialTheme.typography.labelLarge, color = c.text)
                Text(
                    "${statusLabel(h)} · ${h.passed}/${h.total} tests · ${formatWhen(h.finishedAt)}",
                    style = MaterialTheme.typography.bodySmall, color = if (h.solved) c.success else c.textMuted,
                )
            }
            Pill(BattleDifficulty.label(h.difficulty), fg, bg, Modifier.padding(end = 8.dp))
            Text("${h.score}", style = MaterialTheme.typography.titleMedium, color = c.text, fontWeight = FontWeight.ExtraBold)
        }
    }
}

// ─────────────────────────── One challenge: brief, battle, result ───────────────────────────

/** State of the tests of a submitted battle, for the progress card. */
data class BattleTestingState(val current: Int, val total: Int, val problem: String?)

/**
 * One challenge. Shows the brief (before starting), the battle itself (timer, editor, Run, Submit)
 * while this challenge's attempt is active, or the result right after it finished.
 */
@Composable
fun BattleScreen(
    ch: BattleChallenge,
    data: BattleData,
    remainingMs: Long?,
    outcome: BattleOutcome?,
    testing: BattleTestingState?,
    onBack: () -> Unit,
    onStart: () -> Unit,
    onSubmit: () -> Unit,
    onGiveUp: () -> Unit,
    onRetryTests: () -> Unit,
    onOpenOther: (String) -> Unit,
    onDoneResult: () -> Unit,
    otherTitle: (String) -> String?,
    workspace: @Composable (editable: Boolean) -> Unit,
) {
    val a = data.active
    when {
        outcome != null && outcome.entry.challengeId == ch.id -> BattleResultView(ch, outcome, onBack = { onDoneResult(); onBack() }, onTryAgain = { onDoneResult(); onStart() }, canRetry = a == null)
        a != null && a.challengeId == ch.id -> BattlePlayView(ch, a, remainingMs, testing, onBack, onSubmit, onGiveUp, onRetryTests, workspace)
        else -> BattleBriefView(ch, data.records[ch.id], a, otherTitle, onBack, onStart, onOpenOther)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProblemCard(ch: BattleChallenge) {
    val c = AppTheme.colors
    val (fg, bg) = difficultyColors(ch.difficulty)
    AppCard(Modifier.fillMaxWidth()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Pill(BattleDifficulty.label(ch.difficulty), fg, bg)
            Pill("${ch.minutes} min", c.text, c.surfaceAlt)
            Pill("${ch.tests.size} tests · ${ch.hiddenCount} hidden", c.text, c.surfaceAlt)
        }
        Spacer(Modifier.height(10.dp))
        Text(ch.description, style = MaterialTheme.typography.bodyMedium, color = c.text)
        if (ch.requirements.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text("REQUIREMENTS", style = MaterialTheme.typography.labelSmall, color = c.textFaint)
            ch.requirements.forEach { r ->
                Row(Modifier.padding(top = 4.dp)) {
                    Text("•", style = MaterialTheme.typography.bodyMedium, color = c.brand, modifier = Modifier.width(14.dp))
                    Text(r, style = MaterialTheme.typography.bodyMedium, color = c.text)
                }
            }
        }
        ch.examples.forEachIndexed { i, ex ->
            Spacer(Modifier.height(12.dp))
            Text("EXAMPLE ${i + 1}", style = MaterialTheme.typography.labelSmall, color = c.textFaint)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExampleBox("Input", ex.input.ifEmpty { "(empty line)" }, Modifier.weight(1f))
                ExampleBox("Output", ex.output.ifEmpty { "(nothing)" }, Modifier.weight(1f))
            }
        }
        if (ch.examples.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Read input with input() and no prompt text. When you press Run, the console also shows the lines you type.",
                style = MaterialTheme.typography.bodySmall, color = c.textMuted,
            )
        }
    }
}

@Composable
private fun ExampleBox(label: String, text: String, modifier: Modifier) {
    val c = AppTheme.colors
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(c.surfaceAlt).padding(horizontal = 10.dp, vertical = 8.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = c.textMuted)
        Spacer(Modifier.height(2.dp))
        Text(text, style = CodeTextStyle, color = c.text)
    }
}

@Composable
private fun BattleBriefView(
    ch: BattleChallenge,
    record: BattleRecord?,
    other: BattleAttempt?,
    otherTitle: (String) -> String?,
    onBack: () -> Unit,
    onStart: () -> Unit,
    onOpenOther: (String) -> Unit,
) {
    val c = AppTheme.colors
    Column(Modifier.fillMaxSize().background(c.background)) {
        AppTopBar(title = ch.title, subtitle = "Code Battle · ${BattleDifficulty.label(ch.difficulty)}", onBack = onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(4.dp))
            ProblemCard(ch)
            Spacer(Modifier.height(12.dp))
            AppCard(Modifier.fillMaxWidth()) {
                Text("How it works", style = MaterialTheme.typography.titleSmall, color = c.text)
                Spacer(Modifier.height(6.dp))
                Text(
                    "The ${ch.minutes}-minute timer starts when you press Start. Use Run to try your code. Submit tests it against " +
                        "${ch.tests.size} cases: the ${ch.examples.size} examples above and ${ch.hiddenCount} hidden ones you can't see. " +
                        "When time runs out, your code is submitted automatically.",
                    style = MaterialTheme.typography.bodySmall, color = c.textMuted,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Score: up to ${BattleEngine.TEST_POINTS} points for the tests you pass, plus up to ${BattleEngine.TIME_POINTS} for time left when every test passes.",
                    style = MaterialTheme.typography.bodySmall, color = c.textMuted,
                )
                if (record != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Your best: ${record.bestScore} · ${record.attempts} battle${if (record.attempts == 1) "" else "s"}" +
                            (if (record.solved) " · solved" else ""),
                        style = MaterialTheme.typography.labelLarge, color = if (record.solved) c.success else c.text,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            if (other != null) {
                AppCard(Modifier.fillMaxWidth(), background = c.accentSoft, borderColor = c.accent.copy(alpha = 0.35f)) {
                    Text(
                        "Another battle is running: ${otherTitle(other.challengeId) ?: "a challenge"}. Finish it or give up before starting a new one.",
                        style = MaterialTheme.typography.bodySmall, color = c.text,
                    )
                    Spacer(Modifier.height(8.dp))
                    AppButton("Go to that battle", { onOpenOther(other.challengeId) }, Modifier.fillMaxWidth(), kind = ButtonKind.Secondary)
                }
                Spacer(Modifier.height(12.dp))
            }
            AppButton("Start battle · ${ch.minutes} min", onStart, Modifier.fillMaxWidth(), enabled = other == null)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun BattlePlayView(
    ch: BattleChallenge,
    a: BattleAttempt,
    remainingMs: Long?,
    testing: BattleTestingState?,
    onBack: () -> Unit,
    onSubmit: () -> Unit,
    onGiveUp: () -> Unit,
    onRetryTests: () -> Unit,
    workspace: @Composable (editable: Boolean) -> Unit,
) {
    val c = AppTheme.colors
    var confirmLeave by remember { mutableStateOf(false) }
    var confirmSubmit by remember { mutableStateOf(false) }
    var confirmGiveUp by remember { mutableStateOf(false) }
    BackHandler(enabled = !a.submitted) { confirmLeave = true }
    val left = remainingMs ?: BattleEngine.remainingMs(a, System.currentTimeMillis())
    Column(Modifier.fillMaxSize().background(c.background)) {
        AppTopBar(
            title = ch.title,
            subtitle = "Code Battle · ${BattleDifficulty.label(ch.difficulty)}",
            onBack = { if (a.submitted) onBack() else confirmLeave = true },
            backIcon = Icons.Filled.Close,
            actions = {
                val low = left < 60_000 && !a.submitted
                Pill(
                    if (a.submitted) "Submitted" else BattleEngine.formatClock(left),
                    if (low) c.error else c.text, if (low) c.errorSoft else c.surfaceAlt,
                    Modifier.padding(end = 12.dp).semantics {
                        liveRegion = LiveRegionMode.Polite
                        contentDescription = if (a.submitted) "Submitted" else "Time left ${BattleEngine.formatClock(left)}"
                    },
                )
            },
        )
        ProgressBar(
            if (a.timeLimitMs > 0) left / a.timeLimitMs.toFloat() else 0f,
            Modifier.fillMaxWidth().padding(horizontal = 20.dp), height = 6.dp,
            color = if (left < 60_000) c.error else c.brand,
        )
        Column(Modifier.weight(1f).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(12.dp))
            ProblemCard(ch)
            Spacer(Modifier.height(16.dp))
            if (a.submitted) {
                TestingCard(a, testing, onRetryTests)
                Spacer(Modifier.height(12.dp))
            }
            workspace(!a.submitted)
            if (!a.submitted) {
                Spacer(Modifier.height(12.dp))
                AppButton(
                    "Submit for testing",
                    { confirmSubmit = true },
                    Modifier.fillMaxWidth().semantics { contentDescription = "Submit code for testing" },
                    kind = ButtonKind.Success,
                    leading = Icons.Filled.CheckCircle,
                )
                TextButton(onClick = { confirmGiveUp = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Give up", style = MaterialTheme.typography.labelLarge, color = c.textMuted)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("Leave the battle?") },
            text = { Text("The timer keeps running and your code is saved. Come back from Code Battle before it ends; when time runs out your code is submitted automatically.") },
            confirmButton = { TextButton(onClick = { confirmLeave = false; onBack() }) { Text("Leave") } },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text("Keep going") } },
            containerColor = c.surface,
        )
    }
    if (confirmSubmit) {
        AlertDialog(
            onDismissRequest = { confirmSubmit = false },
            title = { Text("Submit your code?") },
            text = { Text("It will be tested against all ${ch.tests.size} tests, including ${ch.hiddenCount} hidden ones. You can't change it after submitting.") },
            confirmButton = { TextButton(onClick = { confirmSubmit = false; onSubmit() }) { Text("Submit") } },
            dismissButton = { TextButton(onClick = { confirmSubmit = false }) { Text("Cancel") } },
            containerColor = c.surface,
        )
    }
    if (confirmGiveUp) {
        AlertDialog(
            onDismissRequest = { confirmGiveUp = false },
            title = { Text("Give up this battle?") },
            text = { Text("It ends now with a score of 0 and goes into your history. You can start it again any time.") },
            confirmButton = { TextButton(onClick = { confirmGiveUp = false; onGiveUp() }) { Text("Give up", color = c.error) } },
            dismissButton = { TextButton(onClick = { confirmGiveUp = false }) { Text("Keep going") } },
            containerColor = c.surface,
        )
    }
}

@Composable
private fun TestingCard(a: BattleAttempt, testing: BattleTestingState?, onRetry: () -> Unit) {
    val c = AppTheme.colors
    val problem = testing?.problem
    AppCard(
        Modifier.fillMaxWidth().semantics { contentDescription = "Testing your code" },
        background = if (problem != null) c.errorSoft else c.brandSoft,
        borderColor = (if (problem != null) c.error else c.brand).copy(alpha = 0.35f),
    ) {
        if (a.timedOut) {
            Pill("Time's up · submitted automatically", c.error, c.errorSoft)
            Spacer(Modifier.height(8.dp))
        }
        if (problem != null) {
            Text("Python couldn't run the tests", style = MaterialTheme.typography.titleSmall, color = c.error)
            Spacer(Modifier.height(4.dp))
            Text("$problem\nNothing was scored. Try again.", style = MaterialTheme.typography.bodySmall, color = c.text)
            Spacer(Modifier.height(10.dp))
            AppButton("Run the tests again", onRetry, Modifier.fillMaxWidth())
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), color = c.brand, strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text(
                    if (testing != null && testing.total > 0) "Testing… test ${testing.current} of ${testing.total}" else "Starting the tests…",
                    style = MaterialTheme.typography.titleSmall, color = c.brand,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Each test runs your code with its own input (up to 10 seconds each). Keep this screen open.",
                style = MaterialTheme.typography.bodySmall, color = c.text,
            )
        }
    }
}

@Composable
private fun BattleResultView(ch: BattleChallenge, outcome: BattleOutcome, onBack: () -> Unit, onTryAgain: () -> Unit, canRetry: Boolean) {
    val c = AppTheme.colors
    val e = outcome.entry
    BackHandler { onBack() }
    Column(Modifier.fillMaxSize().background(c.background)) {
        AppTopBar(title = "Battle result", subtitle = ch.title, onBack = onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(4.dp))
            AppCard(Modifier.fillMaxWidth()) {
                if (e.timedOut) { Pill("Time's up · submitted automatically", c.error, c.errorSoft); Spacer(Modifier.height(10.dp)) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val ring = when { e.solved -> c.success; e.score >= 40 -> c.brand; else -> c.accent }
                    ProgressRing(e.score / 100f, size = 84.dp, color = ring) {
                        Text("${e.score}", style = MaterialTheme.typography.titleLarge, color = c.text, fontWeight = FontWeight.ExtraBold)
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            when { e.gaveUp -> "You gave up"; e.solved -> "Solved!"; else -> "Not solved yet" },
                            style = MaterialTheme.typography.titleMedium, color = if (e.solved) c.success else c.text,
                        )
                        RecordLine("Passed", "${e.passed} of ${e.total} tests")
                        RecordLine("Failed", "${e.failed}")
                        RecordLine("Time", BattleEngine.formatClock(e.timeUsedMs) + " of " + BattleEngine.formatClock(ch.timeLimitMs))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("Score out of 100.", style = MaterialTheme.typography.bodySmall, color = c.textMuted)
            }
            if (outcome.newTrophies.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                AppCard(Modifier.fillMaxWidth(), background = c.accentSoft, borderColor = c.accent.copy(alpha = 0.4f)) {
                    Text(if (outcome.newTrophies.size > 1) "New trophies" else "New trophy", style = MaterialTheme.typography.titleSmall, color = c.text)
                    outcome.newTrophies.forEach { t ->
                        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Star, null, tint = c.accent, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(t.title, style = MaterialTheme.typography.labelLarge, color = c.text)
                                Text(t.description, style = MaterialTheme.typography.bodySmall, color = c.textMuted)
                            }
                        }
                    }
                }
            }
            val results = outcome.report?.results.orEmpty()
            if (results.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                AppCard(Modifier.fillMaxWidth()) {
                    Text("Tests", style = MaterialTheme.typography.titleSmall, color = c.text)
                    Text("Hidden tests never show their input or expected output.", style = MaterialTheme.typography.bodySmall, color = c.textMuted)
                    var hiddenNo = 0
                    var exampleNo = 0
                    results.forEach { r ->
                        val label = if (r.test.hidden) "Hidden test ${++hiddenNo}" else "Example ${++exampleNo}"
                        Spacer(Modifier.height(8.dp))
                        BattleTestRow(ch, label, r)
                    }
                }
            }
            if (!e.solved && !e.gaveUp) ch.hint?.let { hint ->
                Spacer(Modifier.height(12.dp))
                AppCard(Modifier.fillMaxWidth()) {
                    Text("HINT", style = MaterialTheme.typography.labelSmall, color = c.textFaint)
                    Spacer(Modifier.height(4.dp))
                    Text(hint, style = MaterialTheme.typography.bodyMedium, color = c.text)
                }
            }
            Spacer(Modifier.height(16.dp))
            if (canRetry) AppButton("Try again", onTryAgain, Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            AppButton("Back to Code Battle", onBack, Modifier.fillMaxWidth(), kind = ButtonKind.Secondary)
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Why a hidden test failed, without revealing its input or output. */
internal fun hiddenFailure(end: RunEnd): String = when (end) {
    RunEnd.FINISHED -> "wrong output"
    RunEnd.ERROR -> "error"
    RunEnd.TIMED_OUT -> "too slow"
    RunEnd.NEEDS_MORE_INPUT -> "asked for more input"
    RunEnd.EXITED -> "exit() called"
    RunEnd.CRASHED, RunEnd.INTERNAL -> "Python stopped"
}

@Composable
private fun BattleTestRow(ch: BattleChallenge, label: String, r: TestResult) {
    val c = AppTheme.colors
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.surfaceAlt).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (r.passed) Icon(Icons.Filled.CheckCircle, "Passed", tint = c.success, modifier = Modifier.size(18.dp))
            else Icon(Icons.Filled.Clear, "Failed", tint = c.error, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                label + when {
                    r.passed -> " · passed"
                    r.test.hidden -> " · failed (${hiddenFailure(r.run.end)})"
                    else -> " · failed"
                },
                style = MaterialTheme.typography.labelLarge, color = c.text, modifier = Modifier.weight(1f),
            )
        }
        if (r.passed || r.test.hidden) return@Column
        failureReason(ch.task(), r)?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = c.text)
        }
    }
}
