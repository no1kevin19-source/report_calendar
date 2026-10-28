package com.reportcalendar.app;

import java.util.Calendar;
import java.util.TimeZone;

/** Calendar arithmetic preserves the chosen local clock time across DST/month boundaries. */
final class ReminderTime {
    static final int[] DAYS = {1, 2, 3, 7};
    static long trigger(long due, int index, String time, TimeZone zone) {
        if (due <= 0 || index < 0 || index >= DAYS.length || time == null
                || !time.matches("([01][0-9]|2[0-3]):[0-5][0-9]")) return -1;
        Calendar c = Calendar.getInstance(zone);
        c.setTimeInMillis(due);
        c.add(Calendar.DAY_OF_MONTH, -DAYS[index]);
        c.set(Calendar.HOUR_OF_DAY, Integer.parseInt(time.substring(0, 2)));
        c.set(Calendar.MINUTE, Integer.parseInt(time.substring(3)));
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }
    static boolean sameDay(long a, long b, TimeZone zone) {
        Calendar x = Calendar.getInstance(zone), y = Calendar.getInstance(zone);
        x.setTimeInMillis(a); y.setTimeInMillis(b);
        return x.get(Calendar.YEAR) == y.get(Calendar.YEAR)
            && x.get(Calendar.DAY_OF_YEAR) == y.get(Calendar.DAY_OF_YEAR);
    }
    static boolean eligible(long trigger, long now, long pending, long delivered, TimeZone zone) {
        return trigger > 0 && trigger > delivered && (trigger > now
            || (trigger == pending && sameDay(trigger, now, zone)));
    }
}
