package com.pypath.app.ui.screens.dashboard

import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import com.pypath.app.data.settings.ReminderPrefs
import com.pypath.app.domain.A11y
import com.pypath.app.domain.Reminders
import com.pypath.app.ui.components.ReminderDialog
import com.pypath.app.ui.components.AccountDialog
import com.pypath.app.ui.components.PremiumDialog
import com.pypath.app.premium.Entitlements
import com.pypath.app.data.auth.AccountState
import com.pypath.app.data.auth.GoogleAccount
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pypath.app.domain.CourseSnapshot
import com.pypath.app.domain.DailyGoal
import com.pypath.app.domain.LearnerStats
import com.pypath.app.domain.ReviewInfo
import com.pypath.app.ui.components.ReviewCard
import com.pypath.app.ui.badges.BadgeIcon
import com.pypath.app.ui.badges.Badges
import com.pypath.app.domain.LevelState
import com.pypath.app.domain.NodeStatus
import com.pypath.app.ui.components.AppCard
import com.pypath.app.ui.components.DailyGoalDialog
import com.pypath.app.ui.components.FlameIcon
import com.pypath.app.ui.components.StatsIcon
import com.pypath.app.ui.components.StreakGoalChip
import com.pypath.app.ui.components.Pill
import com.pypath.app.ui.components.ProgressBar
import com.pypath.app.ui.components.ProgressRing
import com.pypath.app.ui.components.StatusNode
import com.pypath.app.ui.screens.onboarding.Logo
import com.pypath.app.ui.theme.AppTheme
import java.util.Calendar

