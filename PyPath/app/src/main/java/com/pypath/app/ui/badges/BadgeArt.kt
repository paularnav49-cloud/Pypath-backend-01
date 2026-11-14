package com.pypath.app.ui.badges

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** One badge per level. Shapes and colours are drawn in code; nothing is downloaded. */
data class BadgeSpec(
    val levelNumber: Int,
    val name: String,
    val shape: String,
    val light: Color,
    val dark: Color,
    val rim: Color,
    val emblem: Color,
    /** Length of the celebration animation (2-4 s). */
    val durationMs: Int,
)

object Badges {
    val all = listOf(
        BadgeSpec(1, "Bronze Starter", "hexagon", Color(0xFFE9A46A), Color(0xFF9C5426), Color(0xFFF8D2AE), Color.White, 2400),
        BadgeSpec(2, "Silver Decision Maker", "shield", Color(0xFFEEF2F7), Color(0xFF8D99AE), Color.White, Color(0xFF35425C), 2600),
        BadgeSpec(3, "Loop Master", "circular arrows", Color(0xFF3EDDC6), Color(0xFF0E7A6E), Color(0xFFA7F3E6), Color.White, 2800),
        BadgeSpec(4, "Function Builder", "cog", Color(0xFFA78BFF), Color(0xFF5634D1), Color(0xFFD9CCFF), Color.White, 2600),
        BadgeSpec(5, "List Hero", "stack of bars", Color(0xFFFFA463), Color(0xFFE2550A), Color(0xFFFFD3B0), Color.White, 3200),
        BadgeSpec(6, "Dictionary Pro", "key", Color(0xFFF37AA3), Color(0xFFB91F57), Color(0xFFFBC4D7), Color.White, 3000),
        BadgeSpec(7, "Python Graduate", "crown", Color(0xFFFFE07A), Color(0xFFE09A00), Color(0xFFFFF3C4), Color(0xFF7A4A00), 3800),
    )

    fun forLevel(number: Int): BadgeSpec = all.getOrNull(number - 1) ?: all.last()
}

private val LockedLight = Color(0xFFD5D9E1)
private val LockedDark = Color(0xFF9BA2B0)

/** Small or large static badge. Locked badges are grey. */
@Composable
fun BadgeIcon(levelNumber: Int, earned: Boolean, modifier: Modifier = Modifier, size: Dp = 48.dp) {
    val spec = Badges.forLevel(levelNumber)
    Canvas(
        modifier.size(size).semantics { contentDescription = if (earned) "${spec.name} badge, earned" else "${spec.name} badge, locked" },
    ) { drawBadge(spec, earned) }
}

/** Draws the badge centred in the current draw area. [shine] in 0..1 sweeps a highlight across it. */
fun DrawScope.drawBadge(spec: BadgeSpec, earned: Boolean, shine: Float = -1f) {
    val r = min(size.width, size.height) * 0.46f
    val c = center
    val light = if (earned) spec.light else LockedLight
    val dark = if (earned) spec.dark else LockedDark
    val rim = if (earned) spec.rim else Color(0xFFE9ECF1)
    val emblem = if (earned) spec.emblem else Color.White.copy(alpha = 0.85f)
    val body = bodyPath(spec.levelNumber, c, r)
    val fill = Brush.linearGradient(listOf(light, dark), start = Offset(c.x - r, c.y - r), end = Offset(c.x + r, c.y + r))

    if (spec.levelNumber == 5) {
        drawRoundRect(fill, Offset(c.x - r * 0.9f, c.y - r * 0.9f), Size(r * 1.8f, r * 1.8f), CornerRadius(r * 0.38f))
        drawRoundRect(rim, Offset(c.x - r * 0.9f, c.y - r * 0.9f), Size(r * 1.8f, r * 1.8f), CornerRadius(r * 0.38f), style = Stroke(r * 0.07f))
    } else {
        drawPath(body, fill)
        drawPath(body, rim, style = Stroke(r * 0.07f))
    }
    drawEmblem(spec.levelNumber, c, r, emblem, jewel = if (earned) Color(0xFFE5484D) else Color.White.copy(alpha = 0.7f))

    if (shine in 0f..1f) {
        clipPath(body) {
            val x = c.x - r * 1.6f + shine * r * 3.2f
            drawRect(
                Brush.linearGradient(
                    listOf(Color.Transparent, Color.White.copy(alpha = 0.55f), Color.Transparent),
                    start = Offset(x - r * 0.35f, c.y - r), end = Offset(x + r * 0.35f, c.y + r),
                ),
            )
        }
    }
}

