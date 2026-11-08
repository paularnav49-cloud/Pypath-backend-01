package com.pypath.app.ui.screens.onboarding

import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.platform.LocalDensity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pypath.app.ui.components.AppButton
import com.pypath.app.ui.components.CodeBlock
import com.pypath.app.ui.components.Eyebrow
import com.pypath.app.ui.components.Pill
import com.pypath.app.ui.theme.AppTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

private const val PAGE_COUNT = 3

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val c = AppTheme.colors
    val pager = rememberPagerState { PAGE_COUNT }
    val scope = rememberCoroutineScope()
    val isLast = pager.currentPage == PAGE_COUNT - 1

    BackHandler(enabled = pager.currentPage > 0) {
        scope.launch { pager.animateScrollToPage(pager.currentPage - 1) }
    }

    Column(Modifier.fillMaxSize().background(c.background).statusBarsPadding().navigationBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Logo()
            Spacer(Modifier.width(10.dp))
            Text("PyPath", style = MaterialTheme.typography.titleMedium, color = c.text)
            Spacer(Modifier.weight(1f))
            if (!isLast) {
                TextButton(onClick = { scope.launch { pager.animateScrollToPage(PAGE_COUNT - 1) } }) {
                    Text("Skip", style = MaterialTheme.typography.titleSmall, color = c.textMuted)
                }
            }
        }

        HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { page ->
            // Parallax-fade between pages
            val offset = ((pager.currentPage - page) + pager.currentPageOffsetFraction).absoluteValue
            Box(
                Modifier.fillMaxSize().graphicsLayer {
                    alpha = 1f - (offset * 0.6f).coerceIn(0f, 1f)
                    translationX = offset * 40f
                }
            ) {
                when (page) {
                    0 -> IntroPage()
                    1 -> PythonPage()
                    else -> JourneyPage(visible = pager.currentPage == 2)
                }
            }
        }

        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                repeat(PAGE_COUNT) { i ->
                    val selected = i == pager.currentPage
                    val w by animateDpAsState(if (selected) 26.dp else 8.dp, tween(300), label = "dot")
                    val col by animateColorAsState(if (selected) c.brand else c.border, label = "dotc")
                    Box(Modifier.padding(horizontal = 4.dp).height(8.dp).width(w).clip(CircleShape).background(col))
                }
            }
            Spacer(Modifier.height(20.dp))
            AnimatedContent(isLast, transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) }, label = "cta") { last ->
                AppButton(
                    text = if (last) "Start learning" else "Next",
                    trailing = Icons.AutoMirrored.Filled.ArrowForward,
                    onClick = { if (last) onFinish() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
fun Logo(size: Int = 32) {
    val c = AppTheme.colors
    Box(
        Modifier.size(size.dp).clip(RoundedCornerShape((size * 0.3).dp)).background(c.brand),
        contentAlignment = Alignment.Center,
    ) {
        // Batch 3 Part 3: the logo mark keeps its size with large fonts (12dp text, as at 100%) and is
        // decorative for TalkBack (the app name is next to it).
        val markSize = with(LocalDensity.current) { (size * 0.375f).dp.toSp() }
        Text(
            "</>", color = c.onBrand, style = MaterialTheme.typography.labelMedium.copy(fontSize = markSize, lineHeight = markSize * (16f / 12f)),
            fontWeight = FontWeight.ExtraBold, modifier = Modifier.clearAndSetSemantics { },
        )
        Box(Modifier.align(Alignment.BottomEnd).padding((size * 0.14).dp).size((size * 0.18).dp).clip(CircleShape).background(c.accent))
    }
}

@Composable
private fun PageScaffold(eyebrow: String, title: String, body: String, illustration: @Composable () -> Unit, extra: @Composable () -> Unit = {}) {
    val c = AppTheme.colors
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { illustration() }
        Spacer(Modifier.height(28.dp))
        Eyebrow(eyebrow)
        Spacer(Modifier.height(8.dp))
        Text(title, style = MaterialTheme.typography.headlineMedium, color = c.text)
        Spacer(Modifier.height(12.dp))
        Text(body, style = MaterialTheme.typography.bodyLarge, color = c.textMuted)
        Spacer(Modifier.height(20.dp))
        extra()
        Spacer(Modifier.height(16.dp))
    }
}

// ───────────── Page 1 ─────────────

@Composable
private fun IntroPage() {
    PageScaffold(
        eyebrow = "Welcome to PyPath",
        title = "Learn Python, one small step at a time",
        body = "PyPath teaches you Python from zero with short, focused lessons. The course is split into levels, and every topic ends with a few questions so you know you've really got it.",
        illustration = { IntroIllustration() },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            FeatureRow("1", "Step-by-step levels", "Start with the basics and build up.")
            FeatureRow("?", "Questions after every topic", "Quick MCQs lock in what you learned.")
            FeatureRow("{ }", "Practical coding", "Write and run real Python in the Practical tab. Projects come later.")
        }
    }
}

@Composable
private fun FeatureRow(mark: String, title: String, sub: String, soon: Boolean = false) {
    val c = AppTheme.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.surface)
            .border(1.dp, c.border, RoundedCornerShape(16.dp)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(if (soon) c.surfaceAlt else c.brandSoft),
            contentAlignment = Alignment.Center,
        ) { Text(mark, style = MaterialTheme.typography.titleSmall, color = if (soon) c.textFaint else c.brand, fontWeight = FontWeight.ExtraBold) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = c.text)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = c.textMuted)
        }
        if (soon) Pill("Soon", c.textMuted, c.surfaceAlt)
    }
}

