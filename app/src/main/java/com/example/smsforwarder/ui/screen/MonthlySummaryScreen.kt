package com.example.smsforwarder.ui.screen

import android.widget.Toast
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smsforwarder.domain.model.Filter
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

private fun buildFilterGroups(
    filters: List<Filter>,
    summaries: List<MonthlySummaryEntry>
): List<FilterGroup> {
    val yearFormat = SimpleDateFormat("yy", Locale.KOREA)
    val periodFormat = SimpleDateFormat("yy.MM", Locale.KOREA)

    // 1. Only include filters that have summation enabled AND are active
    val summationFilters = filters.filter { it.isSummationEnabled && it.isActive }

    return summationFilters.map { filter ->
        val dbSummaries = summaries.filter { it.filterId == filter.id }
        val nowCal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val currentMonthlyCycleStart = SummationPeriodEngine.getMonthlyCycleStartTimestamp(nowCal, filter)
        val currentPeriodLabel = SummationPeriodEngine.getMonthlyPeriodLabel(filter)
        val currentYearLabel = SummationPeriodEngine.getYearlyPeriodLabel(filter)

        // Calculate earliest period to display
        val earliestDbTime = dbSummaries.minOfOrNull { it.periodStartTimestamp }
        val earliestFilterTime = filter.lastMonthlyResetTime.takeIf { it > 0 }
        val earliestTime = listOfNotNull(earliestDbTime, earliestFilterTime, currentMonthlyCycleStart).minOrNull() ?: currentMonthlyCycleStart

        val startCal = java.util.Calendar.getInstance().apply {
            timeInMillis = earliestTime
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }

        val currentCal = java.util.Calendar.getInstance().apply {
            timeInMillis = currentMonthlyCycleStart
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }

        val monthEntries = mutableListOf<MonthlySummaryEntry>()
        val addedLabels = mutableSetOf<String>()

        val iterCal = (startCal.clone() as java.util.Calendar).apply {
            set(java.util.Calendar.DAY_OF_MONTH, 15) // Use mid-month to prevent overflow issues on month addition
        }
        val endThreshold = (currentCal.clone() as java.util.Calendar).apply {
            set(java.util.Calendar.DAY_OF_MONTH, 20)
        }

        while (iterCal.before(endThreshold) || (iterCal.get(java.util.Calendar.YEAR) == endThreshold.get(java.util.Calendar.YEAR) && iterCal.get(java.util.Calendar.MONTH) == endThreshold.get(java.util.Calendar.MONTH))) {
            val cycleStart = SummationPeriodEngine.getMonthlyCycleStartTimestamp(iterCal, filter)
            val label = periodFormat.format(Date(cycleStart))

            if (addedLabels.add(label)) {
                if (label == currentPeriodLabel) {
                    val existingDb = dbSummaries.firstOrNull { it.periodLabel == label }
                    monthEntries.add(
                        MonthlySummaryEntry(
                            id = existingDb?.id ?: 0L,
                            filterId = filter.id,
                            filterName = filter.name,
                            periodLabel = label,
                            periodStartTimestamp = cycleStart,
                            totalAmount = filter.monthlyTotal // 당해기간 실시간 집계 및 유저 수정값 반영
                        )
                    )
                } else {
                    val existingDb = dbSummaries.firstOrNull { it.periodLabel == label }
                    monthEntries.add(
                        MonthlySummaryEntry(
                            id = existingDb?.id ?: 0L,
                            filterId = filter.id,
                            filterName = filter.name,
                            periodLabel = label,
                            periodStartTimestamp = cycleStart,
                            totalAmount = existingDb?.totalAmount ?: 0L // 방안 B: 결제 없는 월은 0원 자동 채움
                        )
                    )
                }
            }

            if (iterCal.get(java.util.Calendar.YEAR) == endThreshold.get(java.util.Calendar.YEAR) && iterCal.get(java.util.Calendar.MONTH) == endThreshold.get(java.util.Calendar.MONTH)) {
                break
            }
            iterCal.add(java.util.Calendar.MONTH, 1)
        }

        // 혹시 순회에서 빠진 DB 스냅샷이 있다면 추가
        dbSummaries.forEach { dbEntry ->
            if (addedLabels.add(dbEntry.periodLabel)) {
                monthEntries.add(
                    if (dbEntry.periodLabel == currentPeriodLabel) {
                        dbEntry.copy(totalAmount = filter.monthlyTotal)
                    } else {
                        dbEntry
                    }
                )
            }
        }

        // 당해월이 포함되지 않았을 경우 강제 추가 보장
        if (addedLabels.add(currentPeriodLabel)) {
            monthEntries.add(
                MonthlySummaryEntry(
                    id = 0L,
                    filterId = filter.id,
                    filterName = filter.name,
                    periodLabel = currentPeriodLabel,
                    periodStartTimestamp = currentMonthlyCycleStart,
                    totalAmount = filter.monthlyTotal
                )
            )
        }

        val sortedEntries = monthEntries.sortedByDescending { it.periodStartTimestamp }

        val years = sortedEntries
            .groupBy { yearFormat.format(Date(it.periodStartTimestamp)) }
            .map { (yearLabel, groupMonthEntries) ->
                YearGroup(
                    yearLabel = yearLabel,
                    // The current year shows the filter's own yearlyTotal — the figure the
                    // user can edit and the one %ytot% forwards — rather than a sum of the
                    // rows below it. Earlier years have no stored counterpart (yearlyTotal
                    // only ever holds the running year), so those stay derived.
                    totalAmount = if (yearLabel == currentYearLabel) {
                        filter.yearlyTotal
                    } else {
                        groupMonthEntries.sumOf { it.totalAmount }
                    },
                    months = groupMonthEntries.sortedByDescending { it.periodStartTimestamp }
                )
            }
            .sortedByDescending { it.months.firstOrNull()?.periodStartTimestamp ?: 0L }

        FilterGroup(
            filterId = filter.id,
            filterName = filter.name,
            // Summed from the year rows, not the months, so the card header stays consistent
            // with the yearly figures shown directly beneath it.
            totalAmount = years.sumOf { it.totalAmount },
            years = years
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthlySummaryScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val summaries by viewModel.monthlySummaries.collectAsState()
    val filters by viewModel.filters.collectAsState()
    val filterGroups = remember(filters, summaries) { buildFilterGroups(filters, summaries) }

    val currentPeriodLabelByFilterId = remember(filters) {
        filters
            .filter { it.isSummationEnabled && it.isActive }
            .associate { it.id to SummationPeriodEngine.getMonthlyPeriodLabel(it) }
    }

    val expandedFilters = remember { mutableStateMapOf<Long, Boolean>() }
    val expandedYears = remember { mutableStateMapOf<String, Boolean>() }

    var summaryToDelete by remember { mutableStateOf<MonthlySummaryEntry?>(null) }
    var filterGroupToDelete by remember { mutableStateOf<FilterGroup?>(null) }

    // Auto expand the most recent year for each filter
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

    // Dialog: Delete single monthly entry
    if (summaryToDelete != null) {
        val target = summaryToDelete!!
        AlertDialog(
            onDismissRequest = { summaryToDelete = null },
            title = { Text("월별 합산 내역 삭제", fontWeight = FontWeight.Bold) },
            text = {
                Text("'${target.filterName}'의 ${target.periodLabel} 합산 내역을 삭제하시겠습니까?\n삭제된 내역은 복구할 수 없습니다.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        summaryToDelete = null
                        viewModel.deleteMonthlySummary(target.filterId, target.periodLabel, target.id)
                        Toast.makeText(context, "${target.periodLabel} 내역이 삭제되었습니다.", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("삭제")
                }
            },
            dismissButton = {
                TextButton(onClick = { summaryToDelete = null }) {
                    Text("취소")
                }
            }
        )
    }

    // Dialog: Delete all monthly entries for a filter
    if (filterGroupToDelete != null) {
        val target = filterGroupToDelete!!
        AlertDialog(
            onDismissRequest = { filterGroupToDelete = null },
            title = { Text("필터 합산 내역 전체 삭제", fontWeight = FontWeight.Bold) },
            text = {
                Text("'${target.filterName}'의 모든 월간 합산 내역을 삭제하시겠습니까?\n(필터 설정은 유지되며, 앞으로 수신되는 내역은 계속 합산됩니다.)")
            },
            confirmButton = {
                Button(
                    onClick = {
                        filterGroupToDelete = null
                        viewModel.deleteAllMonthlySummariesForFilter(target.filterId)
                        Toast.makeText(context, "'${target.filterName}'의 모든 합산 내역이 초기화되었습니다.", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("전체 삭제")
                }
            },
            dismissButton = {
                TextButton(onClick = { filterGroupToDelete = null }) {
                    Text("취소")
                }
            }
        )
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
                    text = "금액합산 기능이 켜진 필터가 없습니다.",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.padding(top = 4.dp))
                Text(
                    text = "필터 설정에서 '금액합산 기능'을 활성화해 보세요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
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
                        },
                        onDeleteAllForFilter = {
                            filterGroupToDelete = group
                        },
                        onDeleteMonth = { monthEntry ->
                            summaryToDelete = monthEntry
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
    onToggleYear: (String) -> Unit,
    onDeleteAllForFilter: () -> Unit,
    onDeleteMonth: (MonthlySummaryEntry) -> Unit
) {
    val numberFormat = remember { NumberFormat.getNumberInstance(Locale.KOREA) }

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle() },
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

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onDeleteAllForFilter,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "전체 내역 삭제",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        AnimatedVisibility(visible = isYearExpanded) {
                            Column(modifier = Modifier.padding(top = 4.dp, start = 8.dp)) {
                                year.months.forEach { month ->
                                    val isCurrent = month.periodLabel == currentPeriodLabel

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 2.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = if (isCurrent) {
                                                "${month.periodLabel} (집계중)"
                                            } else {
                                                month.periodLabel
                                            },
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontSize = 13.5.sp,
                                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                                            ),
                                            color = if (isCurrent) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            }
                                        )

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = "${numberFormat.format(month.totalAmount)}원",
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    fontSize = 13.5.sp,
                                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium
                                                )
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            IconButton(
                                                onClick = { onDeleteMonth(month) },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "월별 내역 삭제",
                                                    tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
                                                    modifier = Modifier.size(16.dp)
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
    }
}

