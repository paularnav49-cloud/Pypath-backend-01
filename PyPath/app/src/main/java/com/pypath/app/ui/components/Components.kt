package com.pypath.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pypath.app.domain.NodeStatus
import com.pypath.app.ui.theme.AppTheme
import com.pypath.app.ui.theme.CodeTextStyle
import com.pypath.app.ui.theme.Mono
import com.pypath.app.domain.A11y
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics

// ───────────── Buttons ─────────────

enum class ButtonKind { Primary, Secondary, Ghost, Success }

@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.Primary,
    enabled: Boolean = true,
    leading: ImageVector? = null,
    trailing: ImageVector? = null,
) {
    val c = AppTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && enabled) 0.97f else 1f, spring(stiffness = 600f), label = "press")
    val (bg, fg, border) = when {
        !enabled -> Triple(c.surfaceAlt, c.textFaint, Color.Transparent)
        kind == ButtonKind.Primary -> Triple(c.brand, c.onBrand, Color.Transparent)
        kind == ButtonKind.Success -> Triple(c.success, c.onSuccess, Color.Transparent)
        kind == ButtonKind.Secondary -> Triple(c.surface, c.text, c.border)
        else -> Triple(Color.Transparent, c.brand, Color.Transparent)
    }
    Row(
        modifier = modifier
            .scale(scale)
            // Batch 3 Part 3: grows with large fonts instead of clipping (54dp at normal size).
            .heightIn(min = 54.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(16.dp))
            .clickable(interactionSource = interaction, indication = androidx.compose.material3.ripple(), enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) { Icon(leading, null, tint = fg, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)) }
        Text(
            text, style = MaterialTheme.typography.labelLarge, color = fg, maxLines = 2, textAlign = TextAlign.Center,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false).padding(vertical = 6.dp),
        )
        if (trailing != null) { Spacer(Modifier.width(8.dp)); Icon(trailing, null, tint = fg, modifier = Modifier.size(20.dp)) }
    }
}

// ───────────── Surfaces ─────────────

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    background: Color = AppTheme.colors.surface,
    borderColor: Color = AppTheme.colors.border,
    padding: PaddingValues = PaddingValues(18.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier
            .clip(shape)
            .background(background)
            .border(1.dp, borderColor, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding),
        content = content,
    )
}

@Composable
fun Pill(text: String, color: Color, background: Color, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Row(
        modifier.clip(CircleShape).background(background).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { Icon(icon, null, tint = color, modifier = Modifier.size(13.dp)); Spacer(Modifier.width(4.dp)) }
        Text(text, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

@Composable
fun Eyebrow(text: String, color: Color = AppTheme.colors.brand) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = color)
}

// ───────────── Top bar ─────────────

@Composable
fun AppTopBar(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    backIcon: ImageVector = Icons.AutoMirrored.Filled.ArrowBack,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val c = AppTheme.colors
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) { Icon(backIcon, "Back", tint = c.text) }
        } else Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = c.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                title, style = MaterialTheme.typography.titleMedium, color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
        }
        actions()
    }
}

// ───────────── Progress ─────────────

@Composable
fun ProgressBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    height: Dp = 8.dp,
    color: Color = AppTheme.colors.brand,
    track: Color = AppTheme.colors.surfaceAlt,
) {
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(600), label = "bar")
    val value = fraction.coerceIn(0f, 1f)
    Box(
        modifier.height(height).clip(CircleShape).background(track)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(value, 0f..1f) },
    ) {
        Box(Modifier.fillMaxWidth(animated).height(height).clip(CircleShape).background(color))
    }
}

