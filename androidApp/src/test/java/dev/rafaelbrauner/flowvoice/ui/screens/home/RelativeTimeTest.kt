package dev.rafaelbrauner.flowvoice.ui.screens.home

import java.util.Calendar
import java.util.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

class RelativeTimeTest {
    private val zone = TimeZone.getTimeZone("America/Sao_Paulo")
    private val now = at(2026, Calendar.SEPTEMBER, 13, 10, 0)

    @Test
    fun sameDayShowsHoursAndMinutes() {
        assertEquals("09:41", RelativeTime.format(at(2026, Calendar.SEPTEMBER, 13, 9, 41), now, zone))
        assertEquals("00:05", RelativeTime.format(at(2026, Calendar.SEPTEMBER, 13, 0, 5), now, zone))
    }

    @Test
    fun previousDayShowsYesterdayEvenLateAtNight() {
        assertEquals("ontem", RelativeTime.format(at(2026, Calendar.SEPTEMBER, 12, 23, 59), now, zone))
        assertEquals("ontem", RelativeTime.format(at(2026, Calendar.SEPTEMBER, 12, 0, 1), now, zone))
    }

    @Test
    fun lastWeekShowsAbbreviatedWeekday() {
        assertEquals("seg", RelativeTime.format(at(2026, Calendar.SEPTEMBER, 7, 18, 0), now, zone))
        assertEquals("sex", RelativeTime.format(at(2026, Calendar.SEPTEMBER, 11, 8, 0), now, zone))
    }

    @Test
    fun olderThanAWeekShowsDayAndMonth() {
        assertEquals("06/09", RelativeTime.format(at(2026, Calendar.SEPTEMBER, 6, 12, 0), now, zone))
    }

    @Test
    fun futureTimestampFallsBackToClockTime() {
        assertEquals("11:30", RelativeTime.format(at(2026, Calendar.SEPTEMBER, 13, 11, 30), now, zone))
    }

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance(zone).apply {
            clear()
            set(year, month, day, hour, minute)
        }.timeInMillis
}
