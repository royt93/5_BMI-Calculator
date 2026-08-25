package com.samsunggalaxy.notification

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Unit coverage for EPIC-08 T08.1 (doc/task/todo/EPIC-08-engagement-features.md):
 * the pure initial-delay math behind the daily weigh-in reminder's WorkManager scheduling.
 */
class ReminderSchedulerTest {

    private fun at(hour: Int, minute: Int, second: Int = 0): Long =
        Calendar.getInstance().apply {
            set(2026, Calendar.AUGUST, 23, hour, minute, second)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    @Test
    fun laterToday_delaysUntilThatTime() {
        val now = at(7, 0)
        val delay = ReminderScheduler.computeInitialDelayMs(hour = 8, minute = 0, nowMillis = now)
        assertEquals(TimeUnit.HOURS.toMillis(1), delay)
    }

    @Test
    fun earlierToday_rollsOverToTomorrow() {
        val now = at(9, 0)
        val delay = ReminderScheduler.computeInitialDelayMs(hour = 8, minute = 0, nowMillis = now)
        assertEquals(TimeUnit.HOURS.toMillis(23), delay)
    }

    @Test
    fun exactCurrentMinute_firesImmediately() {
        val now = at(8, 0)
        val delay = ReminderScheduler.computeInitialDelayMs(hour = 8, minute = 0, nowMillis = now)
        assertEquals(0L, delay)
    }

    @Test
    fun midnightRollover_handlesDayBoundary() {
        val now = at(23, 30)
        val delay = ReminderScheduler.computeInitialDelayMs(hour = 0, minute = 0, nowMillis = now)
        assertEquals(TimeUnit.MINUTES.toMillis(30), delay)
    }
}
