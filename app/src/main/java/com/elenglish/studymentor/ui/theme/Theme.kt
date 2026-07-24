package com.elenglish.studymentor.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Feedback colors that Material 3 does not model (success / warning / info).
 * Exposed as a composition local so screens never hard-code hex values.
 */
@Immutable
data class StudyMentorFeedbackColors(
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val warning: Color,
    val onWarning: Color,
    val info: Color,
    val onInfo: Color,
)

// Feedback colours are drawn as text ("Correct", "Lesson completed"), so the
// light theme uses the accessible variants. On the dark surfaces the brand
// values already clear AA comfortably, so they are kept there.
private val LightFeedbackColors = StudyMentorFeedbackColors(
    success = Palette.SuccessText,
    onSuccess = Palette.White,
    successContainer = Palette.SuccessContainer,
    warning = Palette.WarningText,
    onWarning = Palette.White,
    info = Palette.InfoText,
    onInfo = Palette.White,
)

private val DarkFeedbackColors = StudyMentorFeedbackColors(
    success = Palette.FeedbackSuccess,
    onSuccess = Palette.PrimaryStrong,
    successContainer = Palette.DarkSurfaceVariant,
    warning = Palette.FeedbackWarning,
    onWarning = Palette.PrimaryStrong,
    info = Palette.FeedbackInfo,
    onInfo = Palette.PrimaryStrong,
)

val LocalFeedbackColors = staticCompositionLocalOf { LightFeedbackColors }

private val LightColors: ColorScheme = lightColorScheme(
    primary = Palette.PrimaryBase,
    onPrimary = Palette.White,
    primaryContainer = Palette.PrimaryContainer,
    onPrimaryContainer = Palette.OnPrimaryContainer,
    secondary = Palette.SecondaryStrong,
    onSecondary = Palette.White,
    secondaryContainer = Palette.SecondaryContainer,
    onSecondaryContainer = Palette.PrimaryStrong,
    tertiary = Palette.TertiaryBase,
    onTertiary = Palette.White,
    tertiaryContainer = Palette.TertiaryContainer,
    onTertiaryContainer = Palette.PrimaryStrong,
    background = Palette.LightCanvas,
    onBackground = Palette.LightTextPrimary,
    surface = Palette.LightSurface,
    onSurface = Palette.LightTextPrimary,
    surfaceVariant = Palette.LightSurfaceVariant,
    onSurfaceVariant = Palette.LightTextSecondary,
    surfaceContainer = Palette.LightSurfaceVariant,
    surfaceContainerHigh = Palette.LightSurface,
    // `outline` draws input borders, which WCAG treats as meaningful UI; the
    // lighter `outlineVariant` remains for decorative dividers.
    outline = Palette.LightOutlineAccessible,
    outlineVariant = Palette.LightOutlineVariant,
    error = Palette.ErrorText,
    onError = Palette.White,
    errorContainer = Palette.ErrorContainer,
    onErrorContainer = Palette.OnErrorContainer,
    scrim = Palette.Scrim,
)

private val DarkColors: ColorScheme = darkColorScheme(
    primary = Palette.PrimaryOnDarkSurface,
    onPrimary = Palette.PrimaryStrong,
    primaryContainer = Palette.PrimaryStrong,
    onPrimaryContainer = Palette.PrimaryContainer,
    secondary = Palette.SecondaryBase,
    onSecondary = Palette.PrimaryStrong,
    secondaryContainer = Palette.SecondaryStrong,
    onSecondaryContainer = Palette.SecondarySubtle,
    tertiary = Palette.TertiaryBase,
    onTertiary = Palette.PrimaryStrong,
    tertiaryContainer = Palette.TertiaryBase,
    onTertiaryContainer = Palette.TertiaryContainer,
    background = Palette.DarkCanvas,
    onBackground = Palette.DarkTextPrimary,
    surface = Palette.DarkSurface,
    onSurface = Palette.DarkTextPrimary,
    surfaceVariant = Palette.DarkSurfaceVariant,
    onSurfaceVariant = Palette.DarkTextSecondary,
    surfaceContainer = Palette.DarkSurfaceVariant,
    surfaceContainerHigh = Palette.DarkSurfaceVariant,
    outline = Palette.DarkOutlineAccessible,
    outlineVariant = Palette.DarkOutlineVariant,
    error = Palette.FeedbackError,
    onError = Palette.White,
    errorContainer = Palette.DarkErrorContainer,
    onErrorContainer = Palette.TertiaryContainer,
    scrim = Palette.Scrim,
)

/**
 * The single entry point for app styling.
 *
 * Provides the Material 3 color scheme, typography and shapes plus the token
 * families Material does not model (spacing, touch targets, elevation, motion,
 * feedback colors). Screens must consume these rather than redefining styles.
 *
 * Dynamic color is intentionally not enabled: the product has a defined brand
 * palette shared with the Web reference.
 *
 * @param reducedMotion overrides the detected system preference; used by tests
 *  and previews. `null` means "follow the system setting".
 */
@Composable
fun StudyMentorTheme(
    themeMode: ThemeMode = ThemeMode.System,
    reducedMotion: Boolean? = null,
    content: @Composable () -> Unit,
) {
    val useDarkTheme = when (themeMode) {
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
        ThemeMode.System -> isSystemInDarkTheme()
    }

    val systemReducedMotion = rememberSystemReducedMotion()
    val effectiveReducedMotion = reducedMotion ?: systemReducedMotion
    val motion = remember(effectiveReducedMotion) {
        StudyMentorMotion(reducedMotion = effectiveReducedMotion)
    }

    CompositionLocalProvider(
        LocalFeedbackColors provides if (useDarkTheme) DarkFeedbackColors else LightFeedbackColors,
        LocalSpacing provides StudyMentorSpacing(),
        LocalTouchTargets provides StudyMentorTouchTargets(),
        LocalElevation provides StudyMentorElevation(),
        LocalMotion provides motion,
    ) {
        MaterialTheme(
            colorScheme = if (useDarkTheme) DarkColors else LightColors,
            typography = StudyMentorTypography,
            shapes = StudyMentorShapes,
            content = content,
        )
    }
}

/** Convenience accessors so screens read tokens the same way they read Material. */
object StudyMentorTheme {
    val spacing: StudyMentorSpacing
        @Composable get() = LocalSpacing.current

    val touchTargets: StudyMentorTouchTargets
        @Composable get() = LocalTouchTargets.current

    val elevation: StudyMentorElevation
        @Composable get() = LocalElevation.current

    val motion: StudyMentorMotion
        @Composable get() = LocalMotion.current

    val feedback: StudyMentorFeedbackColors
        @Composable get() = LocalFeedbackColors.current
}
