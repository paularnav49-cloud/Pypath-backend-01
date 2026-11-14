package com.pypath.app.ui.screens.certificate

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.res.ResourcesCompat
import com.pypath.app.R
import com.pypath.app.domain.CertificateInfo
import com.pypath.app.ui.badges.Badges
import java.text.DateFormat
import java.util.Date
import kotlin.math.cos
import kotlin.math.sin

/**
 * Draws the course certificate as a landscape PNG-ready bitmap, entirely on the phone
 * (android.graphics + the app's own fonts; no network, no extra libraries).
 */
object CertificateRenderer {
    const val WIDTH = 2000
    const val HEIGHT = 1414

    private const val INK = 0xFF1B2440.toInt()
    private const val MUTED = 0xFF5A6478.toInt()
    private const val PAPER = 0xFFFFFDF7.toInt()
    private const val PAPER_EDGE = 0xFFF6EFDC.toInt()
    private const val BRAND = 0xFF3D5AFE.toInt()
    private const val BRAND_DARK = 0xFF2536B8.toInt()
    private const val GOLD = 0xFFE09A00.toInt()
    private const val GOLD_LIGHT = 0xFFFFE07A.toInt()

    fun render(context: Context, info: CertificateInfo): Bitmap {
        val bmp = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val w = WIDTH.toFloat()
        val cx = w / 2f
        val fonts = Fonts(context)

        // Paper with a soft vignette.
        cv.drawColor(PAPER)
        val vignette = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(cx, HEIGHT / 2f, w * 0.75f, intArrayOf(PAPER, PAPER, PAPER_EDGE), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
        }
        cv.drawRect(0f, 0f, w, HEIGHT.toFloat(), vignette)

        // Frames: thick brand border, thin gold inner border, gold corner diamonds.
        val outer = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 18f
            shader = LinearGradient(0f, 0f, w, HEIGHT.toFloat(), BRAND, BRAND_DARK, Shader.TileMode.CLAMP)
        }
        cv.drawRect(RectF(44f, 44f, w - 44f, HEIGHT - 44f), outer)
        val inner = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 4f; color = GOLD }
        val ir = RectF(84f, 84f, w - 84f, HEIGHT - 84f)
        cv.drawRect(ir, inner)
        val gold = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = GOLD }
        for ((x, y) in listOf(ir.left to ir.top, ir.right to ir.top, ir.left to ir.bottom, ir.right to ir.bottom)) {
            diamond(cv, x, y, 22f, gold)
        }

        // Header.
        text(cv, "PyPath", cx, 232f, fonts.extraBold, 72f, BRAND)
        text(cv, "CERTIFICATE OF COMPLETION", cx, 340f, fonts.bold, 58f, INK, spacing = 0.18f)
        ornamentLine(cv, cx, 392f, 300f, gold)

        // Name.
        text(cv, "This certifies that", cx, 494f, fonts.medium, 42f, MUTED)
        val name = info.name.ifBlank { "Your Name" }
        val nameSize = fitSize(name, fonts.extraBold, maxWidth = 1500f, start = 128f, min = 56f)
        text(cv, name, cx, 650f, fonts.extraBold, nameSize, if (info.name.isBlank()) 0xFFB7BDC9.toInt() else INK)
        val underline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            strokeWidth = 4f
            shader = LinearGradient(cx - 560f, 0f, cx + 560f, 0f, intArrayOf(0x00E09A00, GOLD, 0x00E09A00), null, Shader.TileMode.CLAMP)
        }
        cv.drawLine(cx - 560f, 700f, cx + 560f, 700f, underline)

        // Course.
        text(cv, "has successfully completed the course", cx, 790f, fonts.medium, 42f, MUTED)
        text(cv, info.courseTitle, cx, 884f, fonts.bold, 70f, BRAND, maxWidth = 1500f)
        val stats = buildList {
            add("${info.levels} levels")
            add("${info.lessons} lessons")
            add("${info.projects} guided project${if (info.projects == 1) "" else "s"}")
            info.mcqAverage?.let { add("quiz average $it%") }
        }.joinToString("   •   ")
        text(cv, stats, cx, 966f, fonts.semiBold, 36f, MUTED, maxWidth = 1600f)

        // Badge row.
        val earned = info.badgeLevels.toSet()
        val gap = 150f
        val startX = cx - gap * (Badges.all.size - 1) / 2f
        Badges.all.forEachIndexed { i, spec ->
            badge(cv, startX + i * gap, 1086f, 52f, spec.levelNumber, spec.levelNumber in earned,
                spec.light.toArgb(), spec.dark.toArgb(), spec.rim.toArgb(), spec.emblem.toArgb(), fonts.extraBold)
        }

        // Footer: date (left), seal (centre), certificate id (right).
        val date = DateFormat.getDateInstance(DateFormat.LONG).format(Date(info.completedAt))
        footer(cv, 470f, 1226f, date, "Date completed", fonts)
        footer(cv, w - 470f, 1226f, info.id, "Certificate ID", fonts)
        seal(cv, cx, 1232f, 62f)
        return bmp
    }

    // ───────────── helpers ─────────────

    private class Fonts(private val context: Context) {
        private fun load(id: Int, fallback: Typeface) = runCatching { ResourcesCompat.getFont(context, id) }.getOrNull() ?: fallback
        val medium = load(R.font.jakarta_500, Typeface.DEFAULT)
        val semiBold = load(R.font.jakarta_600, Typeface.DEFAULT_BOLD)
        val bold = load(R.font.jakarta_700, Typeface.DEFAULT_BOLD)
        val extraBold = load(R.font.jakarta_800, Typeface.DEFAULT_BOLD)
    }

    private fun paint(face: Typeface, size: Float, color: Int, spacing: Float = 0f) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = face; textSize = size; this.color = color; textAlign = Paint.Align.CENTER; letterSpacing = spacing
    }

    /** Centred text; shrinks to [maxWidth] if needed. */
    private fun text(cv: Canvas, s: String, x: Float, y: Float, face: Typeface, size: Float, color: Int, spacing: Float = 0f, maxWidth: Float = 1700f) {
        val p = paint(face, size, color, spacing)
        while (p.measureText(s) > maxWidth && p.textSize > 20f) p.textSize -= 2f
        cv.drawText(s, x, y, p)
    }

    private fun fitSize(s: String, face: Typeface, maxWidth: Float, start: Float, min: Float): Float {
        val p = paint(face, start, 0)
        while (p.measureText(s) > maxWidth && p.textSize > min) p.textSize -= 2f
        return p.textSize
    }

    private fun diamond(cv: Canvas, x: Float, y: Float, r: Float, p: Paint) {
        val path = Path().apply { moveTo(x, y - r); lineTo(x + r, y); lineTo(x, y + r); lineTo(x - r, y); close() }
        cv.drawPath(path, p)
    }

    private fun ornamentLine(cv: Canvas, cx: Float, y: Float, half: Float, gold: Paint) {
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = GOLD; strokeWidth = 3f }
        cv.drawLine(cx - half, y, cx - 26f, y, line)
        cv.drawLine(cx + 26f, y, cx + half, y, line)
        diamond(cv, cx, y, 12f, gold)
    }

    private fun badge(
        cv: Canvas, x: Float, y: Float, r: Float, number: Int, earned: Boolean,
        light: Int, dark: Int, rim: Int, emblem: Int, face: Typeface,
    ) {
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = if (earned) RadialGradient(x - r * 0.35f, y - r * 0.35f, r * 1.6f, light, dark, Shader.TileMode.CLAMP) else null
            color = 0xFFE4E7EE.toInt()
        }
        cv.drawCircle(x, y, r, fill)
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 6f; color = if (earned) rim else 0xFFCBD0DA.toInt() }
        cv.drawCircle(x, y, r - 3f, ring)
        val label = paint(face, 46f, if (earned) emblem else 0xFF9AA1B0.toInt())
        cv.drawText("$number", x, y - (label.descent() + label.ascent()) / 2f, label)
    }

    private fun footer(cv: Canvas, x: Float, y: Float, value: String, caption: String, fonts: Fonts) {
        text(cv, value, x, y, fonts.bold, 38f, INK, maxWidth = 520f)
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFCBD0DA.toInt(); strokeWidth = 3f }
        cv.drawLine(x - 230f, y + 22f, x + 230f, y + 22f, line)
        text(cv, caption.uppercase(), x, y + 62f, fonts.semiBold, 24f, MUTED, spacing = 0.12f)
    }

    /** Gold seal with a star. */
    private fun seal(cv: Canvas, x: Float, y: Float, r: Float) {
        val rays = Path()
        val points = 24
        for (i in 0 until points * 2) {
            val a = Math.PI * i / points
            val rr = if (i % 2 == 0) r else r * 0.86f
            val px = x + (rr * cos(a)).toFloat()
            val py = y + (rr * sin(a)).toFloat()
            if (i == 0) rays.moveTo(px, py) else rays.lineTo(px, py)
        }
        rays.close()
        cv.drawPath(rays, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(x - r * 0.3f, y - r * 0.3f, r * 1.4f, GOLD_LIGHT, GOLD, Shader.TileMode.CLAMP)
        })
        cv.drawCircle(x, y, r * 0.66f, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3f; color = 0xFFFFF3C4.toInt() })
        val star = Path()
        for (i in 0 until 10) {
            val a = -Math.PI / 2 + Math.PI * i / 5
            val rr = if (i % 2 == 0) r * 0.46f else r * 0.2f
            val px = x + (rr * cos(a)).toFloat()
            val py = y + (rr * sin(a)).toFloat()
            if (i == 0) star.moveTo(px, py) else star.lineTo(px, py)
        }
        star.close()
        cv.drawPath(star, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF7A4A00.toInt() })
    }
}
