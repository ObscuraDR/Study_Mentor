package com.elenglish.studymentor.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Motion tokens ported from `EL_English_Web/src/design-system/foundations/motion.ts`.
 *
 * When [reducedMotion] is true every duration collapses to zero. Components read
 * durations through [duration] rather than the raw fields so a user who has
 * disabled animations gets an instant, non-animated result everywhere.
 */
@Immutable
data class StudyMentorMotion(
    val fastMillis: Int = 150,
    val normalMillis: Int = 300,
    val slowMillis: Int = 500,
    val reducedMotion: Boolean = false,
) {
    val standard: Easing = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f)
    val decelerate: Easing = CubicBezierEasing(0.0f, 0.0f, 0.2f, 1.0f)
    val accelerate: Easing = CubicBezierEasing(0.4f, 0.0f, 1.0f, 1.0f)

    /** Effective duration for [speed], honouring the reduced-motion preference. */
    fun duration(speed: MotionSpeed): Int = when {
        reducedMotion -> 0
        else -> when (speed) {
            MotionSpeed.Fast -> fastMillis
            MotionSpeed.Normal -> normalMillis
            MotionSpeed.Slow -> slowMillis
        }
    }
}

enum class MotionSpeed { Fast, Normal, Slow }

val LocalMotion = staticCompositionLocalOf { StudyMentorMotion() }
