package com.pypath.app.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pypath.app.audio.LocalSounds
import com.pypath.app.audio.Sfx
import com.pypath.app.domain.DailyGoal
import com.pypath.app.domain.LearnerStats
import com.pypath.app.domain.Streaks
import com.pypath.app.ui.theme.AppTheme
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.ZonedDateTime

// ───────────── Icons (material-icons-core has no flame or chart) ─────────────

val FlameIcon: ImageVector = ImageVector.Builder("Flame", 24.dp, 24.dp, 24f, 24f).apply {
    path(fill = SolidColor(Color.Black)) {
        moveTo(12f, 2f)
        curveTo(12.6f, 5.6f, 17.5f, 8.2f, 17.5f, 13.6f)
        curveTo(17.5f, 17.8f, 15f, 21.5f, 12f, 21.5f)
        curveTo(8.9f, 21.5f, 6.5f, 18.9f, 6.5f, 15.6f)
        curveTo(6.5f, 12.4f, 8.6f, 10.6f, 9.3f, 8.2f)
        curveTo(10.4f, 9.6f, 10.8f, 11.2f, 10.6f, 12.8f)
        curveTo(12.1f, 11f, 12.6f, 6.8f, 12f, 2f)
        close()
    }
}.build()

val StatsIcon: ImageVector = ImageVector.Builder("Stats", 24.dp, 24.dp, 24f, 24f).apply {
    path(stroke = SolidColor(Color.Black), strokeLineWidth = 2.4f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(5f, 20f); lineTo(5f, 13f)
        moveTo(10f, 20f); lineTo(10f, 8f)
        moveTo(15f, 20f); lineTo(15f, 11f)
        moveTo(20f, 20f); lineTo(20f, 4f)
    }
}.build()

/** Flame colour: orange when the streak is alive today, muted otherwise. */
val StreakOrange = Color(0xFFF97316)

// ───────────── Today (re-read at midnight and every minute) ─────────────

/**
 * Today's local epoch day. Updates at local midnight while the screen is open, and re-checks every
 * minute so a time-zone or clock change is picked up without restarting the app.
 */
@Composable
fun rememberToday(): Long {
    var today by remember { mutableLongStateOf(Streaks.today()) }
    LaunchedEffect(Unit) {
        while (true) {
            val now = ZonedDateTime.now()
            val midnight = now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
            val untilMidnight = Duration.between(now, midnight).toMillis() + 500
            delay(untilMidnight.coerceIn(1_000L, 60_000L))
            today = Streaks.today()
        }
    }
    return today
}

// ───────────── Dashboard chip: streak flame + daily goal ring ─────────────

@Composable
fun StreakGoalChip(stats: LearnerStats, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    val streak = stats.streak
    val shown = if (streak.freezePending) streak.protectedStreak else streak.current
    val flameColor = if (streak.activeToday) StreakOrange else c.textMuted
    val goalText = if (stats.goal.xp == 0) {
        if (stats.goalMetToday) "Practised today" else "Not practised yet today"
    } else "Daily goal ${minOf(stats.todayXp, stats.goal.xp)} of ${stats.goal.xp} XP"
    val streakText = when {
        streak.freezePending -> "$shown day streak, protected by a streak freeze. Practise today to keep it."
        shown == 1 -> "1 day streak"
        else -> "$shown day streak"
    }
    Row(
        modifier
            .heightIn(min = 48.dp)
            .clip(CircleShape)
            .background(c.surface)
            .clickable(role = Role.Button, onClick = onClick)
            .clearAndSetSemantics { contentDescription = "$streakText. $goalText. Opens your stats." }
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(FlameIcon, null, tint = flameColor, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(3.dp))
        Text("$shown", style = MaterialTheme.typography.titleSmall, color = c.text, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.width(10.dp))
        ProgressRing(
            fraction = stats.goalFraction, size = 26.dp, stroke = 4.dp,
            color = if (stats.goalMetToday) c.success else c.brand,
        )
    }
}

// ───────────── Daily goal picker ─────────────

@Composable
fun DailyGoalOptions(selected: DailyGoal, onSelect: (DailyGoal) -> Unit) {
    val c = AppTheme.colors
    Column(Modifier.selectableGroup()) {
        DailyGoal.entries.forEach { goal ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(MaterialTheme.shapes.small)
                    .selectable(selected = goal == selected, role = Role.RadioButton, onClick = { onSelect(goal) })
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = goal == selected, onClick = null)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(goal.label, style = MaterialTheme.typography.titleSmall, color = c.text)
                    Text(goal.description, style = MaterialTheme.typography.bodySmall, color = c.textMuted)
                }
            }
        }
    }
}

@Composable
fun DailyGoalDialog(selected: DailyGoal, onSelect: (DailyGoal) -> Unit, onDismiss: () -> Unit) {
    val c = AppTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Daily goal") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "A day counts towards your streak when you reach this goal.",
                    style = MaterialTheme.typography.bodyMedium, color = c.textMuted,
                )
                Spacer(Modifier.height(8.dp))
                DailyGoalOptions(selected) { onSelect(it); onDismiss() }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        containerColor = c.surface,
    )
}

// ───────────── Streak milestone celebration ─────────────

/**
 * Short celebration for 3, 7, 14, 30 and 100 day streaks. Plays the existing "correct" sound
 * (silent when Sound effects is off). With reduced motion it is a plain short fade.
 */
@Composable
fun StreakCelebration(days: Int, onContinue: () -> Unit) {
    val reduced = rememberReducedMotion()
    val sounds = LocalSounds.current
    val alpha = remember { Animatable(0f) }
    val scale = remember { Animatable(if (reduced) 1f else 0.4f) }
    LaunchedEffect(Unit) {
        sounds.play(Sfx.CORRECT)
        if (reduced) {
            alpha.animateTo(1f, tween(200))
        } else {
            alpha.animateTo(1f, tween(250))
            scale.animateTo(1.15f, tween(350, easing = FastOutSlowInEasing))
            scale.animateTo(1f, tween(200))
        }
    }
    BackHandler(onBack = onContinue)
    Box(
        Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha.value }.background(Color(0xF0101426))
            // Taps on the scrim do nothing (the button closes it), so content below can't be pressed.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.statusBarsPadding().navigationBarsPadding().widthIn(max = 480.dp).padding(28.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                Modifier.size(140.dp).graphicsLayer { scaleX = scale.value; scaleY = scale.value }
                    .clip(CircleShape).background(Color(0x33F97316)),
                contentAlignment = Alignment.Center,
            ) { Icon(FlameIcon, null, tint = StreakOrange, modifier = Modifier.size(88.dp)) }
            Spacer(Modifier.height(24.dp))
            Text(
                "$days-day streak!",
                style = MaterialTheme.typography.displaySmall, color = Color.White, textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading(); liveRegion = LiveRegionMode.Polite },
            )
            Spacer(Modifier.height(10.dp))
            Text(
                milestoneMessage(days),
                style = MaterialTheme.typography.bodyLarge, color = Color(0xFFD7DBEA), textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))
            AppButton("Keep going", onContinue, Modifier.fillMaxWidth(), kind = ButtonKind.Success)
        }
    }
}

private fun milestoneMessage(days: Int): String = when (days) {
    3 -> "Three days in a row. A habit is starting."
    7 -> "A whole week of Python. You also earned a streak freeze if you didn't have one."
    14 -> "Two weeks in a row. That's real dedication."
    30 -> "A month of practice. Your future self says thanks."
    else -> "$days days in a row. Incredible."
}
