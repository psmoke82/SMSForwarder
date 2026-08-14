package com.example.smsforwarder.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.smsforwarder.domain.model.ForwardLog

@Entity(tableName = "forward_logs")
data class LogEntity(
    @PrimaryKey(autoGenerate = true)
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
) {
    fun toDomain(): ForwardLog {
        return ForwardLog(
            id = id,
            timestamp = timestamp,
            filterName = filterName,
            appName = appName,
            packageName = packageName,
            rawTitle = rawTitle,
            rawBody = rawBody,
            parsedMessage = parsedMessage,
            recipientNumber = recipientNumber,
            isSuccess = isSuccess,
            errorMessage = errorMessage,
            isSummationEnabled = isSummationEnabled,
            extractedAmountKRW = extractedAmountKRW,
            originalCurrencyCode = originalCurrencyCode,
            originalForeignAmount = originalForeignAmount,
            appliedExchangeRate = appliedExchangeRate
        )
    }

    companion object {
        fun fromDomain(log: ForwardLog): LogEntity {
            return LogEntity(
                id = log.id,
                timestamp = log.timestamp,
                filterName = log.filterName,
                appName = log.appName,
                packageName = log.packageName,
                rawTitle = log.rawTitle,
                rawBody = log.rawBody,
                parsedMessage = log.parsedMessage,
                recipientNumber = log.recipientNumber,
                isSuccess = log.isSuccess,
                errorMessage = log.errorMessage,
                isSummationEnabled = log.isSummationEnabled,
                extractedAmountKRW = log.extractedAmountKRW,
                originalCurrencyCode = log.originalCurrencyCode,
                originalForeignAmount = log.originalForeignAmount,
                appliedExchangeRate = log.appliedExchangeRate
            )
        }
    }
}
