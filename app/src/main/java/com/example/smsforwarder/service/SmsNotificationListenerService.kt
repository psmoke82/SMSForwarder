package com.example.smsforwarder.service

import android.app.Notification
import android.content.pm.PackageManager
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
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

class SmsNotificationListenerService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val filterEvaluator = FilterEvaluator()
    private val templateParser = TemplateParser()
    private lateinit var smsSender: SmsSender

    override fun onCreate() {
        super.onCreate()
        smsSender = SmsSender(this)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i("SmsNotificationListener", "[알림 접근 서비스 연결 성공] StatusBarNotification 감시 시작됨")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.w("SmsNotificationListener", "[알림 접근 서비스 연결 해제됨]")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val packageName = sbn.packageName
        if (packageName == applicationContext.packageName) {
            // Ignore own notifications to prevent infinite loop
            return
        }

        val notification = sbn.notification ?: return
        val extras: Bundle = notification.extras ?: return

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString() ?: ""

        val body = if (bigText.isNotBlank()) bigText else text
        if (title.isBlank() && body.isBlank()) return

        val appName = getAppName(packageName)
        val timestamp = if (sbn.postTime > 0) sbn.postTime else System.currentTimeMillis()

        // 1) General notification deduplication check
        val dedupKey = MessageDeduplicator.createKey("NOTIF", "$packageName:$title", body)
        if (MessageDeduplicator.isDuplicate(dedupKey)) {
            Log.w("SmsNotificationListener", "[중복 알림 차단] 5초 이내 동일 알림 감지됨 (앱: $appName, 제목: $title)")
            return
        }

        // 2) Cross-service deduplication with SmsReceiver
        // Body-only key: SMS sender numbers and notification titles rarely match for the
        // same physical message, so keying on the (near-)identical body text is what
        // actually lets this recognize a message SmsReceiver already processed.
        val rawKey = MessageDeduplicator.createKey("RAW", "", body)
        if (MessageDeduplicator.isDuplicate(rawKey)) {
            Log.w("SmsNotificationListener", "[SMS-알림 중복 차단] SmsReceiver에서 이미 처리된 동일 SMS 메시지 알림입니다. (앱: $appName, 제목: $title)")
            return
        }

        Log.i("SmsNotificationListener", "[1단계: 알림 감지] 앱: $appName ($packageName) | 제목: $title | 본문: $body")

        serviceScope.launch {
            try {
                processNotification(
                    packageName = packageName,
                    appName = appName,
                    title = title,
                    body = body,
                    subText = subText,
                    timestamp = timestamp
                )
            } catch (e: Exception) {
                Log.e("SmsNotificationListener", "[알림 처리 중 오류 발생]", e)
            }
        }
    }

    private suspend fun processNotification(
        packageName: String,
        appName: String,
        title: String,
        body: String,
        subText: String,
        timestamp: Long
    ) {
        val tag = "SmsNotificationListener"
        val database = AppDatabase.getInstance(applicationContext)
        val activeFilterEntities = database.filterDao().getActiveFilters()
        val activeFilters = activeFilterEntities.map { it.toDomain() }

        Log.d(tag, "[2단계: 필터 매칭 시작] 활성화된 필터 개수: ${activeFilters.size}개 (수신 앱: $packageName)")

        if (activeFilters.isEmpty()) {
            Log.w(tag, "[필터 매칭 종료] 등록되어 활성화된 필터가 없습니다.")
            return
        }

        val exchangeRateRepository = com.example.smsforwarder.data.repository.ExchangeRateRepositoryImpl(applicationContext)
        val amountExtractor = com.example.smsforwarder.domain.parser.AmountExtractor(exchangeRateRepository)

        var matchedCount = 0

        for (initialFilter in activeFilters) {
            if (filterEvaluator.isMatch(initialFilter, packageName, title, body)) {
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

                    // 새 구간이 시작되어 이전 구간이 종료되는 경우, 이전 구간의 합산액을 스냅샷으로 확정 저장
                    if (initialFilter.lastMonthlyResetTime > 0 && initialFilter.lastMonthlyResetTime < currentMonthlyCycleStart && initialFilter.monthlyTotal > 0) {
                        val prevPeriodLabel = java.text.SimpleDateFormat("yy.MM", java.util.Locale.KOREA).format(java.util.Date(initialFilter.lastMonthlyResetTime))
                        database.filterMonthlySummaryDao().restoreSummaries(
                            listOf(
                                com.example.smsforwarder.data.local.entity.FilterMonthlySummaryEntity(
                                    filterId = initialFilter.id,
                                    filterName = initialFilter.name,
                                    periodLabel = prevPeriodLabel,
                                    periodStartTimestamp = initialFilter.lastMonthlyResetTime,
                                    totalAmount = initialFilter.monthlyTotal
                                )
                            )
                        )
                    }

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
                    appName = appName,
                    title = title,
                    body = body,
                    subText = subText,
                    timestamp = timestamp,
                    monthlyTotal = currentFilter.monthlyTotal,
                    yearlyTotal = currentFilter.yearlyTotal,
                    monthlyPeriodLabel = com.example.smsforwarder.domain.parser.SummationPeriodEngine.getMonthlyPeriodLabel(currentFilter, timestamp),
                    yearlyPeriodLabel = com.example.smsforwarder.domain.parser.SummationPeriodEngine.getYearlyPeriodLabel(currentFilter, timestamp)
                )

                // 4. Template parsing
                val parsedMessage = templateParser.parse(currentFilter.messageTemplate, meta)
                Log.d(tag, "[3단계: 템플릿 가공 완료] 최종 전달 문자:\n$parsedMessage")

                // 5. Send SMS
                val sendResult = smsSender.sendSms(currentFilter.recipientPhoneNumber, parsedMessage)

                val logEntity = LogEntity(
                    timestamp = timestamp,
                    filterName = currentFilter.name,
                    appName = appName,
                    packageName = packageName,
                    rawTitle = title,
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

    private fun getAppName(packageName: String): String {
        return try {
            val pm = packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            packageName
        }
    }
}
