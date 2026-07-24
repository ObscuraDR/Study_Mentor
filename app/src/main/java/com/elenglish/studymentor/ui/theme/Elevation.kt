package com.elenglish.studymentor.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Elevation tokens. The Web source expresses these as CSS box-shadows
 * (`foundations/elevation.ts`); Compose expresses the same three steps as
 * tonal/shadow elevation in `dp`.
 */
@Immutable
data class StudyMentorElevation(
    val none: Dp = 0.dp,
    val sm: Dp = 1.dp,
    val md: Dp = 4.dp,
    val lg: Dp = 8.dp,
)

val LocalElevation = staticCompositionLocalOf { StudyMentorElevation() }
