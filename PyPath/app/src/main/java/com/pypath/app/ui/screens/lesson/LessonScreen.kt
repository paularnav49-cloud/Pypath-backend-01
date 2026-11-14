package com.pypath.app.ui.screens.lesson

import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pypath.app.data.model.LessonBlock
import com.pypath.app.data.model.LessonPage
import com.pypath.app.domain.SubLevelState
import com.pypath.app.ui.components.AppButton
import com.pypath.app.ui.components.AppTopBar
import com.pypath.app.ui.components.ButtonKind
import com.pypath.app.ui.components.CodeBlock
import com.pypath.app.ui.components.Eyebrow
import com.pypath.app.ui.components.ProgressBar
import com.pypath.app.ui.theme.AppTheme

@Composable
fun LessonScreen(
    state: SubLevelState?,
    onClose: () -> Unit,
    onFinished: () -> Unit,
    /** Batch 3: a page was read to the end (Next or Continue pressed). Counts for the daily streak. */
    onPageDone: () -> Unit = {},
) {
    val c = AppTheme.colors
    val pages = state?.subLevel?.lesson?.pages.orEmpty()
    if (state == null || pages.isEmpty()) {
        Box(Modifier.fillMaxSize().background(c.background), contentAlignment = Alignment.Center) {
            Text("Lesson content is coming soon.", color = c.textMuted)
        }
        return
    }
    var index by rememberSaveable { mutableIntStateOf(0) }
    val last = index == pages.lastIndex
    BackHandler(enabled = index > 0) { index-- }

    // Batch 3 Part 3: on tablets the content is centred with a max width of 640dp; phones are unchanged.
    Box(Modifier.fillMaxSize().background(c.background), contentAlignment = Alignment.TopCenter) {
    Column(Modifier.fillMaxHeight().widthIn(max = 640.dp).fillMaxWidth()) {
        AppTopBar(
            title = "${state.subLevel.code} ${state.subLevel.title}",
            subtitle = "Step 1 · Learn",
            onBack = onClose,
            backIcon = Icons.Filled.Close,
        )
        Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            ProgressBar((index + 1f) / pages.size, Modifier.weight(1f), height = 6.dp)
            Spacer(Modifier.width(12.dp))
            Text("${index + 1}/${pages.size}", style = MaterialTheme.typography.bodySmall, color = c.textMuted)
        }

        AnimatedContent(
            targetState = index,
            transitionSpec = {
                val dir = if (targetState > initialState) 1 else -1
                (slideInHorizontally(tween(300)) { it / 6 * dir } + fadeIn(tween(300))) togetherWith
                    (slideOutHorizontally(tween(250)) { -it / 6 * dir } + fadeOut(tween(200)))
            },
            modifier = Modifier.weight(1f),
            label = "lessonPage",
        ) { i ->
            LessonPageView(pages[i])
        }

        Row(
            Modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (index > 0) {
                AppButton("", { index-- }, Modifier.width(64.dp), kind = ButtonKind.Secondary, leading = Icons.AutoMirrored.Filled.ArrowBack)
            }
            AppButton(
                text = if (last) "Continue to MCQs" else "Next",
                onClick = {
                    onPageDone()
                    if (last) onFinished() else index++
                },
                modifier = Modifier.weight(1f),
                trailing = Icons.AutoMirrored.Filled.ArrowForward,
            )
        }
    }
    }
}

@Composable
private fun LessonPageView(page: LessonPage) {
    val c = AppTheme.colors
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(page.title, style = MaterialTheme.typography.headlineSmall, color = c.text, modifier = Modifier.semantics { heading() })
        page.blocks.forEach { LessonBlockView(it) }
    }
}

/** Renders a single content block. Add new block types here as the content model grows. */
@Composable
fun LessonBlockView(block: LessonBlock) {
    val c = AppTheme.colors
    when (block) {
        is LessonBlock.Text -> Text(block.text, style = MaterialTheme.typography.bodyLarge, color = c.text.copy(alpha = 0.88f))

        is LessonBlock.Bullets -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            block.items.forEach {
                Row {
                    Box(Modifier.padding(top = 9.dp).size(7.dp).clip(CircleShape).background(c.brand))
                    Spacer(Modifier.width(12.dp))
                    Text(it, style = MaterialTheme.typography.bodyLarge, color = c.text.copy(alpha = 0.88f))
                }
            }
        }

        is LessonBlock.Code -> Column {
            block.caption?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = c.textMuted); Spacer(Modifier.height(6.dp)) }
            CodeBlock(block.code, output = block.output)
            if (block.explanation.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.surfaceAlt).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Eyebrow("What's happening", color = c.textMuted)
                    block.explanation.forEachIndexed { i, line ->
                        Row {
                            Text("${i + 1}", style = MaterialTheme.typography.labelMedium, color = c.brand, fontWeight = FontWeight.ExtraBold, modifier = Modifier.width(18.dp).padding(top = 2.dp))
                            Text(line, style = MaterialTheme.typography.bodyMedium, color = c.text.copy(alpha = 0.85f))
                        }
                    }
                }
            }
        }

        is LessonBlock.Tip -> Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.accentSoft).padding(14.dp),
        ) {
            Icon(Icons.Filled.Star, null, tint = c.accent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column {
                Text(block.title, style = MaterialTheme.typography.titleSmall, color = c.text)
                Text(block.text, style = MaterialTheme.typography.bodyMedium, color = c.text.copy(alpha = 0.8f))
            }
        }

        is LessonBlock.KeyPoint -> Row(
            Modifier.fillMaxWidth().height(IntrinsicSize.Min).clip(RoundedCornerShape(16.dp)).background(c.brandSoft).padding(14.dp),
        ) {
            Box(Modifier.width(3.dp).fillMaxHeight().clip(CircleShape).background(c.brand))
            Spacer(Modifier.width(12.dp))
            Column {
                Eyebrow("Key point")
                Spacer(Modifier.height(2.dp))
                Text(block.text, style = MaterialTheme.typography.bodyMedium, color = c.text, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
