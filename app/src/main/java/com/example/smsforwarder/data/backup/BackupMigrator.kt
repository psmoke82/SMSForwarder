package com.example.smsforwarder.data.backup

import com.example.smsforwarder.data.model.BackupPayload
import com.example.smsforwarder.data.model.YearlyTotalBackupEntry
import com.example.smsforwarder.domain.model.Filter
import com.example.smsforwarder.domain.model.KeywordLogic
import com.example.smsforwarder.domain.model.MonthlySummaryEntry
import com.example.smsforwarder.domain.parser.SummationPeriodEngine
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser

data class BackupImportResult(
    val filters: List<Filter>,
    val monthlySummaries: List<MonthlySummaryEntry>,
    val yearlySummaries: List<YearlyTotalBackupEntry> = emptyList()
)

object BackupMigrator {

    private val gson = GsonBuilder().setPrettyPrinting().create()

    /**
     * Serializes current filters and settled monthly-summary history into JSON, matching the
     * current v4 schema.
     *
     * Summation amounts living on the filter itself (monthlyTotal/yearlyTotal/reset timestamps)
     * are still deliberately NEVER written: they describe the period that is currently
     * accumulating, so a restore performed long after the backup (device loss, reinstall)
     * would resurrect a stale running total.
     *
     * Settled monthly summaries are different — a row labelled "26.06" is a closed snapshot
     * that never changes again, so it stays correct no matter when it is restored, and it is
     * the only way to carry performance history to a new device now that the Room DB is
     * excluded from Android auto-backup (see res/xml/backup_rules.xml).
     *
     * The still-accumulating period is written too, but carries its own "26.08"-style label,
     * which is what makes it safe: parseAndMigrate hands it back as an ordinary entry and the
     * restoring side only folds it into monthlyTotal when its own current period carries the
     * same label. A same-week device swap therefore keeps the running total, while a restore
     * months later finds no match and drops it — the stale-amount rule stays intact.
     */
    fun createBackupJson(filters: List<Filter>, monthlySummaries: List<MonthlySummaryEntry> = emptyList()): String {
        val sanitizedFilters = filters.map { it.withoutSummationAmounts() }
        val payload = BackupPayload(
            version = 5,
            exportedAt = System.currentTimeMillis(),
            appVersion = "1.0",
            filters = sanitizedFilters,
            monthlySummaries = selectExportableSummaries(filters, monthlySummaries),
            yearlySummaries = selectYearlyTotals(filters)
        )
        return gson.toJson(payload)
    }

    /**
     * One entry per filter still accumulating a yearly total, tagged with its period so the
     * restoring side can tell whether it is still the current year. Same exclusions as the
     * monthly in-progress row: nothing to carry when summation is off or the total is zero.
     */
    private fun selectYearlyTotals(filters: List<Filter>): List<YearlyTotalBackupEntry> {
        return filters.mapNotNull { filter ->
            if (!filter.isSummationEnabled || filter.yearlyTotal == 0L) return@mapNotNull null
            YearlyTotalBackupEntry(
                filterId = filter.id,
                filterName = filter.name,
                periodLabel = SummationPeriodEngine.getYearlyPeriodLabel(filter),
                periodStartTimestamp = SummationPeriodEngine.getYearlyPeriodStart(filter),
                totalAmount = filter.yearlyTotal
            )
        }
    }

