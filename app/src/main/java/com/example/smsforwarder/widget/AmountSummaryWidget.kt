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
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.example.smsforwarder.MainActivity
import com.example.smsforwarder.data.local.AppDatabase
import com.example.smsforwarder.domain.model.Filter
import com.example.smsforwarder.domain.parser.SummationPeriodEngine
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val appWidgetIdKey = ActionParameters.Key<Int>(AppWidgetManager.EXTRA_APPWIDGET_ID)
private val targetKey = ActionParameters.Key<String>(WidgetNavKeys.EXTRA_TARGET)
private val filterIdParamKey = ActionParameters.Key<Long>(WidgetNavKeys.EXTRA_FILTER_ID)

class AmountSummaryWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition

    override val sizeMode = SizeMode.Responsive(
        setOf(WidgetSizes.XSMALL, WidgetSizes.SMALL, WidgetSizes.MEDIUM, WidgetSizes.LARGE)
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)
        val filterId = prefs[WidgetPreferenceKeys.KEY_FILTER_ID] ?: 0L
        val filterEntity = if (filterId > 0) {
            AppDatabase.getInstance(context).filterDao().getFilterById(filterId)
        } else null
        val filter = filterEntity?.toDomain()
        val backgroundAlphaPercent = prefs[WidgetPreferenceKeys.KEY_BACKGROUND_ALPHA_PERCENT]
            ?: WidgetPreferenceKeys.DEFAULT_BACKGROUND_ALPHA_PERCENT

        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val lastUpdated = System.currentTimeMillis()
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, id) { current ->
            current.toMutablePreferences().apply { this[WidgetPreferenceKeys.KEY_LAST_UPDATED] = lastUpdated }
        }

        provideContent {
            AmountWidgetContent(
                filter = filter,
                lastUpdated = lastUpdated,
                appWidgetId = appWidgetId,
                backgroundAlphaPercent = backgroundAlphaPercent
            )
        }
    }
}

class AmountSummaryWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AmountSummaryWidget()
}

class RefreshAmountWidgetAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        AmountSummaryWidget().update(context, glanceId)
    }
}

@Composable
private fun AmountWidgetContent(filter: Filter?, lastUpdated: Long, appWidgetId: Int, backgroundAlphaPercent: Int) {
    val size = LocalSize.current

    if (filter == null) {
        Column(
            modifier = GlanceModifier.fillMaxSize().background(WidgetColors.backgroundWithAlpha(backgroundAlphaPercent)).cornerRadius(12.dp),
            horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            Text(text = "필터 없음", style = TextStyle(fontSize = 12.sp, color = WidgetColors.onBackgroundVariant))
        }
        return
    }

    val amountFormat = remember { NumberFormat.getNumberInstance(Locale.KOREA) }
    val formattedAmount = "₩${amountFormat.format(filter.monthlyTotal)}"
    val rangeLabel = remember(filter.lastMonthlyResetTime, filter.startDayValue, filter.startDayType) {
        SummationPeriodEngine.getMonthlyPeriodRangeLabel(filter)
    }

    val openSummaryAction = actionStartActivity<MainActivity>(
        parameters = actionParametersOf(
            targetKey to WidgetNavKeys.TARGET_MONTHLY_SUMMARY,
            filterIdParamKey to filter.id
        )
    )

    when {
        size.width <= WidgetSizes.XSMALL.width -> XSmallAmountContent(filter.monthlyTotal, openSummaryAction, backgroundAlphaPercent)
        size.width <= WidgetSizes.SMALL.width -> SmallAmountContent(filter.name, formattedAmount, openSummaryAction, backgroundAlphaPercent)
        size.width < WidgetSizes.LARGE.width -> MediumAmountContent(filter.name, rangeLabel, formattedAmount, openSummaryAction, appWidgetId, backgroundAlphaPercent)
        else -> LargeAmountContent(filter.name, rangeLabel, formattedAmount, lastUpdated, openSummaryAction, appWidgetId, backgroundAlphaPercent)
    }
}