@Composable
fun DashboardScreen(
    snapshot: CourseSnapshot,
    onContinue: (String) -> Unit,
    onOpenLevel: (String) -> Unit,
    onResetProgress: () -> Unit,
    soundEffects: Boolean = true,
    onSoundEffectsChange: (Boolean) -> Unit = {},
    onOpenProjects: () -> Unit = {},
    onOpenGlossary: () -> Unit = {},
    onOpenCheatSheets: () -> Unit = {},
    onOpenCertificate: () -> Unit = {},
    /** Batch 3: XP, streak and goal. When null (e.g. snapshot tests) the header looks as before. */
    stats: LearnerStats? = null,
    onOpenStats: () -> Unit = {},
    onDailyGoalChange: (DailyGoal) -> Unit = {},
    /** Batch 3 Part 2: spaced review card. When null (e.g. snapshot tests) no card is drawn. */
    review: ReviewInfo? = null,
    onReview: () -> Unit = {},
    /** Batch 3 Part 3: daily reminder settings. When null (e.g. snapshot tests) the menu item is hidden. */
    reminder: ReminderPrefs? = null,
    onReminderEnabledChange: (Boolean) -> Unit = {},
    onReminderMinutesChange: (Int) -> Unit = {},
    /** Google Sign-In: account menu item and dialog. When null (e.g. snapshot tests) they are hidden. */
    account: AccountState? = null,
    onSignedIn: (GoogleAccount) -> Unit = {},
    onSignOut: () -> Unit = {},
    /** Premium Phase 1: Premium menu item and dialog. When null (e.g. snapshot tests) they are hidden. */
    premium: Entitlements? = null,
    onDevPremiumChange: (Boolean) -> Unit = {},
    /** Premium Phase 2: shown as an extra row only when not null (null = Free, unchanged). */
    onOpenQuizArena: (() -> Unit)? = null,
    onOpenPremiumProjects: (() -> Unit)? = null,
    onOpenCodeBattle: (() -> Unit)? = null,
    codeBattleLocked: Boolean = false,
) {
    val c = AppTheme.colors
    var menu by remember { mutableStateOf(false) }
    var premiumDialog by rememberSaveable { mutableStateOf(false) }
    // Batch 3 Part 3: dialogs survive rotation.
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    var goalDialog by rememberSaveable { mutableStateOf(false) }
    var reminderDialog by rememberSaveable { mutableStateOf(false) }
    var accountDialog by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(c.background)) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Logo()
            Spacer(Modifier.width(10.dp))
            Text("PyPath", style = MaterialTheme.typography.titleMedium, color = c.text)
            Spacer(Modifier.weight(1f))
            if (stats != null) {
                StreakGoalChip(stats, onClick = onOpenStats)
                IconButton(onClick = onOpenStats) { Icon(StatsIcon, "Your stats", tint = c.textMuted) }
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Menu", tint = c.textMuted) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (account != null) {
                        val user = account.user
                        DropdownMenuItem(
                            text = { Text(if (user != null) "Account: ${user.email}" else "Sign in with Google", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingIcon = { Icon(Icons.Filled.AccountCircle, null) },
                            onClick = { menu = false; accountDialog = true },
                        )
                    }
                    if (premium != null) {
                        DropdownMenuItem(
                            text = { Text("Premium: " + if (premium.isPremium) "On (test mode)" else "Off") },
                            leadingIcon = { Icon(Icons.Filled.Star, null) },
                            onClick = { menu = false; premiumDialog = true },
                        )
                    }
                    if (stats != null) {
                        DropdownMenuItem(
                            text = { Text("Daily goal: ${stats.goal.label}") },
                            leadingIcon = { Icon(FlameIcon, null) },
                            onClick = { menu = false; goalDialog = true },
                        )
                    }
                    if (reminder != null) {
                        DropdownMenuItem(
                            text = { Text("Daily reminder: " + if (reminder.enabled) Reminders.label(reminder.minutes) else "Off") },
                            leadingIcon = { Icon(Icons.Filled.DateRange, null) },
                            onClick = { menu = false; reminderDialog = true },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Sound effects") },
                        leadingIcon = { Icon(Icons.Filled.Notifications, null) },
                        trailingIcon = { Switch(checked = soundEffects, onCheckedChange = { onSoundEffectsChange(it) }) },
                        onClick = { onSoundEffectsChange(!soundEffects) },
                    )
                    DropdownMenuItem(
                        text = { Text("Reset progress") },
                        leadingIcon = { Icon(Icons.Filled.Refresh, null) },
                        onClick = { menu = false; confirmReset = true },
                    )
                }
            }
        }

        // Batch 3 Part 3: below 600dp this is exactly the phone layout; from 600dp the level cards
        // sit in two columns and the content is centred.
        BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= TABLET_MIN_WIDTH
        LazyColumn(
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().widthIn(max = DASHBOARD_MAX_WIDTH),
            contentPadding = PaddingValues(
                start = 20.dp, end = 20.dp, top = 8.dp,
                bottom = 24.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                val firstName = account?.user?.firstName
                Text(if (firstName.isNullOrBlank()) greeting() else "${greeting()}, $firstName", style = MaterialTheme.typography.bodyLarge, color = c.textMuted)
                Text(
                    if (snapshot.completedTotal == 0) "Let's start your Python journey" else "Ready for your next step?",
                    style = MaterialTheme.typography.headlineSmall, color = c.text,
                )
                val streak = stats?.streak
                if (streak != null && (streak.freezePending || streak.recentFreezeDay != null)) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (streak.freezePending) "You missed yesterday. Practise today and a streak freeze keeps your ${streak.protectedStreak}-day streak."
                        else "A streak freeze covered the day you missed, so your streak is safe.",
                        style = MaterialTheme.typography.bodySmall, color = c.textMuted,
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
            item { HeroCard(snapshot, onContinue) }
            if (review != null) item { ReviewCard(review, onReview) }
            item { StatsRow(snapshot) }
            item { BadgesRow(snapshot) }
            item { ExtrasGrid(snapshot, onOpenProjects, onOpenGlossary, onOpenCheatSheets, onOpenCertificate, onOpenQuizArena, onOpenPremiumProjects, onOpenCodeBattle, codeBattleLocked) }
            item {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("Your path", style = MaterialTheme.typography.titleLarge, color = c.text, modifier = Modifier.weight(1f).semantics { heading() })
                    Text("${snapshot.levels.size} levels", style = MaterialTheme.typography.bodySmall, color = c.textMuted)
                }
                Spacer(Modifier.height(2.dp))
            }
            if (!wide) {
                items(snapshot.levels, key = { it.level.id }) { lvl ->
                    LevelCard(lvl, previous = snapshot.levels.getOrNull(snapshot.levels.indexOf(lvl) - 1), onClick = { onOpenLevel(lvl.level.id) })
                }
            } else {
                items(snapshot.levels.chunked(2), key = { row -> row.first().level.id }) { row ->
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { lvl ->
                            LevelCard(
                                lvl, previous = snapshot.levels.getOrNull(snapshot.levels.indexOf(lvl) - 1),
                                onClick = { onOpenLevel(lvl.level.id) },
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                            )
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset progress?") },
            text = {
                Text(
                    "All completed sub-levels, MCQ scores, project progress and badges will be cleared, " +
                        "together with your XP, streaks, answer history and practical task completions. " +
                        "Settings like sound and daily goal stay. This can't be undone.",
                )
            },
            confirmButton = { TextButton(onClick = { confirmReset = false; onResetProgress() }) { Text("Reset", color = c.error) } },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
            containerColor = c.surface,
        )
    }
    if (accountDialog && account != null) {
        AccountDialog(account = account, onSignedIn = onSignedIn, onSignOut = onSignOut, onDismiss = { accountDialog = false })
    }
    if (premiumDialog && premium != null) {
        PremiumDialog(premium, onDevPremiumChange, onDismiss = { premiumDialog = false })
    }
    if (goalDialog && stats != null) {
        DailyGoalDialog(selected = stats.goal, onSelect = onDailyGoalChange, onDismiss = { goalDialog = false })
    }
    if (reminderDialog && reminder != null) {
        ReminderDialog(
            prefs = reminder,
            onEnabledChange = onReminderEnabledChange,
            onMinutesChange = onReminderMinutesChange,
            onDismiss = { reminderDialog = false },
        )
    }
}

/** Batch 3 Part 3: width from which tablet layouts are used. */
private val TABLET_MIN_WIDTH = 600.dp
private val DASHBOARD_MAX_WIDTH = 1040.dp

private fun greeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    else -> "Good evening"
}

