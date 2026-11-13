package com.pypath.app.ui.screens.certificate

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.res.ResourcesCompat
import com.pypath.app.R
import com.pypath.app.ui.components.PythonHighlighter
import com.pypath.app.ui.theme.LightAppColors

/**
 * Premium Phase 1 (Image Center): the two downloadable PyPath images. Like [CertificateRenderer]
 * they are drawn on the phone with android.graphics and the app's own fonts, so they add no image
 * assets to the APK and need no network. Code is coloured by the app's own [PythonHighlighter].
 */
enum class PremiumImage(
    val title: String,
    val description: String,
    val width: Int,
    val height: Int,
    val format: Bitmap.CompressFormat,
    val fileName: String,
) {
    WALLPAPER(
        "Keep Coding wallpaper", "A dark phone wallpaper with highlighted Python and the PyPath mark.",
        1080, 2340, Bitmap.CompressFormat.JPEG, "PyPath-keep-coding-wallpaper.jpg",
    ),
    CHEAT_SHEET(
        "Python quick reference", "A poster of the Python basics: input, decisions, loops, lists, dictionaries and functions.",
        1080, 1350, Bitmap.CompressFormat.PNG, "PyPath-python-quick-reference.png",
    );

    val mimeType: String get() = if (format == Bitmap.CompressFormat.JPEG) "image/jpeg" else "image/png"
    /** JPEG keeps the gradient wallpaper small; the flat-colour poster stays sharp as PNG. */
    val quality: Int get() = if (format == Bitmap.CompressFormat.JPEG) 90 else 100
    val sizeLabel: String get() = "$width \u00d7 $height \u00b7 ${if (format == Bitmap.CompressFormat.JPEG) "JPEG" else "PNG"}"
}

object ImageCenterRenderer {
    private const val BRAND = 0xFF3D5AFE.toInt()
    private const val BRAND_DARK = 0xFF2536B8.toInt()
    private const val ACCENT = 0xFFFFC83D.toInt()
    private const val INK = 0xFF1B2440.toInt()
    private const val MUTED = 0xFF5A6478.toInt()
    private const val CODE_BG = 0xFF111526.toInt()
    private const val CODE_HEADER = 0xFF1A1F33.toInt()

    fun render(context: Context, image: PremiumImage): Bitmap = when (image) {
        PremiumImage.WALLPAPER -> wallpaper(context)
        PremiumImage.CHEAT_SHEET -> cheatSheet(context)
    }

    // ───────────── 1. Keep Coding wallpaper ─────────────

    private val WALLPAPER_CODE = """
        def keep_learning(days):
            skill = 1.0
            for day in range(days):
                skill *= 1.01  # 1% better
            return skill

        print(round(keep_learning(365), 2))
        # 37.78
    """.trimIndent()