    /**
     * Builds the summary list to write: settled snapshot rows, plus one synthesized row per
     * filter for the period still accumulating.
     *
     * The in-progress row is synthesized from filter.monthlyTotal rather than read from the
     * stored rows because monthlyTotal is the authoritative running figure — it is what the
     * summary screen displays for the current period and what the rollover snapshot is later
     * written from, and the user can edit it directly.
     *
     * Note this reads the ORIGINAL filters, not the sanitized copies: withoutSummationAmounts()
     * clears lastMonthlyResetTime, which is exactly what the period is derived from, so
     * sanitized filters would report the wrong one.
     */
    private fun selectExportableSummaries(
        filters: List<Filter>,
        monthlySummaries: List<MonthlySummaryEntry>
    ): List<MonthlySummaryEntry> {
        val inProgressLabelByFilterId = filters.associate { filter ->
            filter.id to SummationPeriodEngine.getMonthlyPeriodLabel(filter)
        }

        val settled = monthlySummaries.mapNotNull { entry ->
            // A row whose filter is not part of this backup would restore as an orphan
            // pointing at nothing. The in-progress label is skipped here because the
            // synthesized row below supersedes it.
            val inProgressLabel = inProgressLabelByFilterId[entry.filterId] ?: return@mapNotNull null
            if (entry.periodLabel == inProgressLabel) return@mapNotNull null
            // id is a local autoincrement PK; it means nothing on the restoring device.
            entry.copy(id = 0L)
        }

        val inProgress = filters.mapNotNull { filter ->
            if (!filter.isSummationEnabled || filter.monthlyTotal == 0L) return@mapNotNull null
            MonthlySummaryEntry(
                id = 0L,
                filterId = filter.id,
                filterName = filter.name,
                periodLabel = SummationPeriodEngine.getMonthlyPeriodLabel(filter),
                periodStartTimestamp = SummationPeriodEngine.getMonthlyPeriodStart(filter),
                totalAmount = filter.monthlyTotal
            )
        }

        return settled + inProgress
    }

