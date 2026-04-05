package id.cachet.wallet.android.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Cachet theme — Palette B ("Warm Accent")
 * Generated from design/tokens/colors.json
 *
 * Brand: Midnight Slate #1E293B + Vivid Emerald #10B981 + Warm Coral #F97068
 */

private val LightColorScheme = lightColorScheme(
    primary = BrandAccent,
    onPrimary = TextOnBrand,
    primaryContainer = TrustVerifiedBg,
    onPrimaryContainer = TrustVerifiedText,
    secondary = BrandPrimary,
    onSecondary = TextOnBrand,
    secondaryContainer = SurfaceElevated,
    onSecondaryContainer = TextPrimary,
    tertiary = TrustPending,
    onTertiary = TextOnBrand,
    tertiaryContainer = TrustPendingBg,
    onTertiaryContainer = TrustPendingText,
    error = BrandWarm,
    onError = TextOnBrand,
    errorContainer = TrustRevokedBg,
    onErrorContainer = TrustRevokedText,
    background = SurfaceBackground,
    onBackground = TextPrimary,
    surface = SurfaceCard,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceElevated,
    onSurfaceVariant = TextSecondary,
    outline = SurfaceBorder,
    outlineVariant = SurfaceBorder
)

private val DarkColorScheme = darkColorScheme(
    primary = BrandAccentLight,
    onPrimary = BrandPrimaryDark,
    primaryContainer = SurfaceAccentTintDark,
    onPrimaryContainer = BrandAccentLight,
    secondary = BrandPrimary,
    onSecondary = TextOnBrand,
    secondaryContainer = SurfaceElevatedDark,
    onSecondaryContainer = TextPrimaryDark,
    tertiary = TrustPending,
    onTertiary = BrandPrimaryDark,
    tertiaryContainer = TrustPendingBg,
    onTertiaryContainer = TrustPendingText,
    error = BrandWarm,
    onError = BrandPrimaryDark,
    errorContainer = TrustRevokedBg,
    onErrorContainer = TrustRevokedText,
    background = SurfaceBackgroundDark,
    onBackground = TextPrimaryDark,
    surface = SurfaceCardDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = SurfaceElevatedDark,
    onSurfaceVariant = TextSecondaryDark,
    outline = SurfaceBorderDark,
    outlineVariant = SurfaceBorderDark
)

@Composable
fun CachetWalletTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
