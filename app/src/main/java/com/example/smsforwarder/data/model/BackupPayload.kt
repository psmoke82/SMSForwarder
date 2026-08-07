package com.example.smsforwarder.data.model

import com.example.smsforwarder.domain.model.Filter

data class BackupPayload(
    val version: Int = 2,
    val exportedAt: Long = System.currentTimeMillis(),
    val appVersion: String = "1.0",
    val filters: List<Filter> = emptyList()
)
