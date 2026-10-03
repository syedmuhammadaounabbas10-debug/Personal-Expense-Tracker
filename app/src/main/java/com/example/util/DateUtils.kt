package com.example.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object DateUtils {
    val KARACHI_TIMEZONE: TimeZone = TimeZone.getTimeZone("Asia/Karachi")

    private val monthFormat = SimpleDateFormat("yyyy-MM", Locale.US).apply {
        timeZone = KARACHI_TIMEZONE
    }

    private val displayDateFormat = SimpleDateFormat("dd MMM yyyy", Locale.US).apply {
        timeZone = KARACHI_TIMEZONE
    }

    private val displayMonthYearFormat = SimpleDateFormat("MMMM yyyy", Locale.US).apply {
        timeZone = KARACHI_TIMEZONE
    }

    private val displayTimeFormat = SimpleDateFormat("hh:mm a", Locale.US).apply {
        timeZone = KARACHI_TIMEZONE
    }

    private val shortDayFormat = SimpleDateFormat("EEE", Locale.US).apply {
        timeZone = KARACHI_TIMEZONE
    }

    fun getCurrentMonth(): String {
        return monthFormat.format(Date())
    }

    fun formatMonthString(monthStr: String): String {
        return try {
            val date = monthFormat.parse(monthStr)
            if (date != null) displayMonthYearFormat.format(date) else monthStr
        } catch (e: Exception) {
            monthStr
        }
    }

    fun formatDate(timestampMillis: Long): String {
        return displayDateFormat.format(Date(timestampMillis))
    }

    fun formatTime(timestampMillis: Long): String {
        return displayTimeFormat.format(Date(timestampMillis))
    }

    fun formatRelativeDate(timestampMillis: Long): String {
        val nowCal = Calendar.getInstance(KARACHI_TIMEZONE)
        val targetCal = Calendar.getInstance(KARACHI_TIMEZONE).apply {
            timeInMillis = timestampMillis
        }

        val isSameDay = nowCal.get(Calendar.YEAR) == targetCal.get(Calendar.YEAR) &&
                nowCal.get(Calendar.DAY_OF_YEAR) == targetCal.get(Calendar.DAY_OF_YEAR)

        if (isSameDay) return "Today"

        nowCal.add(Calendar.DAY_OF_YEAR, -1)
        val isYesterday = nowCal.get(Calendar.YEAR) == targetCal.get(Calendar.YEAR) &&
                nowCal.get(Calendar.DAY_OF_YEAR) == targetCal.get(Calendar.DAY_OF_YEAR)

        if (isYesterday) return "Yesterday"

        return displayDateFormat.format(Date(timestampMillis))
    }

    fun getMonthRange(monthStr: String): Pair<Long, Long> {
        val cal = Calendar.getInstance(KARACHI_TIMEZONE)
        val parts = monthStr.split("-")
        val year = parts.getOrNull(0)?.toIntOrNull() ?: cal.get(Calendar.YEAR)
        val month = (parts.getOrNull(1)?.toIntOrNull() ?: (cal.get(Calendar.MONTH) + 1)) - 1

        cal.set(Calendar.YEAR, year)
        cal.set(Calendar.MONTH, month)
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis

        val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        cal.set(Calendar.DAY_OF_MONTH, maxDay)
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        val end = cal.timeInMillis

        return Pair(start, end)
    }

    fun getWeekRange(currentTimeMillis: Long = System.currentTimeMillis()): Pair<Long, Long> {
        val cal = Calendar.getInstance(KARACHI_TIMEZONE).apply {
            timeInMillis = currentTimeMillis
            firstDayOfWeek = Calendar.MONDAY
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val start = cal.timeInMillis

        cal.add(Calendar.DAY_OF_WEEK, 6)
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        val end = cal.timeInMillis

        return Pair(start, end)
    }

    fun getTodayRange(): Pair<Long, Long> {
        val cal = Calendar.getInstance(KARACHI_TIMEZONE).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val start = cal.timeInMillis

        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        val end = cal.timeInMillis

        return Pair(start, end)
    }

    fun getDayOfWeekLabel(timestampMillis: Long): String {
        return shortDayFormat.format(Date(timestampMillis))
    }
}
