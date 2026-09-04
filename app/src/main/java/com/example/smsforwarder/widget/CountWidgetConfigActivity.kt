package com.example.smsforwarder.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.lifecycle.lifecycleScope
import com.example.smsforwarder.domain.model.Filter
import com.example.smsforwarder.ui.theme.SMSForwarderTheme
import kotlinx.coroutines.launch

class CountWidgetConfigActivity : ComponentActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        setResult(RESULT_CANCELED)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setContent {
            SMSForwarderTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    CountConfigScreen(
                        appWidgetId = appWidgetId,
                        onSave = { filterId, filterName, period, alphaPercent -> save(filterId, filterName, period, alphaPercent) }
                    )
                }
            }
        }
    }

    private fun save(filterId: Long, filterName: String?, period: CountPeriod, alphaPercent: Int) {
        lifecycleScope.launch {
            val glanceId = GlanceAppWidgetManager(this@CountWidgetConfigActivity).getGlanceIdBy(appWidgetId)
            updateAppWidgetState(this@CountWidgetConfigActivity, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                prefs.toMutablePreferences().apply {
                    if (filterId > 0 && filterName != null) {
                        this[WidgetPreferenceKeys.KEY_FILTER_ID] = filterId
                        this[WidgetPreferenceKeys.KEY_FILTER_NAME] = filterName
                    } else {
                        remove(WidgetPreferenceKeys.KEY_FILTER_ID)
                        remove(WidgetPreferenceKeys.KEY_FILTER_NAME)
                    }
                    this[WidgetPreferenceKeys.KEY_PERIOD] = period.name
                    this[WidgetPreferenceKeys.KEY_BACKGROUND_ALPHA_PERCENT] = alphaPercent
                }
            }
            ForwardCountWidget().update(this@CountWidgetConfigActivity, glanceId)

            val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            setResult(Activity.RESULT_OK, resultValue)
            finish()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@androidx.compose.runtime.Composable
private fun CountConfigScreen(
    appWidgetId: Int,
    onSave: (filterId: Long, filterName: String?, period: CountPeriod, backgroundAlphaPercent: Int) -> Unit
) {
    var filters by remember { mutableStateOf<List<Filter>>(emptyList()) }
    var selectedFilterId by remember { mutableStateOf(0L) }
    var selectedPeriod by remember { mutableStateOf(CountPeriod.ALL) }
    var alphaPercent by remember { mutableStateOf(WidgetPreferenceKeys.DEFAULT_BACKGROUND_ALPHA_PERCENT) }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        filters = WidgetConfigSupport.loadAllFilters(context)
        val existing = WidgetConfigSupport.loadExistingConfig(context, appWidgetId)
        selectedFilterId = existing.filterId
        selectedPeriod = existing.period
        alphaPercent = existing.backgroundAlphaPercent
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("재전송 건수 위젯 설정") }) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text("대상 필터", style = MaterialTheme.typography.titleSmall)
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                FilterOptionRow(
                    label = "전체",
                    selected = selectedFilterId == 0L,
                    onClick = { selectedFilterId = 0L }
                )
                filters.forEach { filter ->
                    FilterOptionRow(
                        label = filter.name,
                        selected = selectedFilterId == filter.id,
                        onClick = { selectedFilterId = filter.id }
                    )
                }
            }

            Text("기간", style = MaterialTheme.typography.titleSmall)
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                CountPeriod.values().forEach { period ->
                    FilterOptionRow(
                        label = period.label,
                        selected = selectedPeriod == period,
                        onClick = { selectedPeriod = period }
                    )
                }
            }

            Text("배경 투명도: $alphaPercent%", style = MaterialTheme.typography.titleSmall)
            Slider(
                value = alphaPercent / 100f,
                onValueChange = { alphaPercent = (it * 100).toInt().coerceIn(WidgetPreferenceKeys.MIN_BACKGROUND_ALPHA_PERCENT, 100) },
                valueRange = (WidgetPreferenceKeys.MIN_BACKGROUND_ALPHA_PERCENT / 100f)..1f,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            )

            Button(
                onClick = {
                    val selectedFilter = filters.firstOrNull { it.id == selectedFilterId }
                    onSave(selectedFilter?.id ?: 0L, selectedFilter?.name, selectedPeriod, alphaPercent)
                },
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
            ) {
                Text("위젯 저장")
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun FilterOptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label)
    }
}
