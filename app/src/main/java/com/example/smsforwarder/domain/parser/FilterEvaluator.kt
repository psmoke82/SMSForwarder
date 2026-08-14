package com.example.smsforwarder.domain.parser

import android.util.Log
import com.example.smsforwarder.domain.model.Filter
import com.example.smsforwarder.domain.model.KeywordLogic

class FilterEvaluator {

    companion object {
        const val NATIVE_SMS_PACKAGE = "android.provider.Telephony.SMS_RECEIVED"

        private inline fun logD(tag: String, msg: String) {
            runCatching { Log.d(tag, msg) }
        }

        private inline fun logI(tag: String, msg: String) {
            runCatching { Log.i(tag, msg) }
        }
    }

    fun isMatch(filter: Filter, packageName: String, title: String, body: String): Boolean {
        val tag = "FilterEvaluator"

        if (!filter.isActive) {
            logD(tag, "[필터 스킵] '${filter.name}' (비활성화 상태)")
            return false
        }

        val isNativeSms = packageName.isBlank() ||
                packageName.equals(NATIVE_SMS_PACKAGE, ignoreCase = true) ||
                packageName.equals("SMS", ignoreCase = true) ||
                packageName.equals("기본 문자", ignoreCase = true) ||
                packageName.equals("메시지", ignoreCase = true)

        val validPackages = filter.targetPackageNames.filter { it.isNotBlank() }
        if (validPackages.isNotEmpty()) {
            val matchesPackage = validPackages.any { targetPkg ->
                if (targetPkg.equals(packageName, ignoreCase = true)) {
                    true
                } else if (isNativeSms) {
                    targetPkg.equals(NATIVE_SMS_PACKAGE, ignoreCase = true) ||
                            targetPkg.equals("기본 문자", ignoreCase = true) ||
                            targetPkg.equals("메시지", ignoreCase = true) ||
                            targetPkg.equals("SMS", ignoreCase = true) ||
                            targetPkg.contains("messaging", ignoreCase = true) ||
                            targetPkg.contains("mms", ignoreCase = true)
                } else {
                    false
                }
            }

            if (!matchesPackage) {
                logD(tag, "[필터 미매칭] '${filter.name}' (대상 앱 불일치. 수신 앱: '$packageName', 필터 지정 앱: $validPackages)")
                return false
            }
        } else if (isNativeSms) {
            // "전체 앱" 필터(대상 앱 미지정)는 앱 선택 화면에서 고를 수 있는 알림 앱들만 대상으로 하며,
            // 선택 UI에 존재하지 않는 기본 문자(SMS_RECEIVED 브로드캐스트)는 명시적으로 지정하지 않는 한 매칭에서 제외한다.
            logD(tag, "[필터 미매칭] '${filter.name}' (전체 앱 필터는 기본 문자를 자동으로 포함하지 않음)")
            return false
        }

        val validKeywords = filter.keywords.map { it.trim() }.filter { it.isNotBlank() }
        val combinedContent = "$title\n$body"

        val isKeywordMatch = if (validKeywords.isEmpty()) {
            true
        } else {
            when (filter.keywordLogic) {
                KeywordLogic.AND -> {
                    validKeywords.all { keyword ->
                        combinedContent.contains(keyword, ignoreCase = true)
                    }
                }
                KeywordLogic.OR -> {
                    validKeywords.any { keyword ->
                        combinedContent.contains(keyword, ignoreCase = true)
                    }
                }
            }
        }

        if (!isKeywordMatch) {
            logD(tag, "[필터 미매칭] '${filter.name}' (${filter.keywordLogic} 포착 키워드 미충족: $validKeywords)")
            return false
        }

        // Exclusion Keywords Check (포착되었더라도 제외 키워드가 포함되어 있으면 최종 미매칭 처리)
        val validExcludeKeywords = filter.excludeKeywords.map { it.trim() }.filter { it.isNotBlank() }
        if (validExcludeKeywords.isNotEmpty()) {
            val matchedExcludeKeyword = validExcludeKeywords.firstOrNull { excludeKw ->
                combinedContent.contains(excludeKw, ignoreCase = true)
            }
            if (matchedExcludeKeyword != null) {
                logI(tag, "[필터 스킵/전송 안함] '${filter.name}' (포착 키워드 만족했으나 제외 키워드 '$matchedExcludeKeyword' 포함됨)")
                return false
            }
        }

        logD(tag, "[필터 매칭 성공] '${filter.name}' (포착 조건 충족 및 제외 키워드 미포함)")
        return true
    }
}
