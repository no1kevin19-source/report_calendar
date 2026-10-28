package com.reportcalendar.app;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class ReminderTimeTest {
    private final TimeZone zone = TimeZone.getTimeZone("Asia/Seoul");
    private long date(int year, int month, int day, int hour, int minute, TimeZone z) {
        Calendar c = Calendar.getInstance(z); c.clear(); c.set(year, month - 1, day, hour, minute);
        return c.getTimeInMillis();
    }
    @Test public void offsetsUseSavedSpinnerIndices() {
        long due = date(2026, 10, 20, 0, 0, zone);
        for (int i = 0; i < 4; i++) assertEquals(date(2026, 10, 20 - ReminderTime.DAYS[i], 9, 30, zone),
            ReminderTime.trigger(due, i, "09:30", zone));
    }
    @Test public void yearBoundary() {
        assertEquals(date(2025, 12, 31, 23, 59, zone), ReminderTime.trigger(date(2026, 1, 1, 0, 0, zone), 0, "23:59", zone));
    }
    @Test public void invalidSettingsDoNotSchedule() {
        assertEquals(-1, ReminderTime.trigger(1, 4, "12:00", zone));
        assertEquals(-1, ReminderTime.trigger(1, 0, "24:00", zone));
        assertEquals(-1, ReminderTime.trigger(1, 0, null, zone));
        assertEquals(-1, ReminderTime.trigger(0, 0, "12:00", zone));
    }
    @Test public void dstUsesCalendarNot24HourSubtraction() {
        TimeZone ny = TimeZone.getTimeZone("America/New_York");
        assertEquals(date(2026, 3, 8, 9, 30, ny), ReminderTime.trigger(date(2026, 3, 9, 0, 0, ny), 0, "09:30", ny));
    }
    @Test public void missedNewRemindersAreNotBackfilled() {
        assertFalse(ReminderTime.eligible(1000, 2000, -1, -1, zone));
    }
    @Test public void overduePendingTodayCanRecover() {
        assertTrue(ReminderTime.eligible(1000, 2000, 1000, -1, zone));
    }
    @Test public void previousDayPendingIsSkipped() {
        long old = date(2026, 10, 13, 9, 0, zone), now = date(2026, 10, 14, 8, 0, zone);
        assertFalse(ReminderTime.eligible(old, now, old, -1, zone));
    }
    @Test public void deliveredIsNotRepeated() {
        assertFalse(ReminderTime.eligible(1000, 2000, 1000, 1000, zone));
        assertTrue(ReminderTime.eligible(3000, 2000, -1, 1000, zone));
    }
}
