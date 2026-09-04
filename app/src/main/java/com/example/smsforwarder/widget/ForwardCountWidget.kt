package com.example.smsforwarder.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.example.smsforwarder.MainActivity
import com.example.smsforwarder.data.local.AppDatabase
import com.example.smsforwarder.data.local.dao.LogCountResult
import com.example.smsforwarder.data.repository.ForwarderRepositoryImpl
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val appWidgetIdKey = ActionParameters.Key<Int>(AppWidgetManager.EXTRA_APPWIDGET_ID)
private val targetKey = ActionParameters.Key<String>(WidgetNavKeys.EXTRA_TARGET)
private val filterNameParamKey = ActionParameters.Key<String>(WidgetNavKeys.EXTRA_FILTER_NAME)

class ForwardCountWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition

    override val sizeMode = SizeMode.Responsive(
        setOf(WidgetSizes.XSMALL, WidgetSizes.SMALL, WidgetSizes.MEDIUM, WidgetSizes.LARGE)
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)
        val filterName = prefs[WidgetPreferenceKeys.KEY_FILTER_NAME]
        val period = runCatching {
            CountPeriod.valueOf(prefs[WidgetPreferenceKeys.KEY_PERIOD] ?: CountPeriod.ALL.name)
        }.getOrDefault(CountPeriod.ALL)

        val database = AppDatabase.getInstance(context)
        val repository = ForwarderRepositoryImpl(database.filterDao(), database.logDao(), database.filterMonthlySummaryDao())
        val counts = repository.getLogCounts(filterName, period.sinceTimestamp())
        val backgroundAlphaPercent = prefs[WidgetPreferenceKeys.KEY_BACKGROUND_ALPHA_PERCENT]
            ?: WidgetPreferenceKeys.DEFAULT_BACKGROUND_ALPHA_PERCENT

        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val lastUpdated = System.currentTimeMillis()
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, id) { current ->
            current.toMutablePreferences().apply { this[WidgetPreferenceKeys.KEY_LAST_UPDATED] = lastUpdated }
        }

        provideContent {
            CountWidgetContent(
                filterName = filterName ?: "전체",
                period = period,
                counts = counts,
                lastUpdated = lastUpdated,
                appWidgetId = appWidgetId,
                backgroundAlphaPercent = backgroundAlphaPercent
            )
        }
    }
}

class ForwardCountWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ForwardCountWidget()
}

class RefreshCountWidgetAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        ForwardCountWidget().update(context, glanceId)
    }
}

@Composable
private fun CountWidgetContent(
    filterName: String,
    period: CountPeriod,
    counts: LogCountResult,
    lastUpdated: Long,
    appWidgetId: Int,
    backgroundAlphaPercent: Int
) {
    val size = LocalSize.current
    val openLogsAction = actionStartActivity<MainActivity>(
        parameters = actionParametersOf(
            targetKey to WidgetNavKeys.TARGET_LOG_LIST,
            filterNameParamKey to filterName
        )
    )

    when {
        size.width <= WidgetSizes.XSMALL.width -> XSmallCountContent(counts, openLogsAction, backgroundAlphaPercent)
        size.width <= WidgetSizes.SMALL.width -> SmallCountContent(filterName, counts, openLogsAction, backgroundAlphaPercent)
        size.width < WidgetSizes.LARGE.width -> MediumCountContent(filterName, period, counts, openLogsAction, appWidgetId, backgroundAlphaPercent)
        else -> LargeCountContent(filterName, period, counts, lastUpdated, openLogsAction, appWidgetId, backgroundAlphaPercent)
    }
}

@Composable
private fun XSmallCountContent(counts: LogCountResult, openAction: Action, backgroundAlphaPercent: Int) {
    Column(
        modifier = GlanceModifier.fillMaxSize().background(WidgetColors.backgroundWithAlpha(backgroundAlphaPercent)).cornerRadius(12.dp)
            .clickable(openAction),
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
        verticalAlignment = Alignment.Vertical.CenterVertically
    ) {
        Text(
            text = "${counts.total}",
            maxLines = 1,
            style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = WidgetColors.onBackground)
        )
    }
}

@Composable
private fun SmallCountContent(filterName: String, counts: LogCountResult, openAction: Action, backgroundAlphaPercent: Int) {
    Column(
        modifier = GlanceModifier.fillMaxSize().background(WidgetColors.backgroundWithAlpha(backgroundAlphaPercent)).cornerRadius(12.dp)
            .clickable(openAction)
            .padding(horizontal = 6.dp),
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
        verticalAlignment = Alignment.Vertical.CenterVertically
    ) {
        Text(
            text = filterName,
            maxLines = 1,
            style = TextStyle(fontSize = 9.sp, color = WidgetColors.onBackgroundVariant)
        )
        Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
            Text(
                text = "${counts.total}",
                style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, color = WidgetColors.onBackground)
            )
            Spacer(modifier = GlanceModifier.width(2.dp))
            Text(text = "건", style = TextStyle(fontSize = 11.sp, color = WidgetColors.onBackgroundVariant))
        }
    }
}

@Composable
private fun MediumCountContent(
    filterName: String,
    period: CountPeriod,
    counts: LogCountResult,
    openAction: Action,
    appWidgetId: Int,
    backgroundAlphaPercent: Int
) {
    Column(
        modifier = GlanceModifier.fillMaxSize().background(WidgetColors.backgroundWithAlpha(backgroundAlphaPercent)).cornerRadius(12.dp).padding(10.dp)
    ) {
        WidgetTitleRow(
            title = filterName,
            subtitle = period.label,
            onRefresh = actionRunCallback<RefreshCountWidgetAction>(),
            onSettings = actionStartActivity<CountWidgetConfigActivity>(
                parameters = actionParametersOf(appWidgetIdKey to appWidgetId)
            )
        )
        Column(
            modifier = GlanceModifier.fillMaxSize().clickable(openAction),
            horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            Text(
                text = "${counts.successCount}/${counts.total}",
                style = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, color = WidgetColors.onBackground)
            )
            Text(text = period.label, style = TextStyle(fontSize = 11.sp, color = WidgetColors.onBackgroundVariant))
        }
    }
}

@Composable
private fun LargeCountContent(
    filterName: String,
    period: CountPeriod,
    counts: LogCountResult,
    lastUpdated: Long,
    openAction: Action,
    appWidgetId: Int,
    backgroundAlphaPercent: Int
) {
    val failCount = counts.total - counts.successCount
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.KOREA) }
    Column(
        modifier = GlanceModifier.fillMaxSize().background(WidgetColors.backgroundWithAlpha(backgroundAlphaPercent)).cornerRadius(12.dp).padding(10.dp)
    ) {
        WidgetTitleRow(
            title = filterName,
            subtitle = period.label,
            onRefresh = actionRunCallback<RefreshCountWidgetAction>(),
            onSettings = actionStartActivity<CountWidgetConfigActivity>(
                parameters = actionParametersOf(appWidgetIdKey to appWidgetId)
            )
        )
        Column(
            modifier = GlanceModifier.fillMaxWidth().defaultWeight().clickable(openAction).padding(top = 4.dp),
            horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            Text(
                text = "성공 ${counts.successCount} · 실패 $failCount",
                style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, color = WidgetColors.onBackground)
            )
        }
        Text(
            text = "${timeFormat.format(Date(lastUpdated))} 갱신",
            style = TextStyle(fontSize = 10.sp, color = WidgetColors.onBackgroundVariant, textAlign = TextAlign.End),
            modifier = GlanceModifier.fillMaxWidth()
        )
    }
}
