package com.example.smsforwarder.domain.parser

import android.util.Log
import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe message deduplication helper.
 * Prevents processing duplicate SMS/Notification events occurring within a specified window (default 5000ms).
 */
object MessageDeduplicator {
    private const val TAG = "MessageDeduplicator"
    private const val DEFAULT_WINDOW_MS = 5000L

    private val cache = ConcurrentHashMap<String, Long>()

    /**
     * Checks if a message with [contentKey] is a duplicate within [windowMs].
     * If not duplicate, records the current timestamp and returns false.
     * If duplicate, returns true.
     */
    @Synchronized
    fun isDuplicate(contentKey: String, windowMs: Long = DEFAULT_WINDOW_MS): Boolean {
        val currentTime = System.currentTimeMillis()

        // Purge entries older than windowMs
        cache.entries.removeIf { (currentTime - it.value) > windowMs }

        val normalizedKey = contentKey.trim().lowercase()
        val lastTime = cache[normalizedKey]

        if (lastTime != null && (currentTime - lastTime) < windowMs) {
            Log.w(TAG, "[중복 차단] ${windowMs}ms 이내 동일 메시지 감지됨: key='$normalizedKey'")
            return true
        }

        cache[normalizedKey] = currentTime
        return false
    }

    /**
     * Helper to build a composite deduplication key.
     */
    fun createKey(prefix: String, identifier: String, body: String): String {
        val normalizedBody = body.replace("\\s+".toRegex(), " ").trim()
        val normalizedIdentifier = identifier.trim()
        return "$prefix:$normalizedIdentifier:$normalizedBody"
    }

    /**
     * Clear all cached keys (mainly for testing or reset).
     */
    fun clearCache() {
        cache.clear()
    }
}
