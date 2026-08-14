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
import com.example.smsforwarder.service.ForwarderForegroundService
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
     * Persists an imported filter list. Summation amounts and monthly-summary history are
     * intentionally excluded from backups (see BackupMigrator) — restoring only ever brings
     * back filter settings, never stale accumulated totals — so there is nothing to remap or
     * restore beyond the filters themselves.
     */
    private suspend fun applyImportResult(importResult: com.example.smsforwarder.data.backup.BackupImportResult): Int {
        importResult.filters.forEach { filter ->
            repository.saveFilter(filter)
        }
        return importResult.filters.size
    }

    // Local Backup & Restore
    fun exportLocalBackup(uri: Uri, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val currentFilters = filters.value
            val result = localBackupManager.exportToUri(uri, currentFilters)
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
                val count = applyImportResult(result.getOrThrow())
                onResult(true, "${count}개의 필터가 복원되었습니다.")
            } else {
                onResult(false, result.exceptionOrNull()?.localizedMessage)
            }
        }
    }

    // Google Drive Backup & Restore
    fun uploadDriveBackup(account: GoogleSignInAccount, onResult: (Boolean, String?, Throwable?) -> Unit) {
        viewModelScope.launch {
            val currentFilters = filters.value
            val result = googleDriveBackupManager.uploadBackup(account, currentFilters)
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
                val count = applyImportResult(result.getOrThrow())
                onResult(true, "Google Drive에서 ${count}개의 필터를 복원했습니다.", null)
            } else {
                val ex = result.exceptionOrNull()
                onResult(false, ex?.localizedMessage ?: ex?.toString(), ex)
            }
        }
    }

    fun uploadDriveBackupByEmail(accountEmail: String, onResult: (Boolean, String?, Throwable?) -> Unit) {
        viewModelScope.launch {
            val currentFilters = filters.value
            val result = googleDriveBackupManager.uploadBackupByEmail(accountEmail, currentFilters)
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
                val count = applyImportResult(result.getOrThrow())
                onResult(true, "Google Drive에서 ${count}개의 필터를 복원했습니다.", null)
            } else {
                val ex = result.exceptionOrNull()
                onResult(false, ex?.localizedMessage ?: ex?.toString(), ex)
            }
        }
    }
}
