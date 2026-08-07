package com.example.smsforwarder.domain.parser

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class NotificationMeta(
    val filterName: String = "",
    val appName: String = "",
    val title: String = "",
    val body: String = "",
    val subText: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

data class TemplateTag(
    val tag: String,
    val label: String,
    val description: String
)

object TemplateTags {
    val ALL = listOf(
        TemplateTag("%fn%", "필터명", "설정한 필터의 이름"),
        TemplateTag("%na%", "앱 이름", "알림 발생 앱 명칭"),
        TemplateTag("%pni%", "알림 제목", "알림 상단 제목"),
        TemplateTag("%mb%", "메시지 본문", "알림 본문 텍스트"),
        TemplateTag("%nst%", "서브 텍스트", "알림 서브/추가 텍스트"),
        TemplateTag("%rt%", "수신 시각", "전체 날짜 시각 (yyyy-MM-dd HH:mm:ss)"),
        TemplateTag("%Y%", "연도", "4자리 연도 (yyyy)"),
        TemplateTag("%M%", "월", "2자리 월 (MM)"),
        TemplateTag("%d%", "일", "2자리 일 (dd)"),
        TemplateTag("%H%", "24시", "24시간 포맷 (HH)"),
        TemplateTag("%h%", "12시", "12시간 포맷 (hh)"),
        TemplateTag("%m%", "분", "2자리 분 (mm)"),
        TemplateTag("%w%", "요일", "요일 (월~일)")
    )
}

class TemplateParser {

    fun parse(template: String, meta: NotificationMeta): String {
        val date = Date(meta.timestamp)
        val cal = Calendar.getInstance().apply { time = date }

        val yearStr = SimpleDateFormat("yyyy", Locale.getDefault()).format(date)
        val monthStr = SimpleDateFormat("MM", Locale.getDefault()).format(date)
        val dayStr = SimpleDateFormat("dd", Locale.getDefault()).format(date)
        val hour24Str = SimpleDateFormat("HH", Locale.getDefault()).format(date)
        val hour12Str = SimpleDateFormat("hh", Locale.getDefault()).format(date)
        val minuteStr = SimpleDateFormat("mm", Locale.getDefault()).format(date)
        val fullTimeStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(date)

        val dayOfWeekStr = when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.SUNDAY -> "일"
            Calendar.MONDAY -> "월"
            Calendar.TUESDAY -> "화"
            Calendar.WEDNESDAY -> "수"
            Calendar.THURSDAY -> "목"
            Calendar.FRIDAY -> "금"
            Calendar.SATURDAY -> "토"
            else -> ""
        }

        var result = template

        // Primary placeholder replacements & aliases
        result = result.replace("%fn%", meta.filterName).replace("%filterName%", meta.filterName)
        result = result.replace("%na%", meta.appName).replace("%appName%", meta.appName)
        result = result.replace("%pni%", meta.title).replace("%sender%", meta.title).replace("%title%", meta.title)
        result = result.replace("%mb%", meta.body).replace("%body%", meta.body).replace("%text%", meta.body)
        result = result.replace("%nst%", meta.subText)
        result = result.replace("%rt%", fullTimeStr)

        // Date/Time tags (support both %Y% and %Y)
        result = result.replace("%Y%", yearStr).replace("%Y", yearStr)
        result = result.replace("%M%", monthStr).replace("%M", monthStr)
        result = result.replace("%d%", dayStr).replace("%d", dayStr)
        result = result.replace("%H%", hour24Str).replace("%H", hour24Str)
        result = result.replace("%h%", hour12Str).replace("%h", hour12Str)
        result = result.replace("%m%", minuteStr).replace("%m", minuteStr)
        result = result.replace("%w%", dayOfWeekStr).replace("%w", dayOfWeekStr)

        return result
    }
}
