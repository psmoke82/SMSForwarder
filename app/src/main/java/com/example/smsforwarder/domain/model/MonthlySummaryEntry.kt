package com.example.smsforwarder.domain.model

data class MonthlySummaryEntry(
    val id: Long = 0,
    val filterId: Long,
    val filterName: String,
    val periodLabel: String,
    val periodStartTimestamp: Long,
    val totalAmount: Long
)
