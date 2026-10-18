package com.autoclicker.timed.utils

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object TimeUtils {

    private val dateTimeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    private val simpleDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val simpleTimeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    fun formatFullDateTime(millis: Long): String {
        return dateTimeFormat.format(Date(millis))
    }

    fun formatDate(millis: Long): String {
        return simpleDateFormat.format(Date(millis))
    }

    fun formatTime(millis: Long): String {
        return simpleTimeFormat.format(Date(millis))
    }

    /**
     * Parse epoch timestamp string. Automatically distinguishes between seconds (10 digits)
     * and milliseconds (13 digits).
     */
    fun parseEpoch(input: String): Long? {
        val trimmed = input.trim()
        val num = trimmed.toLongOrNull() ?: return null
        return if (trimmed.length <= 10) {
            num * 1000L // convert seconds to milliseconds
        } else {
            num // already milliseconds
        }
    }

    fun formatRemaining(remainingMillis: Long): String {
        if (remainingMillis <= 0) return "00:00:00.000 (已到達)"
        val hours = remainingMillis / (1000 * 60 * 60)
        val minutes = (remainingMillis / (1000 * 60)) % 60
        val seconds = (remainingMillis / 1000) % 60
        val millis = remainingMillis % 1000
        return String.format(Locale.getDefault(), "%02d:%02d:%02d.%03d", hours, minutes, seconds, millis)
    }
}
