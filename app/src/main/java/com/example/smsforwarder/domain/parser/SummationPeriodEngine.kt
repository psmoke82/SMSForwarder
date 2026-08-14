package com.example.smsforwarder.domain.parser

import com.example.smsforwarder.domain.model.Filter
import java.util.Calendar
import java.util.Date

data class SummationResetResult(
    val updatedFilter: Filter,
    val monthlyWasReset: Boolean,
    val yearlyWasReset: Boolean
)

object SummationPeriodEngine {

    /**
     * Checks if a monthly or yearly reset is required for the given filter at currentTimestamp.
     * Returns updated filter with totals reset if a new period has started.
     */
    fun evaluateAndReset(filter: Filter, currentTimestamp: Long = System.currentTimeMillis()): SummationResetResult {
        if (!filter.isSummationEnabled) {
            return SummationResetResult(filter, monthlyWasReset = false, yearlyWasReset = false)
        }

        val eventCal = Calendar.getInstance().apply {
            timeInMillis = currentTimestamp
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // 1. Calculate the Monthly Period Start Timestamp for current event
        val currentMonthlyCycleStart = getMonthlyCycleStartTimestamp(eventCal, filter)

        val monthlyResetNeeded = filter.lastMonthlyResetTime < currentMonthlyCycleStart

        // 2. Calculate the Yearly Period Start Timestamp for current event (Jan 1 00:00:00 of current year)
        val currentYearlyCycleStart = getYearlyCycleStartTimestamp(currentTimestamp)

        val yearlyResetNeeded = filter.lastYearlyResetTime < currentYearlyCycleStart

        var newMonthlyTotal = filter.monthlyTotal
        var newYearlyTotal = filter.yearlyTotal
        var newLastMonthlyResetTime = filter.lastMonthlyResetTime
        var newLastYearlyResetTime = filter.lastYearlyResetTime

        if (monthlyResetNeeded) {
            newMonthlyTotal = 0L
            newLastMonthlyResetTime = currentMonthlyCycleStart
        }

        if (yearlyResetNeeded) {
            newYearlyTotal = 0L
            newLastYearlyResetTime = currentYearlyCycleStart
        }

        val updatedFilter = filter.copy(
            monthlyTotal = newMonthlyTotal,
            yearlyTotal = newYearlyTotal,
            lastMonthlyResetTime = newLastMonthlyResetTime,
            lastYearlyResetTime = newLastYearlyResetTime
        )

        return SummationResetResult(
            updatedFilter = updatedFilter,
            monthlyWasReset = monthlyResetNeeded,
            yearlyWasReset = yearlyResetNeeded
        )
    }

    /**
     * Calculates the timestamp of Jan 1, 00:00:00 of the year containing currentTimestamp.
     */
    fun getYearlyCycleStartTimestamp(currentTimestamp: Long): Long {
        return Calendar.getInstance().apply {
            timeInMillis = currentTimestamp
            set(Calendar.MONTH, Calendar.JANUARY)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    /**
     * Calculates the timestamp of the start of the monthly period containing eventCal.
     */
    fun getMonthlyCycleStartTimestamp(eventCal: Calendar, filter: Filter): Long {
        val cal = eventCal.clone() as Calendar

        val targetDay = when (filter.startDayType) {
            "MONTH_START" -> 1
            "MONTH_END" -> getClampedDay(cal, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
            else -> getClampedDay(cal, filter.startDayValue)
        }

        // If today is before the target day of current month, cycle start is in previous month.
        // This alone is sufficient to derive the correct cycle for any start-day setting — a
        // separate 전월/당월 month offset used to be layered on top of this and double-shifted
        // the result (e.g. specific-day 14 would resolve two months back instead of one), so it
        // has been removed; the start day is now the only input that determines the cycle.
        if (cal.get(Calendar.DAY_OF_MONTH) < targetDay) {
            cal.add(Calendar.MONTH, -1)
        }

        val finalDay = when (filter.startDayType) {
            "MONTH_START" -> 1
            "MONTH_END" -> cal.getActualMaximum(Calendar.DAY_OF_MONTH)
            else -> getClampedDay(cal, filter.startDayValue)
        }

        cal.set(Calendar.DAY_OF_MONTH, finalDay)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)

        return cal.timeInMillis
    }

    /**
     * Clamps day value to the actual maximum day of the target month in calendar (e.g. 28/29 for Feb, 30 for Apr).
     */
    fun getClampedDay(cal: Calendar, targetDay: Int): Int {
        val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        return targetDay.coerceIn(1, maxDay)
    }

    /**
     * Human-readable label for the filter's current monthly period, e.g. "26.08" —
     * derived from lastMonthlyResetTime (the start of the currently-accumulating period),
     * falling back to "now"'s cycle start when a reset hasn't happened yet (lastMonthlyResetTime == 0).
     */
    fun getMonthlyPeriodLabel(filter: Filter, referenceTimestamp: Long = System.currentTimeMillis()): String {
        val periodStart = if (filter.lastMonthlyResetTime > 0) {
            filter.lastMonthlyResetTime
        } else {
            val eventCal = Calendar.getInstance().apply {
                timeInMillis = referenceTimestamp
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            getMonthlyCycleStartTimestamp(eventCal, filter)
        }
        return java.text.SimpleDateFormat("yy.MM", java.util.Locale.KOREA).format(Date(periodStart))
    }

    /**
     * Human-readable label for the filter's current yearly period, e.g. "26" —
     * derived from lastYearlyResetTime, falling back to "now"'s year when unset.
     */
    fun getYearlyPeriodLabel(filter: Filter, referenceTimestamp: Long = System.currentTimeMillis()): String {
        val periodStart = if (filter.lastYearlyResetTime > 0) {
            filter.lastYearlyResetTime
        } else {
            referenceTimestamp
        }
        return java.text.SimpleDateFormat("yy", java.util.Locale.KOREA).format(Date(periodStart))
    }
}
