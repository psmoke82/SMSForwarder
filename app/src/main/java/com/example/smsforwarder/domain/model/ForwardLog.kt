package com.example.smsforwarder.domain.model

data class ForwardLog(
    val id: Long = 0,
    val timestamp: Long,
    val filterName: String,
    val appName: String,
    val packageName: String,
    val rawTitle: String,
    val rawBody: String,
    val parsedMessage: String,
    val recipientNumber: String,
    val isSuccess: Boolean,
    val errorMessage: String? = null,
    val isSummationEnabled: Boolean = false,
    val extractedAmountKRW: Long = 0L,
    val originalCurrencyCode: String? = null,
    val originalForeignAmount: Double? = null,
    val appliedExchangeRate: Double? = null
)
