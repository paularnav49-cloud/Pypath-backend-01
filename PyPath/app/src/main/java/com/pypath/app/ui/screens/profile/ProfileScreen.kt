package com.pypath.app.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pypath.app.data.auth.AccountState
import com.pypath.app.domain.CourseSnapshot
import com.pypath.app.domain.DailyGoal
import com.pypath.app.domain.LearnerStats
import com.pypath.app.domain.LearningTime
import com.pypath.app.premium.Entitlements
import com.pypath.app.premium.PremiumFeature
import com.pypath.app.premium.PremiumSource
import com.pypath.app.premium.Trophy
import com.pypath.app.ui.components.AppButton
import com.pypath.app.ui.components.AppCard
import com.pypath.app.ui.components.ButtonKind
import com.pypath.app.ui.components.DailyGoalDialog
import com.pypath.app.ui.components.Pill
import com.pypath.app.ui.components.ProgressBar
import com.pypath.app.ui.components.ProgressRing
import com.pypath.app.ui.screens.premium.PremiumBadge
import com.pypath.app.ui.screens.stats.AnswersCard
import com.pypath.app.ui.screens.stats.BadgesCard
import com.pypath.app.ui.screens.stats.CardTitle
import com.pypath.app.ui.screens.stats.MiniStat
import com.pypath.app.ui.screens.stats.Section
import com.pypath.app.ui.screens.stats.StreakCard
import com.pypath.app.ui.screens.stats.WeakestCard
import com.pypath.app.ui.theme.AppTheme

/**
 * Final frontend polish: the Profile tab. Everything that used to live in "Your stats" (XP, streak,
 * daily goal, answers, weakest topics, course progress, badges) plus the learning-time goal, Premium
 * status and Premium rewards. All numbers come from the same [LearnerStats] as before (local data).
 */
@Composable
fun ProfileScreen(
    stats: LearnerStats,
    snapshot: CourseSnapshot,
    entitlements: Entitlements,
    learningMinutes: Int?,
    onGoalChange: (DailyGoal) -> Unit,
    onLearningMinutesChange: (Int) -> Unit,
    onPractise: (subLevelId: String) -> Unit,
    onReviewTopics: (subLevelIds: List<String>) -> Unit,
    /** Opens the Premium plans screen. */
    onOpenPlans: () -> Unit,
    /** Opens the existing Premium test-mode dialog (development and testing). */
    onOpenPremiumTestMode: () -> Unit,
    /** Opens Code Battle (or the Premium offer when locked; decided by the caller). */
    onOpenRewards: () -> Unit,
    /** Earned Code Battle trophy ids (empty while loading or for Free users who never played). */
    earnedTrophies: Set<String> = emptySet(),
    account: AccountState? = null,
    onOpenAccount: () -> Unit = {},
) {
    val c = AppTheme.colors
    var goalDialog by rememberSaveable { mutableStateOf(false) }
    var minutesDialog by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(c.background)) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Profile", style = MaterialTheme.typography.headlineSmall, color = c.text, modifier = Modifier.semantics { heading() })
                Text("Your progress, stats and plan", style = MaterialTheme.typography.bodySmall, color = c.textMuted)
            }
        }
        LazyColumn(
            Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item { Section { HeaderCard(stats, entitlements, account, onOpenAccount) } }
            item { Section { ExperienceCard(stats, snapshot) } }
            item { Section { StreakCard(stats) } }
            item { Section { DailyGoalCard(stats, learningMinutes, { goalDialog = true }, { minutesDialog = true }) } }
            item { SectionHeading("Learning performance") }
            item { Section { AnswersCard(stats) } }
            item { Section { WeakestCard(stats, onPractise, onReviewTopics) } }
            item { Section { CourseProgressCard(stats, snapshot) } }
            item { SectionHeading("Badges") }
            item { Section { BadgesCard(stats) } }
            item { Section { PremiumRewardsCard(entitlements, earnedTrophies, onOpenRewards) } }
            item { SectionHeading("Premium") }
            item { Section { PremiumCard(entitlements, onOpenPlans, onOpenPremiumTestMode) } }
        }
    }
    if (goalDialog) DailyGoalDialog(selected = stats.goal, onSelect = onGoalChange, onDismiss = { goalDialog = false })
    if (minutesDialog) LearningMinutesDialog(learningMinutes, onSelect = { onLearningMinutesChange(it); minutesDialog = false }, onDismiss = { minutesDialog = false })
}

@Composable
private fun SectionHeading(text: String) {
    val c = AppTheme.colors
    Section {
        Text(
            text, style = MaterialTheme.typography.titleMedium, color = c.text,
            modifier = Modifier.padding(top = 8.dp).semantics { heading() },
        )
    }
}