    /**
     * Parses backup JSON and migrates any older/legacy structure into the current Filter model.
     * Per-filter summation amounts are still ignored on the way back in, even when an older
     * backup happens to contain them — a restore never resurrects a stale running total.
     * Settled monthly summaries ARE restored; backups written before they were included simply
     * have no `monthlySummaries` key, which reads back as "no history", not as an error.
     */
    fun parseAndMigrate(jsonContent: String): Result<BackupImportResult> {
        return try {
            val rootElement = JsonParser.parseString(jsonContent)
            if (!rootElement.isJsonObject && !rootElement.isJsonArray) {
                return Result.failure(Exception("올바른 JSON 백업 파일 형식이 아닙니다."))
            }

            val filtersArray: JsonArray = when {
                rootElement.isJsonArray -> rootElement.asJsonArray
                rootElement.isJsonObject -> {
                    val rootObj = rootElement.asJsonObject
                    when {
                        rootObj.has("filters") && rootObj.get("filters").isJsonArray -> rootObj.getAsJsonArray("filters")
                        rootObj.has("name") -> JsonArray().apply { add(rootObj) }
                        else -> return Result.failure(Exception("백업 파일 내 필터 목록을 찾을 수 없습니다."))
                    }
                }
                else -> return Result.failure(Exception("백업 파일 구조를 파악할 수 없습니다."))
            }

            val migratedFilters = mutableListOf<Filter>()
            for ((index, element) in filtersArray.withIndex()) {
                if (!element.isJsonObject) continue
                val obj = element.asJsonObject
                val filter = parseFilterFromJsonObject(obj, defaultOrder = index)
                migratedFilters.add(filter)
            }

            val rootObj = if (rootElement.isJsonObject) rootElement.asJsonObject else null
            val migratedSummaries = rootObj?.let { parseMonthlySummaries(it) } ?: emptyList()
            val migratedYearlyTotals = rootObj?.let { parseYearlyTotals(it) } ?: emptyList()

            Result.success(
                BackupImportResult(
                    filters = migratedFilters,
                    monthlySummaries = migratedSummaries,
                    yearlySummaries = migratedYearlyTotals
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Reads the optional `monthlySummaries` array. Absent (pre-v4-history backups) or malformed
     * entries yield nothing rather than failing the whole restore — losing the filter list
     * because a history row was unreadable would be a far worse outcome than losing the row.
     */
    private fun parseMonthlySummaries(rootObj: JsonObject): List<MonthlySummaryEntry> {
        if (!rootObj.has("monthlySummaries") || !rootObj.get("monthlySummaries").isJsonArray) {
            return emptyList()
        }
        val result = mutableListOf<MonthlySummaryEntry>()
        for (element in rootObj.getAsJsonArray("monthlySummaries")) {
            if (!element.isJsonObject) continue
            val obj = element.asJsonObject
            val periodLabel = obj.optString("periodLabel")
            // periodLabel is half of the (filterId, periodLabel) unique key — a blank one
            // cannot identify a period, so the row is unusable.
            if (periodLabel.isBlank()) continue
            result.add(
                MonthlySummaryEntry(
                    id = 0L,
                    filterId = obj.optLong("filterId"),
                    filterName = obj.optString("filterName"),
                    periodLabel = periodLabel,
                    periodStartTimestamp = obj.optLong("periodStartTimestamp"),
                    totalAmount = obj.optLong("totalAmount")
                )
            )
        }
        return result
    }

    /**
     * Reads the optional `yearlySummaries` array, added in v5. Same tolerance as its monthly
     * counterpart: absent or unreadable entries yield nothing rather than failing the restore.
     */
    private fun parseYearlyTotals(rootObj: JsonObject): List<YearlyTotalBackupEntry> {
        if (!rootObj.has("yearlySummaries") || !rootObj.get("yearlySummaries").isJsonArray) {
            return emptyList()
        }
        val result = mutableListOf<YearlyTotalBackupEntry>()
        for (element in rootObj.getAsJsonArray("yearlySummaries")) {
            if (!element.isJsonObject) continue
            val obj = element.asJsonObject
            val periodLabel = obj.optString("periodLabel")
            // Without a period there is no way to tell whether the total is still current,
            // and applying it blindly is exactly what the label guard exists to prevent.
            if (periodLabel.isBlank()) continue
            result.add(
                YearlyTotalBackupEntry(
                    filterId = obj.optLong("filterId"),
                    filterName = obj.optString("filterName"),
                    periodLabel = periodLabel,
                    periodStartTimestamp = obj.optLong("periodStartTimestamp"),
                    totalAmount = obj.optLong("totalAmount")
                )
            )
        }
        return result
    }

    private fun JsonObject.optString(key: String, default: String = ""): String =
        if (has(key) && !get(key).isJsonNull) runCatching { get(key).asString }.getOrDefault(default) else default

    private fun JsonObject.optLong(key: String, default: Long = 0L): Long =
        if (has(key) && !get(key).isJsonNull) runCatching { get(key).asLong }.getOrDefault(default) else default

    private fun Filter.withoutSummationAmounts(): Filter {
        return copy(
            monthlyTotal = 0L,
            yearlyTotal = 0L,
            lastMonthlyResetTime = 0L,
            lastYearlyResetTime = 0L
        )
    }

    private fun parseFilterFromJsonObject(obj: JsonObject, defaultOrder: Int = 0): Filter {
        val id = if (obj.has("id") && !obj.get("id").isJsonNull) obj.get("id").asLong else 0L
        val name = if (obj.has("name") && !obj.get("name").isJsonNull) obj.get("name").asString else ""

        // Target packages migration (array or single string)
        val targetPackageNames = when {
            obj.has("targetPackageNames") && obj.get("targetPackageNames").isJsonArray -> {
                obj.getAsJsonArray("targetPackageNames").mapNotNull { if (it.isJsonPrimitive) it.asString else null }
            }
            obj.has("targetPackageName") && !obj.get("targetPackageName").isJsonNull -> {
                val singlePkg = obj.get("targetPackageName").asString
                if (singlePkg.isBlank()) emptyList() else singlePkg.split("|||").map { it.trim() }
            }
            else -> emptyList()
        }

        // App names migration (array or single string)
        val appNames = when {
            obj.has("appNames") && obj.get("appNames").isJsonArray -> {
                obj.getAsJsonArray("appNames").mapNotNull { if (it.isJsonPrimitive) it.asString else null }
            }
            obj.has("appName") && !obj.get("appName").isJsonNull -> {
                val singleAppName = obj.get("appName").asString
                if (singleAppName.isBlank()) emptyList() else singleAppName.split("|||", ",").map { it.trim() }
            }
            else -> emptyList()
        }

        // Keywords migration (array or string)
        val keywords = when {
            obj.has("keywords") && obj.get("keywords").isJsonArray -> {
                obj.getAsJsonArray("keywords").mapNotNull { if (it.isJsonPrimitive) it.asString else null }
            }
            obj.has("keywords") && obj.get("keywords").isJsonPrimitive -> {
                val str = obj.get("keywords").asString
                if (str.isBlank()) emptyList() else str.split("|||").map { it.trim() }
            }
            else -> emptyList()
        }

        // Exclude keywords migration (array or string)
        val excludeKeywords = when {
            obj.has("excludeKeywords") && obj.get("excludeKeywords").isJsonArray -> {
                obj.getAsJsonArray("excludeKeywords").mapNotNull { if (it.isJsonPrimitive) it.asString else null }
            }
            obj.has("excludeKeywords") && obj.get("excludeKeywords").isJsonPrimitive -> {
                val str = obj.get("excludeKeywords").asString
                if (str.isBlank()) emptyList() else str.split("|||").map { it.trim() }
            }
            else -> emptyList()
        }

        val keywordLogicStr = if (obj.has("keywordLogic") && !obj.get("keywordLogic").isJsonNull) obj.get("keywordLogic").asString else "OR"
        val keywordLogic = runCatching { KeywordLogic.valueOf(keywordLogicStr) }.getOrDefault(KeywordLogic.OR)

        val recipientPhoneNumber = if (obj.has("recipientPhoneNumber") && !obj.get("recipientPhoneNumber").isJsonNull) obj.get("recipientPhoneNumber").asString else ""
        val messageTemplate = if (obj.has("messageTemplate") && !obj.get("messageTemplate").isJsonNull) obj.get("messageTemplate").asString else "[%na%] %pni%\n%mb%\n시간: %rt%"
        val isActive = if (obj.has("isActive") && !obj.get("isActive").isJsonNull) obj.get("isActive").asBoolean else true

        // Summation fields migration with current structure defaults
        val isSummationEnabled = if (obj.has("isSummationEnabled") && !obj.get("isSummationEnabled").isJsonNull) obj.get("isSummationEnabled").asBoolean else false
        val startMonthOffset = if (obj.has("startMonthOffset") && !obj.get("startMonthOffset").isJsonNull) obj.get("startMonthOffset").asInt else 0
        val startDayType = if (obj.has("startDayType") && !obj.get("startDayType").isJsonNull) obj.get("startDayType").asString else "SPECIFIC_DAY"
        val startDayValue = if (obj.has("startDayValue") && !obj.get("startDayValue").isJsonNull) obj.get("startDayValue").asInt else 1
        val endMonthOffset = if (obj.has("endMonthOffset") && !obj.get("endMonthOffset").isJsonNull) obj.get("endMonthOffset").asInt else 0
        val endDayType = if (obj.has("endDayType") && !obj.get("endDayType").isJsonNull) obj.get("endDayType").asString else "SPECIFIC_DAY"
        val endDayValue = if (obj.has("endDayValue") && !obj.get("endDayValue").isJsonNull) obj.get("endDayValue").asInt else 31
        // Summation amounts are never restored, even if an older backup file happens to
        // contain them (see createBackupJson) — a restore should never resurrect stale totals.
        val monthlyTotal = 0L
        val yearlyTotal = 0L
        val lastMonthlyResetTime = 0L
        val lastYearlyResetTime = 0L
        val displayOrder = if (obj.has("displayOrder") && !obj.get("displayOrder").isJsonNull) obj.get("displayOrder").asInt else defaultOrder

        return Filter(
            id = id,
            name = name,
            targetPackageNames = targetPackageNames,
            appNames = appNames,
            keywords = keywords,
            excludeKeywords = excludeKeywords,
            keywordLogic = keywordLogic,
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
}
