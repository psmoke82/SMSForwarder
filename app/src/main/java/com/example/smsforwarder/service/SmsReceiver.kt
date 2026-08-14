package com.example.smsforwarder.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.example.smsforwarder.data.local.AppDatabase
import com.example.smsforwarder.data.local.entity.LogEntity
import com.example.smsforwarder.domain.parser.FilterEvaluator
import com.example.smsforwarder.domain.parser.MessageDeduplicator
import com.example.smsforwarder.domain.parser.NotificationMeta
import com.example.smsforwarder.domain.parser.TemplateParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {

    private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val filterEvaluator = FilterEvaluator()
    private val templateParser = TemplateParser()

    override fun onReceive(context: Context, intent: Intent) {
        val tag = "SmsReceiver"
        val action = intent.action
        Log.d(tag, "[수신 이벤드] Action: $action")

        if (action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION ||
            action == "android.provider.Telephony.SMS_RECEIVED"
        ) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            if (messages.isNullOrEmpty()) {
                Log.w(tag, "[수신 실패] Intent에서 PDU 메시지를 파싱할 수 없습니다.")
                return
            }

            val sender = messages[0].originatingAddress ?: "알 수 없음"
            val fullBody = messages.joinToString("") { it.messageBody ?: "" }
            val timestamp = if (messages[0].timestampMillis > 0) messages[0].timestampMillis else System.currentTimeMillis()

            Log.i(tag, "[1단계: SMS 수신 감지] 발신자: $sender | 메시지 길이: ${fullBody.length}자 | 본문: $fullBody")

            val pendingResult = goAsync()

            receiverScope.launch {
                try {
                    val dedupKey = MessageDeduplicator.createKey("SMS", sender, fullBody)
                    if (MessageDeduplicator.isDuplicate(dedupKey)) {
                        Log.w(tag, "[SMS 수신 중복 차단] 5초 이내 동일 SMS 감지됨. 발신자: $sender")
                        return@launch
                    }
                    // Cross-service key is body-only: SMS sender numbers and notification
                    // titles rarely match for the same physical message, so keying on the
                    // (near-)identical body text is what actually lets SmsNotificationListenerService
                    // recognize this as the same message and skip re-processing it.
                    val rawKey = MessageDeduplicator.createKey("RAW", "", fullBody)
                    MessageDeduplicator.isDuplicate(rawKey)

                    processIncomingSms(
                        context = context,
                        sender = sender,
                        body = fullBody,
                        timestamp = timestamp
                    )
                } catch (e: Exception) {
                    Log.e(tag, "[SMS 처리 중 오류 발생]", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    private suspend fun processIncomingSms(
        context: Context,
        sender: String,
        body: String,
        timestamp: Long
    ) {
        val tag = "SmsReceiver"
        val database = AppDatabase.getInstance(context)
        val activeFilterEntities = database.filterDao().getActiveFilters()
        val activeFilters = activeFilterEntities.map { it.toDomain() }
        val smsSender = SmsSender(context)

        Log.d(tag, "[2단계: 필터 매칭 시작] 활성화된 필터 개수: ${activeFilters.size}개")

        if (activeFilters.isEmpty()) {
            Log.w(tag, "[필터 매칭 종료] 등록되어 활성화된 필터가 없습니다.")
            return
        }

        val exchangeRateRepository = com.example.smsforwarder.data.repository.ExchangeRateRepositoryImpl(context)
        val amountExtractor = com.example.smsforwarder.domain.parser.AmountExtractor(exchangeRateRepository)

        var matchedCount = 0

        for (initialFilter in activeFilters) {
            val isMatched = filterEvaluator.isMatch(
                filter = initialFilter,
                packageName = FilterEvaluator.NATIVE_SMS_PACKAGE,
                title = sender,
                body = body
            )

            if (isMatched) {
                matchedCount++
                Log.i(tag, "[필터 매칭 성공] 적용 필터명: '${initialFilter.name}' ➔ 수신자 번호: ${initialFilter.recipientPhoneNumber}")

                // 1~3. Amount Extraction & Atomic Period-Reset + Accumulation
                var currentFilter = initialFilter
                var extractionResult = com.example.smsforwarder.domain.parser.AmountExtractionResult(amountKRW = 0L)
                if (initialFilter.isSummationEnabled) {
                    extractionResult = amountExtractor.extractDetailed(body)
                    val engine = com.example.smsforwarder.domain.parser.SummationPeriodEngine
                    val eventCal = java.util.Calendar.getInstance().apply {
                        timeInMillis = timestamp
                        set(java.util.Calendar.HOUR_OF_DAY, 0)
                        set(java.util.Calendar.MINUTE, 0)
                        set(java.util.Calendar.SECOND, 0)
                        set(java.util.Calendar.MILLISECOND, 0)
                    }
                    val currentMonthlyCycleStart = engine.getMonthlyCycleStartTimestamp(eventCal, initialFilter)
                    val currentYearlyCycleStart = engine.getYearlyCycleStartTimestamp(timestamp)
                    // Atomic DB-side reset-check + increment — avoids the lost-update race
                    // that a separate read/compute/write would have if two matching
                    // messages for this filter arrive at nearly the same time.
                    database.filterDao().applySummationDelta(
                        filterId = initialFilter.id,
                        amountDelta = extractionResult.amountKRW,
                        currentMonthlyCycleStart = currentMonthlyCycleStart,
                        currentYearlyCycleStart = currentYearlyCycleStart
                    )
                    currentFilter = database.filterDao().getFilterById(initialFilter.id)?.toDomain() ?: initialFilter
                    if (extractionResult.amountKRW != 0L) {
                        Log.i(tag, "[금액 합산 적용] 필터: '${currentFilter.name}' | 결제액: ${extractionResult.amountKRW}원 | 당월 합계: ${currentFilter.monthlyTotal}원")
                        val periodLabel = java.text.SimpleDateFormat("yy.MM", java.util.Locale.KOREA).format(java.util.Date(currentMonthlyCycleStart))
                        database.filterMonthlySummaryDao().incrementAmount(
                            filterId = initialFilter.id,
                            filterName = currentFilter.name,
                            periodLabel = periodLabel,
                            periodStartTimestamp = currentMonthlyCycleStart,
                            amountDelta = extractionResult.amountKRW
                        )
                    }
                }

                val meta = NotificationMeta(
                    filterName = currentFilter.name,
                    appName = "기본 문자",
                    title = sender,
                    body = body,
                    subText = "SMS 수신",
                    timestamp = timestamp,
                    monthlyTotal = currentFilter.monthlyTotal,
                    yearlyTotal = currentFilter.yearlyTotal,
                    monthlyPeriodLabel = com.example.smsforwarder.domain.parser.SummationPeriodEngine.getMonthlyPeriodLabel(currentFilter, timestamp),
                    yearlyPeriodLabel = com.example.smsforwarder.domain.parser.SummationPeriodEngine.getYearlyPeriodLabel(currentFilter, timestamp)
                )

                // 4. Template processing
                val parsedMessage = templateParser.parse(currentFilter.messageTemplate, meta)
                Log.d(tag, "[3단계: 템플릿 가공 완료] 최종 전달 문자:\n$parsedMessage")

                // 5. Send SMS
                val sendResult = smsSender.sendSms(currentFilter.recipientPhoneNumber, parsedMessage)

                val logEntity = LogEntity(
                    timestamp = timestamp,
                    filterName = currentFilter.name,
                    appName = "기본 문자",
                    packageName = FilterEvaluator.NATIVE_SMS_PACKAGE,
                    rawTitle = sender,
                    rawBody = body,
                    parsedMessage = parsedMessage,
                    recipientNumber = currentFilter.recipientPhoneNumber,
                    isSuccess = sendResult.isSuccess,
                    errorMessage = sendResult.exceptionOrNull()?.localizedMessage,
                    isSummationEnabled = currentFilter.isSummationEnabled,
                    extractedAmountKRW = extractionResult.amountKRW,
                    originalCurrencyCode = extractionResult.currencyCode,
                    originalForeignAmount = extractionResult.foreignAmount,
                    appliedExchangeRate = extractionResult.exchangeRate
                )

                database.logDao().insertLog(logEntity)
                Log.i(tag, "[4단계: 포워딩 처리 완료] 필터 '${currentFilter.name}' SMS 전송 결과: ${if (sendResult.isSuccess) "성공" else "실패"}")
            }
        }

        if (matchedCount == 0) {
            Log.i(tag, "[필터 매칭 종료] 조건에 부합하는 필터가 존재하지 않습니다.")
        }
    }
}
