package com.example.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object DateUtils {

    /**
     * Formats timestamp millis into a user-friendly display string (e.g., "15 Aug 1998").
     */
    fun formatDateToDisplay(millis: Long?): String {
        if (millis == null || millis == 0L) return ""
        val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date(millis))
    }

    /**
     * Formats timestamp millis into ISO date string for database storing (e.g., "1998-08-15").
     */
    fun formatDateForFirestore(millis: Long?): String {
        if (millis == null || millis == 0L) return ""
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date(millis))
    }
}
