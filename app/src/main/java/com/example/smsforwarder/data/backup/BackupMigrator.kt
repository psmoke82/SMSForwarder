package com.example.smsforwarder.data.backup

import com.example.smsforwarder.data.model.BackupPayload
import com.example.smsforwarder.domain.model.Filter
import com.example.smsforwarder.domain.model.KeywordLogic
import com.example.smsforwarder.domain.model.MonthlySummaryEntry
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser

data class BackupImportResult(
    val filters: List<Filter>,
    val monthlySummaries: List<MonthlySummaryEntry>
)

object BackupMigrator {

    private val gson = GsonBuilder().setPrettyPrinting().create()

    /**
     * Serializes current filters into JSON, matching the current v4 schema.
     *
     * Summation amounts (monthlyTotal/yearlyTotal/reset timestamps) and the monthly-summary
     * history are deliberately NEVER written to the backup — restores typically happen long
     * after the backup was made (device loss, reinstall, etc.) purely to recover the filter
     * list/settings, so carrying stale accumulated amounts forward would just be wrong data.
     * The `monthlySummaries` parameter is accepted for source compatibility but ignored.
     */
    fun createBackupJson(filters: List<Filter>, monthlySummaries: List<MonthlySummaryEntry> = emptyList()): String {
        val sanitizedFilters = filters.map { it.withoutSummationAmounts() }
        val payload = BackupPayload(
            version = 4,
            exportedAt = System.currentTimeMillis(),
            appVersion = "1.0",
            filters = sanitizedFilters,
            monthlySummaries = emptyList()
        )
        return gson.toJson(payload)
    }

    /**
     * Parses backup JSON and migrates any older/legacy structure into the current Filter model.
     * Even if the backup file being restored is an older one that DOES contain summation
     * amounts or monthly-summary history (e.g. from before this exclusion policy), those are
     * ignored on the way back in too — restoring never resurrects stale accumulated amounts.
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

            Result.success(BackupImportResult(filters = migratedFilters, monthlySummaries = emptyList()))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

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
