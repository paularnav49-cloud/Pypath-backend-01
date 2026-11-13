package com.pypath.app.ui.screens.completion

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.pypath.app.ui.components.rememberReducedMotion
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pypath.app.domain.CourseSnapshot
import com.pypath.app.domain.NodeStatus
import com.pypath.app.ui.components.AppButton
import com.pypath.app.ui.components.AppCard
import com.pypath.app.ui.components.ButtonKind
import com.pypath.app.ui.components.Eyebrow
import com.pypath.app.ui.components.ProgressBar
import com.pypath.app.ui.theme.AppTheme
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun CompletionScreen(
    snapshot: CourseSnapshot,
    subLevelId: String,
    onNext: (String) -> Unit,
    onBackToLevel: (String) -> Unit,
    onDashboard: () -> Unit,
) {
    val c = AppTheme.colors
    val s = snapshot.subLevel(subLevelId) ?: return
    val level = snapshot.level(s.level.id) ?: return
    val next = snapshot.next(subLevelId)
    val nextPlayable = next != null && next.status != NodeStatus.COMING_SOON
    val finishedLevel = next == null || next.level.id != s.level.id
    val quiz = s.progress.quiz

    BackHandler { onBackToLevel(s.level.id) }

    // Batch 3 Part 3: with "remove animations" on, the pop and burst become a short fade.
    val reduced = rememberReducedMotion()
    val scale = remember { Animatable(if (reduced) 1f else 0.4f) }
    val burst = remember { Animatable(0f) }
    val fade = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (!reduced) scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
    }
    LaunchedEffect(Unit) { if (!reduced) burst.animateTo(1f, tween(900)) }
    LaunchedEffect(Unit) { fade.animateTo(1f, if (reduced) tween(200) else tween(500, delayMillis = 200)) }

    Column(Modifier.fillMaxSize().background(c.background).statusBarsPadding()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(48.dp))
            Box(Modifier.size(180.dp), contentAlignment = Alignment.Center) {
                val dots = listOf(c.brand, c.accent, c.success, c.brandStrong)
                if (!reduced) Canvas(Modifier.fillMaxSize().clearAndSetSemantics { }) {
                    val r = size.minDimension / 2 * (0.45f + 0.5f * burst.value)
                    for (i in 0 until 12) {
                        val a = Math.toRadians(i * 30.0 + 15)
                        drawCircle(
                            color = dots[i % dots.size].copy(alpha = 1f - burst.value * 0.6f),
                            radius = (if (i % 2 == 0) 6f else 4f) * density,
                            center = Offset(center.x + (r * cos(a)).toFloat(), center.y + (r * sin(a)).toFloat()),
                        )
                    }
                }
                Box(
                    Modifier.scale(scale.value).size(104.dp).clip(CircleShape).background(c.success),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.Check, "Completed", tint = c.onSuccess, modifier = Modifier.size(56.dp)) }
            }
            Column(Modifier.alpha(fade.value), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(12.dp))
                Eyebrow(if (finishedLevel) "Level ${s.level.number} complete" else "Sub-level complete", color = c.success)
                Spacer(Modifier.height(8.dp))
                Text("${s.subLevel.code} ${s.subLevel.title}", style = MaterialTheme.typography.headlineMedium, color = c.text, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
                Spacer(Modifier.height(8.dp))
                Text(
                    if (finishedLevel) "You've finished every sub-level in ${s.level.title}. Great work."
                    else "You learned the concept and passed the MCQs.",
                    style = MaterialTheme.typography.bodyLarge, color = c.textMuted, textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AppCard(Modifier.weight(1f)) {
                        Text(quiz?.let { "${it.lastScore}/${it.total}" } ?: "—", style = MaterialTheme.typography.titleLarge, color = c.text)
                        Text("MCQ score", style = MaterialTheme.typography.bodySmall, color = c.textMuted)
                    }
                    AppCard(Modifier.weight(1f)) {
                        Text("${level.completedCount}/${level.total}", style = MaterialTheme.typography.titleLarge, color = c.text)
                        Text("Level ${s.level.number} progress", style = MaterialTheme.typography.bodySmall, color = c.textMuted)
                    }
                }
                Spacer(Modifier.height(12.dp))
                ProgressBar(level.fraction, Modifier.fillMaxWidth(), color = c.success)

                if (next != null) {
                    Spacer(Modifier.height(24.dp))
                    AppCard(Modifier.fillMaxWidth(), background = if (nextPlayable) c.brandSoft else c.surfaceAlt, borderColor = Color.Transparent) {
                        Eyebrow(if (!nextPlayable) "Coming soon" else if (finishedLevel) "Next level" else "Up next", color = if (nextPlayable) c.brand else c.textMuted)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (finishedLevel) "Level ${next.level.number} · ${next.level.title}" else "${next.subLevel.code} ${next.subLevel.title}",
                            style = MaterialTheme.typography.titleMedium, color = c.text,
                        )
                        Text(
                            if (nextPlayable) next.subLevel.summary else "New lessons are on the way. Your progress is saved.",
                            style = MaterialTheme.typography.bodySmall, color = c.textMuted,
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
        Column(
            Modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (next != null && nextPlayable) {
                AppButton(
                    if (finishedLevel) "Start Level ${next.level.number}" else "Next sub-level",
                    { onNext(next.subLevel.id) }, Modifier.fillMaxWidth(), trailing = Icons.AutoMirrored.Filled.ArrowForward,
                )
                AppButton("Back to level", { onBackToLevel(s.level.id) }, Modifier.fillMaxWidth(), kind = ButtonKind.Secondary)
            } else {
                AppButton("Back to dashboard", onDashboard, Modifier.fillMaxWidth())
            }
        }
    }
}
