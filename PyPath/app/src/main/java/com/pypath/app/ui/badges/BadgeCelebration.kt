package com.pypath.app.ui.badges

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pypath.app.audio.LocalSounds
import com.pypath.app.audio.Sfx
import com.pypath.app.ui.components.AppButton
import com.pypath.app.ui.components.ButtonKind
import com.pypath.app.ui.components.rememberReducedMotion
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

private val Scrim = Color(0xF0101426)
private val Party = listOf(Color(0xFF3D5AFE), Color(0xFFFFC83D), Color(0xFF22C55E), Color(0xFFFF5C8A), Color(0xFF00C2FF), Color(0xFFFF8A3D))

/**
 * Full-screen level-complete celebration. Every level has its own animation and sound:
 * L1 spin + shine, L2 confetti burst, L3 expanding rings with stars, L4 bounce drop with sparkles,
 * L5 fireworks, L6 ribbon unroll with orbiting sparkles, L7 confetti + fireworks + glowing crown
 * with a sparkle trail. With reduced motion it becomes a short fade/scale with no particles.
 */
@Composable
fun BadgeCelebration(levelNumber: Int, onContinue: () -> Unit) {
    val spec = Badges.forLevel(levelNumber)
    val reduced = rememberReducedMotion()
    val sounds = LocalSounds.current
    val t = remember { Animatable(0f) }
    var finished by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        Sfx.badge(levelNumber)?.let(sounds::play)
        t.animateTo(1f, tween(if (reduced) 600 else spec.durationMs, easing = LinearEasing))
        finished = true
    }
    BackHandler { if (finished) onContinue() }
    BadgeCelebrationFrame(levelNumber, t.value, reduced, finished, onContinue)
}

/** One frame of the celebration at progress [time] (0..1). Stateless, so it can be previewed. */
@Composable
internal fun BadgeCelebrationFrame(levelNumber: Int, time: Float, reduced: Boolean, finished: Boolean, onContinue: () -> Unit) {
    val spec = Badges.forLevel(levelNumber)
    val particles = remember(levelNumber) { Particles(levelNumber) }

    Box(
        Modifier.fillMaxSize().background(Scrim)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        if (!reduced) Canvas(Modifier.fillMaxSize()) { drawScreenEffect(levelNumber, time, particles) }

        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(Modifier.size(240.dp), contentAlignment = Alignment.Center) {
                if (!reduced) Canvas(Modifier.fillMaxSize()) { drawBadgeEffectBehind(levelNumber, time, particles) }
                Canvas(
                    Modifier.size(180.dp).graphicsLayer {
                        if (reduced) {
                            val s = 0.8f + 0.2f * easeOut(time)
                            scaleX = s; scaleY = s; alpha = time
                        } else badgeTransform(levelNumber, time, size.height)
                    },
                ) {
                    val shine = when (levelNumber) {
                        1 -> ((time - 0.55f) / 0.4f)
                        7 -> ((time - 0.7f) / 0.28f)
                        else -> -1f
                    }
                    drawBadge(spec, earned = true, shine = if (reduced) -1f else shine)
                }
                if (!reduced) Canvas(Modifier.fillMaxSize()) { drawBadgeEffectFront(levelNumber, time, particles) }
            }
            Spacer(Modifier.height(28.dp))
            val textIn = if (reduced) 1f else seg(time, 0.6f, 0.75f)
            Column(
                Modifier.graphicsLayer { alpha = textIn; translationY = (1 - textIn) * 24.dp.toPx() },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                    Text("LEVEL $levelNumber COMPLETE", style = MaterialTheme.typography.labelLarge, color = spec.light)
                    Spacer(Modifier.height(8.dp))
                    Text(spec.name, style = MaterialTheme.typography.headlineMedium, color = Color.White, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(6.dp))
                    Text("Badge earned", style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.75f))
                    Spacer(Modifier.height(28.dp))
                    AppButton("Continue", onContinue, Modifier.fillMaxWidth(), kind = ButtonKind.Success, enabled = finished)
            }
        }
    }
}

// ───────────── easing helpers ─────────────

