package com.example.smsforwarder.data.backup

import android.content.Context
import android.net.Uri
import com.example.smsforwarder.data.model.BackupPayload
import com.example.smsforwarder.domain.model.Filter
import com.example.smsforwarder.domain.model.MonthlySummaryEntry
import com.google.gson.GsonBuilder

class LocalBackupManager(private val context: Context) {

    fun exportToUri(uri: Uri, filters: List<Filter>, monthlySummaries: List<MonthlySummaryEntry> = emptyList()): Result<Unit> {
        return try {
            val json = BackupMigrator.createBackupJson(filters, monthlySummaries)
            context.contentResolver.openOutputStream(uri)?.use { os ->
                os.write(json.toByteArray(Charsets.UTF_8))
            } ?: return Result.failure(Exception("Failed to open output stream for URI"))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun importFromUri(uri: Uri): Result<BackupImportResult> {
        return try {
            context.contentResolver.openInputStream(uri)?.use { isStream ->
                val json = isStream.bufferedReader().readText()
                BackupMigrator.parseAndMigrate(json)
            } ?: Result.failure(Exception("Failed to open input stream for URI"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
