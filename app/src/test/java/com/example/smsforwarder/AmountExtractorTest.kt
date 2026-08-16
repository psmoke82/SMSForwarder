package com.example.smsforwarder

import com.example.smsforwarder.data.repository.ExchangeRateRepository
import com.example.smsforwarder.domain.parser.AmountExtractor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class AmountExtractorTest {

    private lateinit var amountExtractor: AmountExtractor

    private val mockExchangeRateRepo = object : ExchangeRateRepository {
        override suspend fun getRateToKRW(currencyCode: String): Double {
            return when (currencyCode.uppercase()) {
                "USD" -> 1420.0
                "EUR" -> 1638.0
                "JPY" -> 8.92
                "CNY" -> 210.6
                "GBP" -> 1916.0
                "THB" -> 39.0
                "SGD" -> 1050.0
                else -> 1420.0
            }
        }
    }

    @Before
    fun setUp() {
        amountExtractor = AmountExtractor(mockExchangeRateRepo)
    }

    @Test
    fun extractKRWAmount_standardPayment_success() = runBlocking {
        val message = "[신한카드] 김*우 15,400원 일시불 08/04 20:45"
        val amount = amountExtractor.extractAmountInKRW(message)
        assertEquals(15400L, amount)
    }

    @Test
    fun extractKRWAmount_approvalWithComma_success() = runBlocking {
        val message = "[KB국민카드] 승인 1,250,000원 08/04 15:30 (0004)"
        val amount = amountExtractor.extractAmountInKRW(message)
        assertEquals(1250000L, amount)
    }

    @Test
    fun extractKRWAmount_ignoresCumulativeAndBalance() = runBlocking {
        val message = "[신한카드] 김*우 15,400원 일시불 08/04 20:45 체크카드 잔액 125,000원 누적 이용금액 3,500,000원"
        val amount = amountExtractor.extractAmountInKRW(message)
        assertEquals(15400L, amount)
    }

    @Test
    fun extractForeignAmount_usd_convertsToKRW() = runBlocking {
        val message = "[현대카드] 해외승인 USD 10.50 08/04 Amazon.com"
        val amount = amountExtractor.extractAmountInKRW(message)
        // 10.50 * 1420 = 14910
        assertEquals(14910L, amount)
    }

    @Test
    fun extractForeignAmount_usdWithSymbol_convertsToKRW() = runBlocking {
        val message = "[삼성카드] 해외승인 $20.00 08/04 AppStore"
        val amount = amountExtractor.extractAmountInKRW(message)
        // 20.00 * 1420 = 28400
        assertEquals(28400L, amount)
    }

    @Test
    fun extractKRWAmount_ignoresCumulativeOnNextLineOnly() = runBlocking {
        val message = "카드합산 테스트\n카드12\n일시불 200원\n누적 300원"
        val amount = amountExtractor.extractAmountInKRW(message)
        assertEquals(200L, amount)
    }

    @Test
    fun extractKRWAmount_explicitNegative_success() = runBlocking {
        val message = "카드취소 -15,400원 처리완료"
        val amount = amountExtractor.extractAmountInKRW(message)
        assertEquals(-15400L, amount)
    }

    @Test
    fun extractKRWAmount_cancellationKeywordNegatesPositive() = runBlocking {
        val message = "[신한카드] 15,400원 승인취소"
        val amount = amountExtractor.extractAmountInKRW(message)
        assertEquals(-15400L, amount)
    }

    @Test
    fun extractKRWAmount_refundKeywordNegatesPositive() = runBlocking {
        val message = "[신한카드] 15,400원 환불 처리되었습니다"
        val amount = amountExtractor.extractAmountInKRW(message)
        assertEquals(-15400L, amount)
    }

    @Test
    fun extractForeignAmount_cancellation_negatesConvertedAmount() = runBlocking {
        val message = "[현대카드] 해외승인취소 USD 10.50 08/04 Amazon.com"
        val amount = amountExtractor.extractAmountInKRW(message)
        // -10.50 * 1420 = -14910
        assertEquals(-14910L, amount)
    }

    @Test
    fun extractForeignAmount_thb_convertsToKRW() = runBlocking {
        val message = "[하나카드] 해외승인 THB 1,000.00 08/04 Bangkok"
        val amount = amountExtractor.extractAmountInKRW(message)
        // 1000 * 39 = 39000
        assertEquals(39000L, amount)
    }

    @Test
    fun extractForeignAmount_thbAmountBeforeCode_convertsToKRW() = runBlocking {
        val message = "[하나카드] 해외승인 1,000.00 THB 08/04 Bangkok"
        val amount = amountExtractor.extractAmountInKRW(message)
        assertEquals(39000L, amount)
    }

    @Test
    fun extractForeignAmount_sgd_convertsToKRW() = runBlocking {
        val message = "[우리카드] 해외승인 SGD 50.00 08/04 Singapore"
        val amount = amountExtractor.extractAmountInKRW(message)
        // 50 * 1050 = 52500
        assertEquals(52500L, amount)
    }

    @Test
    fun extractAmount_unrecognizedThreeLetterWord_notTreatedAsCurrency() = runBlocking {
        val message = "ATM 001번기에서 조회하신 정보입니다"
        val amount = amountExtractor.extractAmountInKRW(message)
        assertEquals(0L, amount)
    }

    @Test
    fun extractAmount_noPayment_returnsZero() = runBlocking {
        val message = "안녕하세요 SMSForwarder 테스트 메시지입니다."
        val amount = amountExtractor.extractAmountInKRW(message)
        assertEquals(0L, amount)
    }

    @Test
    fun extractKRWAmount_cancellationGluedToCumulativeNoSeparator_negatesTransactionAmount() = runBlocking {
        // 광주카드/광주BC 포맷: 거래금액과 "누적" 사이에 공백/줄바꿈이 없어, 누적 총액(100,052원)뿐 아니라
        // 거래금액(6,500원)까지 ignoreKeyword suffix 윈도우에 걸려 0원으로 스킵되던 회귀 케이스.
        val message = "[Web발신] [광주BC](신용7442)박상*님 06/21 13:35 일시불 취소 6,500원누적 100,052원GS수퍼수지풍덕천점"
        val amount = amountExtractor.extractAmountInKRW(message)
        assertEquals(-6500L, amount)
    }

    @Test
    fun extractKRWAmount_gluedCumulativeWithoutCancellation_extractsTransactionAmount() = runBlocking {
        val message = "[Web발신] [광주카드](신용7442)박상*님 04/22 07:52 일시불 취소 60,490원누적 221,680원(주)와이즐리컴퍼"
        val amount = amountExtractor.extractAmountInKRW(message)
        assertEquals(-60490L, amount)
    }
}
