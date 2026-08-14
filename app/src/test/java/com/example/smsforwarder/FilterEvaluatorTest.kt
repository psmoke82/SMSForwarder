package com.example.smsforwarder

import com.example.smsforwarder.domain.model.Filter
import com.example.smsforwarder.domain.model.KeywordLogic
import com.example.smsforwarder.domain.parser.FilterEvaluator
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterEvaluatorTest {

    private val evaluator = FilterEvaluator()

    @Test
    fun testAndLogicMatching() {
        val filter = Filter(
            id = 1,
            name = "신한카드 결제",
            targetPackageNames = listOf("com.shinhan.smartcareb"),
            keywords = listOf("승인", "결제"),
            keywordLogic = KeywordLogic.AND,
            recipientPhoneNumber = "01012345678",
            messageTemplate = "%mb%",
            isActive = true
        )

        // Both keywords matched
        assertTrue(evaluator.isMatch(filter, "com.shinhan.smartcareb", "[신한카드] 승인", "15,000원 결제 완료"))

        // Only one keyword matched -> Should return false for AND
        assertFalse(evaluator.isMatch(filter, "com.shinhan.smartcareb", "[신한카드] 승인", "취소되었습니다"))
    }

    @Test
    fun testOrLogicMatching() {
        val filter = Filter(
            id = 2,
            name = "알림 포워딩",
            targetPackageNames = emptyList(),
            keywords = listOf("승인", "입금"),
            keywordLogic = KeywordLogic.OR,
            recipientPhoneNumber = "01012345678",
            messageTemplate = "%mb%",
            isActive = true
        )

        // One keyword matched -> Should return true for OR
        assertTrue(evaluator.isMatch(filter, "com.any.app", "계좌 입금", "100,000원이 입금되었습니다."))
        assertFalse(evaluator.isMatch(filter, "com.any.app", "광고", "할인 쿠폰 도착"))
    }

    @Test
    fun testCatchAllFilter_excludesNativeSms() {
        val filter = Filter(
            id = 4,
            name = "전체 앱 필터",
            targetPackageNames = emptyList(),
            keywords = emptyList(),
            keywordLogic = KeywordLogic.OR,
            recipientPhoneNumber = "01012345678",
            messageTemplate = "%mb%",
            isActive = true
        )

        // 대상 앱을 지정하지 않은 "전체 앱" 필터는 알림 앱만 대상으로 하며,
        // 앱 선택 화면에 존재하지 않는 기본 문자(네이티브 SMS)는 제외되어야 한다.
        assertFalse(evaluator.isMatch(filter, FilterEvaluator.NATIVE_SMS_PACKAGE, "발신자", "본문"))
        // 일반 알림 앱은 그대로 매칭되어야 한다.
        assertTrue(evaluator.isMatch(filter, "com.any.app", "발신자", "본문"))
    }

    @Test
    fun testExplicitNativeSmsTarget_stillMatches() {
        val filter = Filter(
            id = 5,
            name = "기본 문자 명시 지정",
            targetPackageNames = listOf("기본 문자"),
            keywords = emptyList(),
            keywordLogic = KeywordLogic.OR,
            recipientPhoneNumber = "01012345678",
            messageTemplate = "%mb%",
            isActive = true
        )

        assertTrue(evaluator.isMatch(filter, FilterEvaluator.NATIVE_SMS_PACKAGE, "발신자", "본문"))
    }

    @Test
    fun testInactiveFilter() {
        val filter = Filter(
            id = 3,
            name = "비활성 필터",
            targetPackageNames = emptyList(),
            keywords = emptyList(),
            keywordLogic = KeywordLogic.AND,
            recipientPhoneNumber = "01012345678",
            messageTemplate = "%mb%",
            isActive = false
        )

        assertFalse(evaluator.isMatch(filter, "com.any.app", "제목", "본문"))
    }
}