private fun seg(t: Float, from: Float, to: Float) = ((t - from) / (to - from)).coerceIn(0f, 1f)
private fun easeOut(x: Float) = 1f - (1f - x) * (1f - x) * (1f - x)
private fun easeOutBack(x: Float): Float {
    val c1 = 1.70158f; val c3 = c1 + 1
    return 1 + c3 * (x - 1) * (x - 1) * (x - 1) + c1 * (x - 1) * (x - 1)
}
private fun bounce(x: Float): Float {
    val n1 = 7.5625f; val d1 = 2.75f
    return when {
        x < 1 / d1 -> n1 * x * x
        x < 2 / d1 -> { val y = x - 1.5f / d1; n1 * y * y + 0.75f }
        x < 2.5 / d1 -> { val y = x - 2.25f / d1; n1 * y * y + 0.9375f }
        else -> { val y = x - 2.625f / d1; n1 * y * y + 0.984375f }
    }
}

private fun androidx.compose.ui.graphics.GraphicsLayerScope.badgeTransform(level: Int, t: Float, h: Float) {
    when (level) {
        1 -> { // spin
            cameraDistance = 14f * density
            rotationY = 720f * easeOut(seg(t, 0f, 0.55f))
            val s = 0.55f + 0.45f * easeOut(seg(t, 0f, 0.35f)); scaleX = s; scaleY = s
        }
        2 -> { val s = easeOutBack(seg(t, 0.05f, 0.3f)); scaleX = s; scaleY = s }
        3 -> { val s = easeOutBack(seg(t, 0f, 0.3f)); scaleX = s; scaleY = s; rotationZ = -20f * (1 - seg(t, 0f, 0.3f)) }
        4 -> { // bounce drop from above
            translationY = -h * 2.2f * (1 - bounce(seg(t, 0f, 0.6f)))
            alpha = seg(t, 0f, 0.05f)
        }
        5 -> { val s = easeOutBack(seg(t, 0.18f, 0.42f)); scaleX = s; scaleY = s; alpha = seg(t, 0.18f, 0.25f) }
        6 -> { val s = easeOutBack(seg(t, 0f, 0.28f)); scaleX = s; scaleY = s }
        else -> { // crown: scale up past full size, settle, gentle pulse
            val up = easeOutBack(seg(t, 0.25f, 0.6f))
            val pulse = 1f + 0.03f * sin(t * 2 * PI.toFloat() * 3) * seg(t, 0.6f, 0.7f)
            val s = up * 1.0f * pulse; scaleX = s; scaleY = s; alpha = seg(t, 0.25f, 0.32f)
        }
    }
}

// ───────────── particles (deterministic per level) ─────────────

private class Particles(level: Int) {
    private val rnd = Random(level * 7919)
    /** Confetti: x (0..1), start delay, speed, sway, rotation speed, colour index, angle for bursts. */
    val confetti = List(90) { floatArrayOf(rnd.nextFloat(), rnd.nextFloat() * 0.25f, 0.6f + rnd.nextFloat() * 0.6f, rnd.nextFloat() * 6f, rnd.nextFloat() * 720f - 360f, rnd.nextInt(Party.size).toFloat(), rnd.nextFloat() * 2 * PI.toFloat()) }
    /** Fireworks: x, y (0..1 of screen), launch time, colour index. */
    val rockets = List(6) { i -> floatArrayOf(0.2f + rnd.nextFloat() * 0.6f, 0.12f + rnd.nextFloat() * 0.3f, 0.04f + i * 0.11f, rnd.nextInt(Party.size).toFloat()) }
    /** Stars/sparkles: angle, distance (0..1), phase, size. */
    val sparkles = List(18) { floatArrayOf(rnd.nextFloat() * 2 * PI.toFloat(), 0.55f + rnd.nextFloat() * 0.45f, rnd.nextFloat(), 0.5f + rnd.nextFloat() * 0.5f) }
}

private fun DrawScope.sparkle(c: Offset, size: Float, color: Color, alpha: Float) {
    if (alpha <= 0f) return
    drawPath(starPath(c, size, size * 0.28f, points = 4, rotationDeg = 0f), color.copy(alpha = alpha.coerceIn(0f, 1f)))
}

