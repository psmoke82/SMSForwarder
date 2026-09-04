package com.example.smsforwarder.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.lifecycle.lifecycleScope
import com.example.smsforwarder.domain.model.Filter
import com.example.smsforwarder.ui.theme.SMSForwarderTheme
import kotlinx.coroutines.launch

class AmountWidgetConfigActivity : ComponentActivity() {

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
                    AmountConfigScreen(
                        appWidgetId = appWidgetId,
                        onSave = { filter, alphaPercent -> save(filter, alphaPercent) }
                    )
                }
            }
        }
    }

    private fun save(filter: Filter, alphaPercent: Int) {
        lifecycleScope.launch {
            val glanceId = GlanceAppWidgetManager(this@AmountWidgetConfigActivity).getGlanceIdBy(appWidgetId)
            updateAppWidgetState(this@AmountWidgetConfigActivity, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                prefs.toMutablePreferences().apply {
                    this[WidgetPreferenceKeys.KEY_FILTER_ID] = filter.id
                    this[WidgetPreferenceKeys.KEY_FILTER_NAME] = filter.name
                    this[WidgetPreferenceKeys.KEY_BACKGROUND_ALPHA_PERCENT] = alphaPercent
                }
            }
            AmountSummaryWidget().update(this@AmountWidgetConfigActivity, glanceId)

            val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            setResult(Activity.RESULT_OK, resultValue)
            finish()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@androidx.compose.runtime.Composable
private fun AmountConfigScreen(
    appWidgetId: Int,
    onSave: (Filter, backgroundAlphaPercent: Int) -> Unit
) {
    var filters by remember { mutableStateOf<List<Filter>>(emptyList()) }
    var selectedFilterId by remember { mutableStateOf(0L) }
    var alphaPercent by remember { mutableStateOf(WidgetPreferenceKeys.DEFAULT_BACKGROUND_ALPHA_PERCENT) }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        filters = WidgetConfigSupport.loadSummationEnabledFilters(context)
        val existing = WidgetConfigSupport.loadExistingConfig(context, appWidgetId)
        selectedFilterId = if (filters.any { it.id == existing.filterId }) existing.filterId else filters.firstOrNull()?.id ?: 0L
        alphaPercent = existing.backgroundAlphaPercent
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("금액 합산 위젯 설정") }) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            if (filters.isEmpty()) {
                Text(
                    "금액합산 기능이 켜진 필터가 없습니다.\n필터 편집에서 금액합산 기능을 먼저 활성화해 주세요.",
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                Text("대상 필터 (금액합산 활성 필터만 표시)", style = MaterialTheme.typography.titleSmall)
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    filters.forEach { filter ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(selected = selectedFilterId == filter.id, onClick = { selectedFilterId = filter.id }),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = selectedFilterId == filter.id, onClick = { selectedFilterId = filter.id })
                            Text(filter.name)
                        }
                    }
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
                    filters.firstOrNull { it.id == selectedFilterId }?.let { onSave(it, alphaPercent) }
                },
                enabled = filters.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
            ) {
                Text("위젯 저장")
            }
        }
    }
}
