package com.example.smsforwarder.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat

object ContactUtils {

    /**
     * Look up display name from phone contacts for [phoneNumber].
     * Returns contact name if found, or null if not found or permission denied.
     */
    fun getContactName(context: Context, phoneNumber: String): String? {
        if (phoneNumber.isBlank()) return null

        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) return null

        return try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(phoneNumber)
            )
            val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        val name = cursor.getString(nameIndex)
                        if (!name.isNullOrBlank()) return name
                    }
                }
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Formats phone number with contact name if available.
     * Example: "01012345678 (홍길동)" or "01012345678"
     */
    fun getFormattedNumberWithContact(context: Context, phoneNumber: String): String {
        if (phoneNumber.isBlank()) return "미지정"
        val name = getContactName(context, phoneNumber)
        return if (!name.isNullOrBlank()) {
            "$phoneNumber ($name)"
        } else {
            phoneNumber
        }
    }
}