private fun DrawScope.confettiBurst(t: Float, p: Particles, origin: Offset, start: Float) {
    val k = seg(t, start, start + 0.75f)
    if (k <= 0f || k >= 1f) return
    p.confetti.forEach { c ->
        val speed = c[2] * min(size.width, size.height) * 0.75f
        val x = origin.x + cos(c[6]) * speed * easeOut(k)
        val y = origin.y + sin(c[6]) * speed * easeOut(k) + 900f * k * k * density / 3f
        rotate(c[4] * k, Offset(x, y)) {
            drawRect(Party[c[5].toInt()].copy(alpha = 1f - k * k), Offset(x - 5 * density, y - 3 * density), Size(10 * density, 6 * density))
        }
    }
}

private fun DrawScope.confettiRain(t: Float, p: Particles, start: Float) {
    p.confetti.forEach { c ->
        val k = seg(t, start + c[1], start + c[1] + 0.7f / c[2])
        if (k <= 0f || k >= 1f) return@forEach
        val x = c[0] * size.width + sin(k * c[3] + c[6]) * 18 * density
        val y = -20 * density + k * (size.height + 40 * density)
        rotate(c[4] * k, Offset(x, y)) {
            drawRect(Party[c[5].toInt()], Offset(x - 5 * density, y - 3 * density), Size(10 * density, 6 * density))
        }
    }
}