private fun polygon(c: Offset, radius: Float, points: Int, startDeg: Float = -90f): Path = Path().apply {
    for (i in 0 until points) {
        val a = Math.toRadians((startDeg + 360f * i / points).toDouble())
        val p = Offset(c.x + radius * cos(a).toFloat(), c.y + radius * sin(a).toFloat())
        if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
    }
    close()
}

private fun circlePath(c: Offset, radius: Float): Path = Path().apply {
    addOval(androidx.compose.ui.geometry.Rect(c, radius))
}

/** Five- or four-point star, also used by the celebration effects. */
fun starPath(c: Offset, outer: Float, inner: Float, points: Int = 5, rotationDeg: Float = -90f): Path = Path().apply {
    for (i in 0 until points * 2) {
        val rad = if (i % 2 == 0) outer else inner
        val a = Math.toRadians((rotationDeg + 180f * i / points).toDouble())
        val p = Offset(c.x + rad * cos(a).toFloat(), c.y + rad * sin(a).toFloat())
        if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
    }
    close()
}

private fun bodyPath(level: Int, c: Offset, r: Float): Path = when (level) {
    1 -> polygon(c, r, 6)
    2 -> Path().apply {
        val w = r * 0.86f
        val top = c.y - r * 0.92f
        moveTo(c.x - w, top + r * 0.1f)
        quadraticTo(c.x, top - r * 0.12f, c.x + w, top + r * 0.1f)
        lineTo(c.x + w, c.y + r * 0.05f)
        cubicTo(c.x + w, c.y + r * 0.6f, c.x + r * 0.35f, c.y + r * 0.85f, c.x, c.y + r)
        cubicTo(c.x - r * 0.35f, c.y + r * 0.85f, c.x - w, c.y + r * 0.6f, c.x - w, c.y + r * 0.05f)
        close()
    }
    4 -> Path().apply {
        val teeth = 10
        for (k in 0 until teeth * 2) {
            val rad = if (k % 2 == 0) r else r * 0.8f
            val a0 = Math.toRadians((360.0 / (teeth * 2)) * k - 90 + 3)
            val a1 = Math.toRadians((360.0 / (teeth * 2)) * (k + 1) - 90 - 3)
            val p0 = Offset(c.x + rad * cos(a0).toFloat(), c.y + rad * sin(a0).toFloat())
            val p1 = Offset(c.x + rad * cos(a1).toFloat(), c.y + rad * sin(a1).toFloat())
            if (k == 0) moveTo(p0.x, p0.y) else lineTo(p0.x, p0.y)
            lineTo(p1.x, p1.y)
        }
        close()
    }
    5 -> Path().apply {
        addRoundRect(androidx.compose.ui.geometry.RoundRect(c.x - r * 0.9f, c.y - r * 0.9f, c.x + r * 0.9f, c.y + r * 0.9f, CornerRadius(r * 0.38f)))
    }
    6 -> { // scalloped rosette: one outline made from a disc plus 12 bumps
        var rosette = circlePath(c, r * 0.84f)
        for (i in 0 until 12) {
            val a = 2 * PI * i / 12
            val bump = circlePath(Offset(c.x + r * 0.8f * cos(a).toFloat(), c.y + r * 0.8f * sin(a).toFloat()), r * 0.2f)
            rosette = Path().apply { op(rosette, bump, PathOperation.Union) }
        }
        rosette
    }
    7 -> starPath(c, r, r * 0.82f, points = 16)
    else -> circlePath(c, r)
}