@Composable
private fun HeaderCard(stats: LearnerStats, entitlements: Entitlements, account: AccountState?, onOpenAccount: () -> Unit) {
    val c = AppTheme.colors
    val user = account?.user
    AppCard(Modifier.fillMaxWidth(), onClick = if (account != null) onOpenAccount else null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(52.dp).clip(CircleShape).background(c.brandSoft), contentAlignment = Alignment.Center) {
                val initial = user?.firstName?.firstOrNull()?.uppercaseChar()
                if (initial != null) Text("$initial", style = MaterialTheme.typography.titleLarge, color = c.brand, fontWeight = FontWeight.ExtraBold)
                else Icon(Icons.Filled.AccountCircle, null, tint = c.brand, modifier = Modifier.size(32.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    user?.displayName?.takeIf { it.isNotBlank() } ?: user?.firstName ?: "Guest learner",
                    style = MaterialTheme.typography.titleMedium, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    user?.email ?: "Not signed in \u00B7 progress is saved on this phone",
                    style = MaterialTheme.typography.bodySmall, color = c.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill(stats.title.name, c.brand, c.brandSoft)
                    if (entitlements.isPremium) Pill(if (entitlements.source == PremiumSource.LOCAL_DEV) "Premium (test)" else "Premium", c.onBrand, c.brand, icon = Icons.Filled.Star)
                    else Pill("Free plan", c.textMuted, c.surfaceAlt)
                }
            }
        }
    }
}

@Composable
private fun ExperienceCard(stats: LearnerStats, snapshot: CourseSnapshot) {
    val c = AppTheme.colors
    val level = snapshot.current?.level
    AppCard(Modifier.fillMaxWidth()) {
        CardTitle("Experience", trailing = level?.let { "Course level ${it.number} of ${snapshot.levels.size}" })
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${stats.totalXp}", style = MaterialTheme.typography.headlineMedium, color = c.text, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.width(4.dp))
                    Text("XP", style = MaterialTheme.typography.titleSmall, color = c.textMuted, modifier = Modifier.padding(bottom = 4.dp))
                }
                Text(stats.title.name, style = MaterialTheme.typography.titleSmall, color = c.brand)
                if (level != null) Text("Level ${level.number} \u00B7 ${level.title}", style = MaterialTheme.typography.bodySmall, color = c.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height(10.dp))
        ProgressBar(stats.progressToNext, Modifier.fillMaxWidth().semantics { contentDescription = "Progress to next title" })
        Spacer(Modifier.height(6.dp))
        val next = stats.nextTitle
        Text(
            if (next == null) "Top title reached" else "${stats.xpToNext} XP to ${next.name}",
            style = MaterialTheme.typography.bodySmall, color = c.textMuted,
        )
    }
}

@Composable
private fun DailyGoalCard(stats: LearnerStats, learningMinutes: Int?, onChangeXp: () -> Unit, onChangeMinutes: () -> Unit) {
    val c = AppTheme.colors
    AppCard(Modifier.fillMaxWidth()) {
        CardTitle("Daily goal")
        Row(verticalAlignment = Alignment.CenterVertically) {
            val desc = if (stats.goal.xp == 0) {
                if (stats.goalMetToday) "Practised today" else "Not practised yet today"
            } else "${minOf(stats.todayXp, stats.goal.xp)} of ${stats.goal.xp} XP today"
            ProgressRing(
                stats.goalFraction, Modifier.clearAndSetSemantics { contentDescription = desc },
                size = 52.dp, stroke = 6.dp, color = if (stats.goalMetToday) c.success else c.brand,
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(desc, style = MaterialTheme.typography.titleSmall, color = c.text)
                Text("${stats.todayXp} XP earned today" + if (stats.goalMetToday) " \u00B7 goal met" else "", style = MaterialTheme.typography.bodySmall, color = c.textMuted)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GoalTile("XP goal", "${stats.goal.label} \u00B7 ${if (stats.goal.xp == 0) "off" else "${stats.goal.xp} XP"}", onChangeXp, Modifier.weight(1f))
            GoalTile("Learning time", learningMinutes?.let { LearningTime.dailyLabel(it) } ?: "Not set", onChangeMinutes, Modifier.weight(1f))
        }
        if (learningMinutes != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                "That's ${LearningTime.monthlyLabel(learningMinutes)} of Python learning a month.",
                style = MaterialTheme.typography.bodySmall, color = c.textMuted,
            )
        }
    }
}

