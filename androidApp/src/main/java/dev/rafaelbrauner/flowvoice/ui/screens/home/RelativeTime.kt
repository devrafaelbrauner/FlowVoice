package dev.rafaelbrauner.flowvoice.ui.screens.home

import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToLong

object RelativeTime {
    private const val DAY_MS = 86_400_000L
    private val weekdays = listOf("dom", "seg", "ter", "qua", "qui", "sex", "sáb")

    fun format(timestampMs: Long, nowMs: Long, timeZone: TimeZone = TimeZone.getDefault()): String {
        val moment = calendarAt(timestampMs, timeZone)
        val elapsedDays = ((startOfDay(nowMs, timeZone) - startOfDay(timestampMs, timeZone)) / DAY_MS.toDouble())
            .roundToLong()
        return when {
            elapsedDays <= 0L -> String.format(
                Locale.ROOT,
                "%02d:%02d",
                moment.get(Calendar.HOUR_OF_DAY),
                moment.get(Calendar.MINUTE)
            )
            elapsedDays == 1L -> "ontem"
            elapsedDays < 7L -> weekdays[moment.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY]
            else -> String.format(
                Locale.ROOT,
                "%02d/%02d",
                moment.get(Calendar.DAY_OF_MONTH),
                moment.get(Calendar.MONTH) + 1
            )
        }
    }

    private fun calendarAt(timestampMs: Long, timeZone: TimeZone): Calendar =
        Calendar.getInstance(timeZone).apply { timeInMillis = timestampMs }

    private fun startOfDay(timestampMs: Long, timeZone: TimeZone): Long =
        calendarAt(timestampMs, timeZone).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
}
