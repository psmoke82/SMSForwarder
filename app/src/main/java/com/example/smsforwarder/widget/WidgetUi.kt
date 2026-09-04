package com.example.smsforwarder.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.layout.Alignment
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle

/**
 * Shared MEDIUM/LARGE title row for both widgets: filter name (+ optional subtitle) on the
 * left, refresh/settings icon buttons (plain-text glyphs, no extra drawable assets) on the right.
 */
@Composable
fun WidgetTitleRow(
    title: String,
    subtitle: String?,
    onRefresh: Action,
    onSettings: Action
) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.Vertical.CenterVertically
    ) {
        Text(
            text = title,
            maxLines = 1,
            style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = WidgetColors.onBackground),
            modifier = GlanceModifier.defaultWeight()
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                maxLines = 1,
                style = TextStyle(fontSize = 10.sp, color = WidgetColors.onBackgroundVariant)
            )
            Spacer(modifier = GlanceModifier.width(6.dp))
        }
        Text(
            text = "⟳",
            style = TextStyle(fontSize = 14.sp, color = WidgetColors.onBackgroundVariant),
            modifier = GlanceModifier.clickable(onRefresh).padding(horizontal = 4.dp)
        )
        Text(
            text = "⚙",
            style = TextStyle(fontSize = 14.sp, color = WidgetColors.onBackgroundVariant),
            modifier = GlanceModifier.clickable(onSettings).padding(start = 4.dp)
        )
    }
}
