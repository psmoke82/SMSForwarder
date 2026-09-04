package com.example.smsforwarder.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.example.smsforwarder.data.local.AppDatabase
import com.example.smsforwarder.domain.model.Filter
import kotlinx.coroutines.flow.first

/** Existing per-instance config a config activity should pre-fill with when reopened (e.g. via the widget's own settings icon), rather than always resetting to defaults. */
data class ExistingWidgetConfig(
    val filterId: Long,
    val filterName: String?,
    val period: CountPeriod,
    val backgroundAlphaPercent: Int
)

/** One-shot filter loads for the two config activities — avoids collecting an ongoing Flow just to populate a picker. */
object WidgetConfigSupport {
    suspend fun loadAllFilters(context: Context): List<Filter> {
        return AppDatabase.getInstance(context).filterDao().getAllFilters().first().map { it.toDomain() }
    }

    suspend fun loadSummationEnabledFilters(context: Context): List<Filter> {
        return loadAllFilters(context).filter { it.isSummationEnabled }
    }

    /** Reads whatever this widget instance last saved, so reopening its settings doesn't silently reset the selection. */
    suspend fun loadExistingConfig(context: Context, appWidgetId: Int): ExistingWidgetConfig {
        val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
        val prefs = getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId)
        val period = runCatching {
            CountPeriod.valueOf(prefs[WidgetPreferenceKeys.KEY_PERIOD] ?: CountPeriod.ALL.name)
        }.getOrDefault(CountPeriod.ALL)
        return ExistingWidgetConfig(
            filterId = prefs[WidgetPreferenceKeys.KEY_FILTER_ID] ?: 0L,
            filterName = prefs[WidgetPreferenceKeys.KEY_FILTER_NAME],
            period = period,
            backgroundAlphaPercent = prefs[WidgetPreferenceKeys.KEY_BACKGROUND_ALPHA_PERCENT]
                ?: WidgetPreferenceKeys.DEFAULT_BACKGROUND_ALPHA_PERCENT
        )
    }
}
