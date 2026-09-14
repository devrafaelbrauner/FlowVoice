package dev.rafaelbrauner.flowvoice.ui.screens.notes

import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

object NoteTimeFormat {
    private val weekdays = arrayOf("dom", "seg", "ter", "qua", "qui", "sex", "sáb")
    private const val DAY_MS = 86_400_000L

    fun label(updatedAtMs: Long, nowMs: Long, timeZone: TimeZone = TimeZone.getDefault()): String {
        val then = calendar(updatedAtMs, timeZone)
        val now = calendar(nowMs, timeZone)
        val dayDiff = dayIndex(now) - dayIndex(then)
        return when {
            dayDiff <= 0L -> String.format(
                Locale.ROOT,
                "%02d:%02d",
                then.get(Calendar.HOUR_OF_DAY),
                then.get(Calendar.MINUTE)
            )
            dayDiff == 1L -> "ontem"
            dayDiff < 7L -> weekdays[then.get(Calendar.DAY_OF_WEEK) - 1]
            then.get(Calendar.YEAR) == now.get(Calendar.YEAR) -> String.format(
                Locale.ROOT,
                "%02d/%02d",
                then.get(Calendar.DAY_OF_MONTH),
                then.get(Calendar.MONTH) + 1
            )
            else -> String.format(
                Locale.ROOT,
                "%02d/%02d/%02d",
                then.get(Calendar.DAY_OF_MONTH),
                then.get(Calendar.MONTH) + 1,
                then.get(Calendar.YEAR) % 100
            )
        }
    }

    private fun calendar(timeMs: Long, timeZone: TimeZone): Calendar =
        Calendar.getInstance(timeZone).apply { timeInMillis = timeMs }

    private fun dayIndex(calendar: Calendar): Long {
        val offset = calendar.get(Calendar.ZONE_OFFSET) + calendar.get(Calendar.DST_OFFSET)
        return Math.floorDiv(calendar.timeInMillis + offset, DAY_MS)
    }
}