    private fun wallpaper(context: Context): Bitmap {
        val img = PremiumImage.WALLPAPER
        val w = img.width.toFloat()
        val h = img.height.toFloat()
        val bmp = Bitmap.createBitmap(img.width, img.height, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val f = Fonts(context)

        // Deep navy gradient with soft brand and accent glows.
        cv.drawRect(0f, 0f, w, h, Paint().apply {
            shader = LinearGradient(0f, 0f, w * 0.3f, h, intArrayOf(0xFF070A16.toInt(), 0xFF10173A.toInt(), 0xFF1A2470.toInt()), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        })
        glow(cv, w * 0.85f, h * 0.18f, w * 0.75f, BRAND, 0.55f)
        glow(cv, w * 0.05f, h * 0.72f, w * 0.6f, 0xFF7C4DFF.toInt(), 0.30f)
        glow(cv, w * 0.9f, h * 0.95f, w * 0.45f, ACCENT, 0.18f)
        // Faint dot grid.
        val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x14FFFFFF }
        var y = 60f
        while (y < h) { var x = 60f; while (x < w) { cv.drawCircle(x, y, 2.2f, dot); x += 72f }; y += 72f }

        // Editor window (the top third stays clear for the lock-screen clock).
        val card = RectF(84f, 930f, w - 84f, 1530f)
        cv.drawRoundRect(RectF(card.left, card.top + 24f, card.right, card.bottom + 24f), 40f, 40f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x55000000 })
        cv.drawRoundRect(card, 40f, 40f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = CODE_BG })
        cv.save(); cv.clipRect(card.left, card.top, card.right, card.top + 84f)
        cv.drawRoundRect(card, 40f, 40f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = CODE_HEADER })
        cv.restore()
        cv.drawRoundRect(card, 40f, 40f, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f; color = 0x22FFFFFF })
        listOf(0xFFFF5F57.toInt(), 0xFFFEBC2E.toInt(), 0xFF28C840.toInt()).forEachIndexed { i, col ->
            cv.drawCircle(card.left + 46f + i * 34f, card.top + 42f, 10f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = col })
        }
        drawLeft(cv, "keep_learning.py", card.left + 160f, card.top + 52f, f.mono, 28f, 0xFF8087A3.toInt())
        drawCode(cv, WALLPAPER_CODE, card.left + 48f, card.top + 150f, f.mono, 34f, 56f, maxWidth = card.width() - 96f, lineNumbers = true)

        // Tagline.
        drawCentered(cv, "Keep coding.", w / 2f, 1720f, f.extraBold, 96f, 0xFFFFFFFF.toInt())
        drawCentered(cv, "1% better every day adds up.", w / 2f, 1800f, f.medium, 42f, 0xFFB8C0DA.toInt())

        // Brand mark.
        val wordWidth = textPaint(f.extraBold, 64f, 0, Paint.Align.LEFT).measureText("PyPath")
        val groupLeft = (w - (96f + 28f + wordWidth)) / 2f
        logo(cv, groupLeft, 1990f, 96f, f)
        drawLeft(cv, "PyPath", groupLeft + 96f + 28f, 2058f, f.extraBold, 64f, 0xFFFFFFFF.toInt())
        drawCentered(cv, "LEARN PYTHON \u00b7 ANYWHERE \u00b7 OFFLINE", w / 2f, 2170f, f.semiBold, 26f, 0x99FFFFFF.toInt(), spacing = 0.2f)
        return bmp
    }

    // ───────────── 2. Python quick reference poster ─────────────

    private val SHEET_CARDS = listOf(
        "Output & input" to "name = input(\"Name: \")\nprint(\"Hi\", name)\nprint(f\"{name}!\")",
        "Decisions" to "if score >= 60:\n    print(\"Pass\")\nelse:\n    print(\"Retry\")",
        "Loops" to "for i in range(3):\n    print(i)\nwhile n > 0:\n    n -= 1",
        "Lists" to "nums = [3, 1, 2]\nnums.append(4)\nnums.sort()\nprint(nums[0], len(nums))",
        "Dictionaries" to "age = {\"Asha\": 16}\nage[\"Ravi\"] = 15\nfor k, v in age.items():\n    print(k, v)",
        "Functions" to "def add(a, b=1):\n    return a + b\n\nprint(add(2))  # 3",
    )

    private fun cheatSheet(context: Context): Bitmap {
        val img = PremiumImage.CHEAT_SHEET
        val w = img.width.toFloat()
        val h = img.height.toFloat()
        val bmp = Bitmap.createBitmap(img.width, img.height, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val f = Fonts(context)

        cv.drawColor(0xFFF6F7FB.toInt())
        // Header band.
        val band = RectF(0f, 0f, w, 300f)
        cv.drawRect(band, Paint().apply { shader = LinearGradient(0f, 0f, w, 300f, BRAND, BRAND_DARK, Shader.TileMode.CLAMP) })
        cv.save(); cv.clipRect(band)
        glow(cv, w * 0.92f, 20f, 360f, 0xFF8EA2FF.toInt(), 0.55f)
        glow(cv, w * 0.12f, 300f, 260f, ACCENT, 0.22f)
        cv.restore()
        logo(cv, 60f, 62f, 84f, f, onBrand = true)
        drawLeft(cv, "PyPath", 166f, 122f, f.extraBold, 52f, 0xFFFFFFFF.toInt())
        drawLeft(cv, "Python quick reference", 60f, 224f, f.extraBold, 64f, 0xFFFFFFFF.toInt())
        drawLeft(cv, "THE BASICS ON ONE PAGE", 62f, 270f, f.semiBold, 24f, 0xCCFFFFFF.toInt(), spacing = 0.18f)

        // 2 × 3 grid of cards.
        val margin = 54f
        val gap = 26f
        val cardW = (w - margin * 2 - gap) / 2f
        val top = 336f
        val cardH = 284f
        SHEET_CARDS.forEachIndexed { i, (title, code) ->
            val col = i % 2
            val row = i / 2
            val l = margin + col * (cardW + gap)
            val t = top + row * (cardH + gap)
            val r = RectF(l, t, l + cardW, t + cardH)
            cv.drawRoundRect(RectF(r.left, r.top + 6f, r.right, r.bottom + 6f), 30f, 30f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x141B2440 })
            cv.drawRoundRect(r, 30f, 30f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() })
            cv.drawRoundRect(r, 30f, 30f, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f; color = 0xFFE3E6EF.toInt() })
            // Numbered title.
            cv.drawCircle(r.left + 44f, r.top + 46f, 20f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE8ECFF.toInt() })
            drawCentered(cv, "${i + 1}", r.left + 44f, r.top + 55f, f.extraBold, 24f, BRAND)
            drawLeft(cv, title, r.left + 78f, r.top + 57f, f.bold, 32f, INK)
            val panel = RectF(r.left + 18f, r.top + 86f, r.right - 18f, r.bottom - 18f)
            cv.drawRoundRect(panel, 20f, 20f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = CODE_BG })
            drawCode(cv, code, panel.left + 20f, panel.top + 42f, f.mono, 25f, 39f, maxWidth = panel.width() - 40f)
        }

        // Footer.
        val fy = top + 3 * cardH + 2 * gap + 62f
        val footer = "Practise every day with PyPath \u00b7 works offline"
        val footerWidth = textPaint(f.semiBold, 28f, 0, Paint.Align.LEFT).measureText(footer)
        val footerLeft = (w - (footerWidth + 28f)) / 2f
        cv.drawCircle(footerLeft + 7f, fy - 9f, 7f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ACCENT })
        drawLeft(cv, footer, footerLeft + 28f, fy, f.semiBold, 28f, MUTED)
        return bmp
    }

    // ───────────── helpers ─────────────

    private class Fonts(context: Context) {
        private fun load(context: Context, id: Int, fallback: Typeface) = runCatching { ResourcesCompat.getFont(context, id) }.getOrNull() ?: fallback
        val medium = load(context, R.font.jakarta_500, Typeface.DEFAULT)
        val semiBold = load(context, R.font.jakarta_600, Typeface.DEFAULT_BOLD)
        val bold = load(context, R.font.jakarta_700, Typeface.DEFAULT_BOLD)
        val extraBold = load(context, R.font.jakarta_800, Typeface.DEFAULT_BOLD)
        val mono = load(context, R.font.mono_regular, Typeface.MONOSPACE)
    }

    private fun glow(cv: Canvas, x: Float, y: Float, r: Float, color: Int, alpha: Float) {
        val a = (alpha * 255).toInt().coerceIn(0, 255)
        val center = (color and 0x00FFFFFF) or (a shl 24)
        cv.drawCircle(x, y, r, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(x, y, r, intArrayOf(center, color and 0x00FFFFFF), null, Shader.TileMode.CLAMP)
        })
    }

    /** The PyPath mark: rounded brand square with "</>" and the accent dot (as in the app's Logo). */
    private fun logo(cv: Canvas, left: Float, top: Float, size: Float, f: Fonts, onBrand: Boolean = false) {
        val r = RectF(left, top, left + size, top + size)
        cv.drawRoundRect(r, size * 0.3f, size * 0.3f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (onBrand) 0xFFFFFFFF.toInt() else BRAND
        })
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = f.extraBold; textSize = size * 0.36f; textAlign = Paint.Align.CENTER
            color = if (onBrand) BRAND else 0xFFFFFFFF.toInt()
        }
        cv.drawText("</>", r.centerX(), r.centerY() - (p.descent() + p.ascent()) / 2f, p)
        val d = size * 0.18f
        cv.drawCircle(r.right - size * 0.14f - d / 2f, r.bottom - size * 0.14f - d / 2f, d / 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ACCENT })
    }

    private fun textPaint(face: Typeface, size: Float, color: Int, align: Paint.Align, spacing: Float = 0f) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = face; textSize = size; this.color = color; textAlign = align; letterSpacing = spacing
    }

    private fun drawCentered(cv: Canvas, s: String, x: Float, y: Float, face: Typeface, size: Float, color: Int, spacing: Float = 0f, maxWidth: Float = 960f) {
        val p = textPaint(face, size, color, Paint.Align.CENTER, spacing)
        while (p.measureText(s) > maxWidth && p.textSize > 16f) p.textSize -= 1f
        cv.drawText(s, x, y, p)
    }

    private fun drawLeft(cv: Canvas, s: String, x: Float, y: Float, face: Typeface, size: Float, color: Int, spacing: Float = 0f) {
        cv.drawText(s, x, y, textPaint(face, size, color, Paint.Align.LEFT, spacing))
    }

    /** Python code coloured exactly like the in-app editor; shrinks the font so the longest line fits [maxWidth]. */
    private fun drawCode(
        cv: Canvas, code: String, x: Float, firstBaseline: Float, mono: Typeface, size: Float, lineHeight: Float,
        maxWidth: Float, lineNumbers: Boolean = false,
    ) {
        val colors = LightAppColors.code
        val defaultColor = colors.text.toArgb()
        val annotated = PythonHighlighter.highlight(code, colors)
        val perChar = IntArray(code.length) { defaultColor }
        annotated.spanStyles.forEach { span ->
            val col = span.item.color
            if (col != androidx.compose.ui.graphics.Color.Unspecified) {
                for (i in span.start until span.end.coerceAtMost(code.length)) perChar[i] = col.toArgb()
            }
        }
        // Ligatures off, so ">=" stays ">=" exactly as the learner types it.
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = mono; textSize = size; fontFeatureSettings = "'liga' 0, 'calt' 0" }
        val lines = code.split('\n')
        val gutter = if (lineNumbers) p.measureText("00") + 28f else 0f
        val longest = lines.maxOf { it.length }.coerceAtLeast(1)
        while (p.measureText("M".repeat(longest)) + gutter > maxWidth && p.textSize > 12f) p.textSize -= 1f
        val scale = p.textSize / size
        val numberPaint = Paint(p).apply { color = colors.comment.toArgb(); alpha = 150; textAlign = Paint.Align.RIGHT }
        var offset = 0
        lines.forEachIndexed { li, line ->
            val baseline = firstBaseline + li * lineHeight * scale
            if (lineNumbers) cv.drawText("${li + 1}", x + gutter - 28f, baseline, numberPaint)
            var cx = x + gutter
            var i = 0
            while (i < line.length) {
                val col = perChar[offset + i]
                var j = i + 1
                while (j < line.length && perChar[offset + j] == col) j++
                val run = line.substring(i, j)
                p.color = col
                cv.drawText(run, cx, baseline, p)
                cx += p.measureText(run)
                i = j
            }
            offset += line.length + 1
        }
    }
}
