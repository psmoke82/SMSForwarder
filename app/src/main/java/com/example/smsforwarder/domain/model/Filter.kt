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
    val isActive: Boolean = true
) {
    val targetPackageName: String
        get() = targetPackageNames.firstOrNull() ?: ""

    val appName: String
        get() = appNames.joinToString(", ")
}