@Composable
private fun GoalTile(label: String, value: String, onClick: () -> Unit, modifier: Modifier) {
    val c = AppTheme.colors
    Column(
        modifier.clip(MaterialTheme.shapes.medium).background(c.surfaceAlt)
            .clickable(onClickLabel = "Change $label", onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = c.textMuted)
        Text(value, style = MaterialTheme.typography.titleSmall, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("Change", style = MaterialTheme.typography.labelMedium, color = c.brand)
    }
}

@Composable
private fun CourseProgressCard(stats: LearnerStats, snapshot: CourseSnapshot) {
    val c = AppTheme.colors
    AppCard(Modifier.fillMaxWidth()) {
        CardTitle("Course progress", trailing = "${(snapshot.overallFraction * 100).toInt()}%")
        ProgressBar(snapshot.overallFraction, Modifier.fillMaxWidth(), height = 8.dp)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MiniStat("Sub-levels passed", "${stats.subLevelsPassed}/${stats.subLevelsTotal}", Modifier.weight(1f))
            MiniStat("Levels completed", "${stats.levelsCompleted}/${stats.levelsTotal}", Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MiniStat("Practical tasks", stats.tasksTotal?.let { "${stats.tasksCompleted}/$it" } ?: "${stats.tasksCompleted}", Modifier.weight(1f))
            MiniStat("Badges", "${stats.badgesEarned}/${stats.badges.size}", Modifier.weight(1f))
        }
    }
}

@Composable
private fun PremiumRewardsCard(entitlements: Entitlements, earned: Set<String>, onOpen: () -> Unit) {
    val c = AppTheme.colors
    val locked = !entitlements.has(PremiumFeature.CODE_BATTLE)
    val count = Trophy.entries.count { it.id in earned }
    AppCard(Modifier.fillMaxWidth(), onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Premium rewards", style = MaterialTheme.typography.titleSmall, color = c.text, modifier = Modifier.weight(1f).semantics { heading() })
            PremiumBadge(locked)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            if (locked) "Code Battle trophies are part of Premium. Tap to see what's included."
            else "$count of ${Trophy.entries.size} Code Battle trophies earned",
            style = MaterialTheme.typography.bodySmall, color = c.textMuted,
        )
        Spacer(Modifier.height(10.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Trophy.entries.forEach { t ->
                val got = !locked && t.id in earned
                Column(
                    Modifier.width(76.dp).clearAndSetSemantics {
                        contentDescription = "${t.title} trophy, " + when { locked -> "Premium, locked"; got -> "earned"; else -> "not earned yet" }
                    },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier.size(44.dp).clip(CircleShape).background(if (got) c.accentSoft else c.surfaceAlt),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(if (locked) Icons.Filled.Lock else Icons.Filled.Star, null, tint = if (got) c.accent else c.textFaint, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        t.title, style = MaterialTheme.typography.labelSmall, color = if (got) c.text else c.textFaint,
                        maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun PremiumCard(entitlements: Entitlements, onOpenPlans: () -> Unit, onOpenTestMode: () -> Unit) {
    val c = AppTheme.colors
    AppCard(Modifier.fillMaxWidth(), background = c.brandSoft, borderColor = c.brand.copy(alpha = 0.35f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(c.brand), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Star, null, tint = c.onBrand)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("PyPath Premium", style = MaterialTheme.typography.titleSmall, color = c.text)
                Text(
                    when {
                        !entitlements.isPremium -> "Free plan \u00B7 Premium features are visible but locked"
                        entitlements.source == PremiumSource.LOCAL_DEV -> "Active through Premium test mode"
                        else -> "Active"
                    },
                    style = MaterialTheme.typography.bodySmall, color = c.textMuted,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        AppButton(if (entitlements.isPremium) "View plans" else "See plans and upgrade", onOpenPlans, Modifier.fillMaxWidth())
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable(onClick = onOpenTestMode).padding(vertical = 10.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Settings, null, tint = c.textMuted, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                "Premium test mode: " + if (entitlements.isPremium) "On" else "Off",
                style = MaterialTheme.typography.labelLarge, color = c.text, modifier = Modifier.weight(1f),
            )
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = c.textFaint)
        }
    }
}

@Composable
fun LearningMinutesDialog(selected: Int?, onSelect: (Int) -> Unit, onDismiss: () -> Unit) {
    val c = AppTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Daily learning time") },
        text = {
            Column(Modifier.selectableGroup()) {
                LearningTime.OPTIONS.forEach { m ->
                    Row(
                        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small)
                            .selectable(selected = m == selected, role = Role.RadioButton) { onSelect(m) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = m == selected, onClick = null)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(LearningTime.dailyLabel(m), style = MaterialTheme.typography.titleSmall, color = c.text)
                            Text("${LearningTime.monthlyLabel(m)} a month", style = MaterialTheme.typography.bodySmall, color = c.textMuted)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        containerColor = c.surface,
    )
}
