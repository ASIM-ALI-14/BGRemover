package com.devx.VisionCut.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme = lightColorScheme(
    primary              = BrandPurple,
    onPrimary            = Color.White,
    primaryContainer     = PrimaryContainerLight,
    onPrimaryContainer   = OnPrimaryContainerLight,
    background           = BackgroundLight,
    onBackground         = OnBackgroundLight,
    surface              = SurfaceLight,
    onSurface            = OnSurfaceLight,
    surfaceVariant       = SurfaceVariantLight,
    onSurfaceVariant     = OnSurfaceVariantLight,
    outlineVariant       = OutlineVariantLight,
)

private val DarkColorScheme = darkColorScheme(
    primary              = BrandPurpleLight,
    onPrimary            = Color.Black,
    primaryContainer     = PrimaryContainerDark,
    onPrimaryContainer   = OnPrimaryContainerDark,
    background           = BackgroundDark,
    onBackground         = OnBackgroundDark,
    surface              = SurfaceDark,
    onSurface            = OnSurfaceDark,
    surfaceVariant       = SurfaceVariantDark,
    onSurfaceVariant     = OnSurfaceVariantDark,
    outlineVariant       = OutlineVariantDark,
)

/**
 * App-wide theme. Light/dark follows system setting by default.
 *
 * ⚠️ RENAMED from TestAppTheme → BGRemoverTheme.
 *    Update your MainActivity/MyApp call site.
 *
 * Set dynamicColor = true to use Android 12+ wallpaper colors (overrides brand purple).
 */
@Composable
fun VisionCutTheme(
    darkTheme   : Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,   // false = always use brand purple
    content     : @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else      -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = Typography,
        content     = content
    )
}