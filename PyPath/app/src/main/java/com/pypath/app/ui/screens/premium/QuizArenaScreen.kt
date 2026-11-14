package com.pypath.app.ui.screens.premium

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import com.pypath.app.audio.LocalSounds
import com.pypath.app.audio.Sfx
import com.pypath.app.domain.CourseSnapshot
import com.pypath.app.domain.NodeStatus
import com.pypath.app.domain.QuestionCheck
import com.pypath.app.premium.ArenaAttempt
import com.pypath.app.premium.ArenaEngine
import com.pypath.app.premium.ArenaMode
import com.pypath.app.premium.ArenaPool
import com.pypath.app.premium.ArenaResult
import com.pypath.app.premium.HardSet
import com.pypath.app.premium.MockDef
import com.pypath.app.premium.PremiumStudyData
import com.pypath.app.ui.components.AppButton
import com.pypath.app.ui.components.AppCard
import com.pypath.app.ui.components.AppTopBar
import com.pypath.app.ui.components.ButtonKind
import com.pypath.app.ui.components.CodeBlock
import com.pypath.app.ui.components.Eyebrow
import com.pypath.app.ui.components.Pill
import com.pypath.app.ui.components.ProgressBar
import com.pypath.app.ui.components.ProgressRing
import com.pypath.app.ui.mcq.McqPhase
import com.pypath.app.ui.mcq.McqQuestionCard
import com.pypath.app.ui.theme.AppTheme
import com.pypath.app.ui.theme.Mono

private val ArenaTabs = listOf("Timed", "Difficult", "Mock tests")

/** Premium Phase 2 · Advanced Quiz Arena home: Timed quizzes, Difficult questions, Mock tests. */
@Composable
fun QuizArenaScreen(
    pool: ArenaPool?,
    snapshot: CourseSnapshot,
    study: PremiumStudyData?,
    active: ArenaAttempt?,
    remainingMs: Long?,
    loadError: String?,
    onBack: () -> Unit,
    onStartTimed: (level: Int, minutes: Int) -> Unit,
    onStartHard: (HardSet) -> Unit,
    onStartMock: (MockDef) -> Unit,
    onResume: () -> Unit,
    onReview: (key: String) -> Unit,
    initialTab: Int = 0,
) {
    val c = AppTheme.colors
    var tab by rememberSaveable { mutableIntStateOf(initialTab) }
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    // Starting something new while an attempt is unfinished asks first (the old one is scored).
    val guard: (() -> Unit) -> Unit = { action -> if (active != null) pending = action else action() }

    Column(Modifier.fillMaxSize().background(c.background)) {
        AppTopBar(title = "Quiz Arena", subtitle = "Timed quizzes, hard questions and mock tests", onBack = onBack)
        if (pool == null) {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                if (loadError != null) Text(loadError, color = c.error) else CircularProgressIndicator(color = c.brand)
            }
            return@Column
        }
        val catalog = pool.catalog
        PremiumTabs(ArenaTabs, tab, { tab = it }, Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (active != null) item(key = "active") { ActiveBanner(active, remainingMs, onResume) }
            when (tab) {
                0 -> {
                    item(key = "timedIntro") {
                        var minutes by rememberSaveable { mutableIntStateOf(catalog.timed.defaultMinutes) }
                        Column {
                            Text(
                                "${catalog.timed.questionCount} questions from one level against the clock. Answers are revealed at the end; " +
                                    "when time runs out the quiz is submitted automatically.",
                                style = MaterialTheme.typography.bodySmall, color = c.textMuted,
                            )
                            Spacer(Modifier.height(10.dp))
                            Text("Time limit", style = MaterialTheme.typography.labelLarge, color = c.text)
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                catalog.timed.minuteOptions.forEach { m ->
                                    val on = m == minutes
                                    Box(
                                        Modifier.clip(CircleShape).background(if (on) c.brand else c.surfaceAlt)
                                            .clickable { minutes = m }.padding(horizontal = 14.dp, vertical = 7.dp)
                                            .semantics { contentDescription = "$m minutes${if (on) ", selected" else ""}" },
                                    ) { Text("$m min", style = MaterialTheme.typography.labelLarge, color = if (on) c.onBrand else c.text) }
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            val levels = pool.timedLevels()
                            levels.forEach { lv ->
                                val key = ArenaEngine.timedKey(lv)
                                // Only levels already completed, so every question is about something taught.
                                val unlocked = snapshot.levels.firstOrNull { it.level.number == lv }?.status == NodeStatus.COMPLETED
                                ArenaRow(
                                    number = "$lv",
                                    title = pool.levelTitles[lv] ?: "Level $lv",
                                    subtitle = if (unlocked) "${catalog.timed.questionCount} questions · $minutes min" else "Complete Level $lv to unlock",
                                    locked = !unlocked,
                                    best = study?.best?.get(key)?.takeIf { study.results.containsKey(key) },
                                    onClick = { guard { onStartTimed(lv, minutes) } },
                                    onReview = study?.results?.get(key)?.let { { onReview(key) } },
                                )
                                Spacer(Modifier.height(10.dp))
                            }
                        }
                    }
                }
                1 -> {
                    item(key = "hardIntro") {
                        Text(
                            "Tricky questions about things you've already learned. No timer: take your time and read the explanation after each answer.",
                            style = MaterialTheme.typography.bodySmall, color = c.textMuted,
                        )
                    }
                    itemsIndexed(catalog.hardSets, key = { _, s -> s.id }) { i, set ->
                        val key = ArenaEngine.hardKey(set)
                        val unlocked = ArenaEngine.hardUnlocked(set, snapshot)
                        val after = set.afterSubLevel?.let { snapshot.subLevel(it) }
                        ArenaRow(
                            number = "${i + 1}",
                            title = set.title,
                            subtitle = if (unlocked) "${set.questions.size} questions · ${set.description}"
                            else "Finish ${after?.let { "${it.subLevel.code} ${it.subLevel.title}" } ?: "earlier lessons"} to unlock",
                            locked = !unlocked,
                            best = study?.best?.get(key)?.takeIf { study.results.containsKey(key) },
                            onClick = { guard { onStartHard(set) } },
                            onReview = study?.results?.get(key)?.let { { onReview(key) } },
                        )
                    }
                }
                else -> {
                    item(key = "mockIntro") {
                        Text(
                            "Full-length tests with questions from several levels. You get a score, accuracy and a topic-by-topic breakdown, then review every answer.",
                            style = MaterialTheme.typography.bodySmall, color = c.textMuted,
                        )
                    }
                    itemsIndexed(catalog.mocks, key = { _, m -> m.id }) { i, mock ->
                        val key = ArenaEngine.mockKey(mock)
                        val first = mock.levels.minOrNull() ?: 1
                        val last = mock.levels.maxOrNull() ?: 1
                        ArenaRow(
                            number = "${i + 1}",
                            title = mock.title,
                            subtitle = "${mock.questionCount} questions · ${mock.minutes} min · Levels $first–$last",
                            locked = false,
                            best = study?.best?.get(key)?.takeIf { study.results.containsKey(key) },
                            onClick = { guard { onStartMock(mock) } },
                            onReview = study?.results?.get(key)?.let { { onReview(key) } },
                        )
                    }
                }
            }
        }
    }

    pending?.let { action ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("Finish the current quiz?") },
            text = { Text("\"${active?.title ?: ""}\" isn't finished. Starting a new one submits it now, and unanswered questions count as wrong.") },
            confirmButton = { TextButton(onClick = { pending = null; action() }) { Text("Submit and start") } },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Cancel") } },
            containerColor = c.surface,
        )
    }
}

