package com.example.smsforwarder.widget

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.glance.state.PreferencesGlanceStateDefinition

/**
 * Single push-refresh entry point called from the SMS ingestion pipelines
 * (SmsReceiver, SmsNotificationListenerService) right after a log row is inserted.
 * Centralizes which widget instances need recomputing so both call sites stay one line.
 */
object WidgetUpdater {

    suspend fun notifyLogInserted(context: Context, filterName: String) {
        try {
            // Count widgets: every instance's aggregate window potentially includes "now",
            // so refresh all of them unconditionally — the aggregate query is cheap and
            // widget instance counts are small, making per-instance filtering not worth it.
            ForwardCountWidget().updateAll(context)

            // Amount widgets: only the instance(s) configured for this exact filter are
            // affected (their monthlyTotal is untouched by other filters' events), so only
            // those get recomputed.
            val manager = GlanceAppWidgetManager(context)
            val glanceIds = manager.getGlanceIds(AmountSummaryWidget::class.java)
            for (id in glanceIds) {
                val prefs = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)
                if (prefs[WidgetPreferenceKeys.KEY_FILTER_NAME] == filterName) {
                    AmountSummaryWidget().update(context, id)
                }
            }
        } catch (e: Exception) {
            // Widgets are a best-effort UI surface — never let a widget refresh failure
            // interrupt the SMS forwarding pipeline that called this.
            Log.e("WidgetUpdater", "위젯 갱신 중 오류 발생", e)
        }
    }
}
