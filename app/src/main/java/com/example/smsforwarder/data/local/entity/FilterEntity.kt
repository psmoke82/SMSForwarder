package com.example.smsforwarder.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.smsforwarder.domain.model.Filter
import com.example.smsforwarder.domain.model.KeywordLogic

@Entity(tableName = "filters")
data class FilterEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val targetPackageName: String,
    val appName: String,
    val keywords: String,
    val excludeKeywords: String = "",
    val keywordLogic: String,
    val recipientPhoneNumber: String,
    val messageTemplate: String,
    val isActive: Boolean,
    val isSummationEnabled: Boolean = false,
    val startMonthOffset: Int = 0,
    val startDayType: String = "SPECIFIC_DAY",
    val startDayValue: Int = 1,
    val endMonthOffset: Int = 0,
    val endDayType: String = "SPECIFIC_DAY",
    val endDayValue: Int = 31,
    val monthlyTotal: Long = 0L,
    val yearlyTotal: Long = 0L,
    val lastMonthlyResetTime: Long = 0L,
    val lastYearlyResetTime: Long = 0L,
    val displayOrder: Int = 0
) {
    fun toDomain(): Filter {
        val packageList = if (targetPackageName.isBlank()) emptyList() else targetPackageName.split("|||")
        val appNameList = if (appName.isBlank()) emptyList() else appName.split("|||")

        return Filter(
            id = id,
            name = name,
            targetPackageNames = packageList,
            appNames = appNameList,
            keywords = if (keywords.isBlank()) emptyList() else keywords.split("|||"),
            excludeKeywords = if (excludeKeywords.isBlank()) emptyList() else excludeKeywords.split("|||"),
            keywordLogic = runCatching { KeywordLogic.valueOf(keywordLogic) }.getOrDefault(KeywordLogic.AND),
            recipientPhoneNumber = recipientPhoneNumber,
            messageTemplate = messageTemplate,
            isActive = isActive,
            isSummationEnabled = isSummationEnabled,
            startMonthOffset = startMonthOffset,
            startDayType = startDayType,
            startDayValue = startDayValue,
            endMonthOffset = endMonthOffset,
            endDayType = endDayType,
            endDayValue = endDayValue,
            monthlyTotal = monthlyTotal,
            yearlyTotal = yearlyTotal,
            lastMonthlyResetTime = lastMonthlyResetTime,
            lastYearlyResetTime = lastYearlyResetTime,
            displayOrder = displayOrder
        )
    }

    companion object {
        fun fromDomain(filter: Filter): FilterEntity {
            return FilterEntity(
                id = filter.id,
                name = filter.name,
                targetPackageName = filter.targetPackageNames.joinToString("|||"),
                appName = filter.appNames.joinToString("|||"),
                keywords = filter.keywords.joinToString("|||"),
                excludeKeywords = filter.excludeKeywords.joinToString("|||"),
                keywordLogic = filter.keywordLogic.name,
                recipientPhoneNumber = filter.recipientPhoneNumber,
                messageTemplate = filter.messageTemplate,
                isActive = filter.isActive,
                isSummationEnabled = filter.isSummationEnabled,
                startMonthOffset = filter.startMonthOffset,
                startDayType = filter.startDayType,
                startDayValue = filter.startDayValue,
                endMonthOffset = filter.endMonthOffset,
                endDayType = filter.endDayType,
                endDayValue = filter.endDayValue,
                monthlyTotal = filter.monthlyTotal,
                yearlyTotal = filter.yearlyTotal,
                lastMonthlyResetTime = filter.lastMonthlyResetTime,
                lastYearlyResetTime = filter.lastYearlyResetTime,
                displayOrder = filter.displayOrder
            )
        }
    }
}
