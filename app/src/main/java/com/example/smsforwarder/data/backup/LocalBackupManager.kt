package com.example.smsforwarder.data.backup

import android.content.Context
import android.net.Uri
import com.example.smsforwarder.data.model.BackupPayload
import com.example.smsforwarder.domain.model.Filter
import com.google.gson.GsonBuilder

class LocalBackupManager(private val context: Context) {

    private val gson = GsonBuilder().setPrettyPrinting().create()

    fun exportToUri(uri: Uri, filters: List<Filter>): Result<Unit> {
        return try {
            val payload = BackupPayload(filters = filters)
            val json = gson.toJson(payload)
            context.contentResolver.openOutputStream(uri)?.use { os ->
                os.write(json.toByteArray(Charsets.UTF_8))
            } ?: return Result.failure(Exception("Failed to open output stream for URI"))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun importFromUri(uri: Uri): Result<List<Filter>> {
        return try {
            context.contentResolver.openInputStream(uri)?.use { isStream ->
                val json = isStream.bufferedReader().readText()
                val payload = gson.fromJson(json, BackupPayload::class.java)
                if (payload?.filters != null) {
                    Result.success(payload.filters)
                } else {
                    Result.failure(Exception("Invalid backup file format"))
                }
            } ?: Result.failure(Exception("Failed to open input stream for URI"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
