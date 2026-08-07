package com.example.smsforwarder.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.ContextCompat

class SmsSender(private val context: Context) {

    fun sendSms(destinationNumber: String, message: String): Result<Unit> {
        val tag = "SmsSender"
        val cleanedNumber = destinationNumber.replace(Regex("[^0-9+]"), "")

        if (cleanedNumber.isBlank()) {
            val errorMsg = "수신 전화번호가 올바르지 않습니다: '$destinationNumber'"
            Log.e(tag, "[SMS 전송 실패] $errorMsg")
            return Result.failure(IllegalArgumentException(errorMsg))
        }

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            val errorMsg = "SEND_SMS 런타임 권한이 승인되지 않았습니다."
            Log.e(tag, "[SMS 전송 실패] $errorMsg")
            return Result.failure(SecurityException(errorMsg))
        }

        return try {
            val smsManager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            Log.d(tag, "[SMS 전송 시도] 수신자: $cleanedNumber | 메시지 길이: ${message.length}자")

            val parts = smsManager.divideMessage(message)
            if (parts.size > 1) {
                Log.d(tag, "[SMS 전송] 분할 전송 (${parts.size}개 조각)")
                smsManager.sendMultipartTextMessage(
                    cleanedNumber,
                    null,
                    parts,
                    null,
                    null
                )
            } else {
                Log.d(tag, "[SMS 전송] 단문 전송")
                smsManager.sendTextMessage(
                    cleanedNumber,
                    null,
                    message,
                    null,
                    null
                )
            }
            Log.d(tag, "[SMS 전송 성공] $cleanedNumber 전송 완료")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(tag, "[SMS 전송 실패] $cleanedNumber 전송 오류: ${e.localizedMessage}", e)
            Result.failure(e)
        }
    }
}
