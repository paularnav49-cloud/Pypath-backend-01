package com.pypath.app.ui.components

import android.os.Build
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView

/**
 * True when the user turned animations off (Settings › Accessibility › Remove animations, or
 * Developer options › Animator duration scale = off). Celebrations then use a short, simple version.
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
    }
}

/**
 * Short "reject" vibration for wrong answers. Uses View haptics, which need no VIBRATE permission
 * and respect the system "Touch feedback" setting. Does nothing if the device has no vibrator.
 */
@Composable
fun rememberWrongAnswerHaptic(): () -> Unit {
    val view = LocalView.current
    return remember(view) {
        {
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.REJECT
            else HapticFeedbackConstants.LONG_PRESS
            view.performHapticFeedback(type)
        }
    }
}
