package com.elenglish.studymentor.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Raw palette ported verbatim from the canonical Web token source
 * `EL_English_Web/src/design-system/foundations/colors.ts`.
 *
 * Screens must not reference these directly. They consume `MaterialTheme.colorScheme`
 * or [StudyMentorFeedbackColors] via [LocalFeedbackColors]. Phase 2 expands the
 * remaining token families (spacing, shape, motion).
 */
internal object Palette {
    val PrimaryBase = Color(0xFF1F4E79)
    val PrimaryStrong = Color(0xFF0A2A47)
    val PrimarySubtle = Color(0xFF3D86C6)
    val PrimaryContainer = Color(0xFFD6E6F7)
    val OnPrimaryContainer = Color(0xFF0A2A47)

    val SecondaryBase = Color(0xFF00B4D8)
    val SecondaryStrong = Color(0xFF0077B6)
    val SecondarySubtle = Color(0xFF90E0EF)
    val SecondaryContainer = Color(0xFFCAF0F8)

    val TertiaryBase = Color(0xFFFF6B6B)
    val TertiaryContainer = Color(0xFFFFE0E0)

    val FeedbackSuccess = Color(0xFF2ECC71)
    val FeedbackWarning = Color(0xFFF39C12)
    val FeedbackError = Color(0xFFE74C3C)
    val FeedbackInfo = Color(0xFF3498DB)

    // Containers derived from the feedback tokens. The Web source defines only the
    // base feedback colors; Material 3 additionally needs low-emphasis containers.
    val SuccessContainer = Color(0xFFE3F9ED)
    val ErrorContainer = Color(0xFFFCE4E1)
    val OnErrorContainer = Color(0xFF5C1B14)
    val DarkErrorContainer = Color(0xFF3A1512)
    val Scrim = Color(0xFF000000)

    /**
     * Accessible variants, used wherever a colour carries text or a meaningful
     * UI boundary.
     *
     * The Web values above are kept as the brand reference, but several of them
     * fail WCAG 2.1 AA on the surfaces this app draws them on — measured in
     * `ContrastAuditTest`, not judged by eye:
     *
     * | Web value | On | Ratio | Needs |
     * |---|---|---|---|
     * | success `#2ECC71` | white | 2.10:1 | 4.5:1 |
     * | warning `#F39C12` | white | 2.19:1 | 4.5:1 |
     * | error `#E74C3C` | white | 3.82:1 | 4.5:1 |
     * | outline `#D0D7DE` | white | 1.45:1 | 3:1 |
     * | primary subtle `#3D86C6` | dark surface | 4.47:1 | 4.5:1 |
     *
     * The hue is preserved; only lightness moves far enough to be readable. The
     * same defects exist in the Web product and are recorded in the Phase 12
     * report for the Web team.
     */
    val SuccessText = Color(0xFF147A43)
    val WarningText = Color(0xFF9A5B00)
    val ErrorText = Color(0xFFC62828)
    val InfoText = Color(0xFF1B6CA8)

    /** Input borders and other meaningful boundaries need 3:1. */
    val LightOutlineAccessible = Color(0xFF767F8C)
    val DarkOutlineAccessible = Color(0xFF6B7480)

    /** Primary as text on a dark surface. */
    val PrimaryOnDarkSurface = Color(0xFF5C9FD6)

    val LightCanvas = Color(0xFFF8F9FA)
    val LightSurface = Color(0xFFFFFFFF)
    val LightSurfaceVariant = Color(0xFFF0F4F8)
    val LightTextPrimary = Color(0xFF1C1C1E)
    val LightTextSecondary = Color(0xFF595959)
    val LightOutline = Color(0xFFD0D7DE)
    val LightOutlineVariant = Color(0xFFE0E6ED)

    val DarkCanvas = Color(0xFF0D1117)
    val DarkSurface = Color(0xFF161B22)
    val DarkSurfaceVariant = Color(0xFF1C2333)
    val DarkTextPrimary = Color(0xFFE6EDF3)
    val DarkTextSecondary = Color(0xFF8B949E)
    val DarkOutline = Color(0xFF30363D)
    val DarkOutlineVariant = Color(0xFF21262D)

    val White = Color(0xFFFFFFFF)
}
