package connects.quotes.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ======================================================================
// QUOTES CONNECT DESIGN LANGUAGE SPECIFICATION (v1.0)
// Architectural Monochrome Tokens & Restrained Geometric Radii
// ======================================================================

// Core Monochrome Color System (Spec Section 4.1 & 75)
val PureBlack = Color(0xFF000000)      // BLACK: Primary background
val Surface01 = Color(0xFF111111)       // SURFACE_01: Main cards / panels
val Surface02 = Color(0xFF181818)       // SURFACE_02: Elevated panels / inputs
val PureWhite = Color(0xFFFFFFFF)      // WHITE: Primary text / high contrast
val Gray01 = Color(0xFFB5B5B5)         // GRAY_01: Secondary text
val Gray02 = Color(0xFF666666)         // GRAY_02: Borders & outlines
val Gray03 = Color(0xFF333333)         // GRAY_03: Subtle borders & disabled content

// Backward-Compatibility Aliases
val DarkSurface = Surface01
val DarkSurfaceVariant = Surface02
val DarkBorder = Gray03
val TextWhite = PureWhite
val TextMuted = Gray01

// Light Theme Palette (Preserving High-Contrast Inversion)
val LightSurface = Color(0xFFF8F9FA)
val LightSurfaceVariant = Color(0xFFEFEFEF)
val LightBorder = Color(0xFFD6D6D6)
val TextBlack = Color(0xFF111111)
val LightTextMuted = Color(0xFF666666)

// Subdued Semantic Colors (Spec Section 4.2)
val SemanticSuccess = Color(0xFF4CAF50)
val SemanticWarning = Color(0xFFFFA726)
val SemanticError = Color(0xFFEF5350)
val SemanticInfo = Color(0xFF42A5F5)

// Architectural Corner Radii (Spec Section 9 & 75)
// 0px structural surfaces, 4px inputs, 6px cards, 8px buttons
val QCRadiusStructural = 0.dp
val QCRadiusInput = 4.dp
val QCRadiusCard = 6.dp
val QCRadiusButton = 8.dp

// Spacing System (Spec Section 7)
object QCSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val base = 16.dp
    val lg = 24.dp
    val xl = 32.dp
    val xxl = 48.dp
}

// Restrained Shapes Scale (Spec Section 9 & 75)
val QCShapes = Shapes(
    extraSmall = RoundedCornerShape(QCRadiusStructural),
    small = RoundedCornerShape(QCRadiusInput),
    medium = RoundedCornerShape(QCRadiusCard),
    large = RoundedCornerShape(QCRadiusButton),
    extraLarge = RoundedCornerShape(QCRadiusButton)
)

private val DarkColorScheme = darkColorScheme(
    primary = PureWhite,
    onPrimary = PureBlack,
    primaryContainer = Surface02,
    onPrimaryContainer = PureWhite,
    background = PureBlack,
    onBackground = PureWhite,
    surface = Surface01,
    onSurface = PureWhite,
    surfaceVariant = Surface02,
    onSurfaceVariant = Gray01,
    outline = Gray03,
    outlineVariant = Gray02,
    error = SemanticError,
    onError = PureWhite
)

private val LightColorScheme = lightColorScheme(
    primary = PureBlack,
    onPrimary = PureWhite,
    primaryContainer = LightSurfaceVariant,
    onPrimaryContainer = PureBlack,
    background = PureWhite,
    onBackground = TextBlack,
    surface = LightSurface,
    onSurface = TextBlack,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightTextMuted,
    outline = LightBorder,
    outlineVariant = Gray02,
    error = SemanticError,
    onError = PureWhite
)

val MinimalTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.15.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.25.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.5.sp
    )
)

@Composable
fun QuotesConnectTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = MinimalTypography,
        shapes = QCShapes,
        content = content
    )
}
