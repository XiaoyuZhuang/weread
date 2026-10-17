package com.xiaoyu.weread

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.view.HapticFeedbackConstants
import android.view.View

data class Palette(
    val dark: Boolean,
    val background: Int,
    val surface: Int,
    val heading: Int,
    val text: Int,
    val muted: Int,
    val panel: Int,
    val accent: Int,
    val accentText: Int
)

object Appearance {
    fun resolve(context: Context): Palette {
        val pref = SecureConfig(context).themeMode
        val systemDark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val dark = when (pref) {
            "light" -> false
            "dark" -> true
            else -> systemDark
        }
        return if (dark) Palette(
            true, Color.rgb(13, 19, 30), Color.rgb(28, 37, 52),
            Color.rgb(240, 246, 255), Color.rgb(226, 234, 244),
            Color.rgb(161, 177, 199), Color.rgb(37, 51, 70),
            Color.rgb(48, 141, 245), Color.WHITE
        ) else Palette(
            false, Color.rgb(244, 248, 252), Color.WHITE,
            Color.rgb(22, 45, 68), Color.rgb(40, 58, 76),
            Color.rgb(95, 115, 131), Color.rgb(228, 237, 248),
            Color.rgb(20, 129, 231), Color.WHITE
        )
    }

    /** Respects the phone's own haptic setting and never requires VIBRATE permission. */
    fun tap(view: View) {
        if (SecureConfig(view.context).hapticEnabled) {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }
    }
}
