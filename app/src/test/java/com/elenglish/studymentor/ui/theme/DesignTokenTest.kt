package com.elenglish.studymentor.ui.theme

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Drift guard.
 *
 * These values are ported from the canonical Web token source in
 * `EL_English_Web/src/design-system/foundations/`. If Android and Web diverge,
 * that must be a deliberate, reviewed change — not an accident.
 */
class DesignTokenTest {

    @Test
    fun `spacing scale matches the Web layout tokens`() {
        val spacing = StudyMentorSpacing()

        assertEquals(4.dp, spacing.xs)
        assertEquals(8.dp, spacing.sm)
        assertEquals(16.dp, spacing.md)
        assertEquals(24.dp, spacing.lg)
        assertEquals(32.dp, spacing.xl)
        assertEquals(48.dp, spacing.xxl)
        assertEquals(64.dp, spacing.xxxl)
    }

    @Test
    fun `touch targets are never below the accessibility floor`() {
        val targets = StudyMentorTouchTargets()

        assertEquals(44.dp, targets.minimum)
        // Interactive primitives use these, and Material requires at least 48dp.
        assert(targets.button >= 48.dp)
        assert(targets.icon >= 48.dp)
        assert(targets.listRow >= 48.dp)
        assert(targets.primaryButton >= targets.button)
    }

    @Test
    fun `motion durations match the Web motion tokens`() {
        val motion = StudyMentorMotion()

        assertEquals(150, motion.duration(MotionSpeed.Fast))
        assertEquals(300, motion.duration(MotionSpeed.Normal))
        assertEquals(500, motion.duration(MotionSpeed.Slow))
    }

    @Test
    fun `reduced motion collapses every duration to zero`() {
        val motion = StudyMentorMotion(reducedMotion = true)

        MotionSpeed.entries.forEach { speed ->
            assertEquals("$speed should be instant", 0, motion.duration(speed))
        }
    }

    @Test
    fun `type scale matches the Web typography roles`() {
        // Web display 36 / heading.large 28 / heading.medium 24 / heading.small 20
        assertEquals(36.sp, StudyMentorTypography.displaySmall.fontSize)
        assertEquals(28.sp, StudyMentorTypography.headlineLarge.fontSize)
        assertEquals(24.sp, StudyMentorTypography.headlineMedium.fontSize)
        assertEquals(20.sp, StudyMentorTypography.headlineSmall.fontSize)
        // Web heading.compact 16 / body.default 15 / body.small 13 / caption 12
        assertEquals(16.sp, StudyMentorTypography.titleMedium.fontSize)
        assertEquals(15.sp, StudyMentorTypography.bodyLarge.fontSize)
        assertEquals(13.sp, StudyMentorTypography.bodyMedium.fontSize)
        assertEquals(12.sp, StudyMentorTypography.labelSmall.fontSize)
        // Web button.default 15 / button.small 13
        assertEquals(15.sp, StudyMentorTypography.labelLarge.fontSize)
        assertEquals(13.sp, StudyMentorTypography.labelMedium.fontSize)
    }

    @Test
    fun `type sizes are expressed in sp so system font scaling applies`() {
        listOf(
            StudyMentorTypography.displaySmall,
            StudyMentorTypography.headlineLarge,
            StudyMentorTypography.titleMedium,
            StudyMentorTypography.bodyLarge,
            StudyMentorTypography.labelSmall,
        ).forEach { style ->
            assert(style.fontSize.isSp) { "font size must be scalable: $style" }
        }
    }
}
