package com.elenglish.studymentor.ui.theme

import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode

/**
 * True when the user has switched animations off in system settings
 * ("Remove animations" / Developer options animation scales).
 *
 * Reads [Settings.Global.ANIMATOR_DURATION_SCALE] and
 * [Settings.Global.TRANSITION_ANIMATION_SCALE]; either being `0` disables motion.
 */
fun isReducedMotionEnabled(context: Context): Boolean {
    val resolver = context.contentResolver
    val animatorScale = Settings.Global.getFloat(
        resolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        DEFAULT_ANIMATION_SCALE,
    )
    val transitionScale = Settings.Global.getFloat(
        resolver,
        Settings.Global.TRANSITION_ANIMATION_SCALE,
        DEFAULT_ANIMATION_SCALE,
    )
    return animatorScale == 0f || transitionScale == 0f
}

private const val DEFAULT_ANIMATION_SCALE = 1.0f

@Composable
@ReadOnlyComposable
internal fun rememberSystemReducedMotion(): Boolean {
    // Previews and Compose tooling have no meaningful system setting.
    if (LocalInspectionMode.current) return false
    return isReducedMotionEnabled(LocalContext.current)
}
