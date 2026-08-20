package com.example.smsforwarder.ui

import android.app.Application
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.smsforwarder.data.backup.GoogleDriveBackupManager
import com.example.smsforwarder.data.backup.LocalBackupManager
import com.example.smsforwarder.data.local.AppDatabase
import com.example.smsforwarder.data.repository.ExecutionMode
import com.example.smsforwarder.data.repository.ForwarderRepository
import com.example.smsforwarder.data.repository.ForwarderRepositoryImpl
import com.example.smsforwarder.data.repository.UserPreferencesRepository
import com.example.smsforwarder.domain.model.Filter
import com.example.smsforwarder.domain.model.ForwardLog
import com.example.smsforwarder.domain.model.MonthlySummaryEntry
import com.example.smsforwarder.domain.parser.SummationPeriodEngine
import com.example.smsforwarder.service.ForwarderForegroundService
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ForwarderRepository by lazy {
        val db = AppDatabase.getInstance(application)
        ForwarderRepositoryImpl(db.filterDao(), db.logDao(), db.filterMonthlySummaryDao())
    }

    private val preferencesRepository by lazy {
        UserPreferencesRepository(application)
    }

    private val localBackupManager by lazy {
        LocalBackupManager(application)
    }

    private val googleDriveBackupManager by lazy {
        GoogleDriveBackupManager(application)
    }

    init {
        viewModelScope.launch {
            preferencesRepository.executionMode.collect { mode ->
                val context = getApplication<Application>()
                if (mode == ExecutionMode.FOREGROUND) {
                    ForwarderForegroundService.start(context)
                } else {
                    ForwarderForegroundService.stop(context)
                }
            }
        }
    }

    val executionMode: StateFlow<ExecutionMode> by lazy {
        preferencesRepository.executionMode.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ExecutionMode.FOREGROUND
        )
    }

    val filters: StateFlow<List<Filter>> by lazy {
        repository.getAllFilters().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    }

    val logs: StateFlow<List<ForwardLog>> by lazy {
        repository.getAllLogs().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    }

    val monthlySummaries: StateFlow<List<MonthlySummaryEntry>> by lazy {
        repository.getMonthlySummaries().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    }

    fun hasSmsPermissions(): Boolean {
        val context = getApplication<Application>()
        val sendSms = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.SEND_SMS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        val receiveSms = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.RECEIVE_SMS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        return sendSms && receiveSms
    }

    fun setExecutionMode(mode: ExecutionMode) {
        viewModelScope.launch {
            preferencesRepository.setExecutionMode(mode)
            val context = getApplication<Application>()
            if (mode == ExecutionMode.FOREGROUND) {
                ForwarderForegroundService.start(context)
            } else {
                ForwarderForegroundService.stop(context)
            }
        }
    }

    suspend fun getFilterById(id: Long): Filter? {
        return try {
            repository.getFilterById(id)
        } catch (e: Exception) {
            Log.e("MainViewModel", "Error fetching filter by id", e)
            null
        }
    }

    fun saveFilter(filter: Filter) {
        viewModelScope.launch {
            try {
                val savedId = repository.saveFilter(filter)
                Log.d("MainViewModel", "Filter saved with id: $savedId, name: ${filter.name}")
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error saving filter", e)
            }
        }
    }

    suspend fun saveFilterSync(filter: Filter): Long {
        return try {
            val savedId = repository.saveFilter(filter)
            Log.d("MainViewModel", "Filter saved sync with id: $savedId, name: ${filter.name}")
            savedId
        } catch (e: Exception) {
            Log.e("MainViewModel", "Error saving filter sync", e)
            0L
        }
    }

    fun toggleFilterActive(filter: Filter, isActive: Boolean = !filter.isActive) {
        viewModelScope.launch {
            try {
                repository.setFilterActive(filter.id, isActive)
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error toggling filter active", e)
            }
        }
    }

    fun updateFilterOrders(orderedFilters: List<Filter>) {
        viewModelScope.launch {
            try {
                val orderPairs = orderedFilters.mapIndexed { index, filter -> Pair(filter.id, index) }
                repository.updateFilterOrders(orderPairs)
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error updating filter orders", e)
            }
        }
    }

    fun deleteFilter(filter: Filter) {
        viewModelScope.launch {
            try {
                repository.deleteFilter(filter)
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error deleting filter", e)
            }
        }
    }

    fun clearLogs() {
        viewModelScope.launch {
            try {
                repository.clearLogs()
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error clearing logs", e)
            }
        }
    }

    fun isNotificationListenerEnabled(): Boolean {
        return try {
            val context = getApplication<Application>()
            val flat = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            )
            flat != null && flat.contains(context.packageName)
        } catch (e: Exception) {
            false
        }
    }

    fun toggleForegroundService(enable: Boolean) {
        try {
            val mode = if (enable) ExecutionMode.FOREGROUND else ExecutionMode.BACKGROUND
            setExecutionMode(mode)
        } catch (e: Exception) {
            Log.e("MainViewModel", "Error toggling foreground service", e)
        }
    }

    /**
     * Reads filters and monthly-summary history straight from the repository instead of the
     * exposed StateFlows. Those are WhileSubscribed(5000), and nothing on the backup screen
     * collects `monthlySummaries`, so `.value` there would be the empty initial value — the
     * export would silently write no history at all.
     */
    private suspend fun snapshotForBackup(): Pair<List<Filter>, List<MonthlySummaryEntry>> {
        val currentFilters = repository.getAllFilters().first()
        val currentSummaries = repository.getMonthlySummaries().first()
        return currentFilters to currentSummaries
    }

    /**
     * Persists an imported filter list, then restores its monthly-summary history.
     *
     * Summary rows key off the filter's primary key, which is NOT stable across devices: a
     * restore inserts filters into a fresh table and they can come back with different ids.
     * saveFilter returns the id actually written, so the old-to-new mapping is built as the
     * filters land and every summary row is re-pointed through it before being stored —
     * without that, history would attach to the wrong filter or to none at all.
     *
     * Each entry is then routed by period. One whose label matches the period this device is
     * currently accumulating into is the backup's in-progress total: it goes back onto the
     * filter as monthlyTotal, which is what the summary screen reads and what the next
     * rollover snapshots. Any other label is a settled period and is stored as a row.
     * Yearly totals work the same way against the yearly label, except there is no history
     * table to fall back on, so a non-matching one is simply dropped.
     * An in-progress total from an earlier period matches nothing and is discarded either
     * way, so a restore performed months later never resurrects a stale running total.
     */
    private suspend fun applyImportResult(importResult: com.example.smsforwarder.data.backup.BackupImportResult): RestoreCounts {
        val savedFilterById = mutableMapOf<Long, Filter>()
        val savedFilterByName = mutableMapOf<String, Filter>()
        importResult.filters.forEach { filter ->
            val persistedId = repository.saveFilter(filter)
            if (persistedId > 0) {
                val persisted = filter.copy(id = persistedId)
                if (filter.id > 0) savedFilterById[filter.id] = persisted
                if (filter.name.isNotBlank()) savedFilterByName[filter.name] = persisted
            }
        }

        // Legacy backups can carry filters with id 0, leaving nothing to key on;
        // the entry's filterName is then the only link back to its filter.
        fun resolveTarget(filterId: Long, filterName: String): Filter? =
            savedFilterById[filterId] ?: savedFilterByName[filterName]

        // Monthly and yearly totals land on the same filter row, so they are collected here
        // and written once — saving per entry would have the second write overwrite the first.
        val amountPatches = mutableMapOf<Long, Filter>()
        val settledRows = mutableListOf<MonthlySummaryEntry>()
        var restoredInProgressCount = 0

        importResult.monthlySummaries.forEach { entry ->
            val target = resolveTarget(entry.filterId, entry.filterName) ?: return@forEach
            // Labels are always derived from `target`, the freshly saved filter whose amounts
            // and reset timestamps are cleared, so this resolves to the period THIS device is
            // in right now — never to a period a patch has already written back.
            if (entry.periodLabel != SummationPeriodEngine.getMonthlyPeriodLabel(target)) {
                settledRows.add(entry.copy(id = 0L, filterId = target.id))
                return@forEach
            }
            amountPatches[target.id] = (amountPatches[target.id] ?: target).copy(
                monthlyTotal = entry.totalAmount,
                lastMonthlyResetTime = entry.periodStartTimestamp.takeIf { it > 0 } ?: 0L
            )
            restoredInProgressCount++
        }

        importResult.yearlySummaries.forEach { entry ->
            val target = resolveTarget(entry.filterId, entry.filterName) ?: return@forEach
            // No yearly history table exists, so a total from a previous year has nowhere to
            // go and nothing to say about this one — it is simply dropped.
            if (entry.periodLabel != SummationPeriodEngine.getYearlyPeriodLabel(target)) return@forEach
            amountPatches[target.id] = (amountPatches[target.id] ?: target).copy(
                yearlyTotal = entry.totalAmount,
                lastYearlyResetTime = entry.periodStartTimestamp.takeIf { it > 0 } ?: 0L
            )
        }

        amountPatches.values.forEach { repository.saveFilter(it) }

        if (settledRows.isNotEmpty()) {
            repository.restoreMonthlySummaries(settledRows)
        }

        return RestoreCounts(
            filterCount = importResult.filters.size,
            summaryCount = settledRows.size + restoredInProgressCount
        )
    }

    data class RestoreCounts(val filterCount: Int, val summaryCount: Int) {
        /** "3개의 필터" / "3개의 필터와 12건의 금액합산 내역" */
        fun toMessage(prefix: String = ""): String {
            val body = if (summaryCount > 0) {
                "${filterCount}개의 필터와 ${summaryCount}건의 금액합산 내역"
            } else {
                "${filterCount}개의 필터"
            }
            return "$prefix${body}를 복원했습니다."
        }
    }

    // Local Backup & Restore
    fun exportLocalBackup(uri: Uri, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val (currentFilters, currentSummaries) = snapshotForBackup()
            val result = localBackupManager.exportToUri(uri, currentFilters, currentSummaries)
            if (result.isSuccess) {
                onResult(true, null)
            } else {
                onResult(false, result.exceptionOrNull()?.localizedMessage)
            }
        }
    }

    fun importLocalBackup(uri: Uri, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val result = localBackupManager.importFromUri(uri)
            if (result.isSuccess) {
                val counts = applyImportResult(result.getOrThrow())
                onResult(true, counts.toMessage())
            } else {
                onResult(false, result.exceptionOrNull()?.localizedMessage)
            }
        }
    }

    // Google Drive Backup & Restore
    fun uploadDriveBackup(account: GoogleSignInAccount, onResult: (Boolean, String?, Throwable?) -> Unit) {
        viewModelScope.launch {
            val (currentFilters, currentSummaries) = snapshotForBackup()
            val result = googleDriveBackupManager.uploadBackup(account, currentFilters, currentSummaries)
            if (result.isSuccess) {
                onResult(true, "Google Drive에 백업이 성공적으로 저장되었습니다.", null)
            } else {
                val ex = result.exceptionOrNull()
                onResult(false, ex?.localizedMessage ?: ex?.toString(), ex)
            }
        }
    }

    fun downloadDriveBackup(account: GoogleSignInAccount, onResult: (Boolean, String?, Throwable?) -> Unit) {
        viewModelScope.launch {
            val result = googleDriveBackupManager.downloadBackup(account)
            if (result.isSuccess) {
                val counts = applyImportResult(result.getOrThrow())
                onResult(true, counts.toMessage("Google Drive에서 "), null)
            } else {
                val ex = result.exceptionOrNull()
                onResult(false, ex?.localizedMessage ?: ex?.toString(), ex)
            }
        }
    }

    fun uploadDriveBackupByEmail(accountEmail: String, onResult: (Boolean, String?, Throwable?) -> Unit) {
        viewModelScope.launch {
            val (currentFilters, currentSummaries) = snapshotForBackup()
            val result = googleDriveBackupManager.uploadBackupByEmail(accountEmail, currentFilters, currentSummaries)
            if (result.isSuccess) {
                onResult(true, "Google Drive에 백업이 성공적으로 저장되었습니다.", null)
            } else {
                val ex = result.exceptionOrNull()
                onResult(false, ex?.localizedMessage ?: ex?.toString(), ex)
            }
        }
    }

    fun downloadDriveBackupByEmail(accountEmail: String, onResult: (Boolean, String?, Throwable?) -> Unit) {
        viewModelScope.launch {
            val result = googleDriveBackupManager.downloadBackupByEmail(accountEmail)
            if (result.isSuccess) {
                val counts = applyImportResult(result.getOrThrow())
                onResult(true, counts.toMessage("Google Drive에서 "), null)
            } else {
                val ex = result.exceptionOrNull()
                onResult(false, ex?.localizedMessage ?: ex?.toString(), ex)
            }
        }
    }

    fun deleteMonthlySummary(filterId: Long, periodLabel: String, summaryId: Long = 0L) {
        viewModelScope.launch {
            repository.deleteMonthlySummary(filterId, periodLabel, summaryId)
            val targetFilter = repository.getFilterById(filterId)
            if (targetFilter != null && targetFilter.isSummationEnabled) {
                val currentPeriodLabel = com.example.smsforwarder.domain.parser.SummationPeriodEngine.getMonthlyPeriodLabel(targetFilter)
                if (periodLabel == currentPeriodLabel) {
                    repository.saveFilter(targetFilter.copy(monthlyTotal = 0L))
                }
            }
        }
    }

    fun deleteAllMonthlySummariesForFilter(filterId: Long) {
        viewModelScope.launch {
            repository.deleteAllMonthlySummariesForFilter(filterId)
            val targetFilter = repository.getFilterById(filterId)
            if (targetFilter != null) {
                repository.saveFilter(targetFilter.copy(monthlyTotal = 0L, yearlyTotal = 0L))
            }
        }
    }
}

