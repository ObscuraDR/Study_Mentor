package com.elenglish.studymentor.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * WCAG 2.1 contrast audit of the palette.
 *
 * Ratios are computed, not eyeballed. Normal body text needs 4.5:1 (AA), large
 * or bold text and non-text UI boundaries need 3:1.
 */
class ContrastAuditTest {

    private fun relativeLuminance(color: Color): Double {
        fun channel(value: Float): Double {
            val c = value.toDouble()
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(color.red) +
            0.7152 * channel(color.green) +
            0.0722 * channel(color.blue)
    }

    private fun contrastRatio(foreground: Color, background: Color): Double {
        val a = relativeLuminance(foreground)
        val b = relativeLuminance(background)
        return (max(a, b) + 0.05) / (min(a, b) + 0.05)
    }

    private fun assertContrast(
        label: String,
        foreground: Color,
        background: Color,
        minimum: Double,
    ) {
        val ratio = contrastRatio(foreground, background)
        assertTrue(
            "$label contrast is %.2f:1, below the required %.1f:1".format(ratio, minimum),
            ratio >= minimum,
        )
    }

    private companion object {
        const val AA_NORMAL_TEXT = 4.5
        const val AA_LARGE_TEXT_AND_UI = 3.0
    }

    // Light theme -------------------------------------------------------------

    @Test
    fun `light theme body text meets AA on every surface it is used on`() {
        assertContrast(
            "onSurface on surface",
            Palette.LightTextPrimary,
            Palette.LightSurface,
            AA_NORMAL_TEXT,
        )
        assertContrast(
            "onBackground on canvas",
            Palette.LightTextPrimary,
            Palette.LightCanvas,
            AA_NORMAL_TEXT,
        )
        assertContrast(
            "onSurfaceVariant on surface",
            Palette.LightTextSecondary,
            Palette.LightSurface,
            AA_NORMAL_TEXT,
        )
        assertContrast(
            "onSurfaceVariant on surfaceVariant",
            Palette.LightTextSecondary,
            Palette.LightSurfaceVariant,
            AA_NORMAL_TEXT,
        )
    }

    @Test
    fun `light theme primary button text meets AA`() {
        assertContrast("onPrimary on primary", Palette.White, Palette.PrimaryBase, AA_NORMAL_TEXT)
    }

    @Test
    fun `light theme primary text on canvas meets AA`() {
        // Screen headings and text buttons are drawn in the primary colour.
        assertContrast(
            "primary on canvas",
            Palette.PrimaryBase,
            Palette.LightCanvas,
            AA_NORMAL_TEXT,
        )
        assertContrast(
            "primary on surface",
            Palette.PrimaryBase,
            Palette.LightSurface,
            AA_NORMAL_TEXT,
        )
    }

    @Test
    fun `light theme error text meets AA both as text and as a button background`() {
        assertContrast("error on surface", Palette.ErrorText, Palette.LightSurface, AA_NORMAL_TEXT)
        assertContrast("onError on error", Palette.White, Palette.ErrorText, AA_NORMAL_TEXT)
    }

    @Test
    fun `light theme primary container text meets AA`() {
        assertContrast(
            "onPrimaryContainer on primaryContainer",
            Palette.OnPrimaryContainer,
            Palette.PrimaryContainer,
            AA_NORMAL_TEXT,
        )
    }

    // Dark theme --------------------------------------------------------------

    @Test
    fun `dark theme body text meets AA on every surface it is used on`() {
        assertContrast(
            "onSurface on surface",
            Palette.DarkTextPrimary,
            Palette.DarkSurface,
            AA_NORMAL_TEXT,
        )
        assertContrast(
            "onBackground on canvas",
            Palette.DarkTextPrimary,
            Palette.DarkCanvas,
            AA_NORMAL_TEXT,
        )
        assertContrast(
            "onSurfaceVariant on surface",
            Palette.DarkTextSecondary,
            Palette.DarkSurface,
            AA_NORMAL_TEXT,
        )
    }

    @Test
    fun `dark theme primary text meets AA`() {
        assertContrast(
            "primary on dark surface",
            Palette.PrimaryOnDarkSurface,
            Palette.DarkSurface,
            AA_NORMAL_TEXT,
        )
        assertContrast(
            "primary on dark canvas",
            Palette.PrimaryOnDarkSurface,
            Palette.DarkCanvas,
            AA_NORMAL_TEXT,
        )
    }

    @Test
    fun `dark theme primary button text meets AA`() {
        assertContrast(
            "onPrimary on primary",
            Palette.PrimaryStrong,
            Palette.PrimaryOnDarkSurface,
            AA_NORMAL_TEXT,
        )
    }

    @Test
    fun `dark theme error text meets AA`() {
        assertContrast(
            "error on dark surface",
            Palette.FeedbackError,
            Palette.DarkSurface,
            AA_NORMAL_TEXT,
        )
    }

    // Feedback colours used for meaning ---------------------------------------

    @Test
    fun `feedback colours meet AA where they carry meaning`() {
        // These label quiz verdicts, completion outcomes and truncated answers,
        // so they must be readable rather than merely decorative.
        assertContrast(
            "success on light surface",
            Palette.SuccessText,
            Palette.LightSurface,
            AA_NORMAL_TEXT,
        )
        assertContrast(
            "warning on light surface",
            Palette.WarningText,
            Palette.LightSurface,
            AA_NORMAL_TEXT,
        )
        assertContrast(
            "info on light surface",
            Palette.InfoText,
            Palette.LightSurface,
            AA_NORMAL_TEXT,
        )
        assertContrast(
            "success on dark surface",
            Palette.FeedbackSuccess,
            Palette.DarkSurface,
            AA_NORMAL_TEXT,
        )
        assertContrast(
            "warning on dark surface",
            Palette.FeedbackWarning,
            Palette.DarkSurface,
            AA_NORMAL_TEXT,
        )
        assertContrast(
            "info on dark surface",
            Palette.FeedbackInfo,
            Palette.DarkSurface,
            AA_NORMAL_TEXT,
        )
    }

    // Non-text boundaries ------------------------------------------------------

    @Test
    fun `outlines meet the 3 to 1 requirement for UI boundaries`() {
        assertContrast(
            "light outline on surface",
            Palette.LightOutlineAccessible,
            Palette.LightSurface,
            AA_LARGE_TEXT_AND_UI,
        )
        assertContrast(
            "dark outline on surface",
            Palette.DarkOutlineAccessible,
            Palette.DarkSurface,
            AA_LARGE_TEXT_AND_UI,
        )
    }

    /**
     * Records the defects inherited from the Web palette so the divergence is
     * deliberate and visible, rather than something a later "resync with Web"
     * could quietly undo.
     */
    @Test
    fun `the raw Web values are documented as failing, which is why variants exist`() {
        assertTrue(contrastRatio(Palette.FeedbackSuccess, Palette.LightSurface) < AA_NORMAL_TEXT)
        assertTrue(contrastRatio(Palette.FeedbackWarning, Palette.LightSurface) < AA_NORMAL_TEXT)
        assertTrue(contrastRatio(Palette.FeedbackError, Palette.LightSurface) < AA_NORMAL_TEXT)
        assertTrue(contrastRatio(Palette.LightOutline, Palette.LightSurface) < AA_LARGE_TEXT_AND_UI)
        assertTrue(contrastRatio(Palette.PrimarySubtle, Palette.DarkSurface) < AA_NORMAL_TEXT)
    }
}
