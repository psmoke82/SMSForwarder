package com.example.smsforwarder

import com.example.smsforwarder.domain.model.Filter
import com.example.smsforwarder.domain.parser.SummationPeriodEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class SummationPeriodEngineTest {

    @Test
    fun getClampedDay_februaryLeapYearAndNormalYear() {
        val febCal = Calendar.getInstance().apply {
            set(Calendar.YEAR, 2026) // 2026 is normal year (28 days)
            set(Calendar.MONTH, Calendar.FEBRUARY)
            set(Calendar.DAY_OF_MONTH, 1)
        }

        val day31InFeb = SummationPeriodEngine.getClampedDay(febCal, 31)
        assertEquals(28, day31InFeb)

        val leapFebCal = Calendar.getInstance().apply {
            set(Calendar.YEAR, 2028) // 2028 is leap year (29 days)
            set(Calendar.MONTH, Calendar.FEBRUARY)
            set(Calendar.DAY_OF_MONTH, 1)
        }

        val day31InLeapFeb = SummationPeriodEngine.getClampedDay(leapFebCal, 31)
        assertEquals(29, day31InLeapFeb)
    }

    @Test
    fun getClampedDay_april30Days() {
        val aprCal = Calendar.getInstance().apply {
            set(Calendar.YEAR, 2026)
            set(Calendar.MONTH, Calendar.APRIL)
            set(Calendar.DAY_OF_MONTH, 1)
        }

        val day31InApr = SummationPeriodEngine.getClampedDay(aprCal, 31)
        assertEquals(30, day31InApr)
    }

    @Test
    fun evaluateAndReset_triggersResetInNewCycle() {
        val lastMonthCal = Calendar.getInstance().apply {
            set(Calendar.YEAR, 2026)
            set(Calendar.MONTH, Calendar.JULY)
            set(Calendar.DAY_OF_MONTH, 15)
        }

        val currentMonthCal = Calendar.getInstance().apply {
            set(Calendar.YEAR, 2026)
            set(Calendar.MONTH, Calendar.AUGUST)
            set(Calendar.DAY_OF_MONTH, 5)
        }

        val filter = Filter(
            name = "테스트 필터",
            recipientPhoneNumber = "01012345678",
            messageTemplate = "%mb%",
            isSummationEnabled = true,
            startMonthOffset = 0,
            startDayType = "SPECIFIC_DAY",
            startDayValue = 1,
            monthlyTotal = 500000L,
            yearlyTotal = 3000000L,
            lastMonthlyResetTime = lastMonthCal.timeInMillis,
            lastYearlyResetTime = lastMonthCal.timeInMillis
        )

        val result = SummationPeriodEngine.evaluateAndReset(filter, currentMonthCal.timeInMillis)

        assertTrue(result.monthlyWasReset)
        assertFalse(result.yearlyWasReset)
        assertEquals(0L, result.updatedFilter.monthlyTotal)
        assertEquals(3000000L, result.updatedFilter.yearlyTotal)
    }

    @Test
    fun getMonthlyPeriodLabel_derivedFromLastResetTime() {
        val periodStartCal = Calendar.getInstance().apply {
            set(Calendar.YEAR, 2026)
            set(Calendar.MONTH, Calendar.AUGUST)
            set(Calendar.DAY_OF_MONTH, 1)
        }

        val filter = Filter(
            name = "테스트 필터",
            recipientPhoneNumber = "01012345678",
            messageTemplate = "%mb%",
            isSummationEnabled = true,
            lastMonthlyResetTime = periodStartCal.timeInMillis
        )

        assertEquals("26.08", SummationPeriodEngine.getMonthlyPeriodLabel(filter))
    }

    @Test
    fun getMonthlyPeriodLabel_fallsBackToCurrentCycleWhenUnset() {
        val filter = Filter(
            name = "테스트 필터",
            recipientPhoneNumber = "01012345678",
            messageTemplate = "%mb%",
            isSummationEnabled = true,
            startDayType = "SPECIFIC_DAY",
            startDayValue = 1,
            lastMonthlyResetTime = 0L
        )

        val referenceCal = Calendar.getInstance().apply {
            set(Calendar.YEAR, 2026)
            set(Calendar.MONTH, Calendar.AUGUST)
            set(Calendar.DAY_OF_MONTH, 15)
        }

        assertEquals("26.08", SummationPeriodEngine.getMonthlyPeriodLabel(filter, referenceCal.timeInMillis))
    }

    @Test
    fun getYearlyPeriodLabel_derivedFromLastResetTime() {
        val periodStartCal = Calendar.getInstance().apply {
            set(Calendar.YEAR, 2026)
            set(Calendar.MONTH, Calendar.JANUARY)
            set(Calendar.DAY_OF_MONTH, 1)
        }

        val filter = Filter(
            name = "테스트 필터",
            recipientPhoneNumber = "01012345678",
            messageTemplate = "%mb%",
            isSummationEnabled = true,
            lastYearlyResetTime = periodStartCal.timeInMillis
        )

        assertEquals("26", SummationPeriodEngine.getYearlyPeriodLabel(filter))
    }
}