@Composable
private fun ActiveBanner(a: ArenaAttempt, remainingMs: Long?, onResume: () -> Unit) {
    val c = AppTheme.colors
    AppCard(Modifier.fillMaxWidth(), background = c.brandSoft, borderColor = c.brand.copy(alpha = 0.35f)) {
        Eyebrow("Unfinished")
        Spacer(Modifier.height(4.dp))
        Text(a.title, style = MaterialTheme.typography.titleMedium, color = c.text)
        Text(
            "${a.answeredCount} of ${a.questionIds.size} answered" + (remainingMs?.let { " · ${ArenaEngine.formatClock(it)} left" } ?: ""),
            style = MaterialTheme.typography.bodySmall, color = c.textMuted,
        )
        Spacer(Modifier.height(10.dp))
        AppButton("Resume", onResume, Modifier.fillMaxWidth())
    }
}

@Composable
private fun ArenaRow(
    number: String,
    title: String,
    subtitle: String,
    locked: Boolean,
    best: Int?,
    onClick: () -> Unit,
    onReview: (() -> Unit)?,
) {
    val c = AppTheme.colors
    AppCard(
        Modifier.fillMaxWidth(),
        onClick = if (locked) null else onClick,
        background = if (locked) c.surface.copy(alpha = 0.6f) else c.surface,
        padding = PaddingValues(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(if (locked) c.surfaceAlt else c.brandSoft),
                contentAlignment = Alignment.Center,
            ) {
                if (locked) Icon(Icons.Filled.Lock, "Locked", tint = c.locked, modifier = Modifier.size(18.dp))
                else Text(number, style = MaterialTheme.typography.titleSmall, color = c.brand, fontWeight = FontWeight.ExtraBold)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = if (locked) c.textMuted else c.text)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = c.textMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (best != null) Pill("Best $best%", c.success, c.successSoft, Modifier.padding(start = 8.dp))
            else if (!locked) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = c.textFaint)
        }
        if (onReview != null && !locked) {
            TextButton(onClick = onReview, contentPadding = PaddingValues(horizontal = 0.dp)) {
                Text("Review last result", style = MaterialTheme.typography.labelLarge, color = c.brand)
            }
        }
    }
}

