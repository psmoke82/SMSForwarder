package com.example.smsforwarder.domain.model

data class Filter(
    val id: Long = 0,
    val name: String,
    val targetPackageNames: List<String> = emptyList(),
    val appNames: List<String> = emptyList(),
    val keywords: List<String> = emptyList(),
    val excludeKeywords: List<String> = emptyList(),
    val keywordLogic: KeywordLogic = KeywordLogic.OR,
    val recipientPhoneNumber: String,
    val messageTemplate: String,
    val isActive: Boolean = true,
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
    val targetPackageName: String
        get() = targetPackageNames.firstOrNull() ?: ""

    val appName: String
        get() = appNames.joinToString(", ")
}
