package com.example.smsforwarder.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.smsforwarder.domain.model.MonthlySummaryEntry

@Entity(
    tableName = "filter_monthly_summaries",
    indices = [Index(value = ["filterId", "periodLabel"], unique = true)]
)
data class FilterMonthlySummaryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val filterId: Long,
    val filterName: String,
    val periodLabel: String,
    val periodStartTimestamp: Long,
    val totalAmount: Long
) {
    fun toDomain(): MonthlySummaryEntry {
        return MonthlySummaryEntry(
            id = id,
            filterId = filterId,
            filterName = filterName,
            periodLabel = periodLabel,
            periodStartTimestamp = periodStartTimestamp,
            totalAmount = totalAmount
        )
    }
}
