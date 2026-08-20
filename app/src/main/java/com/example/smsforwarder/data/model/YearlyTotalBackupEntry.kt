package com.example.smsforwarder.data.model

/**
 * A filter's yearly running total, tagged with the period it belongs to ("26").
 *
 * There is no yearly counterpart to filter_monthly_summaries — the yearly figure lives only
 * on the filter as yearlyTotal — so this exists purely to carry that in-progress total across
 * a restore. The label is what makes carrying it safe: the restoring side applies it only
 * when its own current yearly period matches, so a restore in a later year drops it instead
 * of resurrecting a stale total.
 */
data class YearlyTotalBackupEntry(
    val filterId: Long,
    val filterName: String,
    val periodLabel: String,
    val periodStartTimestamp: Long,
    val totalAmount: Long
)
