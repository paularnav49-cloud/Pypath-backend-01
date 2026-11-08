package com.pypath.app.ui.screens.stats

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pypath.app.domain.DailyGoal
import com.pypath.app.domain.LearnerStats
import com.pypath.app.domain.Streaks
import com.pypath.app.domain.XpTitles
import com.pypath.app.ui.badges.BadgeIcon
import com.pypath.app.ui.badges.Badges
import com.pypath.app.ui.components.AppButton
import com.pypath.app.ui.components.AppCard
import com.pypath.app.ui.components.AppTopBar
import com.pypath.app.ui.components.ButtonKind
import com.pypath.app.ui.components.DailyGoalOptions
import com.pypath.app.ui.components.FlameIcon
import com.pypath.app.ui.components.ProgressBar
import com.pypath.app.ui.components.ProgressRing
import com.pypath.app.ui.components.StreakOrange
import com.pypath.app.ui.theme.AppTheme
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Batch 3: XP, title, streaks, daily goal, accuracy, progress and weakest topics. All local. */
@Composable
fun StatsScreen(
    stats: LearnerStats,
    onBack: () -> Unit,
    onGoalChange: (DailyGoal) -> Unit,
    onPractise: (subLevelId: String) -> Unit,
    /** Batch 3 Part 2: starts a review limited to these sub-levels. */
    onReviewTopics: (subLevelIds: List<String>) -> Unit = {},
) {
    val c = AppTheme.colors
    Column(Modifier.fillMaxSize().background(c.background)) {
        AppTopBar(title = "Your stats", subtitle = "Everything stays on this phone", onBack = onBack)
        LazyColumn(
            Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(
                start = 20.dp, end = 20.dp, top = 8.dp,
                bottom = 24.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item { Section { XpCard(stats) } }
            item { Section { StreakCard(stats) } }
            item { Section { GoalCard(stats, onGoalChange) } }
            item { Section { AnswersCard(stats) } }
            item { Section { WeakestCard(stats, onPractise, onReviewTopics) } }
            item { Section { ProgressCard(stats) } }
            item { Section { BadgesCard(stats) } }
        }
    }
}

/** Keeps cards readable on wide screens. */
@Composable
private fun Section(content: @Composable () -> Unit) {
    Box(Modifier.widthIn(max = 640.dp).fillMaxWidth()) { content() }
}

@Composable
private fun CardTitle(text: String, trailing: String? = null) {
    val c = AppTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text, style = MaterialTheme.typography.titleSmall, color = c.text,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        if (trailing != null) Text(trailing, style = MaterialTheme.typography.bodySmall, color = c.textMuted)
    }
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun XpCard(stats: LearnerStats) {
    val c = AppTheme.colors
    AppCard(Modifier.fillMaxWidth()) {
        CardTitle("Experience")
        Row(verticalAlignment = Alignment.Bottom) {
            Text("${stats.totalXp}", style = MaterialTheme.typography.displaySmall, color = c.text, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.width(6.dp))
            Text("XP", style = MaterialTheme.typography.titleMedium, color = c.textMuted, modifier = Modifier.padding(bottom = 6.dp))
        }
        Text(stats.title.name, style = MaterialTheme.typography.titleMedium, color = c.brand)
        Spacer(Modifier.height(10.dp))
        val next = stats.nextTitle
        val label = if (next == null) "Top title reached" else "${stats.xpToNext} XP to ${next.name}"
        ProgressBar(
            stats.progressToNext,
            Modifier.fillMaxWidth().semantics {
                contentDescription = "Progress to next title"
                progressBarRangeInfo = ProgressBarRangeInfo(stats.progressToNext, 0f..1f)
            },
        )
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = c.textMuted)
        Spacer(Modifier.height(12.dp))
        Text("Titles", style = MaterialTheme.typography.labelLarge, color = c.textMuted)
        Spacer(Modifier.height(4.dp))
        XpTitles.all.forEach { t ->
            val reached = stats.totalXp >= t.minXp
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Text(
                    t.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f),
                    color = if (reached) c.text else c.textFaint,
                    fontWeight = if (t == stats.title) FontWeight.Bold else FontWeight.Normal,
                )
                Text("${t.minXp} XP", style = MaterialTheme.typography.bodyMedium, color = if (reached) c.text else c.textFaint)
            }
        }
    }
}

