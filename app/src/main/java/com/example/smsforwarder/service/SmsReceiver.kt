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
                    val rawKey = MessageDeduplicator.createKey("RAW", sender, fullBody)
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

        var matchedCount = 0

        for (filter in activeFilters) {
            val isMatched = filterEvaluator.isMatch(
                filter = filter,
                packageName = FilterEvaluator.NATIVE_SMS_PACKAGE,
                title = sender,
                body = body
            )

            if (isMatched) {
                matchedCount++
                Log.i(tag, "[필터 매칭 성공] 적용 필터명: '${filter.name}' ➔ 수신자 번호: ${filter.recipientPhoneNumber}")

                val meta = NotificationMeta(
                    filterName = filter.name,
                    appName = "기본 문자",
                    title = sender,
                    body = body,
                    subText = "SMS 수신",
                    timestamp = timestamp
                )

                // 3. Template processing
                val parsedMessage = templateParser.parse(filter.messageTemplate, meta)
                Log.d(tag, "[3단계: 템플릿 가공 완료] 최종 전달 문자:\n$parsedMessage")

                // 4. Send SMS
                val sendResult = smsSender.sendSms(filter.recipientPhoneNumber, parsedMessage)

                val logEntity = LogEntity(
                    timestamp = timestamp,
                    filterName = filter.name,
                    appName = "기본 문자",
                    packageName = FilterEvaluator.NATIVE_SMS_PACKAGE,
                    rawTitle = sender,
                    rawBody = body,
                    parsedMessage = parsedMessage,
                    recipientNumber = filter.recipientPhoneNumber,
                    isSuccess = sendResult.isSuccess,
                    errorMessage = sendResult.exceptionOrNull()?.localizedMessage
                )

                database.logDao().insertLog(logEntity)
                Log.i(tag, "[4단계: 포워딩 처리 완료] 필터 '${filter.name}' SMS 전송 결과: ${if (sendResult.isSuccess) "성공" else "실패"}")
            }
        }

        if (matchedCount == 0) {
            Log.i(tag, "[필터 매칭 종료] 조건에 부합하는 필터가 존재하지 않습니다.")
        }
    }
}
