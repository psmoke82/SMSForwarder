package com.example.smsforwarder

import com.example.smsforwarder.data.backup.BackupMigrator
import com.example.smsforwarder.domain.model.Filter
import com.example.smsforwarder.domain.model.KeywordLogic
import com.example.smsforwarder.domain.model.MonthlySummaryEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

        assertTrue(json.contains("\"version\": 5"))
        // Settings are preserved...
        assertTrue(json.contains("\"isSummationEnabled\": true"))
        // ...while the filter object itself carries no amounts or reset bookkeeping.
        val filtersSection = json.substringAfter("\"filters\"").substringBefore("\"monthlySummaries\"")
        assertFalse(filtersSection.contains("150000"))
        assertFalse(filtersSection.contains("1200000"))
        assertFalse(filtersSection.contains("1754006400000"))
        assertFalse(filtersSection.contains("1735689600000"))

        // Running totals are not dropped outright any more — each moves into its own
        // period-tagged carrier, which is what lets a restore decide whether it is still
        // current. Restoring them is the routing step's job, so they come back off the wire
        // separately from the filter, which stays cleared.
        val restored = BackupMigrator.parseAndMigrate(json).getOrThrow()
        assertEquals(0L, restored.filters[0].monthlyTotal)
        assertEquals(0L, restored.filters[0].yearlyTotal)

        assertEquals(1, restored.monthlySummaries.size)
        assertEquals(150000L, restored.monthlySummaries[0].totalAmount)
        assertEquals(labelOf(1754006400000L), restored.monthlySummaries[0].periodLabel)

        assertEquals(1, restored.yearlySummaries.size)
        assertEquals(1200000L, restored.yearlySummaries[0].totalAmount)
        assertEquals(yearLabelOf(1735689600000L), restored.yearlySummaries[0].periodLabel)
        assertEquals(1735689600000L, restored.yearlySummaries[0].periodStartTimestamp)
    }

    @Test
    fun createBackupJson_omitsYearlyTotalWhenZeroOrSummationOff() {
        val zeroTotal = Filter(
            id = 10,
            name = "합계 없음",
            recipientPhoneNumber = "01012345678",
            messageTemplate = "%mb%",
            isActive = true,
            isSummationEnabled = true,
            yearlyTotal = 0L
        )
        val summationOff = Filter(
            id = 11,
            name = "합산 꺼짐",
            recipientPhoneNumber = "01012345678",
            messageTemplate = "%mb%",
            isActive = true,
            isSummationEnabled = false,
            yearlyTotal = 4400000L
        )

        val json = BackupMigrator.createBackupJson(listOf(zeroTotal, summationOff))

        assertFalse(json.contains("4400000"))
        assertTrue(BackupMigrator.parseAndMigrate(json).getOrThrow().yearlySummaries.isEmpty())
    }

    @Test
    fun parseAndMigrate_treatsMissingYearlyKeyAsNothingToRestore() {
        // v4 backups predate yearlySummaries entirely.
        val v4Backup = """
            {
                "version": 4,
                "filters": [
                    { "id": 1, "name": "필터", "recipientPhoneNumber": "01011112222" }
                ],
                "monthlySummaries": []
            }
        """.trimIndent()

        val importResult = BackupMigrator.parseAndMigrate(v4Backup).getOrThrow()

        assertEquals(1, importResult.filters.size)
        assertTrue(importResult.yearlySummaries.isEmpty())
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
    fun parseAndMigrate_restoresSettledSummariesButNeverFilterAmounts() {
        // A filter's own running totals must never come back — they describe a period that
        // has since moved on. A settled summary row is a closed snapshot, so it does.
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

        assertEquals(1, importResult.monthlySummaries.size)
        val summary = importResult.monthlySummaries[0]
        assertEquals(1L, summary.filterId)
        assertEquals("26.07", summary.periodLabel)
        assertEquals(300000L, summary.totalAmount)
        // The local autoincrement PK from the source device is meaningless here.
        assertEquals(0L, summary.id)
    }

    @Test
    fun parseAndMigrate_treatsMissingSummariesKeyAsNoHistory() {
        // Backups written before summaries were included simply have no such key.
        val legacyBackup = """
            {
                "version": 3,
                "filters": [
                    { "id": 1, "name": "필터", "recipientPhoneNumber": "01011112222" }
                ]
            }
        """.trimIndent()

        val importResult = BackupMigrator.parseAndMigrate(legacyBackup).getOrThrow()

        assertEquals(1, importResult.filters.size)
        assertTrue(importResult.monthlySummaries.isEmpty())
    }

    @Test
    fun parseAndMigrate_skipsUnusableSummaryRowsWithoutFailingTheRestore() {
        // periodLabel is half of the (filterId, periodLabel) key — a blank one cannot
        // identify a period. Losing the filter list over it would be far worse.
        val backupWithBadRow = """
            {
                "version": 4,
                "filters": [
                    { "id": 1, "name": "필터", "recipientPhoneNumber": "01011112222" }
                ],
                "monthlySummaries": [
                    { "filterId": 1, "filterName": "필터", "periodLabel": "", "totalAmount": 100 },
                    { "filterId": 1, "filterName": "필터", "periodLabel": "26.05", "totalAmount": 700 }
                ]
            }
        """.trimIndent()

        val result = BackupMigrator.parseAndMigrate(backupWithBadRow)

        assertTrue(result.isSuccess)
        val importResult = result.getOrThrow()
        assertEquals(1, importResult.filters.size)
        assertEquals(1, importResult.monthlySummaries.size)
        assertEquals("26.05", importResult.monthlySummaries[0].periodLabel)
    }

    @Test
    fun createBackupJson_writesSettledRowsAndTheInProgressTotalFromMonthlyTotal() {
        // monthlyTotal is the authoritative running figure, so the stored row for the
        // in-progress period (230894) must be superseded by it (559900), not written twice.
        val filter = Filter(
            id = 10,
            name = "신한카드 필터",
            recipientPhoneNumber = "01012345678",
            messageTemplate = "%mb%",
            isActive = true,
            isSummationEnabled = true,
            monthlyTotal = 559900L,
            lastMonthlyResetTime = CURRENT_PERIOD_START
        )
        val settled = MonthlySummaryEntry(
            filterId = 10,
            filterName = "신한카드 필터",
            periodLabel = labelOf(SETTLED_PERIOD_START),
            periodStartTimestamp = SETTLED_PERIOD_START,
            totalAmount = 481200L
        )
        val staleInProgressRow = MonthlySummaryEntry(
            filterId = 10,
            filterName = "신한카드 필터",
            periodLabel = labelOf(CURRENT_PERIOD_START),
            periodStartTimestamp = CURRENT_PERIOD_START,
            totalAmount = 230894L
        )

        val json = BackupMigrator.createBackupJson(listOf(filter), listOf(settled, staleInProgressRow))
        assertFalse(json.contains("230894"))

        val restored = BackupMigrator.parseAndMigrate(json).getOrThrow().monthlySummaries
        assertEquals(2, restored.size)

        val settledEntry = restored.first { it.periodLabel == labelOf(SETTLED_PERIOD_START) }
        assertEquals(481200L, settledEntry.totalAmount)

        val inProgressEntry = restored.first { it.periodLabel == labelOf(CURRENT_PERIOD_START) }
        assertEquals(559900L, inProgressEntry.totalAmount)
        // The period start rides along so a restore can put lastMonthlyResetTime back.
        assertEquals(CURRENT_PERIOD_START, inProgressEntry.periodStartTimestamp)
    }

    @Test
    fun createBackupJson_omitsInProgressTotalWhenNothingHasAccumulated() {
        val filter = Filter(
            id = 10,
            name = "신한카드 필터",
            recipientPhoneNumber = "01012345678",
            messageTemplate = "%mb%",
            isActive = true,
            isSummationEnabled = true,
            monthlyTotal = 0L,
            lastMonthlyResetTime = CURRENT_PERIOD_START
        )

        val json = BackupMigrator.createBackupJson(listOf(filter), emptyList())

        assertTrue(BackupMigrator.parseAndMigrate(json).getOrThrow().monthlySummaries.isEmpty())
    }

    @Test
    fun createBackupJson_omitsInProgressTotalWhenSummationIsOff() {
        // A leftover total on a filter whose summation was switched off is not a figure
        // the user is tracking any more.
        val filter = Filter(
            id = 10,
            name = "신한카드 필터",
            recipientPhoneNumber = "01012345678",
            messageTemplate = "%mb%",
            isActive = true,
            isSummationEnabled = false,
            monthlyTotal = 88000L,
            lastMonthlyResetTime = CURRENT_PERIOD_START
        )

        val json = BackupMigrator.createBackupJson(listOf(filter), emptyList())

        assertFalse(json.contains("88000"))
        assertTrue(BackupMigrator.parseAndMigrate(json).getOrThrow().monthlySummaries.isEmpty())
    }

    @Test
    fun createBackupJson_dropsSummariesWhoseFilterIsNotInTheBackup() {
        // Such a row would restore as an orphan pointing at a filter that never arrives.
        val filter = Filter(
            id = 10,
            name = "신한카드 필터",
            recipientPhoneNumber = "01012345678",
            messageTemplate = "%mb%",
            isActive = true,
            lastMonthlyResetTime = CURRENT_PERIOD_START
        )
        val orphan = MonthlySummaryEntry(
            filterId = 99,
            filterName = "삭제된 필터",
            periodLabel = labelOf(SETTLED_PERIOD_START),
            periodStartTimestamp = SETTLED_PERIOD_START,
            totalAmount = 777777L
        )

        val json = BackupMigrator.createBackupJson(listOf(filter), listOf(orphan))

        assertFalse(json.contains("777777"))
        assertTrue(BackupMigrator.parseAndMigrate(json).getOrThrow().monthlySummaries.isEmpty())
    }

    private companion object {
        const val SETTLED_PERIOD_START = 1751328000000L
        const val CURRENT_PERIOD_START = 1754006400000L

        /**
         * Derives the label the way SummationPeriodEngine does rather than hardcoding one,
         * so the expectation holds regardless of the JVM's default time zone.
         */
        fun labelOf(timestamp: Long): String =
            SimpleDateFormat("yy.MM", Locale.KOREA).format(Date(timestamp))

        fun yearLabelOf(timestamp: Long): String =
            SimpleDateFormat("yy", Locale.KOREA).format(Date(timestamp))
    }
}