@Composable
fun ProgressRing(
    fraction: Float,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    stroke: Dp = 7.dp,
    color: Color = AppTheme.colors.brand,
    track: Color = AppTheme.colors.surfaceAlt,
    content: @Composable () -> Unit = {},
) {
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(900), label = "ring")
    val value = fraction.coerceIn(0f, 1f)
    Box(
        modifier.size(size).semantics(mergeDescendants = true) { progressBarRangeInfo = ProgressBarRangeInfo(value, 0f..1f) },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size).clearAndSetSemantics { }) {
            val s = stroke.toPx()
            val arc = Size(this.size.width - s, this.size.height - s)
            val tl = Offset(s / 2, s / 2)
            drawArc(track, 0f, 360f, false, tl, arc, style = Stroke(s))
            drawArc(color, -90f, 360f * animated, false, tl, arc, style = Stroke(s, cap = StrokeCap.Round))
        }
        content()
    }
}

/** Circular status marker used for levels and sub-levels. */
@Composable
fun StatusNode(status: NodeStatus, label: String, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    val c = AppTheme.colors
    val (bg, fg) = when (status) {
        NodeStatus.COMPLETED -> c.success to c.onSuccess
        NodeStatus.AVAILABLE, NodeStatus.IN_PROGRESS -> c.brand to c.onBrand
        NodeStatus.LOCKED, NodeStatus.COMING_SOON -> c.surfaceAlt to c.locked
    }
    Box(modifier.size(size).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
        when (status) {
            NodeStatus.COMPLETED -> Icon(Icons.Filled.Check, "Completed", tint = fg, modifier = Modifier.size(size * 0.48f))
            NodeStatus.LOCKED, NodeStatus.COMING_SOON -> Icon(Icons.Filled.Lock, "Locked", tint = fg, modifier = Modifier.size(size * 0.42f))
            NodeStatus.IN_PROGRESS -> Icon(Icons.Filled.PlayArrow, "In progress", tint = fg, modifier = Modifier.size(size * 0.5f))
            NodeStatus.AVAILABLE -> Text(label, style = MaterialTheme.typography.titleSmall, color = fg, fontWeight = FontWeight.ExtraBold)
        }
    }
}

// ───────────── Code ─────────────

@Composable
fun CodeBlock(code: String, modifier: Modifier = Modifier, output: String? = null, title: String = "main.py") {
    val c = AppTheme.colors.code
    val highlighted = remember(code) { PythonHighlighter.highlight(code, c) }
    val shape = RoundedCornerShape(16.dp)
    // Batch 3 Part 3: TalkBack reads the code line by line, with indentation spoken as "indented".
    val spoken = remember(code, output) { A11y.describeCode(code, output) }
    Column(
        modifier.fillMaxWidth().clip(shape).background(c.background)
            .clearAndSetSemantics { contentDescription = spoken },
    ) {
        Row(
            Modifier.fillMaxWidth().background(c.header).padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf(Color(0xFFFF5F57), Color(0xFFFEBC2E), Color(0xFF28C840)).forEach {
                Box(Modifier.size(9.dp).clip(CircleShape).background(it.copy(alpha = 0.85f)))
                Spacer(Modifier.width(6.dp))
            }
            Spacer(Modifier.width(6.dp))
            Text(title, style = MaterialTheme.typography.bodySmall.copy(fontFamily = Mono), color = c.comment)
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 14.dp)) {
            val lines = code.lines().size
            Text(
                (1..lines).joinToString("\n"), style = CodeTextStyle, color = c.comment, // Batch 3 Part 3: full colour for contrast
                textAlign = TextAlign.End, modifier = Modifier.width(18.dp),
            )
            Spacer(Modifier.width(14.dp))
            Text(highlighted, style = CodeTextStyle, softWrap = false)
        }
        if (output != null) {
            Column(Modifier.fillMaxWidth().background(c.outputBg).padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text("OUTPUT", style = MaterialTheme.typography.labelSmall, color = c.comment)
                Spacer(Modifier.height(6.dp))
                Text(output, style = CodeTextStyle, color = c.outputText, modifier = Modifier.horizontalScroll(rememberScrollState()), softWrap = false)
            }
        }
    }
}
