package com.example.smsforwarder.data.backup

import android.content.Context
import com.example.smsforwarder.data.model.BackupPayload
import com.example.smsforwarder.domain.model.Filter
import com.example.smsforwarder.domain.model.MonthlySummaryEntry
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.ByteArrayContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GoogleDriveBackupManager(private val context: Context) {

    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val BACKUP_FILENAME = "smsforwarder_backup.json"

    private fun getDriveService(account: GoogleSignInAccount): Drive {
        val credential = GoogleAccountCredential.usingOAuth2(
            context, listOf(DriveScopes.DRIVE_APPDATA)
        ).setSelectedAccount(account.account)

        return Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        ).setApplicationName("SMSForwarder").build()
    }

    private fun getDriveServiceByEmail(accountEmail: String): Drive {
        val credential = GoogleAccountCredential.usingOAuth2(
            context, listOf(DriveScopes.DRIVE_APPDATA)
        ).setSelectedAccountName(accountEmail)

        return Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        ).setApplicationName("SMSForwarder").build()
    }

    suspend fun uploadBackup(account: GoogleSignInAccount, filters: List<Filter>, monthlySummaries: List<MonthlySummaryEntry> = emptyList()): Result<Unit> {
        return uploadBackupInternal(getDriveService(account), filters, monthlySummaries)
    }

    suspend fun uploadBackupByEmail(accountEmail: String, filters: List<Filter>, monthlySummaries: List<MonthlySummaryEntry> = emptyList()): Result<Unit> {
        return uploadBackupInternal(getDriveServiceByEmail(accountEmail), filters, monthlySummaries)
    }

    private suspend fun uploadBackupInternal(driveService: Drive, filters: List<Filter>, monthlySummaries: List<MonthlySummaryEntry>): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val jsonContent = BackupMigrator.createBackupJson(filters, monthlySummaries)

            val fileList = driveService.files().list()
                .setSpaces("appDataFolder")
                .setQ("name = '$BACKUP_FILENAME'")
                .execute()

            val contentStream = ByteArrayContent.fromString("application/json", jsonContent)

            if (fileList.files.isNotEmpty()) {
                val fileId = fileList.files[0].id
                driveService.files().update(fileId, null, contentStream).execute()
            } else {
                val fileMetaData = File().apply {
                    name = BACKUP_FILENAME
                    parents = listOf("appDataFolder")
                }
                driveService.files().create(fileMetaData, contentStream).execute()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadBackup(account: GoogleSignInAccount): Result<BackupImportResult> {
        return downloadBackupInternal(getDriveService(account))
    }

    suspend fun downloadBackupByEmail(accountEmail: String): Result<BackupImportResult> {
        return downloadBackupInternal(getDriveServiceByEmail(accountEmail))
    }

    private suspend fun downloadBackupInternal(driveService: Drive): Result<BackupImportResult> = withContext(Dispatchers.IO) {
        try {
            val fileList = driveService.files().list()
                .setSpaces("appDataFolder")
                .setQ("name = '$BACKUP_FILENAME'")
                .execute()

            if (fileList.files.isEmpty()) {
                return@withContext Result.failure(Exception("Google Drive에서 백업 파일($BACKUP_FILENAME)을 찾을 수 없습니다."))
            }

            val fileId = fileList.files[0].id
            val outputStream = java.io.ByteArrayOutputStream()
            driveService.files().get(fileId).executeMediaAndDownloadTo(outputStream)

            val jsonContent = outputStream.toString("UTF-8")
            BackupMigrator.parseAndMigrate(jsonContent)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