@Composable
private fun IntroIllustration() {
    val c = AppTheme.colors
    Box(Modifier.size(width = 280.dp, height = 200.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(170.dp).clip(CircleShape)
                .background(Brush.radialGradient(listOf(c.brand.copy(alpha = 0.22f), Color.Transparent)))
        )
        // back card
        Box(
            Modifier.offset(x = 34.dp, y = (-18).dp).rotate(8f).size(150.dp, 104.dp)
                .clip(RoundedCornerShape(20.dp)).background(c.accentSoft).border(1.dp, c.border, RoundedCornerShape(20.dp))
        )
        // front card
        Column(
            Modifier.offset(x = (-12).dp, y = 8.dp).rotate(-4f).size(176.dp, 116.dp)
                .clip(RoundedCornerShape(20.dp)).background(c.surface).border(1.dp, c.border, RoundedCornerShape(20.dp))
                .padding(14.dp),
        ) {
            Text("LEVEL 1", style = MaterialTheme.typography.labelSmall, color = c.brand)
            Spacer(Modifier.height(4.dp))
            Text("Python Basics", style = MaterialTheme.typography.titleSmall, color = c.text)
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(4) { i ->
                    Box(Modifier.weight(1f).height(6.dp).clip(CircleShape).background(if (i < 2) c.brand else c.surfaceAlt))
                }
            }
        }
        // floating chip
        Row(
            Modifier.align(Alignment.BottomEnd).offset(x = (-6).dp, y = (-10).dp)
                .clip(CircleShape).background(c.success).padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Check, null, tint = c.onSuccess, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text("Correct!", style = MaterialTheme.typography.labelMedium, color = c.onSuccess)
        }
        Box(
            Modifier.align(Alignment.TopStart).offset(x = 12.dp, y = 16.dp).size(36.dp).clip(RoundedCornerShape(12.dp)).background(c.brand),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.Star, null, tint = c.accent, modifier = Modifier.size(20.dp)) }
    }
}

// ───────────── Page 2 ─────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PythonPage() {
    val c = AppTheme.colors
    PageScaffold(
        eyebrow = "Meet Python",
        title = "A language that reads like English",
        body = "Python is one of the world's most popular programming languages. Its clean, simple syntax makes it the perfect first language — you focus on ideas, not confusing symbols.",
        illustration = {
            CodeBlock(
                code = "name = \"Learner\"\nprint(f\"Hello, {name}!\")",
                output = "Hello, Learner!",
                modifier = Modifier.padding(top = 8.dp),
            )
        },
    ) {
        Text("What people build with Python", style = MaterialTheme.typography.titleSmall, color = c.text)
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Websites", "Data analysis", "AI & ML", "Automation", "Games", "Apps").forEach {
                Pill(it, c.brand, c.brandSoft)
            }
        }
        Spacer(Modifier.height(22.dp))
        Text("What you'll learn in this course", style = MaterialTheme.typography.titleSmall, color = c.text)
        Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                "Python basics: variables, data types, input & output",
                "Making decisions with conditions",
                "Loops, functions and lists",
            ).forEach {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Filled.CheckCircle, null, tint = c.success, modifier = Modifier.size(18.dp).padding(top = 2.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
                }
            }
        }
    }
}

// ───────────── Page 3 ─────────────

private data class JourneyStep(val title: String, val sub: String, val live: Boolean)

@Composable
private fun JourneyPage(visible: Boolean) {
    val c = AppTheme.colors
    val steps = listOf(
        JourneyStep("Learn", "Short, visual lessons with code examples", true),
        JourneyStep("MCQs", "Check your understanding instantly", true),
        JourneyStep("Practice", "Write real Python code", true),
        JourneyStep("Projects", "Build something on your own", false),
        JourneyStep("Complete Levels", "Move on to the next challenge", false),
    )
    var shown by remember { mutableStateOf(0) }
    LaunchedEffect(visible) {
        if (visible) for (i in shown until steps.size) { delay(110); shown = i + 1 }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
        Spacer(Modifier.height(8.dp))
        Eyebrow("Your learning journey")
        Spacer(Modifier.height(8.dp))
        Text("Every sub-level follows the same path", style = MaterialTheme.typography.headlineMedium, color = c.text)
        Spacer(Modifier.height(10.dp))
        Text(
            "Learn, MCQs and Practical coding are live. Projects arrive in a future update — your progress will carry over.",
            style = MaterialTheme.typography.bodyLarge, color = c.textMuted,
        )
        Spacer(Modifier.height(24.dp))
        steps.forEachIndexed { i, step ->
            val a by animateFloatAsState(if (i < shown) 1f else 0f, tween(350), label = "js")
            Row(Modifier.alpha(a).graphicsLayer { translationY = (1 - a) * 24f }) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).background(if (step.live) c.brand else c.surfaceAlt),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (step.live) Text("${i + 1}", style = MaterialTheme.typography.titleSmall, color = c.onBrand, fontWeight = FontWeight.ExtraBold)
                        else Icon(Icons.Filled.Lock, null, tint = c.locked, modifier = Modifier.size(17.dp))
                    }
                    if (i < steps.lastIndex) {
                        Box(Modifier.width(2.dp).height(30.dp).background(if (step.live && steps[i + 1].live) c.brand else c.border))
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f).padding(top = 2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(step.title, style = MaterialTheme.typography.titleMedium, color = if (step.live) c.text else c.textMuted)
                        Spacer(Modifier.width(8.dp))
                        if (step.live) Pill("Live", c.success, c.successSoft) else Pill("Coming soon", c.textFaint, c.surfaceAlt)
                    }
                    Text(step.sub, style = MaterialTheme.typography.bodySmall, color = c.textMuted)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}