/** 40dp 폭에 통화 포맷 전체가 안 들어가므로 억/만 단위로 축약 (예: 1234만원 → "1234만"). */
private fun abbreviateKrw(amount: Long): String {
    return when {
        amount >= 100_000_000L -> "%.1f억".format(amount / 100_000_000.0)
        amount >= 10_000L -> "${amount / 10_000L}만"
        else -> amount.toString()
    }
}

@Composable
private fun XSmallAmountContent(monthlyTotal: Long, openAction: Action, backgroundAlphaPercent: Int) {
    Column(
        modifier = GlanceModifier.fillMaxSize().background(WidgetColors.backgroundWithAlpha(backgroundAlphaPercent)).cornerRadius(12.dp)
            .clickable(openAction),
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
        verticalAlignment = Alignment.Vertical.CenterVertically
    ) {
        Text(
            text = abbreviateKrw(monthlyTotal),
            maxLines = 1,
            style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = WidgetColors.onBackground)
        )
    }
}

@Composable
private fun SmallAmountContent(filterName: String, formattedAmount: String, openAction: Action, backgroundAlphaPercent: Int) {
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
        Text(
            text = formattedAmount,
            maxLines = 1,
            style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = WidgetColors.onBackground)
        )
    }
}

@Composable
private fun MediumAmountContent(
    filterName: String,
    rangeLabel: String,
    formattedAmount: String,
    openAction: Action,
    appWidgetId: Int,
    backgroundAlphaPercent: Int
) {
    Column(
        modifier = GlanceModifier.fillMaxSize().background(WidgetColors.backgroundWithAlpha(backgroundAlphaPercent)).cornerRadius(12.dp).padding(10.dp)
    ) {
        WidgetTitleRow(
            title = filterName,
            subtitle = null,
            onRefresh = actionRunCallback<RefreshAmountWidgetAction>(),
            onSettings = actionStartActivity<AmountWidgetConfigActivity>(
                parameters = actionParametersOf(appWidgetIdKey to appWidgetId)
            )
        )
        Column(
            modifier = GlanceModifier.fillMaxSize().clickable(openAction),
            horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            Text(
                text = formattedAmount,
                maxLines = 1,
                style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, color = WidgetColors.onBackground)
            )
            Text(text = rangeLabel, style = TextStyle(fontSize = 10.sp, color = WidgetColors.onBackgroundVariant))
        }
    }
}

@Composable
private fun LargeAmountContent(
    filterName: String,
    rangeLabel: String,
    formattedAmount: String,
    lastUpdated: Long,
    openAction: Action,
    appWidgetId: Int,
    backgroundAlphaPercent: Int
) {
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.KOREA) }
    Column(
        modifier = GlanceModifier.fillMaxSize().background(WidgetColors.backgroundWithAlpha(backgroundAlphaPercent)).cornerRadius(12.dp).padding(10.dp)
    ) {
        WidgetTitleRow(
            title = filterName,
            subtitle = null,
            onRefresh = actionRunCallback<RefreshAmountWidgetAction>(),
            onSettings = actionStartActivity<AmountWidgetConfigActivity>(
                parameters = actionParametersOf(appWidgetIdKey to appWidgetId)
            )
        )
        Column(
            modifier = GlanceModifier.fillMaxWidth().defaultWeight().clickable(openAction).padding(top = 4.dp),
            horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            Text(
                text = formattedAmount,
                maxLines = 1,
                style = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, color = WidgetColors.onBackground)
            )
            Text(text = rangeLabel, style = TextStyle(fontSize = 11.sp, color = WidgetColors.onBackgroundVariant))
        }
        Text(
            text = "${timeFormat.format(Date(lastUpdated))} 갱신",
            style = TextStyle(fontSize = 10.sp, color = WidgetColors.onBackgroundVariant, textAlign = TextAlign.End),
            modifier = GlanceModifier.fillMaxWidth()
        )
    }
}
