package com.example.smsforwarder.widget

/** Intent-extra keys used when a widget tap launches MainActivity, so it can navigate straight to the right screen/filter. */
object WidgetNavKeys {
    const val EXTRA_TARGET = "widget_target"
    const val EXTRA_FILTER_NAME = "widget_filter_name"
    const val EXTRA_FILTER_ID = "widget_filter_id"

    const val TARGET_LOG_LIST = "log_list"
    const val TARGET_MONTHLY_SUMMARY = "monthly_summary"
}