// Hero keeps the same deep brand gradient in light and dark themes for contrast.
private val HeroStart = Color(0xFF3D5AFE)
private val HeroEnd = Color(0xFF2335C8)

@Composable
private fun HeroCard(snapshot: CourseSnapshot, onContinue: (String) -> Unit) {
    val c = AppTheme.colors
    val target = snapshot.continueTarget
    val current = snapshot.current
    val shape = RoundedCornerShape(24.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape)
            .background(Brush.linearGradient(listOf(HeroStart, HeroEnd)))
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                if (current != null) {
                    Text(
                        "LEVEL ${current.level.number} · ${current.level.title.uppercase()}",
                        style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.92f), // Batch 3 Part 3: 4.60:1 (was 3.59:1)
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (target != null) "${target.subLevel.code}  ${target.subLevel.title}" else "All caught up",
                        style = MaterialTheme.typography.titleLarge, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        when {
                            target == null && snapshot.allAvailableComplete -> "You've finished the whole course. Revisit any level to practise."
                            target == null -> "You've finished every available sub-level. New levels are on the way."
                            target.status == NodeStatus.IN_PROGRESS -> "Pick up where you left off"
                            target.subLevel.isProject -> "${target.subLevel.estimatedMinutes} min · Guided project"
                            else -> "${target.subLevel.estimatedMinutes} min · Learn + MCQs"
                        },
                        style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.92f), // Batch 3 Part 3: 4.60:1 (was 3.86:1)
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            ProgressRing(
                fraction = snapshot.overallFraction, size = 68.dp, stroke = 7.dp,
                color = c.accent, track = Color.White.copy(alpha = 0.18f),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${(snapshot.overallFraction * 100).toInt()}%", style = MaterialTheme.typography.titleSmall, color = Color.White, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
        if (target != null) {
            Spacer(Modifier.height(18.dp))
            Row(
                Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(16.dp)).background(Color.White)
                    .clickable { onContinue(target.subLevel.id) },
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (snapshot.completedTotal == 0 && target.status == NodeStatus.AVAILABLE) "Start Learning" else "Continue Learning",
                    style = MaterialTheme.typography.labelLarge, color = HeroEnd,
                )
                Spacer(Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = HeroEnd, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun StatsRow(snapshot: CourseSnapshot) {
    val c = AppTheme.colors
    val scores = snapshot.orderedSubLevels.mapNotNull { it.progress.quiz?.takeIf { q -> q.total > 0 } }
    val avg = if (scores.isEmpty()) null else scores.sumOf { it.bestPercent } / scores.size
    val currentLevel = snapshot.current?.level?.number ?: 1
    // Batch 3 Part 3: with very large text the three tiles stack instead of truncating.
    if (LocalDensity.current.fontScale >= LARGE_FONT_SCALE) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("Sub-levels", "${snapshot.completedTotal}/${snapshot.playableTotal}", Modifier.fillMaxWidth())
            StatCard("Current level", "$currentLevel of ${snapshot.levels.size}", Modifier.fillMaxWidth())
            StatCard("MCQ average", avg?.let { "$it%" } ?: "—", Modifier.fillMaxWidth())
        }
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatCard("Sub-levels", "${snapshot.completedTotal}/${snapshot.playableTotal}", Modifier.weight(1f))
        StatCard("Current level", "$currentLevel of ${snapshot.levels.size}", Modifier.weight(1f))
        StatCard("MCQ average", avg?.let { "$it%" } ?: "—", Modifier.weight(1f))
    }
}

/** Projects, glossary, cheat sheets and the certificate. */
@Composable
private fun ExtrasGrid(
    snapshot: CourseSnapshot,
    onOpenProjects: () -> Unit,
    onOpenGlossary: () -> Unit,
    onOpenCheatSheets: () -> Unit,
    onOpenCertificate: () -> Unit,
    onOpenQuizArena: (() -> Unit)? = null,
    onOpenPremiumProjects: (() -> Unit)? = null,
    onOpenCodeBattle: (() -> Unit)? = null,
    codeBattleLocked: Boolean = false,
) {
    val c = AppTheme.colors
    val projects = snapshot.allProjects
    val built = projects.count { it.status == NodeStatus.COMPLETED }
    val sheets = snapshot.levels.filter { it.level.cheatSheet != null }
    val unlockedSheets = sheets.count { it.cheatSheetUnlocked }
    Column {
        Text("Extras", style = MaterialTheme.typography.titleLarge, color = c.text, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ExtraCard(Icons.Filled.Build, "Projects", "$built/${projects.size} built", c.brand, c.brandSoft, Modifier.weight(1f), onOpenProjects)
            ExtraCard(Icons.Filled.Search, "Glossary", "${snapshot.course.glossary.size} terms", c.accent, c.accentSoft, Modifier.weight(1f), onOpenGlossary)
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ExtraCard(
                Icons.AutoMirrored.Filled.List, "Cheat sheets", "$unlockedSheets/${sheets.size} unlocked",
                c.success, c.successSoft, Modifier.weight(1f), onOpenCheatSheets,
            )
            val ready = snapshot.certificateUnlocked
            ExtraCard(
                if (ready) Icons.Filled.Star else Icons.Filled.Lock, "Certificate", if (ready) "Ready to share" else "After Level 7",
                if (ready) c.accent else c.textFaint, if (ready) c.accentSoft else c.surfaceAlt, Modifier.weight(1f), onOpenCertificate,
            )
        }
        // Premium Phase 2 (Premium only; Free shows exactly the four cards above).
        if (onOpenQuizArena != null || onOpenPremiumProjects != null) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (onOpenQuizArena != null) {
                    ExtraCard(Icons.Filled.PlayArrow, "Quiz Arena", "Timed · hard · mock", c.brand, c.brandSoft, Modifier.weight(1f), onOpenQuizArena)
                } else Spacer(Modifier.weight(1f))
                if (onOpenPremiumProjects != null) {
                    ExtraCard(Icons.Filled.Build, "Premium projects", "Advanced · real-world", c.accent, c.accentSoft, Modifier.weight(1f), onOpenPremiumProjects)
                } else Spacer(Modifier.weight(1f))
            }
        }
        // Premium Code Battle (Free sees it locked; tapping explains how to get Premium).
        if (onOpenCodeBattle != null) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ExtraCard(
                    if (codeBattleLocked) Icons.Filled.Lock else Icons.Filled.Star, "Code Battle",
                    if (codeBattleLocked) "Premium" else "Timed · hidden tests",
                    if (codeBattleLocked) c.textFaint else c.success, if (codeBattleLocked) c.surfaceAlt else c.successSoft,
                    Modifier.weight(1f), onOpenCodeBattle,
                )
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ExtraCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String,
    tint: androidx.compose.ui.graphics.Color, soft: androidx.compose.ui.graphics.Color,
    modifier: Modifier, onClick: () -> Unit,
) {
    val c = AppTheme.colors
    AppCard(modifier, onClick = onClick, padding = PaddingValues(14.dp)) {
        Box(
            Modifier.size(40.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp)).background(soft),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = tint) }
        Spacer(Modifier.height(10.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, color = c.text, maxLines = 1)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = c.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier) {
    val c = AppTheme.colors
    AppCard(modifier, padding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
        Text(value, style = MaterialTheme.typography.titleMedium, color = c.text, maxLines = 1)
        Text(label, style = MaterialTheme.typography.bodySmall, color = c.textMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** Batch 3 Part 3: from this font scale some rows stack or widen so text isn't cut off. */
private const val LARGE_FONT_SCALE = 1.3f

@Composable
private fun LevelCard(lvl: LevelState, previous: LevelState?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    val locked = lvl.status == NodeStatus.LOCKED || lvl.status == NodeStatus.COMING_SOON
    // Batch 3 Part 3: one TalkBack item with title, progress and locked state.
    val spoken = A11y.levelCard(
        number = lvl.level.number, title = lvl.level.title,
        status = when (lvl.status) {
            NodeStatus.COMPLETED -> "Completed"
            NodeStatus.IN_PROGRESS -> "In progress"
            NodeStatus.COMING_SOON -> "Coming soon"
            else -> "Not started"
        } + if (lvl.badgeEarned) ", badge earned" else "",
        done = lvl.completedCount, total = lvl.total,
        locked = lvl.status == NodeStatus.LOCKED,
        unlockHint = previous?.let { "Complete Level ${it.level.number} to unlock" },
    )
    AppCard(
        modifier.fillMaxWidth().clearAndSetSemantics {
            contentDescription = spoken
            role = Role.Button
            onClick(label = "Open level") { onClick(); true }
        },
        onClick = onClick,
        background = if (locked) c.surface.copy(alpha = 0.6f) else c.surface,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusNode(lvl.status, "${lvl.level.number}", size = 46.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("LEVEL ${lvl.level.number}", style = MaterialTheme.typography.labelSmall, color = if (locked) c.textFaint else c.brand)
                    Spacer(Modifier.width(8.dp))
                    when (lvl.status) {
                        NodeStatus.COMPLETED -> Pill("Completed", c.success, c.successSoft)
                        NodeStatus.COMING_SOON -> Pill("Coming soon", c.textFaint, c.surfaceAlt)
                        NodeStatus.IN_PROGRESS -> Pill("In progress", c.brand, c.brandSoft)
                        else -> {}
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(lvl.level.title, style = MaterialTheme.typography.titleMedium, color = if (locked) c.textMuted else c.text)
                Text(
                    when (lvl.status) {
                        NodeStatus.LOCKED -> "Complete Level ${previous?.level?.number ?: ""} to unlock"
                        else -> lvl.level.description
                    },
                    style = MaterialTheme.typography.bodySmall, color = c.textMuted, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
            if (lvl.badgeEarned) {
                BadgeIcon(lvl.level.number, earned = true, size = 34.dp)
                Spacer(Modifier.width(4.dp))
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = c.textFaint)
        }
        if (!locked) {
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProgressBar(lvl.fraction, Modifier.weight(1f), height = 6.dp, color = if (lvl.status == NodeStatus.COMPLETED) c.success else c.brand)
                Spacer(Modifier.width(10.dp))
                Text("${lvl.completedCount}/${lvl.total}", style = MaterialTheme.typography.bodySmall, color = c.textMuted)
            }
        }
    }
}

/** "My badges": every level badge, grey until earned. */
@Composable
private fun BadgesRow(snapshot: CourseSnapshot) {
    val c = AppTheme.colors
    val levels = snapshot.levels.filter { it.level.available }
    val earned = levels.count { it.badgeEarned }
    AppCard(Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("My badges", style = MaterialTheme.typography.titleSmall, color = c.text, modifier = Modifier.weight(1f).semantics { heading() })
            Text("$earned of ${levels.size}", style = MaterialTheme.typography.bodySmall, color = c.textMuted)
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            levels.forEach { lvl ->
                val spec = Badges.forLevel(lvl.level.number)
                Column(
                    // Batch 3 Part 3: wider with large text so badge names aren't cut mid-word.
                    Modifier.width(80.dp * LocalDensity.current.fontScale.coerceIn(1f, 2f)).clearAndSetSemantics { contentDescription = A11y.badge(spec.name, lvl.badgeEarned) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    BadgeIcon(lvl.level.number, earned = lvl.badgeEarned, size = 48.dp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        spec.name, style = MaterialTheme.typography.labelSmall,
                        color = if (lvl.badgeEarned) c.text else c.textFaint,
                        maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
