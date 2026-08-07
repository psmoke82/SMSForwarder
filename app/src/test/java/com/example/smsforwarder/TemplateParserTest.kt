package com.example.smsforwarder

import com.example.smsforwarder.domain.parser.NotificationMeta
import com.example.smsforwarder.domain.parser.TemplateParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class TemplateParserTest {

    private val parser = TemplateParser()

    @Test
    fun testPrimaryPlaceholdersReplacement() {
        val meta = NotificationMeta(
            filterName = "카드필터",
            appName = "신한SOL",
            title = "카드승인",
            body = "10,000원 결제완료",
            subText = "잔액 50,000원",
            timestamp = 1700000000000L
        )

        val template = "[%fn% / %na%] %pni%: %mb% (%nst%)"
        val expected = "[카드필터 / 신한SOL] 카드승인: 10,000원 결제완료 (잔액 50,000원)"

        val actual = parser.parse(template, meta)
        assertEquals(expected, actual)
    }

    @Test
    fun testDateTimePlaceholdersReplacement() {
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.AUGUST, 4, 20, 45, 30)
        }

        val meta = NotificationMeta(
            filterName = "테스트",
            appName = "앱",
            title = "제목",
            body = "본문",
            timestamp = cal.timeInMillis
        )

        val template = "날짜: %Y%년 %M%월 %d%일 (%w%) %H%:%m%"
        val actual = parser.parse(template, meta)

        assertTrue(actual.contains("2026년 08월 04일"))
        assertTrue(actual.contains("20:45"))
        assertTrue(actual.contains("화"))
    }
}
