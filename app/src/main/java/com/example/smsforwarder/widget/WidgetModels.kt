package com.example.smsforwarder.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import java.util.Calendar

/**
 * Responsive breakpoints both widgets support, as (width, height) Glance will match against
 * the REAL bounds the launcher hands it — which on many OEM launchers (verified via
 * `adb shell dumpsys appwidget` on a Samsung OneUI device) is nowhere near the stock AOSP
 * `70dp*n - 30dp` grid formula this file used to assume. On that device a real "1x1" slot
 * measured ~72x93dp and a real "2x1" slot ~150x93dp — i.e. OneUI keeps row height generous
 * (~90dp+) even for a single row, and only grows width per extra column. Values below are
 * chosen to safely fit under those real numbers (with margin) so Responsive picks the
 * richer bucket instead of silently falling back to the sparsest one on every real device.
 * Glance's Responsive mode picks the largest declared size that fits within the actual
 * bounds on BOTH axes, so each tier here must be strictly larger than the previous in both
 * width and height for the progression to behave as intended.
 */
object WidgetSizes {
    /** Ultra-narrow fallback for launchers even more constrained than the ones this was tuned against. */
    val XSMALL = DpSize(40.dp, 40.dp)

    /** Narrow-tall shape — matches a real single-column ("1x1"-ish) slot on OneUI-style launchers. */
    val SMALL = DpSize(65.dp, 85.dp)

    /** Wide-short shape — matches a real two-column ("2x1"-ish) slot on OneUI-style launchers. */
    val MEDIUM = DpSize(140.dp, 85.dp)

    /** Bigger placements (3 columns / 2 rows or more). */
    val LARGE = DpSize(220.dp, 170.dp)
}

/** Aggregation window for the count widget. "전체"/"오늘" etc. period selection. */
enum class CountPeriod(val label: String) {
    TODAY("오늘"),
    LAST_7("최근 7일"),
    LAST_30("최근 30일"),
    ALL("전체");

    fun sinceTimestamp(referenceTimestamp: Long = System.currentTimeMillis()): Long = when (this) {
        TODAY -> Calendar.getInstance().apply {
            timeInMillis = referenceTimestamp
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        LAST_7 -> referenceTimestamp - 7L * 86_400_000L
        LAST_30 -> referenceTimestamp - 30L * 86_400_000L
        ALL -> 0L
    }
}
