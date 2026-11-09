package com.pypath.app.ui.screens.onboarding

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pypath.app.domain.LearningTime
import com.pypath.app.ui.components.AppButton
import com.pypath.app.ui.components.Eyebrow
import com.pypath.app.ui.components.ProgressRing
import com.pypath.app.ui.theme.AppTheme

/** Onboarding step 3: "How much time can you spend learning Python every day?" */
@Composable
fun LearningGoalScreen(initial: Int? = null, onContinue: (minutes: Int) -> Unit) {
    val c = AppTheme.colors
    var picked by rememberSaveable { mutableStateOf(initial?.takeIf { LearningTime.isValid(it) }) }
    OnboardingStepScaffold(
        step = 3,
        button = { AppButton("Continue", { picked?.let(onContinue) }, Modifier.fillMaxWidth(), enabled = picked != null, trailing = Icons.AutoMirrored.Filled.ArrowForward) },
    ) {
        Eyebrow("Your daily goal")
        Spacer(Modifier.height(8.dp))
        Text(
            "How much time can you spend learning Python every day?",
            style = MaterialTheme.typography.headlineSmall, color = c.text, modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(8.dp))
        Text("Pick what fits your day. You can change it later in Profile.", style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
        Spacer(Modifier.height(20.dp))
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            LearningTime.OPTIONS.forEach { m ->
                GoalOption(m, picked == m) { picked = m }
            }
        }
    }
}

private fun optionHint(minutes: Int) = when (minutes) {
    5 -> "A quick start"
    10 -> "Steady progress"
    15 -> "Solid habit"
    else -> "Fast track"
}

@Composable
private fun GoalOption(minutes: Int, selected: Boolean, onClick: () -> Unit) {
    val c = AppTheme.colors
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).clip(shape)
            .background(if (selected) c.brandSoft else c.surface)
            .border(BorderStroke(if (selected) 2.dp else 1.dp, if (selected) c.brand else c.border), shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(if (selected) c.brand else c.surfaceAlt),
            contentAlignment = Alignment.Center,
        ) { Text("$minutes", style = MaterialTheme.typography.titleMedium, color = if (selected) c.onBrand else c.text, fontWeight = FontWeight.ExtraBold) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(LearningTime.dailyLabel(minutes), style = MaterialTheme.typography.titleSmall, color = c.text)
            Text(optionHint(minutes), style = MaterialTheme.typography.bodySmall, color = c.textMuted)
        }
        Box(
            Modifier.size(24.dp).clip(CircleShape).background(if (selected) c.brand else Color.Transparent)
                .border(2.dp, if (selected) c.brand else c.border, CircleShape),
            contentAlignment = Alignment.Center,
        ) { if (selected) Icon(Icons.Filled.Check, null, tint = c.onBrand, modifier = Modifier.size(15.dp)) }
    }
}

/** Onboarding step 4: what the daily goal adds up to over a 30-day month. */
@Composable
fun LearningImpactScreen(minutes: Int, onStart: () -> Unit) {
    val c = AppTheme.colors
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val grow by animateFloatAsState(if (shown) 1f else 0f, tween(700), label = "impact")
    OnboardingStepScaffold(
        step = 4,
        button = { AppButton("Let's Start Learning", onStart, Modifier.fillMaxWidth(), trailing = Icons.AutoMirrored.Filled.ArrowForward) },
    ) {
        Eyebrow("Small steps add up")
        Spacer(Modifier.height(16.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            ProgressRing(fraction = grow, size = 150.dp, stroke = 12.dp, color = c.brand) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(LearningTime.shortLabel(LearningTime.monthlyMinutes(minutes)), style = MaterialTheme.typography.headlineMedium, color = c.text, fontWeight = FontWeight.ExtraBold)
                    Text("per month", style = MaterialTheme.typography.bodySmall, color = c.textMuted)
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(
            LearningTime.impactMessage(minutes),
            style = MaterialTheme.typography.headlineSmall, color = c.text, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().semantics { heading() },
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "${LearningTime.dailyLabel(minutes)} \u00D7 ${LearningTime.DAYS_IN_MONTH} days = ${LearningTime.monthlyLabel(minutes)}",
            style = MaterialTheme.typography.bodyLarge, color = c.brand, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Missing a day is okay. Every lesson you finish still counts, and your progress is always saved.",
            style = MaterialTheme.typography.bodyMedium, color = c.textMuted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun OnboardingStepScaffold(step: Int, button: @Composable () -> Unit, content: @Composable () -> Unit) {
    val c = AppTheme.colors
    Column(Modifier.fillMaxSize().background(c.background).statusBarsPadding().navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Logo()
            Spacer(Modifier.width(10.dp))
            Text("PyPath", style = MaterialTheme.typography.titleMedium, color = c.text, modifier = Modifier.weight(1f))
            Text("Step $step of 4", style = MaterialTheme.typography.labelMedium, color = c.textMuted)
        }
        Column(
            Modifier.weight(1f).widthIn(max = 520.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            content()
            Spacer(Modifier.height(16.dp))
        }
        Box(Modifier.widthIn(max = 520.dp).fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) { button() }
    }
}
