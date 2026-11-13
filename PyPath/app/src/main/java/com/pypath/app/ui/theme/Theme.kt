package com.pypath.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pypath.app.R

// ───────────── Colors ─────────────

@Immutable
data class AppColors(
    val background: Color,
    val surface: Color,
    val surfaceAlt: Color,
    val border: Color,
    val text: Color,
    val textMuted: Color,
    val textFaint: Color,
    val brand: Color,
    val brandStrong: Color,
    val brandSoft: Color,
    val onBrand: Color,
    val accent: Color,
    val accentSoft: Color,
    val success: Color,
    val successSoft: Color,
    val error: Color,
    val errorSoft: Color,
    val locked: Color,
    val code: CodeColors,
    val isDark: Boolean,
) {
    /** Batch 3 Part 3: text and icons on [success] / [error] fills, at least 4.5:1 in both themes. */
    val onSuccess: Color get() = if (isDark) Color(0xFF06281B) else Color.White
    val onError: Color get() = if (isDark) Color(0xFF2A0A0A) else Color.White
}

@Immutable
data class CodeColors(
    val background: Color,
    val header: Color,
    val text: Color,
    val keyword: Color,
    val builtin: Color,
    val string: Color,
    val number: Color,
    val comment: Color,
    val function: Color,
    val operator: Color,
    val outputBg: Color,
    val outputText: Color,
)

private val codeColors = CodeColors(
    background = Color(0xFF111526),
    header = Color(0xFF1A1F33),
    text = Color(0xFFE6E9F5),
    keyword = Color(0xFFC792EA),
    builtin = Color(0xFF82AAFF),
    string = Color(0xFFC3E88D),
    number = Color(0xFFF78C6C),
    comment = Color(0xFF8087A3), // Batch 3 Part 3: was 6B7394 (3.89:1), now 5.10:1 on the code background
    function = Color(0xFFFFCB6B),
    operator = Color(0xFF89DDFF),
    outputBg = Color(0xFF0A0D18),
    outputText = Color(0xFFB8C0DA),
)

val LightAppColors = AppColors(
    background = Color(0xFFF6F7FB),
    surface = Color(0xFFFFFFFF),
    surfaceAlt = Color(0xFFEFF1F8),
    border = Color(0xFFE3E6EF),
    text = Color(0xFF0E1222),
    textMuted = Color(0xFF5B6275),
    textFaint = Color(0xFF686D7A), // Batch 3 Part 3 contrast: was 8E94A6
    brand = Color(0xFF3D5AFE),
    brandStrong = Color(0xFF2335C8),
    brandSoft = Color(0xFFEEF1FF), // Batch 3 Part 3 contrast: was E8ECFF
    onBrand = Color.White,
    accent = Color(0xFFFFC83D),
    accentSoft = Color(0xFFFFF5DB),
    success = Color(0xFF0E7D52), // Batch 3 Part 3 contrast: was 12A56C
    successSoft = Color(0xFFE2F6EC),
    error = Color(0xFFC23D41), // Batch 3 Part 3 contrast: was E5484D
    errorSoft = Color(0xFFFDECEC),
    locked = Color(0xFF868A95), // Batch 3 Part 3 contrast: was B4B9C8
    code = codeColors,
    isDark = false,
)

val DarkAppColors = AppColors(
    background = Color(0xFF0B0E17),
    surface = Color(0xFF141826),
    surfaceAlt = Color(0xFF1B2033),
    border = Color(0xFF262C40),
    text = Color(0xFFEEF0F7),
    textMuted = Color(0xFFA0A7BB),
    textFaint = Color(0xFF82889A), // Batch 3 Part 3 contrast: was 6E758A
    brand = Color(0xFF6E86FF),
    brandStrong = Color(0xFF8FA1FF),
    brandSoft = Color(0xFF1E2547),
    onBrand = Color(0xFF0B1030), // Batch 3 Part 3 contrast: white on 6E86FF was 3.22:1
    accent = Color(0xFFFFC83D),
    accentSoft = Color(0xFF332A12),
    success = Color(0xFF34D399),
    successSoft = Color(0xFF0F2C22),
    error = Color(0xFFF87171),
    errorSoft = Color(0xFF3A1717),
    locked = Color(0xFF666B7F), // Batch 3 Part 3 contrast: was 4A5068
    code = codeColors.copy(background = Color(0xFF0D1120), header = Color(0xFF151A2C), outputBg = Color(0xFF070A12)),
    isDark = true,
)

val LocalAppColors = staticCompositionLocalOf { LightAppColors }

// ───────────── Typography ─────────────

val Jakarta = FontFamily(
    Font(R.font.jakarta_400, FontWeight.Normal),
    Font(R.font.jakarta_500, FontWeight.Medium),
    Font(R.font.jakarta_600, FontWeight.SemiBold),
    Font(R.font.jakarta_700, FontWeight.Bold),
    Font(R.font.jakarta_800, FontWeight.ExtraBold),
)

val Mono = FontFamily(
    Font(R.font.mono_regular, FontWeight.Normal),
    Font(R.font.mono_medium, FontWeight.Medium),
)

private fun style(size: Int, weight: FontWeight, line: Int, tracking: Double = 0.0) = TextStyle(
    fontFamily = Jakarta, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp, letterSpacing = tracking.sp,
)

val AppTypography = Typography(
    displaySmall = style(32, FontWeight.ExtraBold, 38, -0.6),
    headlineMedium = style(26, FontWeight.ExtraBold, 32, -0.4),
    headlineSmall = style(22, FontWeight.Bold, 28, -0.3),
    titleLarge = style(19, FontWeight.Bold, 25, -0.2),
    titleMedium = style(16, FontWeight.Bold, 22),
    titleSmall = style(14, FontWeight.SemiBold, 20),
    bodyLarge = style(16, FontWeight.Normal, 25),
    bodyMedium = style(14, FontWeight.Normal, 21),
    bodySmall = style(12, FontWeight.Medium, 17),
    labelLarge = style(15, FontWeight.Bold, 20),
    labelMedium = style(12, FontWeight.SemiBold, 16, 0.2),
    labelSmall = style(11, FontWeight.Bold, 14, 0.8),
)

val CodeTextStyle = TextStyle(fontFamily = Mono, fontSize = 13.5.sp, lineHeight = 21.sp)

// ───────────── Shapes & spacing ─────────────

object Dimens {
    val screenPadding = 20.dp
    val cardRadius = 20.dp
    val gap = 12.dp
    val sectionGap = 28.dp
}

private val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
)

@Composable
fun PyPathTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val c = if (dark) DarkAppColors else LightAppColors
    val scheme = if (dark) darkColorScheme(
        primary = c.brand, onPrimary = c.onBrand, background = c.background, onBackground = c.text,
        surface = c.surface, onSurface = c.text, surfaceVariant = c.surfaceAlt, onSurfaceVariant = c.textMuted,
        outline = c.border, error = c.error, secondary = c.accent,
    ) else lightColorScheme(
        primary = c.brand, onPrimary = c.onBrand, background = c.background, onBackground = c.text,
        surface = c.surface, onSurface = c.text, surfaceVariant = c.surfaceAlt, onSurfaceVariant = c.textMuted,
        outline = c.border, error = c.error, secondary = c.accent,
    )
    CompositionLocalProvider(LocalAppColors provides c) {
        MaterialTheme(colorScheme = scheme, typography = AppTypography, shapes = AppShapes, content = content)
    }
}

object AppTheme {
    val colors: AppColors @Composable get() = LocalAppColors.current
}