private fun DrawScope.drawEmblem(level: Int, c: Offset, r: Float, color: Color, jewel: Color) {
    val w = r * 0.13f
    when (level) {
        1 -> drawPath(starPath(c, r * 0.48f, r * 0.2f), color)
        2 -> { // decision fork with two arrow heads
            val base = Offset(c.x, c.y + r * 0.45f)
            val mid = Offset(c.x, c.y)
            val left = Offset(c.x - r * 0.38f, c.y - r * 0.42f)
            val right = Offset(c.x + r * 0.38f, c.y - r * 0.42f)
            drawLine(color, base, mid, w, StrokeCap.Round)
            drawLine(color, mid, left, w, StrokeCap.Round)
            drawLine(color, mid, right, w, StrokeCap.Round)
            arrowHead(left, Offset(-1f, -1.1f), r * 0.2f, color)
            arrowHead(right, Offset(1f, -1.1f), r * 0.2f, color)
        }
        3 -> { // two circular arrows
            val rr = r * 0.5f
            val tl = Offset(c.x - rr, c.y - rr)
            drawArc(color, 200f, 130f, false, tl, Size(rr * 2, rr * 2), style = Stroke(w, cap = StrokeCap.Round))
            drawArc(color, 20f, 130f, false, tl, Size(rr * 2, rr * 2), style = Stroke(w, cap = StrokeCap.Round))
            for (endDeg in listOf(330f, 150f)) {
                val a = Math.toRadians(endDeg.toDouble())
                val p = Offset(c.x + rr * cos(a).toFloat(), c.y + rr * sin(a).toFloat())
                val tangent = Offset(-sin(a).toFloat(), cos(a).toFloat())
                arrowHead(p, tangent, r * 0.2f, color)
            }
        }
        4 -> {
            drawCircle(color, r * 0.36f, c, style = Stroke(w * 1.2f))
            drawCircle(color, r * 0.1f, c)
        }
        5 -> { // stack of bars
            val widths = listOf(1.0f, 0.72f, 0.46f)
            widths.forEachIndexed { i, f ->
                val bw = r * 1.1f * f
                val y = c.y - r * 0.42f + i * r * 0.32f
                drawRoundRect(color, Offset(c.x - r * 0.55f, y), Size(bw, r * 0.2f), CornerRadius(r * 0.1f))
            }
        }
        6 -> { // key
            val head = Offset(c.x - r * 0.28f, c.y - r * 0.18f)
            drawCircle(color, r * 0.22f, head, style = Stroke(w))
            val shaftStart = Offset(head.x + r * 0.16f, head.y + r * 0.16f)
            val shaftEnd = Offset(c.x + r * 0.42f, c.y + r * 0.42f)
            drawLine(color, shaftStart, shaftEnd, w, StrokeCap.Round)
            drawLine(color, Offset(c.x + r * 0.22f, c.y + r * 0.22f), Offset(c.x + r * 0.34f, c.y + r * 0.1f), w, StrokeCap.Round)
            drawLine(color, Offset(c.x + r * 0.34f, c.y + r * 0.34f), Offset(c.x + r * 0.46f, c.y + r * 0.22f), w, StrokeCap.Round)
        }
        7 -> { // crown
            val crown = Path().apply {
                moveTo(c.x - r * 0.48f, c.y + r * 0.28f)
                lineTo(c.x - r * 0.52f, c.y - r * 0.28f)
                lineTo(c.x - r * 0.22f, c.y - r * 0.02f)
                lineTo(c.x, c.y - r * 0.42f)
                lineTo(c.x + r * 0.22f, c.y - r * 0.02f)
                lineTo(c.x + r * 0.52f, c.y - r * 0.28f)
                lineTo(c.x + r * 0.48f, c.y + r * 0.28f)
                close()
            }
            drawPath(crown, Color(0xFFFFF7DA))
            drawPath(crown, color, style = Stroke(w * 0.6f))
            drawRoundRect(color, Offset(c.x - r * 0.5f, c.y + r * 0.3f), Size(r, r * 0.14f), CornerRadius(r * 0.05f))
            listOf(-0.52f to -0.28f, 0f to -0.42f, 0.52f to -0.28f).forEach { (dx, dy) ->
                drawCircle(jewel, r * 0.07f, Offset(c.x + r * dx, c.y + r * dy))
            }
        }
    }
}

private fun DrawScope.arrowHead(tip: Offset, direction: Offset, size: Float, color: Color) {
    val len = kotlin.math.sqrt(direction.x * direction.x + direction.y * direction.y)
    val d = Offset(direction.x / len, direction.y / len)
    val n = Offset(-d.y, d.x)
    val back = Offset(tip.x - d.x * size, tip.y - d.y * size)
    val path = Path().apply {
        moveTo(tip.x + d.x * size * 0.35f, tip.y + d.y * size * 0.35f)
        lineTo(back.x + n.x * size * 0.6f, back.y + n.y * size * 0.6f)
        lineTo(back.x - n.x * size * 0.6f, back.y - n.y * size * 0.6f)
        close()
    }
    drawPath(path, color)
}