// ─────────────────────────── Playing ───────────────────────────

@Composable
fun ArenaPlayScreen(vm: ArenaViewModel, onExit: () -> Unit, onFinished: (String) -> Unit) {
    val c = AppTheme.colors
    val a = vm.attempt
    val s = vm.session
    val done = vm.finishedKey
    LaunchedEffect(done) { if (done != null) onFinished(done) }
    if (a == null || s == null) {
        Box(Modifier.fillMaxSize().background(c.background), contentAlignment = Alignment.Center) {
            if (done == null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No quiz in progress.", color = c.textMuted)
                    Spacer(Modifier.height(12.dp))
                    AppButton("Back to Quiz Arena", onExit)
                }
            }
        }
        return
    }
    val exam = a.mode != ArenaMode.HARD
    var confirmLeave by remember { mutableStateOf(false) }
    var confirmSubmit by remember { mutableStateOf(false) }
    BackHandler { confirmLeave = true }
    val sounds = LocalSounds.current

    Column(Modifier.fillMaxSize().background(c.background)) {
        AppTopBar(
            title = a.title,
            subtitle = when (a.mode) { ArenaMode.TIMED -> "Timed quiz"; ArenaMode.MOCK -> "Mock test"; else -> "Difficult questions" },
            onBack = { confirmLeave = true },
            backIcon = Icons.Filled.Close,
            actions = {
                if (exam) TextButton(onClick = { confirmSubmit = true }) { Text("Submit", color = c.brand) }
                else if (a.answeredCount > 0) TextButton(onClick = { confirmSubmit = true }) { Text("End", color = c.brand) }
            },
        )
        Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            ProgressBar(a.answeredCount.toFloat() / a.questionIds.size.coerceAtLeast(1), Modifier.weight(1f))
            vm.remainingMs()?.let { left ->
                Spacer(Modifier.width(12.dp))
                val low = left < 60_000
                Pill(
                    ArenaEngine.formatClock(left), if (low) c.error else c.text, if (low) c.errorSoft else c.surfaceAlt,
                    Modifier.semantics { liveRegion = LiveRegionMode.Polite; contentDescription = "Time left ${ArenaEngine.formatClock(left)}" },
                )
            }
        }
        // key(): a new card per question, no transition (so an exam never flashes the right answer).
        key(a.questionIds.size, s.index) {
            McqQuestionCard(s, Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp))
        }
        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp).navigationBarsPadding()) {
            if (exam) {
                val last = a.answeredCount == a.questionIds.size - 1
                AppButton(
                    if (last) "Save and submit" else "Save answer",
                    onClick = { vm.check() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = s.canCheck,
                )
            } else if (s.phase == McqPhase.ANSWERING) {
                AppButton(
                    "Check answer",
                    onClick = {
                        vm.check()
                        sounds.play(if (vm.session?.results?.lastOrNull() == true) Sfx.CORRECT else Sfx.WRONG)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = s.canCheck,
                )
            } else {
                AppButton(
                    if (a.isComplete) "See results" else "Next question",
                    onClick = { vm.next() },
                    modifier = Modifier.fillMaxWidth(),
                    kind = if (s.isCorrect) ButtonKind.Success else ButtonKind.Primary,
                )
            }
        }
    }

    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text(if (a.timed) "Leave the quiz?" else "Leave for now?") },
            text = {
                Text(
                    if (a.timed) "The timer keeps running. Resume from Quiz Arena before it ends; when time runs out your answers are submitted automatically."
                    else "Your answers are saved. Resume any time from Quiz Arena.",
                )
            },
            confirmButton = { TextButton(onClick = { confirmLeave = false; onExit() }) { Text("Leave") } },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text("Keep going") } },
            containerColor = c.surface,
        )
    }
    if (confirmSubmit) {
        val left = a.questionIds.size - a.answeredCount
        AlertDialog(
            onDismissRequest = { confirmSubmit = false },
            title = { Text(if (exam) "Submit now?" else "End this set?") },
            text = { Text(if (left > 0) "$left question${if (left == 1) " is" else "s are"} not answered yet and will count as wrong." else "You've answered every question.") },
            confirmButton = { TextButton(onClick = { confirmSubmit = false; vm.submit() }) { Text(if (exam) "Submit" else "End") } },
            dismissButton = { TextButton(onClick = { confirmSubmit = false }) { Text("Cancel") } },
            containerColor = c.surface,
        )
    }
}

// ─────────────────────────── Results ───────────────────────────

