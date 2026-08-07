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
    val isActive: Boolean
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
            isActive = isActive
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
                isActive = filter.isActive
            )
        }
    }
}
