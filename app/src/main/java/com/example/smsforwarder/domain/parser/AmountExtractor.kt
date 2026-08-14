package com.example.smsforwarder.domain.parser

import com.example.smsforwarder.data.repository.ExchangeRateRepository
import kotlin.math.roundToLong

data class AmountExtractionResult(
    val amountKRW: Long,
    val currencyCode: String? = null,
    val foreignAmount: Double? = null,
    val exchangeRate: Double? = null
)

class AmountExtractor(
    private val exchangeRateRepository: ExchangeRateRepository? = null
) {

    // Indicators for cumulative usage, remaining balance, or limit, which must be ignored
    private val ignoreKeywords = listOf(
        "누적", "잔액", "한도", "포인트", "잔여", "누적금액", "이용금액", "누계", "총액"
    )

    // Indicators that the matched amount is a cancellation/refund and must be subtracted
    // from the running total, even when the SMS itself doesn't include a literal minus sign.
    private val cancellationKeywords = listOf("취소", "환불")

    // ISO 4217 codes recognized by the generic 3-letter-code patterns below. Kept as a
    // whitelist (rather than matching any [A-Z]{3}) so incidental abbreviations in SMS text
    // ("ATM", "PIN", "URL", ...) never get misread as a currency.
    private val knownCurrencyCodes = setOf(
        "USD", "EUR", "JPY", "CNY", "GBP", "THB", "VND", "PHP", "IDR", "MYR", "SGD",
        "HKD", "TWD", "AUD", "CAD", "CHF", "NZD", "INR", "AED", "SAR", "RUB", "TRY",
        "MXN", "BRL", "ZAR", "DKK", "SEK", "NOK", "PLN", "CZK", "HUF", "ILS", "KWD",
        "QAR", "BHD", "OMR", "EGP", "PKR", "BDT", "LKR", "NPR", "KHR", "LAK", "MMK",
        "MOP", "BND", "FJD"
    )

    // Foreign currency symbols/codes/words to currency unit mapping (amount may carry a leading '-' for cancellations)
    private val foreignCurrencyPatterns = listOf(
        // Common symbols
        Regex("""([$€¥£])\s*(-?[0-9]+(?:,[0-9]{3})*(?:\.[0-9]{1,2})?)"""),
        Regex("""(-?[0-9]+(?:,[0-9]{3})*(?:\.[0-9]{1,2})?)\s*([$€¥£])"""),
        // Korean currency words
        Regex("""(-?[0-9]+(?:,[0-9]{3})*(?:\.[0-9]{1,2})?)\s*(달러|유로|엔)"""),
        // Any known ISO 4217 code, either side of the amount (covers USD, THB, and everything else).
        // Amount-then-code is tried first: with code-then-amount tried first instead, a trailing
        // date like "1,000.00 THB 08/04" would let the code-then-amount pattern wrongly latch onto
        // "THB 08" (treating the date as the amount) before the correct "1,000.00 THB" is ever seen.
        Regex("""(?i)\b(-?[0-9]+(?:,[0-9]{3})*(?:\.[0-9]{1,2})?)\s*([A-Z]{3})\b"""),
        Regex("""(?i)\b([A-Z]{3})\s*(-?[0-9]+(?:,[0-9]{3})*(?:\.[0-9]{1,2})?)\b""")
    )

    // KRW amount patterns (amount may carry a leading '-' for cancellations)
    private val krwAmountPatternWithWon = Regex("""(-?[0-9]{1,3}(?:,[0-9]{3})+|-?[0-9]+)\s*원""")
    private val krwAmountPatternWithContext = Regex("""(-?[0-9]{1,3}(?:,[0-9]{3})+|-?[0-9]+)\s*(?:일시불|승인|결제)""")

    suspend fun extractAmountInKRW(text: String): Long {
        return extractDetailed(text).amountKRW
    }

    suspend fun extractDetailed(text: String): AmountExtractionResult {
        if (text.isBlank()) return AmountExtractionResult(amountKRW = 0L)

        // 1. Check for Foreign Currency payment first
        val foreignResult = tryExtractForeignCurrency(text)
        if (foreignResult != null) {
            val (amount, code) = foreignResult
            val rate = exchangeRateRepository?.getRateToKRW(code) ?: getFallbackRate(code)
            val krw = (amount * rate).roundToLong()
            return AmountExtractionResult(
                amountKRW = krw,
                currencyCode = code,
                foreignAmount = amount,
                exchangeRate = rate
            )
        }

        // 2. Check for KRW payment
        return AmountExtractionResult(amountKRW = extractKRWAmount(text))
    }

    private fun tryExtractForeignCurrency(text: String): Pair<Double, String>? {
        for (pattern in foreignCurrencyPatterns) {
            for (match in pattern.findAll(text)) {
                if (isSurroundedByIgnoreKeyword(text, match.range.first, match.range.last)) {
                    continue
                }
                val groupValues = match.groupValues
                val looksNumeric = { s: String -> s.toDoubleOrNull() != null || s.contains(",") || s.startsWith("-") }
                val (rawCurrencyToken, amountStr) = if (looksNumeric(groupValues[1])) {
                    Pair(groupValues[2], groupValues[1])
                } else {
                    Pair(groupValues[1], groupValues[2])
                }

                // Generic 3-letter-code matches must be a recognized ISO code — otherwise this
                // is just an incidental abbreviation next to a number, not a currency amount.
                val isPlainAlphaCode = rawCurrencyToken.length == 3 && rawCurrencyToken.all { it.isLetter() }
                if (isPlainAlphaCode && rawCurrencyToken.uppercase() !in knownCurrencyCodes) {
                    continue
                }

                val code = normalizeCurrencyCode(rawCurrencyToken)
                var cleanAmount = amountStr.replace(",", "").toDoubleOrNull()
                if (cleanAmount != null && cleanAmount != 0.0) {
                    if (cleanAmount > 0 && isSurroundedByCancellationKeyword(text, match.range.first, match.range.last)) {
                        cleanAmount = -cleanAmount
                    }
                    return Pair(cleanAmount, code)
                }
            }
        }
        return null
    }

    private fun extractKRWAmount(text: String): Long {
        // Match pattern 1: "15,400원"
        for (match in krwAmountPatternWithWon.findAll(text)) {
            if (!isSurroundedByIgnoreKeyword(text, match.range.first, match.range.last)) {
                val amountStr = match.groupValues[1].replace(",", "")
                var amount = amountStr.toLongOrNull()
                if (amount != null && amount != 0L) {
                    if (amount > 0 && isSurroundedByCancellationKeyword(text, match.range.first, match.range.last)) {
                        amount = -amount
                    }
                    return amount
                }
            }
        }

        // Match pattern 2: "15,400 일시불" or "15,400 승인"
        for (match in krwAmountPatternWithContext.findAll(text)) {
            if (!isSurroundedByIgnoreKeyword(text, match.range.first, match.range.last)) {
                val amountStr = match.groupValues[1].replace(",", "")
                var amount = amountStr.toLongOrNull()
                if (amount != null && amount != 0L) {
                    if (amount > 0 && isSurroundedByCancellationKeyword(text, match.range.first, match.range.last)) {
                        amount = -amount
                    }
                    return amount
                }
            }
        }

        return 0L
    }

    private fun isSurroundedByIgnoreKeyword(text: String, startIdx: Int, endIdx: Int): Boolean {
        // Only look within the same line as the match — payment SMS commonly puts
        // "누적/잔액" totals on their own line, which must not disqualify the actual
        // payment amount on a preceding/following line.
        val lineStart = (text.lastIndexOf('\n', (startIdx - 1).coerceAtLeast(0)) + 1).coerceAtLeast(0)
        val lineEndCandidate = text.indexOf('\n', endIdx)
        val lineEnd = if (lineEndCandidate == -1) text.length else lineEndCandidate

        // Check window before match (up to 20 chars before, same line only)
        val prefixStart = (startIdx - 20).coerceAtLeast(lineStart)
        val prefix = text.substring(prefixStart, startIdx)

        // Check window after match (up to 15 chars after, same line only)
        val suffixEnd = (endIdx + 15).coerceAtMost(lineEnd)
        val suffix = text.substring(endIdx, suffixEnd)

        // If prefix or suffix contains any ignore keyword, skip match
        for (keyword in ignoreKeywords) {
            if (prefix.contains(keyword) || suffix.contains(keyword)) {
                return true
            }
        }
        return false
    }

    private fun isSurroundedByCancellationKeyword(text: String, startIdx: Int, endIdx: Int): Boolean {
        // Same-line window check, mirroring isSurroundedByIgnoreKeyword, but used to flip
        // an otherwise-positive amount to negative (e.g. "15,400원 승인취소").
        val lineStart = (text.lastIndexOf('\n', (startIdx - 1).coerceAtLeast(0)) + 1).coerceAtLeast(0)
        val lineEndCandidate = text.indexOf('\n', endIdx)
        val lineEnd = if (lineEndCandidate == -1) text.length else lineEndCandidate

        val prefixStart = (startIdx - 20).coerceAtLeast(lineStart)
        val prefix = text.substring(prefixStart, startIdx)

        val suffixEnd = (endIdx + 15).coerceAtMost(lineEnd)
        val suffix = text.substring(endIdx, suffixEnd)

        for (keyword in cancellationKeywords) {
            if (prefix.contains(keyword) || suffix.contains(keyword)) {
                return true
            }
        }
        return false
    }

    private fun normalizeCurrencyCode(symbolOrCode: String): String {
        return when (symbolOrCode.uppercase()) {
            "$", "USD", "달러" -> "USD"
            "€", "EUR", "유로" -> "EUR"
            "¥", "JPY", "엔" -> "JPY"
            else -> symbolOrCode.uppercase()
        }
    }

    private fun getFallbackRate(code: String): Double {
        // Only used when no ExchangeRateRepository is supplied (e.g. unit tests) —
        // production code always goes through ExchangeRateRepositoryImpl's live-rate lookup.
        return fallbackRatesKRW[code.uppercase()] ?: 1350.0
    }

    companion object {
        // Rough KRW-per-unit reference rates, used purely as a last-resort fallback.
        val fallbackRatesKRW = mapOf(
            "USD" to 1420.0, "EUR" to 1638.0, "JPY" to 8.92, "CNY" to 210.6, "GBP" to 1916.0,
            "THB" to 39.0, "VND" to 0.056, "PHP" to 24.0, "IDR" to 0.088, "MYR" to 300.0,
            "SGD" to 1050.0, "HKD" to 182.0, "TWD" to 44.0, "AUD" to 930.0, "CAD" to 1030.0,
            "CHF" to 1650.0, "NZD" to 850.0, "INR" to 17.0, "AED" to 387.0, "SAR" to 379.0,
            "RUB" to 14.0, "TRY" to 40.0, "MXN" to 70.0, "BRL" to 260.0, "ZAR" to 75.0,
            "DKK" to 205.0, "SEK" to 135.0, "NOK" to 130.0, "PLN" to 355.0, "CZK" to 60.0,
            "HUF" to 3.8, "ILS" to 385.0, "KWD" to 4600.0, "QAR" to 390.0, "BHD" to 3770.0,
            "OMR" to 3690.0, "EGP" to 28.0, "PKR" to 5.0, "BDT" to 12.0, "LKR" to 4.6,
            "NPR" to 10.5, "KHR" to 0.35, "LAK" to 0.065, "MMK" to 0.65, "MOP" to 177.0,
            "BND" to 1050.0, "FJD" to 630.0
        )
    }
}
