package com.example.smsforwarder.widget

import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

/**
 * Per-appWidgetId configuration keys stored in Glance's built-in
 * PreferencesGlanceStateDefinition (one Preferences DataStore instance per widget instance,
 * auto-created/cleared by Glance — no manual DataStore needed).
 */
object WidgetPreferenceKeys {
    /** Selected filter id. Absent/0L means "전체" for the count widget; always set for the amount widget. */
    val KEY_FILTER_ID = longPreferencesKey("filterId")

    /** Cached filter name, used both for display and for WidgetUpdater's per-instance match. Absent means "전체". */
    val KEY_FILTER_NAME = stringPreferencesKey("filterName")

    /** CountPeriod enum name — count widget only. */
    val KEY_PERIOD = stringPreferencesKey("period")

    /** Last successful refresh timestamp — shown in the LARGE breakpoint's footer. */
    val KEY_LAST_UPDATED = longPreferencesKey("lastUpdated")

    /** Background opacity as a 20-100 percent (never fully transparent, keeps the widget legible/tappable). Absent = 100 (opaque). */
    val KEY_BACKGROUND_ALPHA_PERCENT = intPreferencesKey("backgroundAlphaPercent")

    const val DEFAULT_BACKGROUND_ALPHA_PERCENT = 100
    const val MIN_BACKGROUND_ALPHA_PERCENT = 20
}
