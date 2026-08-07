package com.example.smsforwarder.domain.parser

import android.util.Log
import com.example.smsforwarder.domain.model.Filter
import com.example.smsforwarder.domain.model.KeywordLogic

class FilterEvaluator {

    companion object {
        const val NATIVE_SMS_PACKAGE = "android.provider.Telephony.SMS_RECEIVED"
    }

    fun isMatch(filter: Filter, packageName: String, title: String, body: String): Boolean {
        val tag = "FilterEvaluator"

        if (!filter.isActive) {
            Log.d(tag, "[필터 스킵] '${filter.name}' (비활성화 상태)")
            return false
        }

        val isNativeSms = packageName.isBlank() ||
                packageName.equals(NATIVE_SMS_PACKAGE, ignoreCase = true) ||
                packageName.equals("SMS", ignoreCase = true) ||
                packageName.equals("기본 문자", ignoreCase = true)

        val validPackages = filter.targetPackageNames.filter { it.isNotBlank() }
        if (validPackages.isNotEmpty()) {
            val matchesPackage = validPackages.any { targetPkg ->
                if (targetPkg.equals(packageName, ignoreCase = true)) {
                    true
                } else if (isNativeSms) {
                    targetPkg.equals(NATIVE_SMS_PACKAGE, ignoreCase = true) ||
                            targetPkg.equals("기본 문자", ignoreCase = true) ||
                            targetPkg.equals("SMS", ignoreCase = true) ||
                            targetPkg.contains("messaging", ignoreCase = true) ||
                            targetPkg.contains("mms", ignoreCase = true)
                } else {
                    false
                }
            }

            if (!matchesPackage) {
                Log.d(tag, "[필터 미매칭] '${filter.name}' (대상 앱 불일치. 수신 앱: '$packageName', 필터 지정 앱: $validPackages)")
                return false
            }
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
            Log.d(tag, "[필터 미매칭] '${filter.name}' (${filter.keywordLogic} 포착 키워드 미충족: $validKeywords)")
            return false
        }

        // Exclusion Keywords Check (포착되었더라도 제외 키워드가 포함되어 있으면 최종 미매칭 처리)
        val validExcludeKeywords = filter.excludeKeywords.map { it.trim() }.filter { it.isNotBlank() }
        if (validExcludeKeywords.isNotEmpty()) {
            val matchedExcludeKeyword = validExcludeKeywords.firstOrNull { excludeKw ->
                combinedContent.contains(excludeKw, ignoreCase = true)
            }
            if (matchedExcludeKeyword != null) {
                Log.i(tag, "[필터 스킵/전송 안함] '${filter.name}' (포착 키워드 만족했으나 제외 키워드 '$matchedExcludeKeyword' 포함됨)")
                return false
            }
        }

        Log.d(tag, "[필터 매칭 성공] '${filter.name}' (포착 조건 충족 및 제외 키워드 미포함)")
        return true
    }
}
