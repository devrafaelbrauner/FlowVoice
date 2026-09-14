package dev.rafaelbrauner.flowvoice.ui.screens.notes

import java.util.Calendar
import java.util.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

class NoteTimeFormatTest {
    private val zone = TimeZone.getTimeZone("America/Sao_Paulo")

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance(zone).apply {
            clear()
            set(year, month - 1, day, hour, minute)
        }.timeInMillis

    private val now = at(2026, 9, 13, 21, 30)

    @Test
    fun sameDayShowsHourAndMinute() {
        assertEquals("09:41", NoteTimeFormat.label(at(2026, 9, 13, 9, 41), now, zone))
        assertEquals("00:05", NoteTimeFormat.label(at(2026, 9, 13, 0, 5), now, zone))
    }

    @Test
    fun previousCalendarDayIsOntemEvenLessThan24HoursAgo() {
        assertEquals("ontem", NoteTimeFormat.label(at(2026, 9, 12, 23, 50), at(2026, 9, 13, 0, 10), zone))
    }

    @Test
    fun lastWeekUsesWeekdayAbbreviation() {
        assertEquals("seg", NoteTimeFormat.label(at(2026, 9, 7, 10, 0), now, zone))
        assertEquals("qui", NoteTimeFormat.label(at(2026, 9, 10, 10, 0), now, zone))
    }

    @Test
    fun olderDatesUseDayMonthAndYearWhenDifferent() {
        assertEquals("01/09", NoteTimeFormat.label(at(2026, 9, 1, 10, 0), now, zone))
        assertEquals("24/12/25", NoteTimeFormat.label(at(2025, 12, 24, 10, 0), now, zone))
    }

    @Test
    fun futureTimestampFallsBackToTime() {
        assertEquals("22:00", NoteTimeFormat.label(at(2026, 9, 13, 22, 0), now, zone))
    }
}