private fun DrawScope.fireworks(t: Float, p: Particles, span: Float) {
    p.rockets.forEach { r ->
        val launch = r[2] * span
        val target = Offset(r[0] * size.width, r[1] * size.height)
        val color = Party[r[3].toInt()]
        val rise = seg(t, launch, launch + 0.12f)
        if (rise in 0.001f..0.999f) {
            val y = size.height + (target.y - size.height) * easeOut(rise)
            drawCircle(Color.White, 3 * density, Offset(target.x, y))
            drawLine(color.copy(alpha = 0.5f), Offset(target.x, y), Offset(target.x, y + 30 * density), 2 * density)
        }
        val boom = seg(t, launch + 0.12f, launch + 0.45f)
        if (boom > 0f && boom < 1f) {
            val fall = 30 * density * boom * boom
            for (i in 0 until 24) {
                val a = 2 * PI * i / 24
                val d = 90 * density * easeOut(boom)
                val dir = Offset(cos(a).toFloat(), sin(a).toFloat())
                val tip = Offset(target.x + dir.x * d, target.y + dir.y * d + fall)
                val tail = Offset(target.x + dir.x * d * 0.65f, target.y + dir.y * d * 0.65f + fall)
                drawLine(color.copy(alpha = 1f - boom), tail, tip, 2.6f * density, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                drawCircle(Color.White.copy(alpha = (1f - boom) * 0.8f), 1.6f * density, tip)
            }
            if (boom < 0.25f) drawCircle(Color.White.copy(alpha = 0.7f * (1 - boom * 4)), 10 * density, target)
        }
    }
}

private fun DrawScope.drawScreenEffect(level: Int, t: Float, p: Particles) {
    val mid = Offset(size.width / 2, size.height * 0.42f)
    when (level) {
        2 -> confettiBurst(t, p, mid, 0.08f)
        5 -> fireworks(t, p, 1f)
        7 -> { confettiRain(t, p, 0.35f); fireworks(t, p, 0.85f) }
        else -> {}
    }
}

private fun DrawScope.drawBadgeEffectBehind(level: Int, t: Float, p: Particles) {
    val c = center
    val r = min(size.width, size.height) / 2
    when (level) {
        3 -> for (i in 0 until 3) { // expanding rings
            val k = seg(t, 0.1f + i * 0.18f, 0.65f + i * 0.18f)
            if (k > 0f && k < 1f) drawCircle(Color(0xFF3EDDC6).copy(alpha = 1 - k), r * (0.5f + 0.9f * k), c, style = Stroke(4 * density * (1 - k) + 1))
        }
        7 -> { // glow
            val g = seg(t, 0.25f, 0.55f) * (0.85f + 0.15f * sin(t * 2 * PI.toFloat() * 4))
            drawCircle(Brush.radialGradient(listOf(Color(0xFFFFE07A).copy(alpha = 0.75f * g), Color.Transparent), c, r * 1.1f), r * 1.1f, c)
        }
        else -> {}
    }
}

private fun DrawScope.drawBadgeEffectFront(level: Int, t: Float, p: Particles) {
    val c = center
    val r = min(size.width, size.height) / 2
    when (level) {
        1 -> { // little glints when the spin ends
            val k = seg(t, 0.55f, 0.95f)
            p.sparkles.take(4).forEachIndexed { i, s ->
                val pos = Offset(c.x + cos(s[0]) * r * 0.75f, c.y + sin(s[0]) * r * 0.75f)
                sparkle(pos, 9 * density * s[3], Color.White, sin(PI.toFloat() * seg(k, i * 0.15f, i * 0.15f + 0.5f)))
            }
        }
        3 -> p.sparkles.forEach { s -> // stars pop on the rings
            val k = seg(t, 0.3f + s[2] * 0.4f, 0.55f + s[2] * 0.4f)
            val pos = Offset(c.x + cos(s[0]) * r * s[1] * 1.05f, c.y + sin(s[0]) * r * s[1] * 1.05f)
            if (k > 0f && k < 1f) drawPath(starPath(pos, 8 * density * s[3], 3.5f * density * s[3]), Color(0xFFFFE07A).copy(alpha = sin(PI.toFloat() * k)))
        }
        4 -> { // sparkles on every impact
            listOf(0.218f, 0.436f, 0.545f).forEachIndexed { hit, at ->
                val k = seg(t, at * 0.6f / 0.6f, at + 0.25f)
                if (k > 0f && k < 1f) p.sparkles.take(8 - hit * 2).forEach { s ->
                    val d = r * (0.6f + 0.5f * easeOut(k))
                    val pos = Offset(c.x + cos(s[0]) * d, c.y + r * 0.5f + sin(s[0]) * d * 0.4f)
                    sparkle(pos, 8 * density * s[3], Color(0xFFD9CCFF), 1 - k)
                }
            }
        }
        6 -> { // ribbon + sparkles orbiting on an ellipse
            drawRibbon(t, c, r)
            val k = seg(t, 0.15f, 1f)
            if (k > 0f) for (i in 0 until 6) {
                val a = 2 * PI.toFloat() * (i / 6f + k * 1.5f)
                val pos = Offset(c.x + cos(a) * r * 0.95f, c.y + sin(a) * r * 0.45f - r * 0.1f)
                sparkle(pos, 7 * density, Color.White, seg(t, 0.15f, 0.3f))
            }
        }
        7 -> { // sparkle trail spiralling into the crown
            val k = seg(t, 0f, 0.4f)
            if (k > 0f && k < 1f) for (j in 0 until 14) {
                val kk = (k - j * 0.025f).coerceIn(0f, 1f)
                if (kk <= 0f) continue
                val ang = 4 * PI.toFloat() * kk
                val d = r * 1.6f * (1 - kk)
                val pos = Offset(c.x + cos(ang) * d, c.y + sin(ang) * d)
                sparkle(pos, (9 - j * 0.5f) * density, Color(0xFFFFE07A), 1f - j / 14f)
            }
            val after = seg(t, 0.6f, 1f)
            if (after > 0f) p.sparkles.take(6).forEach { s ->
                val pos = Offset(c.x + cos(s[0]) * r * 0.85f, c.y + sin(s[0]) * r * 0.85f)
                sparkle(pos, 8 * density * s[3], Color.White, sin(PI.toFloat() * ((after * 2 + s[2]) % 1f)))
            }
        }
        else -> {}
    }
}

private fun DrawScope.drawRibbon(t: Float, c: Offset, r: Float) {
    val k = easeOut(seg(t, 0.2f, 0.6f))
    if (k <= 0f) return
    val w = r * 1.7f * k
    val top = c.y + r * 0.46f
    val h = r * 0.26f
    val tailColor = Color(0xFF8E1745)
    drawRect(tailColor, Offset(c.x - w / 2 - h * 0.6f, top + h * 0.3f), Size(h * 0.8f, h))
    drawRect(tailColor, Offset(c.x + w / 2 - h * 0.2f, top + h * 0.3f), Size(h * 0.8f, h))
    drawRect(Brush.horizontalGradient(listOf(Color(0xFFF37AA3), Color(0xFFB91F57)), startX = c.x - w / 2, endX = c.x + w / 2), Offset(c.x - w / 2, top), Size(w, h))
}