private val dateFormat: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

@Composable
private fun StreakCard(stats: LearnerStats) {
    val c = AppTheme.colors
    val s = stats.streak
    AppCard(Modifier.fillMaxWidth()) {
        CardTitle("Streak")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(FlameIcon, null, tint = if (s.activeToday) StreakOrange else c.textMuted, modifier = Modifier.size(36.dp))
            Spacer(Modifier.width(10.dp))
            Column {
                val shown = if (s.freezePending) s.protectedStreak else s.current
                Text(
                    if (shown == 1) "1 day" else "$shown days",
                    style = MaterialTheme.typography.headlineSmall, color = c.text,
                )
                Text(
                    when {
                        s.freezePending -> "Protected: practise today and a streak freeze covers yesterday."
                        s.activeToday -> "Today counts. See you tomorrow."
                        s.current > 0 -> "Practise today to keep it going."
                        else -> "Reach your daily goal to start a streak."
                    },
                    style = MaterialTheme.typography.bodySmall, color = c.textMuted,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MiniStat("Longest", "${s.longest}", Modifier.weight(1f))
            MiniStat("Days practised", "${s.daysPractised}", Modifier.weight(1f))
            MiniStat("Freezes", "${s.freezes}", Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "A 7-day streak earns one streak freeze. It's used automatically if you miss exactly one day.",
            style = MaterialTheme.typography.bodySmall, color = c.textMuted,
        )
        s.recentFreezeDay?.let { day ->
            Spacer(Modifier.height(4.dp))
            Text(
                "A streak freeze covered ${Streaks.date(day).format(dateFormat)}.",
                style = MaterialTheme.typography.bodySmall, color = c.text,
            )
        }
    }
}

@Composable
private fun GoalCard(stats: LearnerStats, onGoalChange: (DailyGoal) -> Unit) {
    val c = AppTheme.colors
    AppCard(Modifier.fillMaxWidth()) {
        CardTitle("Daily goal", trailing = stats.goal.label)
        Row(verticalAlignment = Alignment.CenterVertically) {
            val desc = if (stats.goal.xp == 0) {
                if (stats.goalMetToday) "Practised today" else "Not practised yet today"
            } else "${minOf(stats.todayXp, stats.goal.xp)} of ${stats.goal.xp} XP today"
            ProgressRing(
                stats.goalFraction, Modifier.clearAndSetSemantics { contentDescription = desc },
                size = 56.dp, stroke = 6.dp, color = if (stats.goalMetToday) c.success else c.brand,
            )
            Spacer(Modifier.width(14.dp))
            Column {
                Text(desc, style = MaterialTheme.typography.titleSmall, color = c.text)
                Text(
                    "${stats.todayXp} XP earned today" + if (stats.goalMetToday) " · goal met" else "",
                    style = MaterialTheme.typography.bodySmall, color = c.textMuted,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        DailyGoalOptions(stats.goal, onGoalChange)
    }
}

@Composable
private fun AnswersCard(stats: LearnerStats) {
    val c = AppTheme.colors
    AppCard(Modifier.fillMaxWidth()) {
        CardTitle("Answers", trailing = if (stats.historyPartial) "since this update" else null)
        if (stats.questionsAnswered == 0) {
            EmptyNote("No answers tracked yet. Accuracy appears here after your next quiz.")
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MiniStat("Answered", "${stats.questionsAnswered}", Modifier.weight(1f))
                MiniStat("Correct", "${stats.questionsCorrect}", Modifier.weight(1f))
                MiniStat("Accuracy", "${stats.accuracyPercent ?: 0}%", Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            Text("By level", style = MaterialTheme.typography.labelLarge, color = c.textMuted)
            Spacer(Modifier.height(6.dp))
            stats.perLevel.forEach { la ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        .clearAndSetSemantics {
                            contentDescription = "Level ${la.level.number}, ${la.level.title}: ${la.percent} percent of ${la.answered} answers correct"
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "L${la.level.number} ${la.level.title}", style = MaterialTheme.typography.bodyMedium, color = c.text,
                        modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(8.dp))
                    ProgressBar(la.percent / 100f, Modifier.width(72.dp), height = 6.dp, color = c.success)
                    Spacer(Modifier.width(8.dp))
                    Text("${la.percent}%", style = MaterialTheme.typography.bodySmall, color = c.textMuted, modifier = Modifier.widthIn(min = 36.dp), textAlign = TextAlign.End)
                }
            }
        }
    }
}

@Composable
private fun WeakestCard(stats: LearnerStats, onPractise: (String) -> Unit, onReview: (List<String>) -> Unit) {
    val c = AppTheme.colors
    AppCard(Modifier.fillMaxWidth()) {
        CardTitle("Weakest topics")
        if (stats.weakest.isEmpty()) {
            EmptyNote(
                if (stats.questionsAnswered == 0) "Answer a few quizzes and the topics that need more practice show up here."
                else "Nothing weak so far. Topics appear here after ${LearnerStats.WEAK_MIN_ANSWERS} or more tracked answers with a mistake.",
            )
        } else {
            stats.weakest.forEachIndexed { i, w ->
                if (i > 0) Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Tapping the topic opens its lesson; the button reviews its questions.
                    Column(Modifier.weight(1f).clickable(onClickLabel = "Open lesson") { onPractise(w.subLevel.subLevel.id) }.heightIn(min = 48.dp)) {
                        Text(
                            "${w.subLevel.subLevel.code}  ${w.subLevel.subLevel.title}",
                            style = MaterialTheme.typography.titleSmall, color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                        Text("${w.percent}% of ${w.answered} answers correct", style = MaterialTheme.typography.bodySmall, color = c.textMuted)
                    }
                    Spacer(Modifier.width(10.dp))
                    AppButton("Review", { onReview(listOf(w.subLevel.subLevel.id)) }, kind = ButtonKind.Secondary)
                }
            }
            if (stats.weakest.size > 1) {
                Spacer(Modifier.height(14.dp))
                AppButton(
                    "Review these ${stats.weakest.size} topics", { onReview(stats.weakest.map { it.subLevel.subLevel.id }) },
                    Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun ProgressCard(stats: LearnerStats) {
    AppCard(Modifier.fillMaxWidth()) {
        CardTitle("Course progress")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MiniStat("Sub-levels passed", "${stats.subLevelsPassed}/${stats.subLevelsTotal}", Modifier.weight(1f))
            MiniStat("Levels completed", "${stats.levelsCompleted}/${stats.levelsTotal}", Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MiniStat(
                "Practical tasks",
                stats.tasksTotal?.let { "${stats.tasksCompleted}/$it" } ?: "${stats.tasksCompleted}",
                Modifier.weight(1f),
            )
            MiniStat("Badges", "${stats.badgesEarned}/${stats.badges.size}", Modifier.weight(1f))
        }
    }
}

@Composable
private fun BadgesCard(stats: LearnerStats) {
    val c = AppTheme.colors
    AppCard(Modifier.fillMaxWidth()) {
        CardTitle("Badges", trailing = "${stats.badgesEarned} earned · ${stats.badges.size - stats.badgesEarned} locked")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            stats.badges.forEach { b ->
                val name = Badges.forLevel(b.level.number).name
                Column(
                    Modifier.width(80.dp).clearAndSetSemantics {
                        contentDescription = "$name badge, " + if (b.earned) "earned" else "locked"
                    },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    BadgeIcon(b.level.number, earned = b.earned, size = 48.dp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        name, style = MaterialTheme.typography.labelSmall,
                        color = if (b.earned) c.text else c.textFaint,
                        maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String, modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    Column(modifier.background(c.surfaceAlt, MaterialTheme.shapes.medium).padding(horizontal = 12.dp, vertical = 10.dp)) {
        Text(value, style = MaterialTheme.typography.titleMedium, color = c.text, maxLines = 1)
        Text(label, style = MaterialTheme.typography.bodySmall, color = c.textMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun EmptyNote(text: String) {
    val c = AppTheme.colors
    Box(
        Modifier.fillMaxWidth().background(c.surfaceAlt, MaterialTheme.shapes.medium).padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = c.textMuted, textAlign = TextAlign.Center)
    }
}
