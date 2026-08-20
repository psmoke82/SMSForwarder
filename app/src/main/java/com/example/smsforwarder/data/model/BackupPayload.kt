package com.example.smsforwarder.data.model

import com.example.smsforwarder.domain.model.Filter
import com.example.smsforwarder.domain.model.MonthlySummaryEntry

data class BackupPayload(
    val version: Int = 5,
    val exportedAt: Long = System.currentTimeMillis(),
    val appVersion: String = "1.0",
    val filters: List<Filter> = emptyList(),
    // Added in v4. Older backups won't have this key — BackupMigrator treats its
    // absence as "no history to restore", not an error.
    val monthlySummaries: List<MonthlySummaryEntry> = emptyList(),
    // Added in v5, same absence-is-not-an-error handling.
    val yearlySummaries: List<YearlyTotalBackupEntry> = emptyList()
)
