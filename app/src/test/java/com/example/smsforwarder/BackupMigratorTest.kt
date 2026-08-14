package com.example.smsforwarder

import com.example.smsforwarder.data.backup.BackupMigrator
import com.example.smsforwarder.domain.model.Filter
import com.example.smsforwarder.domain.model.KeywordLogic
import com.example.smsforwarder.domain.model.MonthlySummaryEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupMigratorTest {

    @Test
    fun createBackupJson_excludesSummationAmounts() {
        val filter = Filter(
            id = 10,
            name = "신한카드 필터",
            targetPackageNames = listOf("com.shinhan.smartcareb"),
            appNames = listOf("신한SOL"),
            keywords = listOf("승인", "결제"),
            excludeKeywords = listOf("취소"),
            keywordLogic = KeywordLogic.AND,
            recipientPhoneNumber = "01012345678",
            messageTemplate = "%mb%",
            isActive = true,
            isSummationEnabled = true,
            monthlyTotal = 150000L,
            yearlyTotal = 1200000L,
            lastMonthlyResetTime = 1754006400000L,
            lastYearlyResetTime = 1735689600000L
        )

        val json = BackupMigrator.createBackupJson(listOf(filter))

        assertTrue(json.contains("\"version\": 4"))
        // Settings are preserved...
        assertTrue(json.contains("\"isSummationEnabled\": true"))
        // ...but amounts and reset bookkeeping are never written to the backup.
        assertFalse(json.contains("150000"))
        assertFalse(json.contains("1200000"))
        assertFalse(json.contains("1754006400000"))
        assertFalse(json.contains("1735689600000"))
    }

    @Test
    fun parseAndMigrate_legacyV1Json_migratesToCurrentStructure() {
        // Legacy v1 JSON (single targetPackageName, single appName, no summation fields, no excludeKeywords)
        val legacyJson = """
            {
                "version": 1,
                "filters": [
                    {
                        "id": 1,
                        "name": "구형 필터 1",
                        "targetPackageName": "com.old.app",
                        "appName": "구형앱",
                        "keywords": "승인|||결제",
                        "keywordLogic": "OR",
                        "recipientPhoneNumber": "01011112222",
                        "messageTemplate": "테스트",
                        "isActive": true
                    }
                ]
            }
        """.trimIndent()

        val result = BackupMigrator.parseAndMigrate(legacyJson)

        assertTrue(result.isSuccess)
        val importResult = result.getOrThrow()
        val filters = importResult.filters
        assertEquals(1, filters.size)
        assertTrue(importResult.monthlySummaries.isEmpty())

        val filter = filters[0]
        assertEquals("구형 필터 1", filter.name)
        assertEquals(listOf("com.old.app"), filter.targetPackageNames)
        assertEquals(listOf("구형앱"), filter.appNames)
        assertEquals(listOf("승인", "결제"), filter.keywords)
        assertEquals(emptyList<String>(), filter.excludeKeywords)
        assertEquals(KeywordLogic.OR, filter.keywordLogic)

        // Summation fields defaulted to current structure defaults
        assertFalse(filter.isSummationEnabled)
        assertEquals(0, filter.startMonthOffset)
        assertEquals("SPECIFIC_DAY", filter.startDayType)
        assertEquals(1, filter.startDayValue)
        assertEquals(0, filter.endMonthOffset)
        assertEquals("SPECIFIC_DAY", filter.endDayType)
        assertEquals(31, filter.endDayValue)
        assertEquals(0L, filter.monthlyTotal)
        assertEquals(0L, filter.yearlyTotal)
    }

    @Test
    fun parseAndMigrate_ignoresSummationAmountsEvenIfPresentInOlderBackup() {
        // A backup file from before this exclusion policy might still contain amounts —
        // restoring it must not resurrect them.
        val oldBackupWithAmounts = """
            {
                "version": 3,
                "filters": [
                    {
                        "id": 1,
                        "name": "필터",
                        "recipientPhoneNumber": "01011112222",
                        "messageTemplate": "테스트",
                        "isActive": true,
                        "isSummationEnabled": true,
                        "monthlyTotal": 999000,
                        "yearlyTotal": 5000000,
                        "lastMonthlyResetTime": 1754006400000,
                        "lastYearlyResetTime": 1735689600000
                    }
                ],
                "monthlySummaries": [
                    {
                        "filterId": 1,
                        "filterName": "필터",
                        "periodLabel": "26.07",
                        "periodStartTimestamp": 1751328000000,
                        "totalAmount": 300000
                    }
                ]
            }
        """.trimIndent()

        val result = BackupMigrator.parseAndMigrate(oldBackupWithAmounts)

        assertTrue(result.isSuccess)
        val importResult = result.getOrThrow()
        assertEquals(1, importResult.filters.size)
        val filter = importResult.filters[0]
        assertEquals(0L, filter.monthlyTotal)
        assertEquals(0L, filter.yearlyTotal)
        assertEquals(0L, filter.lastMonthlyResetTime)
        assertEquals(0L, filter.lastYearlyResetTime)
        assertTrue(importResult.monthlySummaries.isEmpty())
    }

    @Test
    fun createBackupJson_ignoresMonthlySummariesArgument() {
        val filter = Filter(
            id = 10,
            name = "신한카드 필터",
            recipientPhoneNumber = "01012345678",
            messageTemplate = "%mb%",
            isActive = true
        )
        val summary = MonthlySummaryEntry(
            filterId = 10,
            filterName = "신한카드 필터",
            periodLabel = "26.08",
            periodStartTimestamp = 1754006400000L,
            totalAmount = 230894L
        )

        val json = BackupMigrator.createBackupJson(listOf(filter), listOf(summary))
        val result = BackupMigrator.parseAndMigrate(json)

        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().monthlySummaries.isEmpty())
        assertFalse(json.contains("230894"))
    }
}
