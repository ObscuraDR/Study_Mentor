package com.elenglish.studymentor.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Type scale ported from `EL_English_Web/src/design-system/foundations/typography.ts`.
 *
 * The Web source expresses line height as a unitless multiplier; it is resolved
 * here against each size. Sizes stay in `sp` so system font scaling applies.
 *
 * Font family: the Web stack leads with Inter. No font file is bundled yet, so
 * [FontFamily.Default] (Roboto) is used and the family is centralised here so a
 * later Inter addition is a one-line change.
 */
private val InterfaceFontFamily = FontFamily.Default

val StudyMentorTypography = Typography(
    // Web `display`: 36px / 800 / 1.2
    displaySmall = TextStyle(
        fontFamily = InterfaceFontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 36.sp,
        lineHeight = 43.sp,
    ),
    // Web `heading.large`: 28px / 700 / 1.3
    headlineLarge = TextStyle(
        fontFamily = InterfaceFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
    ),
    // Web `heading.medium`: 24px / 700 / 1.3
    headlineMedium = TextStyle(
        fontFamily = InterfaceFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 31.sp,
    ),
    // Web `heading.small`: 20px / 700 / 1.4
    headlineSmall = TextStyle(
        fontFamily = InterfaceFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = InterfaceFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
    ),
    // Web `heading.compact`: 16px / 700 / 1.4
    titleMedium = TextStyle(
        fontFamily = InterfaceFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = InterfaceFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 21.sp,
    ),
    // Web `body.default`: 15px / 400 / 1.6
    bodyLarge = TextStyle(
        fontFamily = InterfaceFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 24.sp,
    ),
    // Web `body.small`: 13px / 400 / 1.5
    bodyMedium = TextStyle(
        fontFamily = InterfaceFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = InterfaceFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 17.sp,
    ),
    // Web `button.default`: 15px / 600 / 1
    labelLarge = TextStyle(
        fontFamily = InterfaceFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
    ),
    // Web `button.small`: 13px / 600 / 1
    labelMedium = TextStyle(
        fontFamily = InterfaceFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    // Web `caption`: 12px / 500 / 1.4
    labelSmall = TextStyle(
        fontFamily = InterfaceFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 17.sp,
    ),
)

/** Corner radii ported from the Web `radius` tokens (8 / 12 / 16 / 20). */
val StudyMentorShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp),
)
