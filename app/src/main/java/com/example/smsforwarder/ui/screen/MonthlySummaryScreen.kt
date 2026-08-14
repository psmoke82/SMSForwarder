package com.example.smsforwarder.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.smsforwarder.domain.model.MonthlySummaryEntry
import com.example.smsforwarder.domain.parser.SummationPeriodEngine
import com.example.smsforwarder.ui.MainViewModel
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class YearGroup(
    val yearLabel: String,
    val totalAmount: Long,
    val months: List<MonthlySummaryEntry>
)

private data class FilterGroup(
    val filterId: Long,
    val filterName: String,
    val totalAmount: Long,
    val years: List<YearGroup>
)

private fun buildFilterGroups(summaries: List<MonthlySummaryEntry>): List<FilterGroup> {
    val yearFormat = SimpleDateFormat("yy", Locale.KOREA)

    return summaries
        .groupBy { it.filterId }
        .map { (filterId, entries) ->
            val sortedEntries = entries.sortedByDescending { it.periodStartTimestamp }
            val filterName = sortedEntries.first().filterName

            val years = sortedEntries
                .groupBy { yearFormat.format(Date(it.periodStartTimestamp)) }
                .map { (yearLabel, monthEntries) ->
                    YearGroup(
                        yearLabel = yearLabel,
                        totalAmount = monthEntries.sumOf { it.totalAmount },
                        months = monthEntries
                    )
                }
                .sortedByDescending { it.months.first().periodStartTimestamp }

            FilterGroup(
                filterId = filterId,
                filterName = filterName,
                totalAmount = entries.sumOf { it.totalAmount },
                years = years
            )
        }
        .sortedBy { it.filterName }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthlySummaryScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val summaries by viewModel.monthlySummaries.collectAsState()
    val filters by viewModel.filters.collectAsState()
    val filterGroups = remember(summaries) { buildFilterGroups(summaries) }

    // The period label each filter is currently accumulating into, derived from its live
    // settings (not the history table) — used to mark that one row as "집계중".
    val currentPeriodLabelByFilterId = remember(filters) {
        filters
            .filter { it.isSummationEnabled }
            .associate { it.id to SummationPeriodEngine.getMonthlyPeriodLabel(it) }
    }

    val expandedFilters = remember { mutableStateMapOf<Long, Boolean>() }
    val expandedYears = remember { mutableStateMapOf<String, Boolean>() }

    // Default each filter open with only its most recent year expanded — older years stay
    // collapsed until the user drills in. Guarded by containsKey so it only sets the initial
    // state once per filter/year and never clobbers a toggle the user already made.
    LaunchedEffect(filterGroups) {
        filterGroups.forEach { group ->
            if (!expandedFilters.containsKey(group.filterId)) {
                expandedFilters[group.filterId] = true
            }
            val mostRecentYear = group.years.firstOrNull()
            if (mostRecentYear != null) {
                val yearKey = "${group.filterId}-${mostRecentYear.yearLabel}"
                if (!expandedYears.containsKey(yearKey)) {
                    expandedYears[yearKey] = true
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("월간 금액합산 확인") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        if (filterGroups.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "저장된 월간 합산 내역이 없습니다.",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filterGroups, key = { it.filterId }) { group ->
                    FilterGroupCard(
                        group = group,
                        currentPeriodLabel = currentPeriodLabelByFilterId[group.filterId],
                        isExpanded = expandedFilters[group.filterId] == true,
                        onToggle = {
                            expandedFilters[group.filterId] = expandedFilters[group.filterId] != true
                        },
                        expandedYears = expandedYears,
                        onToggleYear = { yearKey ->
                            expandedYears[yearKey] = expandedYears[yearKey] != true
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterGroupCard(
    group: FilterGroup,
    currentPeriodLabel: String?,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    expandedYears: Map<String, Boolean>,
    onToggleYear: (String) -> Unit
) {
    val numberFormat = remember { NumberFormat.getNumberInstance(Locale.KOREA) }

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = group.filterName,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "누적 합계: ${numberFormat.format(group.totalAmount)}원",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    group.years.forEach { year ->
                        val yearKey = "${group.filterId}-${year.yearLabel}"
                        val isYearExpanded = expandedYears[yearKey] == true

                        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggleYear(yearKey) },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${year.yearLabel}년",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${numberFormat.format(year.totalAmount)}원",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = if (isYearExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null,
                                    modifier = Modifier.width(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        AnimatedVisibility(visible = isYearExpanded) {
                            Column(modifier = Modifier.padding(top = 4.dp, start = 8.dp)) {
                                year.months.forEach { month ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 3.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = if (month.periodLabel == currentPeriodLabel) {
                                                "${month.periodLabel} (집계중)"
                                            } else {
                                                month.periodLabel
                                            },
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (month.periodLabel == currentPeriodLabel) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            }
                                        )
                                        Text(
                                            text = "${numberFormat.format(month.totalAmount)}원",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
