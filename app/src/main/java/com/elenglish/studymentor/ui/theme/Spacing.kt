package com.elenglish.studymentor.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Spacing scale ported from the canonical Web token source
 * `EL_English_Web/src/design-system/foundations/layout.ts`.
 *
 * Web pixels map 1:1 to Android `dp`: both are density-independent at the
 * reference density, so the two products stay visually aligned.
 */
@Immutable
data class StudyMentorSpacing(
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 16.dp,
    val lg: Dp = 24.dp,
    val xl: Dp = 32.dp,
    val xxl: Dp = 48.dp,
    val xxxl: Dp = 64.dp,
)

/**
 * Minimum interactive sizes. These are floors, not fixed sizes: a component may
 * grow, but shared primitives must never shrink below them.
 *
 * `minimum` = 44dp matches the Web token; Android's Material guidance is 48dp,
 * so the primitives in this app use [button] / [icon] = 48dp as the effective
 * floor and keep 44dp only as the documented contract minimum.
 */
@Immutable
data class StudyMentorTouchTargets(
    val minimum: Dp = 44.dp,
    val button: Dp = 48.dp,
    val primaryButton: Dp = 56.dp,
    val icon: Dp = 48.dp,
    val listRow: Dp = 48.dp,
    val bottomNav: Dp = 56.dp,
)

val LocalSpacing = staticCompositionLocalOf { StudyMentorSpacing() }
val LocalTouchTargets = staticCompositionLocalOf { StudyMentorTouchTargets() }