@Composable
fun ArenaResultScreen(result: ArenaResult?, pool: ArenaPool?, autoSubmitted: Boolean, onBack: () -> Unit) {
    val c = AppTheme.colors
    Column(Modifier.fillMaxSize().background(c.background)) {
        AppTopBar(title = "Results", subtitle = result?.title, onBack = onBack)
        if (result == null || pool == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (result == null && pool != null) Text("No result yet.", color = c.textMuted) else CircularProgressIndicator(color = c.brand)
            }
            return@Column
        }
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "summary") {
                AppCard(Modifier.fillMaxWidth()) {
                    if (result.timedOut) {
                        Pill(if (autoSubmitted) "Time's up · submitted automatically" else "Time ran out", c.error, c.errorSoft)
                        Spacer(Modifier.height(10.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val ringColor = when { result.percent >= 80 -> c.success; result.percent >= 50 -> c.brand; else -> c.accent }
                        ProgressRing(result.percent / 100f, size = 84.dp, color = ringColor) {
                            Text("${result.percent}%", style = MaterialTheme.typography.titleLarge, color = c.text, fontWeight = FontWeight.ExtraBold)
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            StatLine("Score", "${result.correct} / ${result.total}")
                            StatLine("Accuracy", if (result.answered == 0) "–" else "${result.accuracy}% of ${result.answered} answered")
                            StatLine("Time used", ArenaEngine.formatClock(result.timeUsedMs) + (result.timeLimitMs?.let { " of ${ArenaEngine.formatClock(it)}" } ?: ""))
                        }
                    }
                }
            }
            item(key = "topics") {
                AppCard(Modifier.fillMaxWidth()) {
                    Text("Topic-wise performance", style = MaterialTheme.typography.titleSmall, color = c.text)
                    result.topics().forEach { t ->
                        Spacer(Modifier.height(10.dp))
                        Row {
                            Text(t.topic, style = MaterialTheme.typography.bodySmall, color = c.text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${t.correct}/${t.total}", style = MaterialTheme.typography.labelMedium, color = c.textMuted)
                        }
                        Spacer(Modifier.height(4.dp))
                        ProgressBar(
                            t.correct.toFloat() / t.total.coerceAtLeast(1), Modifier.fillMaxWidth(), height = 6.dp,
                            color = if (t.percent >= 70) c.success else if (t.percent >= 40) c.brand else c.accent,
                        )
                    }
                }
            }
            item(key = "reviewTitle") { Text("Review answers", style = MaterialTheme.typography.titleSmall, color = c.text) }
            itemsIndexed(result.items, key = { _, it -> it.questionId }) { i, item ->
                val q = pool.byId[item.questionId]?.question
                AppCard(Modifier.fillMaxWidth(), borderColor = if (item.correct) c.success.copy(alpha = 0.4f) else c.error.copy(alpha = 0.4f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Eyebrow("Question ${i + 1}", if (item.correct) c.success else c.error)
                        Spacer(Modifier.weight(1f))
                        Pill(
                            when { item.correct -> "Correct"; item.answered -> "Wrong"; else -> "Not answered" },
                            if (item.correct) c.success else c.error, if (item.correct) c.successSoft else c.errorSoft,
                        )
                    }
                    if (q == null) {
                        Text("This question is no longer in the app.", style = MaterialTheme.typography.bodySmall, color = c.textMuted)
                        return@AppCard
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(q.prompt, style = MaterialTheme.typography.bodyMedium, color = c.text)
                    q.code?.let { Spacer(Modifier.height(8.dp)); CodeBlock(it) }
                    Spacer(Modifier.height(8.dp))
                    AnswerLine("Your answer", item.answer?.takeIf { item.answered && it.isNotBlank() } ?: "Not answered", if (item.correct) c.success else c.error)
                    if (!item.correct) AnswerLine("Correct answer", QuestionCheck.correctAnswerText(q), c.success)
                    if (!item.correct || result.mode == ArenaMode.HARD) {
                        Spacer(Modifier.height(6.dp))
                        Text(q.explanation, style = MaterialTheme.typography.bodySmall, color = c.textMuted)
                    }
                }
            }
            item(key = "back") { AppButton("Back to Quiz Arena", onBack, Modifier.fillMaxWidth(), kind = ButtonKind.Secondary) }
        }
    }
}

@Composable
private fun StatLine(label: String, value: String) {
    val c = AppTheme.colors
    Row {
        Text(label, style = MaterialTheme.typography.bodySmall, color = c.textMuted, modifier = Modifier.width(78.dp))
        Text(value, style = MaterialTheme.typography.labelLarge, color = c.text)
    }
}

@Composable
private fun AnswerLine(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    val c = AppTheme.colors
    Column(Modifier.padding(vertical = 2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = color)
        Text(value, style = MaterialTheme.typography.bodySmall.copy(fontFamily = Mono), color = c.text)
    }
}
