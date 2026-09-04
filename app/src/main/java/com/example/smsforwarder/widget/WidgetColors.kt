package com.example.smsforwarder.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.glance.LocalContext
import androidx.glance.unit.ColorProvider
import com.example.smsforwarder.R

/** Day/night widget palette backed by res/values(-night)/colors.xml, so Glance auto-switches with the system theme. */
object WidgetColors {
    val background = ColorProvider(R.color.widget_background)
    val onBackground = ColorProvider(R.color.widget_on_background)
    val onBackgroundVariant = ColorProvider(R.color.widget_on_background_variant)

    /**
     * Same day/night background, but with the user's configured opacity applied. Resolved via
     * [ContextCompat.getColor] (which already picks the day/night variant for the current
     * context) rather than a resource-based [ColorProvider], since ColorProvider has no alpha
     * parameter — the alpha has to be baked into a concrete [Color] up front.
     */
    @Composable
    fun backgroundWithAlpha(alphaPercent: Int): ColorProvider {
        val context = LocalContext.current
        val alpha = alphaPercent.coerceIn(
            WidgetPreferenceKeys.MIN_BACKGROUND_ALPHA_PERCENT,
            100
        ) / 100f
        val baseColor = Color(ContextCompat.getColor(context, R.color.widget_background))
        return ColorProvider(baseColor.copy(alpha = alpha))
    }
}
